package main

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"mime"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
)

const maxJSON = 5 << 20

var playlistURI = regexp.MustCompile(`URI="([^"]+)"`)
var allowedHeaders = regexp.MustCompile(`(?i)^(accept|authorization|origin|referer|user-agent|x-[a-z0-9-]+)$`)
var nonPublicNetworks = []netip.Prefix{
	netip.MustParsePrefix("100.64.0.0/10"),
	netip.MustParsePrefix("192.0.0.0/24"),
	netip.MustParsePrefix("198.18.0.0/15"),
	netip.MustParsePrefix("240.0.0.0/4"),
}

type appServer struct {
	origin    string
	prefix    string
	assets    http.Handler
	statePath string
	open      func(string) error
	transport *http.Client
	secret    []byte
	mu        sync.Mutex
	sessions  map[string]*streamSession
}

type streamRequest struct {
	URL     string            `json:"url"`
	Headers map[string]string `json:"headers"`
	HLS     bool              `json:"hls"`
}

type streamSession struct {
	remote  *url.URL
	headers http.Header
	hls     bool
	touched time.Time
}

func randomToken() string {
	key := make([]byte, 24)
	rand.Read(key)
	return base64.RawURLEncoding.EncodeToString(key)
}

func newAppServer(address string, assets fs.FS, statePath string, open func(string) error) *appServer {
	return &appServer{
		origin:    "http://" + address,
		prefix:    "/" + randomToken() + "/",
		assets:    http.FileServerFS(assets),
		statePath: statePath,
		open:      open,
		transport: newRemoteClient(),
		secret:    []byte(randomToken()),
		sessions:  make(map[string]*streamSession),
	}
}

func newRemoteClient() *http.Client {
	dialer := &net.Dialer{Timeout: 8 * time.Second, KeepAlive: 30 * time.Second}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.Proxy = nil
	transport.ResponseHeaderTimeout = 12 * time.Second
	transport.MaxIdleConnsPerHost = 6
	transport.DialContext = func(ctx context.Context, network, address string) (net.Conn, error) {
		host, port, err := net.SplitHostPort(address)
		if err != nil {
			return nil, err
		}
		addresses, err := net.DefaultResolver.LookupNetIP(ctx, "ip", host)
		if err != nil {
			return nil, err
		}
		for _, ip := range addresses {
			if !publicIP(ip.Unmap()) {
				continue
			}
			if conn, err := dialer.DialContext(ctx, network, net.JoinHostPort(ip.String(), port)); err == nil {
				return conn, nil
			}
		}
		return nil, errors.New("the public provider could not be reached")
	}

	return &http.Client{Transport: transport, CheckRedirect: func(req *http.Request, previous []*http.Request) error {
		if len(previous) > 4 {
			return errors.New("too many redirects")
		}
		if _, err := secureURL(req.URL.String()); err != nil {
			return err
		}
		if req.URL.Host != previous[0].URL.Host {
			copyMediaHeaders(req.Header, req.Header.Clone(), false)
		}
		return nil
	}}
}

func publicIP(ip netip.Addr) bool {
	if !ip.IsGlobalUnicast() || ip.IsPrivate() {
		return false
	}
	for _, network := range nonPublicNetworks {
		if network.Contains(ip) {
			return false
		}
	}
	return true
}

func secureURL(raw string) (*url.URL, error) {
	if len(raw) > 8192 {
		return nil, errors.New("URL exceeds the size limit")
	}
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Scheme != "https" || parsed.User != nil ||
		parsed.Hostname() == "" || !strings.Contains(parsed.Hostname(), ".") {
		return nil, errors.New("use a public HTTPS address")
	}
	host := strings.TrimSuffix(strings.ToLower(parsed.Hostname()), ".")
	if _, err := netip.ParseAddr(host); err == nil || strings.HasSuffix(host, ".localhost") || strings.HasSuffix(host, ".local") {
		return nil, errors.New("use a public HTTPS hostname")
	}
	return parsed, nil
}

