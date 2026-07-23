//go:build windows

package login

import "testing"

func TestEndpointForSuccessURL(t *testing.T) {
	if WebVPNEndpoint.LoginURL != "https://webvpn.zstu.edu.cn/" {
		t.Fatalf("WebVPN login URL = %q", WebVPNEndpoint.LoginURL)
	}
	tests := []struct {
		url      string
		baseURL  string
		accepted bool
	}{
		{"https://jwglxt.zstu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs", DirectEndpoint.BaseURL, true},
		{"https://jwglxt-443.webvpn.zstu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs", WebVPNEndpoint.BaseURL, true},
		{"https://jwglxt-443.webvpn.zstu.edu.cn/jwglxt/xtgl/other.html", "", false},
		{"https://example.com/jwglxt/xtgl/index_initMenu.html", "", false},
	}
	for _, test := range tests {
		endpoint, accepted := endpointForSuccessURL(test.url)
		if accepted != test.accepted {
			t.Fatalf("endpointForSuccessURL(%q) accepted = %v", test.url, accepted)
		}
		if accepted && endpoint.BaseURL != test.baseURL {
			t.Fatalf("endpointForSuccessURL(%q) base = %q, want %q", test.url, endpoint.BaseURL, test.baseURL)
		}
	}
}
