package me.yeoc.educlient.service;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.yeoc.educlient.object.CourseQueryContext;
import me.yeoc.educlient.object.CourseTab;
import me.yeoc.educlient.object.UserInfo;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>High‑level HTTP client used to interact with the ZSTU teaching‑affairs system (教务系统).<br>
 * The class is intentionally kept as a thin, stateless wrapper around the HTTP endpoints
 * so that it can be reused, unit‑tested and mocked with ease.</p>
 *
 * <p>All public APIs throw {@link IOException} in case of network‑ or protocol‑level errors.
 * Business‑logic problems should be handled by the caller.</p>
 */
public class EduService {

    /* ----------------------------------------------------------
     *  Constants
     * ---------------------------------------------------------- */

    private static final String BASE_URL = "https://jwglxt.zstu.edu.cn";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edge/138.0.0.0";

    private static final MediaType FORM =
            MediaType.parse("application/x-www-form-urlencoded;charset=UTF-8");

    private static final Pattern COURSE_TAB_PATTERN = Pattern.compile(
            "queryCourse\\(this,'(\\d+?)','(.*?)','(\\d+?)','(\\d+?)'\\).*?>(.*?)<");

    /* ----------------------------------------------------------
     *  Dependencies
     * ---------------------------------------------------------- */

    private final OkHttpClient httpClient;

    /* ----------------------------------------------------------
     *  Session‑scoped fields
     * ---------------------------------------------------------- */

    @Setter
    private String cookie;

    /** Cached after the first successful call to {@link #fetchCourseTabs()}. */
    @Getter
    private UserInfo userInfo;

    /* ----------------------------------------------------------
     *  Constructors
     * ---------------------------------------------------------- */

    public EduService() {
        this(new OkHttpClient());
    }

    public EduService(@NonNull OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /* ----------------------------------------------------------
     *  Public API
     * ---------------------------------------------------------- */

    /**
     * Download the course‑selection landing page and extract the tab list together with hidden user fields.
     *
     * @return list of {@link CourseTab} never {@code null} (may be empty).
     * @throws IOException when the remote service cannot be reached or returns a non‑200 status.
     */
    public List<CourseTab> fetchCourseTabs() throws IOException {
        final String url = BASE_URL + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default";

        Request request = new Request.Builder()
                .url(url)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            final String html = Objects.requireNonNull(response.body()).string();
            this.userInfo = parseUserInfoFromHtml(html);
            return parseCourseTabsFromHtml(html);
        }
    }

