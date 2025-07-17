package me.yeoc.educlient.service;

import lombok.Getter;
import lombok.Setter;
import me.yeoc.educlient.object.CourseQueryContext;
import me.yeoc.educlient.object.CourseTab;
import me.yeoc.educlient.object.UserInfo;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EduService {


    private final OkHttpClient client = new OkHttpClient();
    private final String baseUrl = "https://jwglxt.zstu.edu.cn";
    @Setter
    private String cookie;
    @Getter
    private UserInfo userInfo;

    public List<CourseTab> fetchCourseTabs() throws IOException {
        String coursePageUrl = baseUrl + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default";
//        System.out.println(sessionId);
        Request request = new Request.Builder()
                .url(coursePageUrl)
                .addHeader("Cookie", cookie)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败，状态码：" + response.code());
            }

            String html = Objects.requireNonNull(response.body()).string();
            userInfo = parseUserInfoFromHtml(html);
            return parseCourseTabsFromHtml(html);
        }
    }
    public UserInfo parseUserInfoFromHtml(String html) {
        UserInfo userInfo = new UserInfo();
        Document doc = Jsoup.parse(html);

        // 先解析 <input type="hidden" ...> 字段
        Elements inputs = doc.select("input[type=hidden]");
        for (Element input : inputs) {
            String name = input.attr("name");
            String value = input.attr("value");

            switch (name) {
                case "xqh_id": userInfo.setXqhId(value); break;
                case "jg_id": userInfo.setJgId(value); break;
                case "njdm_id": userInfo.setNjdmId(value); break;
                case "zyh_id": userInfo.setZyhId(value); break;
                case "zyfx_id": userInfo.setZyfxId(value); break;
                case "bh_id": userInfo.setBhId(value); break;
                case "xbm": userInfo.setXbm(value); break;
                case "xslbdm": userInfo.setXslbdm(value); break;
                case "mzm": userInfo.setMzm(value); break;
                case "xz": userInfo.setXz(value); break;
                case "ccdm": userInfo.setCcdm(value); break;
                case "xsbj": userInfo.setXsbj(value); break;
                case "njdm_id_xs": userInfo.setNjdmId_xs(value); break;
                case "zyh_id_xs": userInfo.setZyhId_xs(value); break;
                case "xkxnm": userInfo.setXkxnm(value); break;
                case "xkxqm": userInfo.setXkxqm(value); break;
            }
        }

        return userInfo;
    }
    private List<CourseTab> parseCourseTabsFromHtml(String html) {
        List<CourseTab> tabs = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "queryCourse\\(this,'(\\d+?)','(.*?)','(\\d+?)','(\\d+?)'\\).*?>(.*?)<"
        );
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            CourseTab tab = new CourseTab();
            tab.setKklxdm(matcher.group(1));
            tab.setXkkzId(matcher.group(2));
            tab.setNjdmId(matcher.group(3));
            tab.setZyhId(matcher.group(4));
            tab.setName(matcher.group(5).replaceAll("<.*?>", "").trim());
            tabs.add(tab);
        }

        return tabs;
    }
    private String toCamel(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
        }
        return sb.toString();
    }
    private CourseQueryContext parseCourseQueryContextFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Elements inputs = doc.select("input[type=hidden]");

        CourseQueryContext context = new CourseQueryContext();
        for (Element input : inputs) {
            String name = input.attr("name");
            String value = input.attr("value");

            if (name == null || name.isEmpty()) continue;

            try {
                Field field = CourseQueryContext.class.getDeclaredField(toCamel(name));
                field.setAccessible(true);
                field.set(context, value);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // 若无对应字段或无法设置，则跳过
            }
        }
        return context;
    }
    public CourseQueryContext fetchCourseQueryContext(CourseTab tab) throws IOException {
        String url = baseUrl + "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html?gnmkdm=N253512";

        RequestBody formBody = new FormBody.Builder()
                .add("xkkz_id", tab.getXkkzId())
                .add("xszxzt", "1")
                .add("njdm_id", tab.getNjdmId())
                .add("zyh_id", tab.getZyhId())
                .add("kspage", "0")
                .add("jspage", "0")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Cookie", cookie)
                .addHeader("Origin", baseUrl)
//                .addHeader("Referer", coursePageUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Accept", "text/html, */*; q=0.01")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败，状态码：" + response.code());
            }

            String html = Objects.requireNonNull(response.body()).string();
            return parseCourseQueryContextFromHtml(html);
        }
    }

    public static void main(String[] args) throws IOException {
        EduService service = new EduService();
        String cookie = "";
        service.setCookie(cookie);
        List<CourseTab> tabs = service.fetchCourseTabs();
        System.out.println(service.getUserInfo());
//        for (CourseTab tab : tabs) {
//            System.out.println(service.fetchCourseQueryContext(tab));
////            break;
//        }
//        tabs.forEach(System.out::println);
    }
}
