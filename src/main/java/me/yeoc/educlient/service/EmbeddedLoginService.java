package me.yeoc.educlient.service;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class EmbeddedLoginService {
    public static final String LOGIN_URL = "https://jwglxt.zstu.edu.cn/sso/jasiglogin";
    private static final String SUCCESS_PATH = "/jwglxt/xtgl/index_initMenu.html";
    private static final URI SYSTEM_URI = URI.create("https://jwglxt.zstu.edu.cn/jwglxt/");

    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private WebEngine engine;
    private boolean completed;

    public void initialize(JFXPanel panel, Consumer<String> onLocationChanged,
            Consumer<String> onLoginSuccess, Consumer<String> onError) {
        CookieHandler.setDefault(cookieManager);
        Platform.runLater(() -> {
            try {
                WebView webView = new WebView();
                engine = webView.getEngine();
                engine.setJavaScriptEnabled(true);
                engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");

                ChangeListener<String> listener = (observable, oldValue, newValue) -> {
                    if (newValue == null) {
                        return;
                    }
                    onLocationChanged.accept(newValue);
                    if (!completed && isSuccessUrl(newValue)) {
                        String cookieHeader = buildCookieHeader();
                        if (cookieHeader.isBlank()) {
                            onError.accept("已进入教务系统，但没有获取到有效 Cookie，请重新登录。");
                            return;
                        }
                        completed = true;
                        onLoginSuccess.accept(cookieHeader);
                    }
                };
                engine.locationProperty().addListener(listener);
                engine.getLoadWorker().exceptionProperty().addListener((observable, oldValue, error) -> {
                    if (error != null) {
                        onError.accept("登录页面加载失败：" + error.getMessage());
                    }
                });
                panel.setScene(new Scene(webView));
                engine.load(LOGIN_URL);
            } catch (Exception error) {
                onError.accept("无法初始化内嵌登录页面：" + error.getMessage());
            }
        });
    }

    public void reloadLogin() {
        completed = false;
        cookieManager.getCookieStore().removeAll();
        Platform.runLater(() -> {
            if (engine != null) {
                engine.load(LOGIN_URL);
            }
        });
    }

    private boolean isSuccessUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "jwglxt.zstu.edu.cn".equalsIgnoreCase(uri.getHost())
                    && SUCCESS_PATH.equals(uri.getPath());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String buildCookieHeader() {
        Map<String, String> cookies = new LinkedHashMap<>();
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            String domain = cookie.getDomain();
            if (domain == null || domain.isBlank() || "jwglxt.zstu.edu.cn".endsWith(stripLeadingDot(domain))) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }

        try {
            Map<String, java.util.List<String>> headers = cookieManager.get(SYSTEM_URI, Map.of());
            for (String header : headers.getOrDefault("Cookie", java.util.List.of())) {
                for (String part : header.split(";\\s*")) {
                    int separator = part.indexOf('=');
                    if (separator > 0) {
                        cookies.put(part.substring(0, separator), part.substring(separator + 1));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private String stripLeadingDot(String domain) {
        return domain.startsWith(".") ? domain.substring(1) : domain;
    }
}
