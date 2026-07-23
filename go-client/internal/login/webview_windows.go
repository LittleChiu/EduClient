//go:build windows

// Package login hosts the school SSO page in a native Windows WebView2 window
// and returns its full jwglxt cookie store after authentication completes.
package login

import (
	"context"
	"errors"
	"net/url"
	"runtime"
	"strings"
	"sync"
	"time"

	webview "github.com/GopeedLab/webview_go"
)

const (
	directHost        = "jwglxt.zstu.edu.cn"
	webVPNHost        = "jwglxt-443.webvpn.zstu.edu.cn"
	successPathPrefix = "/jwglxt/xtgl/index_initMenu"
)

// Endpoint describes one supported route into the teaching-affairs system.
type Endpoint struct {
	Name      string
	BaseURL   string
	LoginURL  string
	CookieURL string
}

// Session contains cookies and the origin on which those cookies were issued.
type Session struct {
	BaseURL string
	Cookie  string
}

var (
	DirectEndpoint = Endpoint{
		Name:      "教务系统直连",
		BaseURL:   "https://jwglxt.zstu.edu.cn",
		LoginURL:  "https://jwglxt.zstu.edu.cn/sso/jasiglogin",
		CookieURL: "https://jwglxt.zstu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs",
	}
	WebVPNEndpoint = Endpoint{
		Name:      "学校 WebVPN",
		BaseURL:   "https://jwglxt-443.webvpn.zstu.edu.cn",
		LoginURL:  "https://webvpn.zstu.edu.cn/",
		CookieURL: "https://jwglxt-443.webvpn.zstu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs",
	}
)

func endpointForSuccessURL(rawURL string) (Endpoint, bool) {
	parsed, err := url.Parse(strings.TrimSpace(rawURL))
	if err != nil || !strings.HasPrefix(parsed.Path, successPathPrefix) {
		return Endpoint{}, false
	}
	switch strings.ToLower(parsed.Hostname()) {
	case directHost:
		return DirectEndpoint, true
	case webVPNHost:
		return WebVPNEndpoint, true
	default:
		return Endpoint{}, false
	}
}

// Open displays the embedded login window. It closes itself immediately after
// the teaching-affairs landing page is reached and returns every jwglxt cookie,
// including HTTP-only session cookies supplied by WebView2.
func Open(ctx context.Context, endpoint Endpoint) (Session, error) {
	result := make(chan Session, 1)
	finished := make(chan struct{})
	var closeOnce sync.Once

	go func() {
		runtime.LockOSThread()
		defer runtime.UnlockOSThread()
		defer close(finished)

		if !webview.IsAvailable() {
			return
		}

		w := webview.New(false)
		defer w.Destroy()
		w.SetTitle("EduClient Go｜学校统一认证")
		w.SetSize(1000, 720, webview.HintNone)

		cookieParts := func(cookies []webview.Cookie) []string {
			best := make(map[string]webview.Cookie)
			for _, cookie := range cookies {
				if cookie.Name == "" {
					continue
				}
				old, exists := best[cookie.Name]
				if !exists || len(cookie.Path) > len(old.Path) {
					best[cookie.Name] = cookie
				}
			}
			parts := make([]string, 0, len(best))
			for _, cookie := range best {
				parts = append(parts, cookie.Name+"="+cookie.Value)
			}
			return parts
		}
		publish := func(actual Endpoint, parts []string) {
			closeOnce.Do(func() {
				result <- Session{BaseURL: actual.BaseURL, Cookie: strings.Join(parts, "; ")}
				w.Terminate()
			})
		}
		capture := func(actual Endpoint, browserCookie string) {
			cookies, _ := w.GetCookies(actual.CookieURL)
			parts := cookieParts(cookies)
			// The native cookie manager supplies HTTP-only cookies. document.cookie
			// provides a fallback for runtimes which expose only non-HTTP-only values.
			if len(parts) == 0 && strings.TrimSpace(browserCookie) != "" {
				parts = append(parts, strings.TrimSpace(browserCookie))
			}
			if len(parts) == 0 {
				return
			}
			publish(actual, parts)
		}
		finish := func(pageURL string, browserCookie string) error {
			actual, ok := endpointForSuccessURL(pageURL)
			if !ok {
				return errors.New("当前页面不是支持的教务系统首页")
			}
			// Cookie APIs must run on the WebView UI thread. The URL received from
			// JavaScript decides whether direct or WebVPN cookies are collected.
			w.Dispatch(func() { capture(actual, browserCookie) })
			return nil
		}

		if err := w.Bind("eduClientCaptureSession", finish); err != nil {
			return
		}
		successCondition := `(location.hostname === '` + directHost + `' || location.hostname === '` + webVPNHost + `') && location.pathname.startsWith('` + successPathPrefix + `')`
		trigger := `try { if (` + successCondition + `) { window.eduClientCaptureSession(location.href, document.cookie); } } catch (_) {}`
		w.Init(`
(() => {
  let sent = false;
  const watch = () => {
	if (sent || !(` + successCondition + `)) return;
	sent = true;
	window.eduClientCaptureSession(location.href, document.cookie);
  };
  setInterval(watch, 350);
  window.addEventListener('load', watch);
})();`)
		w.Navigate(endpoint.LoginURL)
		go func() {
			ticker := time.NewTicker(400 * time.Millisecond)
			defer ticker.Stop()
			select {
			case <-ctx.Done():
				closeOnce.Do(w.Terminate)
			case <-finished:
				return
			case <-ticker.C:
				w.Dispatch(func() { w.Eval(trigger) })
			}
			for {
				select {
				case <-ctx.Done():
					closeOnce.Do(w.Terminate)
					return
				case <-finished:
					return
				case <-ticker.C:
					w.Dispatch(func() { w.Eval(trigger) })
				}
			}
		}()
		w.Run()
	}()

	select {
	case cookie := <-result:
		return cookie, nil
	case <-finished:
		select {
		case cookie := <-result:
			return cookie, nil
		default:
			return Session{}, errors.New("登录窗口已关闭，未完成会话获取")
		}
	case <-ctx.Done():
		return Session{}, ctx.Err()
	}
}