    /**
     * Call the “part‑display” endpoint with a text filter and return the raw HTML document.
     */
    public String fetchCourseQueryContextFiltered(@NonNull CourseTab tab, @NonNull String keyword) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("filter_list[0]", keyword)
                .add("xqh_id", userInfo.getXqhId())
                .add("jg_id", userInfo.getJgId())
                .add("njdm_id_xs", tab.getNjdmId())
                .add("zyh_id_xs", tab.getZyhId())
                .add("xkxnm", userInfo.getXkxnm())
                .add("xkxqm", userInfo.getXkxqm())
                .add("kklxdm", tab.getKklxdm())
                .add("xkkz_id", tab.getXkkzId())
                .add("kspage", "1")
                .add("jspage", "10")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            return Objects.requireNonNull(response.body()).string();
        }
    }

    /**
     * Fetch all hidden‑field context required to perform subsequent course queries.
     */
    public CourseQueryContext fetchCourseQueryContext(@NonNull CourseTab tab) throws IOException {
        RequestBody formBody = new FormBody.Builder()
                .add("xkkz_id", tab.getXkkzId())
                .add("xszxzt", "1")
                .add("njdm_id", tab.getNjdmId())
                .add("zyh_id", tab.getZyhId())
                .add("kspage", "0")
                .add("jspage", "0")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html?gnmkdm=N253512")
                .post(formBody)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "text/html, */*; q=0.01")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            return parseCourseQueryContextFromHtml(Objects.requireNonNull(response.body()).string());
        }
    }

    /**
     * Example call to the grade‑query endpoint. Returns raw JSON (as text).
     */
    public String fetchCourseData() throws IOException {
        RequestBody formBody = new FormBody.Builder()
                .add("xnm", "2024")
                .add("xqm", "12")
                .add("_search", "false")
                .add("nd", String.valueOf(Instant.now().toEpochMilli()))
                .add("queryModel.showCount", "15")
                .add("queryModel.currentPage", "1")
                .add("queryModel.sortOrder", "asc")
                .add("time", "1")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/cjcx/cjjdcx_cxXsjdxmcjIndex.html?doType=query&gnmkdm=N305099")
                .post(formBody)
                .header("Cookie", requireCookie())
                .header("Content-Type", FORM.toString())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            return Objects.requireNonNull(response.body()).string();
        }
    }

    /* ----------------------------------------------------------
     *  Private helpers
     * ---------------------------------------------------------- */

    private UserInfo parseUserInfoFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Elements inputs = doc.select("input[type=hidden]");
        UserInfo info = new UserInfo();

        inputs.forEach(input -> {
            switch (input.attr("name")) {
                case "xqh_id": info.setXqhId(input.attr("value")); break;
                case "jg_id_1": info.setJgId(input.attr("value")); break;
                case "njdm_id": info.setNjdmId(input.attr("value")); break;
                case "zyh_id": info.setZyhId(input.attr("value")); break;
                case "zyfx_id": info.setZyfxId(input.attr("value")); break;
                case "bh_id": info.setBhId(input.attr("value")); break;
                case "xbm": info.setXbm(input.attr("value")); break;
                case "xslbdm": info.setXslbdm(input.attr("value")); break;
                case "mzm": info.setMzm(input.attr("value")); break;
                case "xz": info.setXz(input.attr("value")); break;
                case "ccdm": info.setCcdm(input.attr("value")); break;
                case "xsbj": info.setXsbj(input.attr("value")); break;
                case "njdm_id_xs": info.setNjdmId_xs(input.attr("value")); break;
                case "zyh_id_xs": info.setZyhId_xs(input.attr("value")); break;
                case "xkxnm": info.setXkxnm(input.attr("value")); break;
                case "xkxqm": info.setXkxqm(input.attr("value")); break;
                default: /* ignore */
            }
        });
        return info;
    }

    private List<CourseTab> parseCourseTabsFromHtml(String html) {
        List<CourseTab> tabs = new ArrayList<>();
        Matcher matcher = COURSE_TAB_PATTERN.matcher(html);
        while (matcher.find()) {
            CourseTab tab = new CourseTab();
            tab.setKklxdm(matcher.group(1));
            tab.setXkkzId(matcher.group(2));
            tab.setNjdmId(matcher.group(3));
            tab.setZyhId(matcher.group(4));
            tab.setName(matcher.group(5).replaceAll("<.*?>", "").trim());
            tabs.add(tab);
        }
        return Collections.unmodifiableList(tabs);
    }

    private CourseQueryContext parseCourseQueryContextFromHtml(String html) {
        CourseQueryContext ctx = new CourseQueryContext();
        Document doc = Jsoup.parse(html);
        Elements inputs = doc.select("input[type=hidden]");
        inputs.forEach(input -> mapHiddenInputToField(ctx, input.attr("name"), input.attr("value")));
        return ctx;
    }

    private void mapHiddenInputToField(CourseQueryContext ctx, String name, String value) {
        if (name == null || name.isEmpty()) return;
        try {
            Field field = CourseQueryContext.class.getDeclaredField(toCamel(name));
            field.setAccessible(true);
            field.set(ctx, value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
//            log.debug("Ignore unknown field: {}", name);
        }
    }

    private static String toCamel(String snake) {
        String[] parts = snake.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private String requireCookie() {
        if (cookie == null || cookie.isEmpty()) {
            throw new IllegalStateException("Cookie must be set before making HTTP calls.");
        }
        return cookie;
    }

    private static void ensureSuccess(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code() + " - " + response.message());
        }
    }
    public static void main(String[] args) throws IOException {
        EduService service = new EduService();
        String cookie =
                "JSESSIONID=0ADD8ABB4262926C505F2A56E7C7D1D8; old_device_token=48a1ab76bcf56a38e2effed01b55b4dc; route=b6c4112deca225961740e038b0b4c078";
        service.setCookie(cookie);

        List<CourseTab> tabs = service.fetchCourseTabs();
        System.out.println(service.userInfo);
        for (CourseTab tab : tabs) {
            System.out.println(tab);
//            break;
        }
        System.out.println(service.fetchCourseQueryContextFiltered(tabs.get(0), "62729"));
    }
}



