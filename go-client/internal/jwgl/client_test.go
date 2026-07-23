package jwgl

import "testing"

func TestSetSessionSwitchesOriginAndCookieTogether(t *testing.T) {
	client := NewClient()
	if client.BaseURL() != defaultBaseURL {
		t.Fatalf("default base URL = %q", client.BaseURL())
	}

	const webVPN = "https://jwglxt-443.webvpn.zstu.edu.cn"
	client.SetSession(webVPN+"/", "JSESSIONID=test")
	if client.BaseURL() != webVPN {
		t.Fatalf("base URL = %q, want %q", client.BaseURL(), webVPN)
	}
	if client.Cookie() != "JSESSIONID=test" {
		t.Fatalf("cookie = %q", client.Cookie())
	}
}