func (app *appServer) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Referrer-Policy", "no-referrer")
	w.Header().Set("Cross-Origin-Resource-Policy", "same-origin")
	w.Header().Set("X-Frame-Options", "DENY")
	if "http://"+req.Host != app.origin || !strings.HasPrefix(req.URL.Path, app.prefix) {
		http.NotFound(w, req)
		return
	}

	path := strings.TrimPrefix(req.URL.Path, app.prefix)
	if !strings.HasPrefix(path, "api/") {
		if req.Method != http.MethodGet && req.Method != http.MethodHead {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		http.StripPrefix(strings.TrimSuffix(app.prefix, "/"), app.assets).ServeHTTP(w, req)
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	if origin := req.Header.Get("Origin"); origin != "" && origin != app.origin {
		w.WriteHeader(http.StatusForbidden)
		return
	}
	if strings.HasPrefix(path, "api/media/") {
		if req.Method != http.MethodGet && req.Method != http.MethodHead {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		app.media(w, req, strings.TrimPrefix(path, "api/media/"))
		return
	}
	if req.Method != http.MethodPost || req.Header.Get("Content-Type") != "application/json" {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	req.Body = http.MaxBytesReader(w, req.Body, 1<<20)
	switch path {
	case "api/json":
		app.provider(w, req)
	case "api/state":
		app.readState(w)
	case "api/save":
		app.saveState(w, req)
	case "api/play":
		app.newStream(w, req)
	case "api/external":
		var body streamRequest
		if !readRequest(w, req, &body) {
			return
		}
		destination, err := secureURL(body.URL)
		if err != nil || app.open(destination.String()) != nil {
			http.Error(w, "Could not open the browser.", http.StatusBadRequest)
			return
		}
		sendJSON(w, map[string]bool{"ok": true})
	default:
		http.NotFound(w, req)
	}
}

func readRequest(w http.ResponseWriter, req *http.Request, data any) bool {
	if json.NewDecoder(req.Body).Decode(data) != nil {
		http.Error(w, "Invalid request.", http.StatusBadRequest)
		return false
	}
	return true
}

func sendJSON(w http.ResponseWriter, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(data)
}

func (app *appServer) provider(w http.ResponseWriter, req *http.Request) {
	var body streamRequest
	if !readRequest(w, req, &body) {
		return
	}
	destination, err := secureURL(body.URL)
	if err != nil {
		http.Error(w, "Enter a public HTTPS provider URL.", http.StatusBadRequest)
		return
	}
	ctx, cancel := context.WithTimeout(req.Context(), 15*time.Second)
	defer cancel()
	outgoing, _ := http.NewRequestWithContext(ctx, http.MethodGet, destination.String(), nil)
	outgoing.Header.Set("Accept", "application/json")
	outgoing.Header.Set("User-Agent", "MoonCat/3 Windows")
	response, err := app.transport.Do(outgoing)
	if err != nil {
		http.Error(w, "The provider is unavailable.", http.StatusBadGateway)
		return
	}
	defer response.Body.Close()
	content, err := io.ReadAll(io.LimitReader(response.Body, maxJSON+1))
	if response.StatusCode != http.StatusOK || err != nil || len(content) > maxJSON || !json.Valid(content) {
		http.Error(w, "The provider returned an invalid response.", http.StatusBadGateway)
		return
	}
	sendJSON(w, json.RawMessage(content))
}

func (app *appServer) readState(w http.ResponseWriter) {
	app.mu.Lock()
	defer app.mu.Unlock()
	data, err := os.ReadFile(app.statePath)
	if err != nil || !json.Valid(data) {
		sendJSON(w, map[string]any{})
		return
	}
	sendJSON(w, json.RawMessage(data))
}

func (app *appServer) saveState(w http.ResponseWriter, req *http.Request) {
	var data map[string]json.RawMessage
	if !readRequest(w, req, &data) {
		return
	}
	app.mu.Lock()
	defer app.mu.Unlock()
	temp, err := os.CreateTemp(filepath.Dir(app.statePath), "client-state-*.tmp")
	if err == nil {
		defer os.Remove(temp.Name())
		err = json.NewEncoder(temp).Encode(data)
		if closeErr := temp.Close(); err == nil {
			err = closeErr
		}
		if err == nil {
			err = replaceStateFile(temp.Name(), app.statePath)
		}
	}
	if err != nil {
		http.Error(w, "Could not save MoonCat settings.", http.StatusInternalServerError)
		return
	}
	sendJSON(w, map[string]bool{"ok": true})
}

func (app *appServer) newStream(w http.ResponseWriter, req *http.Request) {
	var body streamRequest
	if !readRequest(w, req, &body) {
		return
	}
	remote, err := secureURL(body.URL)
	if err != nil {
		http.Error(w, "The selected source has no public HTTPS stream.", http.StatusBadRequest)
		return
	}
	session := &streamSession{
		remote: remote, headers: make(http.Header),
		hls: body.HLS || strings.HasSuffix(strings.ToLower(remote.Path), ".m3u8"), touched: time.Now(),
	}
	session.headers.Set("User-Agent", "Mozilla/5.0 MoonCat/3")
	for name, value := range body.Headers {
		if allowedHeaders.MatchString(name) && len(value) < 2048 && !strings.ContainsAny(value, "\r\n") {
			session.headers.Set(name, value)
		}
	}
	key := randomToken()
	app.mu.Lock()
	for id, value := range app.sessions {
		if time.Since(value.touched) > 3*time.Hour {
			delete(app.sessions, id)
		}
	}
	if len(app.sessions) >= 64 {
		oldest, timeSeen := "", time.Now()
		for id, value := range app.sessions {
			if value.touched.Before(timeSeen) {
				oldest, timeSeen = id, value.touched
			}
		}
		delete(app.sessions, oldest)
	}
	app.sessions[key] = session
	app.mu.Unlock()
	sendJSON(w, map[string]any{"hls": session.hls, "path": app.prefix + "api/media/" + key})
}

func (app *appServer) media(w http.ResponseWriter, req *http.Request, path string) {
	sessionID, child, _ := strings.Cut(path, "/")
	app.mu.Lock()
	session := app.sessions[sessionID]
	if session != nil {
		session.touched = time.Now()
	}
	app.mu.Unlock()
	if session == nil {
		http.NotFound(w, req)
		return
	}

	remote := session.remote
	if child != "" {
		resolved, err := app.decodeMediaURL(sessionID, child)
		if err != nil {
			http.NotFound(w, req)
			return
		}
		remote = resolved
	}
	outgoing, _ := http.NewRequestWithContext(req.Context(), req.Method, remote.String(), nil)
	copyMediaHeaders(outgoing.Header, session.headers, remote.Host == session.remote.Host)
	if rangeHeader := req.Header.Get("Range"); rangeHeader != "" {
		outgoing.Header.Set("Range", rangeHeader)
	}
	response, err := app.transport.Do(outgoing)
	if err != nil {
		http.Error(w, "The stream is unavailable.", http.StatusBadGateway)
		return
	}
	defer response.Body.Close()

	mediaType, _, _ := mime.ParseMediaType(response.Header.Get("Content-Type"))
	isPlaylist := (session.hls && child == "") ||
		strings.HasSuffix(strings.ToLower(response.Request.URL.Path), ".m3u8") ||
		strings.Contains(strings.ToLower(mediaType), "mpegurl")
	if response.StatusCode == http.StatusOK && isPlaylist && req.Method == http.MethodGet {
		content, err := io.ReadAll(io.LimitReader(response.Body, (2<<20)+1))
		if err != nil || len(content) > 2<<20 || !strings.HasPrefix(strings.TrimSpace(strings.TrimPrefix(string(content), "\ufeff")), "#EXTM3U") {
			http.Error(w, "Invalid HLS playlist.", http.StatusBadGateway)
			return
		}
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		_, _ = io.WriteString(w, app.rewritePlaylist(sessionID, response.Request.URL, string(content)))
		return
	}
	// Keep remote HTML, SVG and other executable document formats inert on the application origin.
	delete(w.Header(), "Content-Type")
	if strings.HasPrefix(mediaType, "audio/") || strings.HasPrefix(mediaType, "video/") ||
		mediaType == "text/vtt" || mediaType == "application/vnd.apple.mpegurl" {
		w.Header().Set("Content-Type", mediaType)
	} else {
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Header().Set("Content-Disposition", "attachment")
	}
	w.Header().Set("Content-Security-Policy", "default-src 'none'; sandbox")
	for _, name := range []string{"Accept-Ranges", "Content-Range", "Content-Length"} {
		if value := response.Header.Get(name); value != "" {
			w.Header().Set(name, value)
		}
	}
	w.WriteHeader(response.StatusCode)
	_, _ = io.Copy(w, response.Body)
}

func copyMediaHeaders(target http.Header, source http.Header, sameHost bool) {
	if !sameHost {
		for name := range target {
			if strings.EqualFold(name, "Authorization") || strings.HasPrefix(strings.ToLower(name), "x-") {
				target.Del(name)
			}
		}
	}
	for name, values := range source {
		if sameHost || (!strings.EqualFold(name, "Authorization") && !strings.HasPrefix(strings.ToLower(name), "x-")) {
			target[name] = values
		}
	}
}

func (app *appServer) rewritePlaylist(session string, base *url.URL, content string) string {
	wrap := func(raw string) string {
		child, err := base.Parse(raw)
		if err != nil {
			return ""
		}
		if _, err := secureURL(child.String()); err != nil {
			return ""
		}
		encoded := base64.RawURLEncoding.EncodeToString([]byte(child.String()))
		return app.prefix + "api/media/" + session + "/" + encoded + "." + app.signURL(session, encoded)
	}
	lines := strings.Split(strings.TrimPrefix(content, "\ufeff"), "\n")
	for i, line := range lines {
		line = strings.TrimSpace(line)
		switch {
		case strings.HasPrefix(line, "#"):
			lines[i] = playlistURI.ReplaceAllStringFunc(line, func(match string) string {
				parts := playlistURI.FindStringSubmatch(match)
				return `URI="` + wrap(parts[1]) + `"`
			})
		case line != "":
			lines[i] = wrap(line)
		}
	}
	return strings.Join(lines, "\n")
}

func (app *appServer) signURL(session, encoded string) string {
	signer := hmac.New(sha256.New, app.secret)
	_, _ = fmt.Fprintf(signer, "%s/%s", session, encoded)
	return base64.RawURLEncoding.EncodeToString(signer.Sum(nil))
}

func (app *appServer) decodeMediaURL(session, child string) (*url.URL, error) {
	encoded, signature, found := strings.Cut(child, ".")
	if !found || !hmac.Equal([]byte(signature), []byte(app.signURL(session, encoded))) {
		return nil, errors.New("invalid media reference")
	}
	raw, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return nil, err
	}
	return secureURL(string(raw))
}
