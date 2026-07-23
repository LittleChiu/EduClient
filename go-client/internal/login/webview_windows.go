//go:build windows

// Package login hosts the school SSO page in a native Windows WebView2 window
// and returns its full jwglxt cookie store after authentication completes.
package login

import (
	"context"
	"errors"
	"runtime"
	"strings"
	"sync"
	"time"

	webview "github.com/GopeedLab/webview_go"
)

const (
	loginURL   = "https://jwglxt.zstu.edu.cn/sso/jasiglogin"
	successURL = "/jwglxt/xtgl/"
	cookieURL  = "https://jwglxt.zstu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs"
)

// Open displays the embedded login window. It closes itself immediately after
// the teaching-affairs landing page is reached and returns every jwglxt cookie,
// including HTTP-only session cookies supplied by WebView2.
func Open(ctx context.Context) (string, error) {
	result := make(chan string, 1)
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
		publish := func(parts []string) {
			closeOnce.Do(func() {
				result <- strings.Join(parts, "; ")
				w.Terminate()
			})
		}
		finish := func(_ string, browserCookie string) error {
			cookies, err := w.GetCookies(cookieURL)
			parts := cookieParts(cookies)
			// The native cookie manager supplies HTTP-only cookies. document.cookie
			// provides a fallback for runtimes which expose only non-HTTP-only values.
			if len(parts) == 0 && strings.TrimSpace(browserCookie) != "" {
				parts = append(parts, strings.TrimSpace(browserCookie))
			}
			if len(parts) == 0 {
				if err != nil {
					return err
				}
				return errors.New("未读取到 jwglxt Cookie")
			}
			publish(parts)
			return nil
		}
		// Poll the native cookie store as well as the page URL. This does not
		// depend on page-side JavaScript and closes the login window as soon as
		// the SSO redirect has created the teaching-affairs JSESSIONID cookie.
		captureFromCookieStore := func() {
			cookies, err := w.GetCookies(cookieURL)
			if err != nil {
				return
			}
			hasSession := false
			for _, cookie := range cookies {
				if strings.EqualFold(cookie.Name, "JSESSIONID") {
					hasSession = true
				}
			}
			if hasSession {
				publish(cookieParts(cookies))
			}
		}

		if err := w.Bind("eduClientCaptureSession", finish); err != nil {
			return
		}
		trigger := `try { if (location.href.includes('` + successURL + `')) { window.eduClientCaptureSession(location.href, document.cookie); } } catch (_) {}`
		w.Init(`
(() => {
  let sent = false;
  const watch = () => {
    if (sent || !location.href.includes('` + successURL + `')) return;
    sent = true;
    window.eduClientCaptureSession(location.href, document.cookie);
  };
  setInterval(watch, 350);
  window.addEventListener('load', watch);
})();`)
		w.Navigate(loginURL)
		go func() {
			ticker := time.NewTicker(400 * time.Millisecond)
			defer ticker.Stop()
			select {
			case <-ctx.Done():
				closeOnce.Do(w.Terminate)
			case <-finished:
				return
			case <-ticker.C:
				w.Dispatch(func() { captureFromCookieStore(); w.Eval(trigger) })
			}
			for {
				select {
				case <-ctx.Done():
					closeOnce.Do(w.Terminate)
					return
				case <-finished:
					return
				case <-ticker.C:
					w.Dispatch(func() { captureFromCookieStore(); w.Eval(trigger) })
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
			return "", errors.New("登录窗口已关闭，未完成会话获取")
		}
	case <-ctx.Done():
		return "", ctx.Err()
	}
}
