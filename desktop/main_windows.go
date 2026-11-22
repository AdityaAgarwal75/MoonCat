package main

import (
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"syscall"
	"time"
	"unsafe"

	"github.com/jchv/go-webview2"
	"github.com/jchv/go-webview2/webviewloader"
	"golang.org/x/sys/windows"
)

//go:embed web
var embedded embed.FS

const runtimeDownload = "https://developer.microsoft.com/en-us/microsoft-edge/webview2/#download-section"

var user32 = windows.NewLazySystemDLL("user32.dll")
var messageBox = user32.NewProc("MessageBoxW")
var shellExecute = windows.NewLazySystemDLL("shell32.dll").NewProc("ShellExecuteW")
var errMissingRuntime = errors.New("MoonCat needs the Microsoft Edge WebView2 Evergreen Runtime")

func main() {
	runtime.LockOSThread()
	if err := launch(); err != nil {
		if !errors.Is(err, errMissingRuntime) {
			message(err.Error(), 0x10)
		} else if message(err.Error()+"\n\nOpen the WebView2 Runtime download page?", 0x34) == 6 {
			_ = openBrowser(runtimeDownload)
		}
	}
}

func launch() error {
	if version, err := webviewloader.GetInstalledVersion(); err != nil || version == "" {
		return errMissingRuntime
	}
	config, err := os.UserConfigDir()
	if err != nil {
		return fmt.Errorf("MoonCat could not locate the Windows profile folder: %w", err)
	}
	dataPath := filepath.Join(config, "MoonCat")
	if err := os.MkdirAll(dataPath, 0700); err != nil {
		return fmt.Errorf("MoonCat could not create its profile folder: %w", err)
	}
	assets, err := fs.Sub(embedded, "web")
	if err != nil {
		return err
	}
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		return fmt.Errorf("MoonCat could not open its local player: %w", err)
	}
	defer listener.Close()
	app := newAppServer(listener.Addr().String(), assets, filepath.Join(dataPath, "client-v3.json"), openBrowser)
	server := &http.Server{Handler: app, ReadHeaderTimeout: 5 * time.Second, IdleTimeout: 35 * time.Second, MaxHeaderBytes: 32 << 10}
	defer server.Close()
	go server.Serve(listener)

	view := webview2.NewWithOptions(webview2.WebViewOptions{
		DataPath: filepath.Join(dataPath, "WebView2"), AutoFocus: true,
		WindowOptions: webview2.WindowOptions{Title: "MoonCat", Width: 1100, Height: 720, Center: true, IconId: 1},
	})
	if view == nil {
		return errors.New("MoonCat could not initialize its playback window")
	}
	defer view.Destroy()
	view.SetSize(390, 430, webview2.HintMin)
	view.Navigate(app.origin + app.prefix)
	view.Run()
	return nil
}

func replaceStateFile(source, destination string) error {
	from, err := windows.UTF16PtrFromString(source)
	if err != nil {
		return err
	}
	to, err := windows.UTF16PtrFromString(destination)
	if err != nil {
		return err
	}
	return windows.MoveFileEx(from, to, windows.MOVEFILE_REPLACE_EXISTING|windows.MOVEFILE_WRITE_THROUGH)
}

func message(body string, flags uintptr) uintptr {
	text, _ := syscall.UTF16PtrFromString(body)
	title, _ := syscall.UTF16PtrFromString("MoonCat")
	result, _, _ := messageBox.Call(0, uintptr(unsafe.Pointer(text)), uintptr(unsafe.Pointer(title)), flags)
	return result
}

func openBrowser(link string) error {
	verb, _ := syscall.UTF16PtrFromString("open")
	target, err := syscall.UTF16PtrFromString(link)
	if err != nil {
		return err
	}
	result, _, _ := shellExecute.Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(target)), 0, 0, 1)
	if result <= 32 {
		return fmt.Errorf("Windows could not open the link (%d)", result)
	}
	return nil
}
