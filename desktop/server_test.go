package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"testing/fstest"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func newTestServer(t *testing.T) *appServer {
	t.Helper()
	return newAppServer("127.0.0.1:10000",
		fstest.MapFS{"index.html": {Data: []byte("MoonCat")}},
		filepath.Join(t.TempDir(), "client.json"), func(string) error { return nil })
}

func call(t *testing.T, app *appServer, method, path, body string) *httptest.ResponseRecorder {
	t.Helper()
	request := httptest.NewRequest(method, app.origin+path, strings.NewReader(body))
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	app.ServeHTTP(response, request)
	return response
}

func TestStateAndOrigin(t *testing.T) {
	app := newTestServer(t)
	if response := call(t, app, "GET", "/index.html", ""); response.Code != http.StatusNotFound {
		t.Fatalf("unscoped private server request returned %d", response.Code)
	}
	req := httptest.NewRequest("POST", app.origin+app.prefix+"api/state", strings.NewReader("{}"))
	req.Header.Set("Origin", "https://unrelated.example")
	response := httptest.NewRecorder()
	app.ServeHTTP(response, req)
	if response.Code != http.StatusForbidden {
		t.Fatalf("cross-origin request returned %d", response.Code)
	}
	for _, state := range []string{`{"preferred":"hi"}`, `{"preferred":"en"}`} {
		saved := call(t, app, "POST", app.prefix+"api/save", state)
		read := call(t, app, "POST", app.prefix+"api/state", "{}")
		if saved.Code != http.StatusOK || strings.TrimSpace(read.Body.String()) != state {
			t.Fatalf("state did not round-trip: %d %d %s", saved.Code, read.Code, read.Body)
		}
	}
}

func TestHLSRelayAndRange(t *testing.T) {
	app := newTestServer(t)
	app.transport = &http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		resp := &http.Response{StatusCode: http.StatusOK, Header: make(http.Header), Request: req}
		if req.URL.Path == "/video/master.m3u8" {
			if req.Header.Get("Authorization") != "Bearer fixture" {
				t.Error("request header did not reach the stream origin")
			}
			resp.Header.Set("Content-Type", "application/vnd.apple.mpegurl")
			resp.Body = io.NopCloser(strings.NewReader("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:4.0,\nhttps://cdn.example/segment.ts"))
		} else {
			if req.URL.Host != "cdn.example" || req.Header.Get("Authorization") != "" || req.Header.Get("Range") != "bytes=10-20" {
				t.Errorf("segment request was not isolated: %s %v", req.URL, req.Header)
			}
			resp.StatusCode = http.StatusPartialContent
			resp.Header.Set("Content-Range", "bytes 10-20/50")
			resp.Header.Set("Content-Type", "video/mp2t")
			resp.Body = io.NopCloser(strings.NewReader("segment"))
		}
		return resp, nil
	})}
	reply := call(t, app, "POST", app.prefix+"api/play",
		`{"url":"https://video.example/video/master.m3u8","headers":{"Authorization":"Bearer fixture"}}`)
	var start struct {
		Path string
		HLS  bool
	}
	if json.Unmarshal(reply.Body.Bytes(), &start) != nil || !start.HLS || start.Path == "" {
		t.Fatalf("invalid start response: %s", reply.Body)
	}
	manifest := call(t, app, "GET", start.Path, "")
	lines := strings.Split(manifest.Body.String(), "\n")
	if len(lines) != 4 || !strings.HasPrefix(lines[1], `#EXT-X-KEY:METHOD=AES-128,URI="`+start.Path+"/") ||
		!strings.HasPrefix(lines[3], start.Path+"/") {
		t.Fatalf("playlist URIs were not rewritten: %q", manifest.Body.String())
	}
	req := httptest.NewRequest("GET", app.origin+lines[3], nil)
	req.Header.Set("Range", "bytes=10-20")
	segment := httptest.NewRecorder()
	app.ServeHTTP(segment, req)
	if segment.Code != http.StatusPartialContent || segment.Header().Get("Content-Range") != "bytes 10-20/50" {
		t.Fatalf("range response was not forwarded: %d %v", segment.Code, segment.Header())
	}
	if response := call(t, app, "GET", lines[3]+"invalid", ""); response.Code != http.StatusNotFound {
		t.Fatalf("modified segment reference returned %d", response.Code)
	}
}
