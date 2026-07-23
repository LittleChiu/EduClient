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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import me.yeoc.educlient.object.CourseItem;

/**
 * <p>
 * High‑level HTTP client used to interact with the ZSTU teaching‑affairs system
 * (教务系统).<br>
 * The class is intentionally kept as a thin, stateless wrapper around the HTTP
 * endpoints
 * so that it can be reused, unit‑tested and mocked with ease.
 * </p>
 *
 * <p>
 * All public APIs throw {@link IOException} in case of network‑ or
 * protocol‑level errors.
 * Business‑logic problems should be handled by the caller.
 * </p>
 */
public class EduService {

    /*
     * ----------------------------------------------------------
     * Constants
     * ----------------------------------------------------------
     */

    private static final String BASE_URL = "https://jwglxt.zstu.edu.cn";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edge/138.0.0.0";

    private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded;charset=UTF-8");

    private static final Pattern COURSE_TAB_PATTERN = Pattern.compile(
            "queryCourse\\(this,'(\\d+?)','(.*?)','(\\d+?)','(\\d+?)'\\).*?>(.*?)<");

    /*
     * ----------------------------------------------------------
     * Dependencies
     * ----------------------------------------------------------
     */

    private final OkHttpClient httpClient;

    /*
     * ----------------------------------------------------------
     * Session‑scoped fields
     * ----------------------------------------------------------
     */

    @Setter
    @Getter
    private String cookie;

    /** Cached after the first successful call to {@link #fetchCourseTabs()}. */
    @Getter
    private UserInfo userInfo;

    @Getter
    private CourseQueryContext lastContext;

    /*
     * ----------------------------------------------------------
     * Constructors
     * ----------------------------------------------------------
     */

    public EduService() {
        this(new OkHttpClient());
    }

    public EduService(@NonNull OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /*
     * ----------------------------------------------------------
     * Public API
     * ----------------------------------------------------------
     */

    /**
     * Download the course‑selection landing page and extract the tab list together
     * with hidden user fields.
     *
     * @return list of {@link CourseTab} never {@code null} (may be empty).
     * @throws IOException when the remote service cannot be reached or returns a
     *                     non‑200 status.
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
     * Call the “part‑display” endpoint with a text filter and return the raw HTML
     * document.
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

    private String val(String value) {
        return value == null ? "" : value;
    }

    /**
     * Fetch the list of courses for a given tab and keyword.
     * This only fetches the basic course list (Master View).
     */
    public List<CourseItem> fetchCourseList(@NonNull CourseTab tab, @NonNull String keyword) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("filter_list[0]", val(keyword))
                .add("xqh_id", val(userInfo.getXqhId()))
                .add("jg_id", val(userInfo.getJgId()))
                .add("njdm_id", val(userInfo.getNjdmId()))
                .add("zyh_id", val(userInfo.getZyhId()))
                .add("xkxnm", val(userInfo.getXkxnm()))
                .add("xkxqm", val(userInfo.getXkxqm()))
                .add("kklxdm", val(tab.getKklxdm()))
                .add("xkkz_id", val(tab.getXkkzId()))
                .add("kspage", "1")
                .add("jspage", "100")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String json = Objects.requireNonNull(response.body()).string();
            // System.out.println("DEBUG PartDisplay JSON: " + json);

            if ("\"0\"".equals(json.trim()) || "0".equals(json.trim())) {
                return Collections.emptyList();
            }

            JSONObject jsonObj = JSON.parseObject(json);
            if (jsonObj != null && jsonObj.containsKey("tmpList")) {
                List<CourseItem> rawList = jsonObj.getJSONArray("tmpList").toJavaList(CourseItem.class);

                return rawList;
            }
            return Collections.emptyList();
        }
    }

    /**
     * Pre-fetches metadata for a course tab (e.g., dynamic bklx_id).
     * This mimics the "Display" request in the web app.
     */
    public void fetchCourseTabMetadata(CourseTab tab) throws IOException {
        System.out.println("DEBUG Fetching metadata for tab: " + tab.getName());
        System.out.println("DEBUG Params: xkkz_id=" + tab.getXkkzId() + ", xszxzt=1, kklxdm=" + tab.getKklxdm() +
                ", njdm_id=" + tab.getNjdmId() + ", zyh_id=" + tab.getZyhId());

        RequestBody body = new FormBody.Builder()
                .add("xkkz_id", val(tab.getXkkzId()))
                .add("xszxzt", "1")
                .add("kklxdm", val(tab.getKklxdm()))
                .add("njdm_id", val(tab.getNjdmId()))
                .add("zyh_id", val(tab.getZyhId()))
                .add("kspage", "0")
                .add("jspage", "0")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String html = Objects.requireNonNull(response.body()).string();
            // System.out.println(html);
            this.parseCourseQueryContextFromHtml(html);

            Document doc = Jsoup.parse(html);
            Element bklxIdInput = doc.getElementById("bklx_id");
            if (bklxIdInput != null) {
                String bklxId = bklxIdInput.attr("value");
                tab.setBklxId(bklxId);
                System.out.println("DEBUG Parsed bklx_id for " + tab.getName() + ": " + bklxId);
            } else {
                System.err.println("DEBUG Failed to find bklx_id element! HTML length: " + html.length());
                // Optional: Print the whole HTML if needed for debugging
                // System.out.println(html);
            }
        }
    }

    public List<CourseItem> fetchCourseDetails(CourseTab tab, String keyword, String kchId,
            List<CourseItem> contextItems)
            throws IOException {
        // Find a representative context item (first one or match kchId) for general
        // course info
        CourseItem representative = contextItems.isEmpty() ? new CourseItem() : contextItems.get(0);
        String cxbj = val(representative.getCxbj(), "0");
        String fxbj = val(representative.getFxbj(), "0");

        System.out.println(tab);
        System.out.println("DEBUG Fetching details for kch_id: " + kchId);
        RequestBody body = new FormBody.Builder()
                .add("filter_list[0]", val(keyword))
                .add("xqh_id", val(userInfo.getXqhId()))
                .add("jg_id", val(userInfo.getJgId()))
                .add("njdm_id", val(userInfo.getNjdmId()))
                .add("zyh_id", val(userInfo.getZyhId()))
                .add("njdm_id_1", val(userInfo.getNjdmId()))
                .add("zyh_id_1", val(userInfo.getZyhId()))
                .add("xkxnm", val(userInfo.getXkxnm()))
                .add("xkxqm", val(userInfo.getXkxqm()))
                .add("kklxdm", val(tab.getKklxdm()))
                .add("xkkz_id", val(tab.getXkkzId()))
                .add("kch_id", kchId)
                .add("rwlx", "1")
                .add("xkly", "1")
                .add("bklx_id", val(tab.getBklxId(), "0"))
                .add("sfkkjyxdxnxq", "0")
                .add("kzkcgs", "0")
                .add("zyfx_id", val(userInfo.getZyfxId()))
                .add("njdm_id_xs", val(userInfo.getNjdmId_xs()))
                .add("bh_id", val(userInfo.getBhId()))
                .add("xbm", val(userInfo.getXbm()))
                .add("xslbdm", val(userInfo.getXslbdm()))
                .add("mzm", val(userInfo.getMzm()))
                .add("xz", val(userInfo.getXz()))
                .add("ccdm", val(userInfo.getCcdm()))
                .add("xsbj", val(userInfo.getXsbj()))
                .add("sfkknj", "1")
                .add("sfkkzy", "1")
                .add("kzybkxy", "0")
                .add("sfznkx", "0")
                .add("zdkxms", "0")
                .add("sfkxq", "0")
                .add("sfkcfx", "0")
                .add("bbhzxjxb", "0")
                .add("kkbk", "0")
                .add("kkbkdj", "0")
                .add("bklbkcj", "0")
                .add("xkxskcgskg", "0")
                .add("rlkz", "0")
                .add("cdrlkz", "0")
                .add("rlzlkz", "0")
                .add("jxbzcxskg", "0")
                .add("xklc", "1")
                .add("cxbj", cxbj)
                .add("fxbj", fxbj)
                .add("txbsfrl", "0")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String json = Objects.requireNonNull(response.body()).string();
            // System.out.println("DEBUG Detail JSON for " + kchId + ": " + json);

            if ("\"0\"".equals(json.trim()) || "0".equals(json.trim())) {
                return Collections.emptyList();
            }

            if (json.trim().startsWith("[")) {
                List<CourseItem> details = JSON.parseArray(json, CourseItem.class);

                // Index context items by jxb_id for fast lookup
                Map<String, CourseItem> contextMap = new HashMap<>();
                for (CourseItem item : contextItems) {
                    if (item.getJxbId() != null) {
                        contextMap.put(item.getJxbId(), item);
                        System.out.println(item.getJxbId());
                    }
                }

                // Merge fields from context items to details based on jxb_id
                for (CourseItem detail : details) {
                    // System.out.println(detail.getJxbId() + "gogogo");
                    CourseItem match = contextMap.get(detail.getJxbId());

                    // Fallback to representative if exact match not found (unlikely but safe)
                    if (match == null)
                        continue;
                    // System.out.println("matched!");
                    CourseItem source = match;

                    detail.setCxbj(val(source.getCxbj(), "0"));
                    detail.setFxbj(val(source.getFxbj(), "0"));
                    detail.setXxkbj(val(source.getXxkbj(), "0"));

                    // Sync other common fields
                    if (detail.getKcmc() == null || detail.getKcmc().isEmpty())
                        detail.setKcmc(source.getKcmc());
                    if (detail.getKch() == null || detail.getKch().isEmpty())
                        detail.setKch(source.getKch());
                    if (detail.getXf() == null || detail.getXf().isEmpty())
                        detail.setXf(source.getXf());
                    if (detail.getKklxdm() == null || detail.getKklxdm().isEmpty())
                        detail.setKklxdm(source.getKklxdm());
                    if (detail.getJxbmc() == null || detail.getJxbmc().isEmpty())
                        detail.setJxbmc(source.getJxbmc());
                    if (detail.getKchId() == null || detail.getKchId().isEmpty()) {
                        detail.setKchId(kchId);
                    }
                    if (detail.getJxbzls() == null || detail.getJxbzls().isEmpty()) {
                        detail.setJxbzls(source.getJxbzls());
                    }
                }
                return details;
            }
            return Collections.emptyList();
        }

    }

    /**
     * Enroll in a specific course (teaching class).
     */
    public boolean enrollCourse(CourseTab tab, CourseItem course) throws IOException {
        return enrollCourse(tab, course, 0);
    }

    /**
     * Enroll in a specific course (teaching class) with a per-request timeout.
     */
    public boolean enrollCourse(CourseTab tab, CourseItem course, int timeoutMs) throws IOException {
        CourseQueryContext ctx = lastContext;
        if (ctx == null) {
            throw new IllegalStateException("Context not initialized. Please search for courses first.");
        }
        System.out.println(course);
        // Mimic saveCourse logic
        String rlkz = val(ctx.getRlkz(), "0");
        String cdrlkz = val(ctx.getCdrllkz(), "0");
        String rlzlkz = val(ctx.getRlzlkz(), "0");
        String sxbj = ("1".equals(rlkz) || "1".equals(cdrlkz) || "1".equals(rlzlkz)) ? "1" : "0";

        RequestBody body = new FormBody.Builder()
                .add("jxb_ids", val(course.getDoJxbId()))
                .add("kch_id", val(course.getKchId()))
                .add("kcmc", val(course.getKcmc()))
                .add("rwlx", val(ctx.getRwlx(), "1"))
                .add("rlkz", rlkz)
                .add("cdrlkz", cdrlkz)
                .add("rlzlkz", rlzlkz)
                .add("sxbj", sxbj)
                .add("xxkbj", val(course.getXxkbj(), "0"))
                .add("qz", "0")
                .add("cxbj", val(course.getCxbj(), "0"))
                .add("xkkz_id", val(tab.getXkkzId()))
                .add("njdm_id", val(tab.getNjdmId()))
                .add("zyh_id", val(tab.getZyhId()))
                .add("kklxdm", val(tab.getKklxdm()))
                .add("xklc", val(ctx.getXklc()))
                .add("xkxnm", val(userInfo.getXkxnm()))
                .add("xkxqm", val(userInfo.getXkxqm()))
                .add("jcxx_id", "")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = execute(request, timeoutMs)) {
            ensureSuccess(response);
            String json = Objects.requireNonNull(response.body()).string();
            System.out.println("DEBUG Enroll Response: " + json);
            return json.contains("\"flag\":\"1\"");
        }
    }

    /**
     * Enroll in a course or sub-courses using formatted jxb_ids.
     * 
     * @param jxbIds Comma-separated do_jxb_ids
     */
    public boolean enrollCourse(CourseTab tab, CourseItem headCourse, String jxbIds) throws IOException {
        return enrollCourse(tab, headCourse, jxbIds, 0);
    }

    /**
     * Enroll in a course or sub-courses using formatted jxb_ids with a per-request
     * timeout.
     */
    public boolean enrollCourse(CourseTab tab, CourseItem headCourse, String jxbIds, int timeoutMs) throws IOException {
        CourseQueryContext ctx = lastContext;
        if (ctx == null) {
            throw new IllegalStateException("Context not initialized.");
        }

        String rlkz = val(ctx.getRlkz(), "0");
        String cdrlkz = val(ctx.getCdrllkz(), "0");
        String rlzlkz = val(ctx.getRlzlkz(), "0");
        String sxbj = ("1".equals(rlkz) || "1".equals(cdrlkz) || "1".equals(rlzlkz)) ? "1" : "0";

        RequestBody body = new FormBody.Builder()
                .add("jxb_ids", jxbIds)
                .add("kch_id", val(headCourse.getKchId()))
                .add("kcmc", val(headCourse.getKcmc()))
                .add("rwlx", val(ctx.getRwlx(), "1"))
                .add("rlkz", rlkz)
                .add("cdrlkz", cdrlkz)
                .add("rlzlkz", rlzlkz)
                .add("sxbj", sxbj)
                .add("xxkbj", val(headCourse.getXxkbj(), "0"))
                .add("qz", "0")
                .add("cxbj", val(headCourse.getCxbj(), "0"))
                .add("xkkz_id", val(tab.getXkkzId()))
                .add("njdm_id", val(tab.getNjdmId()))
                .add("zyh_id", val(tab.getZyhId()))
                .add("kklxdm", val(tab.getKklxdm()))
                .add("xklc", val(ctx.getXklc()))
                .add("xkxnm", val(userInfo.getXkxnm()))
                .add("xkxqm", val(userInfo.getXkxqm()))
                .add("jcxx_id", "")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = execute(request, timeoutMs)) {
            ensureSuccess(response);
            String json = Objects.requireNonNull(response.body()).string();
            // System.out.println("DEBUG Enroll Response: " + json);
            return json.contains("\"flag\":\"1\"");
        }
    }

    /**
     * Fetch sub-classes (e.g. labs) for a given teaching class.
     */
    public List<CourseItem> fetchChildClasses(CourseTab tab, CourseItem parent) throws IOException {
        CourseQueryContext ctx = lastContext; // Should be available
        RequestBody body = new FormBody.Builder()
                .add("xkxnm", val(userInfo.getXkxnm()))
                .add("xkxqm", val(userInfo.getXkxqm()))
                .add("xkly", "1")
                .add("jxb_id", val(parent.getDoJxbId()))
                .add("jxbzls", "2") // Likely "Number of sub-classes" or similar? The curl says 2.
                .add("cdrlkz", val(ctx.getCdrllkz(), "0"))
                .add("rlkz", val(ctx.getRlkz(), "0"))
                .add("rlzlkz", val(ctx.getRlzlkz(), "0"))
                .add("rwlx", val(ctx.getRwlx(), "1"))
                .add("syqz", "100")
                .add("zyfx_id", val(userInfo.getZyfxId()))
                .add("bh_id", val(userInfo.getBhId()))
                .add("zyh_id", val(userInfo.getZyhId()))
                .add("txbsfrl", "0")
                .add("njdm_id", val(userInfo.getNjdmId()))
                .add("sfkknj", "1")
                .add("gnjkxdnj", "0")
                .add("sfkkzy", "1")
                .add("zh", "")
                .add("sfznkx", "0")
                .add("kklxdm", val(tab.getKklxdm()))
                .add("bklx_id", val(tab.getBklxId(), "0"))
                .add("xklc", val(ctx.getXklc()))
                .add("kkbk", "0")
                .add("kkbkdj", "0")
                .add("bklbkcj", "0")
                .add("fxbj", val(parent.getFxbj(), "0"))
                .add("cxbj", val(parent.getCxbj(), "0"))
                .add("kzybkxy", "0")
                .add("zcongbj", "0")
                .build();
        FormBody b = (FormBody) body;
        for (int i = 0; i < b.size(); i++) {
            System.out.println(b.name(i) + " : " + b.value(i));
        }

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzb_xkZyDisplayZzxkYzbZjxb.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String json = Objects.requireNonNull(response.body()).string();
            // System.out.println("DEBUG Child Classes JSON: " + json);
            if (json.trim().startsWith("[")) {
                return JSON.parseArray(json, CourseItem.class);
            }
            return Collections.emptyList();
        }
    }

    /**
     * Smartly resolve the enrollment IDs.
     * If the course looks like a multi-part course (jxbzls == 2), it fetches
     * children, groups them, and returns the joined IDs.
     * Otherwise returns null (meaning standard enrollment).
     */
    public String getSmartJxbIds(CourseTab tab, CourseItem detail) {
        try {
            int zls = 0;
            try {
                if (detail.getJxbzls() != null)
                    zls = Integer.parseInt(detail.getJxbzls().trim());
            } catch (Exception e) {
            }

            if (zls == 2) {
                // Fetch sub-classes
                List<CourseItem> children = fetchChildClasses(tab, detail);
                if (children != null && !children.isEmpty()) {
                    Map<String, String> distinctMap = new HashMap<>();
                    // Group by xsdm (01=Lecture, 02=Lab etc)
                    for (CourseItem child : children) {
                        String xsdm = child.getXsdm();
                        if (xsdm != null && !distinctMap.containsKey(xsdm)) {
                            distinctMap.put(xsdm, child.getDoJxbId());
                        }
                    }

                    if (distinctMap.size() > 1) {
                        return String.join(",", distinctMap.values());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Fetch the list of already enrolled courses.
     */
    public List<CourseItem> fetchEnrolledCourses() throws IOException {
        if (userInfo == null)
            fetchCourseTabs();
        RequestBody body = new FormBody.Builder()
                .add("jg_id", val(userInfo.getJgId()))
                .add("zyh_id", val(userInfo.getZyhId()))
                .add("njdm_id", val(userInfo.getNjdmId()))
                .add("zyfx_id", val(userInfo.getZyfxId()))
                .add("bh_id", val(userInfo.getBhId()))
                .add("xz", val(userInfo.getXz()))
                .add("ccdm", val(userInfo.getCcdm()))
                .add("xqh_id", val(userInfo.getXqhId()))
                .add("xkxnm", val(userInfo.getXkxnm()))
                .add("xkxqm", val(userInfo.getXkxqm()))
                .add("xkly", "1")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbChoosedDisplay.html?gnmkdm=N253512")
                .post(body)
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String json = Objects.requireNonNull(response.body()).string();
            // System.out.println("DEBUG Enrolled Courses JSON: " + json);

            if (json.trim().startsWith("[")) {
                return JSON.parseArray(json, CourseItem.class);
            }
            return Collections.emptyList();
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

    public boolean validateSession() throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/xtgl/index_initMenu.html?jsdm=xs")
                .header("Cookie", requireCookie())
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String finalUrl = response.request().url().toString();
            String html = Objects.requireNonNull(response.body()).string();
            return finalUrl.contains("/jwglxt/xtgl/index_initMenu.html")
                    && !html.contains("/sso/jasiglogin");
        }
    }

    /**
     * Query student grades. An empty year or semester means all available values.
     */
    public String fetchCourseData(String academicYear, String semester, int pageSize) throws IOException {
        RequestBody formBody = new FormBody.Builder()
                .add("xnm", val(academicYear))
                .add("xqm", val(semester))
                .add("_search", "false")
                .add("nd", String.valueOf(Instant.now().toEpochMilli()))
                .add("queryModel.showCount", String.valueOf(Math.max(15, pageSize)))
                .add("queryModel.currentPage", "1")
                .add("queryModel.sortName", "xnm,xqm,kch")
                .add("queryModel.sortOrder", "desc")
                .add("time", "1")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/jwglxt/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005")
                .post(formBody)
                .header("Cookie", requireCookie())
                .header("Content-Type", FORM.toString())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", BASE_URL + "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ensureSuccess(response);
            String content = Objects.requireNonNull(response.body()).string();
            if (content.trim().startsWith("<")) {
                throw new IOException("登录状态已失效，请重新登录。");
            }
            return content;
        }
    }

    /*
     * ----------------------------------------------------------
     * Private helpers
     * ----------------------------------------------------------
     */

    private UserInfo parseUserInfoFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Elements inputs = doc.select("input[type=hidden]");
        UserInfo info = new UserInfo();

        inputs.forEach(input -> {
            switch (input.attr("name")) {
                case "xqh_id":
                    info.setXqhId(input.attr("value"));
                    break;
                case "jg_id_1":
                    info.setJgId(input.attr("value"));
                    break;
                case "njdm_id":
                    info.setNjdmId(input.attr("value"));
                    break;
                case "zyh_id":
                    info.setZyhId(input.attr("value"));
                    break;
                case "zyfx_id":
                    info.setZyfxId(input.attr("value"));
                    break;
                case "bh_id":
                    info.setBhId(input.attr("value"));
                    break;
                case "xbm":
                    info.setXbm(input.attr("value"));
                    break;
                case "xslbdm":
                    info.setXslbdm(input.attr("value"));
                    break;
                case "mzm":
                    info.setMzm(input.attr("value"));
                    break;
                case "xz":
                    info.setXz(input.attr("value"));
                    break;
                case "ccdm":
                    info.setCcdm(input.attr("value"));
                    break;
                case "xsbj":
                    info.setXsbj(input.attr("value"));
                    break;
                case "njdm_id_xs":
                    info.setNjdmId_xs(input.attr("value"));
                    break;
                case "zyh_id_xs":
                    info.setZyhId_xs(input.attr("value"));
                    break;
                case "xkxnm":
                    info.setXkxnm(input.attr("value"));
                    break;
                case "xkxqm":
                    info.setXkxqm(input.attr("value"));
                    break;
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
        this.lastContext = ctx; // Cache the context
        return ctx;
    }

    private void mapHiddenInputToField(CourseQueryContext ctx, String name, String value) {
        if (name == null || name.isEmpty())
            return;
        try {
            Field field = CourseQueryContext.class.getDeclaredField(toCamel(name));
            field.setAccessible(true);
            field.set(ctx, value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            // log.debug("Ignore unknown field: {}", name);
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

    private Response execute(Request request, int timeoutMs) throws IOException {
        if (timeoutMs <= 0) {
            return httpClient.newCall(request).execute();
        }
        int normalizedTimeoutMs = Math.max(200, timeoutMs);
        OkHttpClient timeoutClient = httpClient.newBuilder()
                .callTimeout(normalizedTimeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(normalizedTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(normalizedTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(normalizedTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        try {
            return timeoutClient.newCall(request).execute();
        } catch (InterruptedIOException ex) {
            throw new IOException("请求超时(" + normalizedTimeoutMs + "ms)", ex);
        }
    }

    private String val(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    public static void main(String[] args) throws IOException {
        EduService service = new EduService();
        String cookie = "JSESSIONID=C63D945A712820DFE214F8E3B022CAF7; route=5a9875512de08d173cb2b599f226035c";
        service.setCookie(cookie);

        List<CourseTab> tabs = service.fetchCourseTabs();
        System.out.println(service.userInfo);
        for (CourseTab tab : tabs) {
            System.out.println(tab);
            // break;
        }
        System.out.println(service.fetchCourseQueryContextFiltered(tabs.get(0), "01501"));

        // Test parsing
        // List<CourseItem> courses = service.fetchCourseList(tabs.get(0), "01501");
        // System.out.println(courses);
    }
}
