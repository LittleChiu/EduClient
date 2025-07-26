import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import okhttp3.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Main {
    private final String baseUrl;
    private final String cookie;
    private final OkHttpClient client;
    private final String outputDir; // 输出文件目录

    // 线程池 - 可根据需要调整核心线程数
    private final ExecutorService executorService = Executors.newFixedThreadPool(
            4
    );

    public Main(String baseUrl, String cookie, String outputDir) {
        this.baseUrl = baseUrl;
        this.cookie = cookie;
        this.outputDir = outputDir;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // 确保输出目录存在
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 多线程批量抓取
     * @param indices 需要抓取的索引集合
     * @return 所有任务的Future结果
     */
    public List<Future<String>> fetchAll(List<Integer> indices) {
        return indices.stream()
                .map(index -> executorService.submit(() -> fetchAndSave(index)))
                .collect(Collectors.toList());
    }

    /**
     * 抓取单个页面并保存到文件
     * @param i 索引值
     * @return 页面HTML内容
     * @throws IOException 网络或IO异常
     */
    private String fetchAndSave(int i) throws IOException {
        String html = fetch(i);
        // 保存到文件，文件名包含索引以便区分
        saveToFile(html, i);
        return html;
    }

    /**
     * 抓取单个页面内容
     */
    private String fetch(int i) throws IOException {
        String coursePageUrl = baseUrl + "/jwglxt/xsxxxggl/xsgrxxwh_cxXsgrxx.html?gnmkdm=N"+i+"&layout=default#";

        Request request = new Request.Builder()
                .url(coursePageUrl)
                .addHeader("Cookie", cookie)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败，状态码：" + response.code() + "，索引：" + i);
            }

            return Objects.requireNonNull(response.body()).string();
        }
    }

    /**
     * 将HTML内容保存到文件
     */
    private void saveToFile(String html, int index) throws IOException {
        // 解析HTML提取需要的内容
        Document doc = Jsoup.parse(html);
//        System.out.println(html);
        Elements anchors = doc.select("a[onclick*=onClickMenu.call]");

        // 用Set去重
        Set<String> liHtmlSet = new LinkedHashSet<>();
        for (Element a : anchors) {
            Element li = a.closest("li");
            if (li != null) {
                liHtmlSet.add(li.outerHtml());
            }
        }
        if (!liHtmlSet.isEmpty()){
            // 构建输出文件名
            String fileName = outputDir + File.separator + "result_" + index + ".html";
            System.out.println("saved "+index);
            // 写入文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                writer.write(String.join("\n", liHtmlSet));
            }
        }

    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    // 使用示例
    public static void main(String[] args) {
        String baseUrl = "https://jwglxt.zstu.edu.cn"; // 替换为实际的基础URL
        String cookie =
                "JSESSIONID=F3E65B3938F5263D0F2E584B9D74BC15; old_device_token=48a1ab76bcf56a38e2effed01b55b4dc; device_token=48a1ab76bcf56a38e2effed01b55b4dc; route=0303183e34eb4f9d5f46294b2fb9a86f";
        String outputDir = "./output"; // 输出文件目录



        // 需要抓取的索引列表


        for (int i = 10; i < 10000; i+=100) {
            List<Integer> indices = new ArrayList<>(); // 根据实际需求修改
            for (int j = 0; j < 100; j++) {
                indices.add(i+j);
            }
            Main fetcher = new Main(baseUrl, cookie, outputDir);
            try {
                // 提交所有任务
                List<Future<String>> futures = fetcher.fetchAll(indices);
                // 等待所有任务完成并处理结果
                for (Future<String> future : futures) {
                    // 如果需要获取结果可以在这里处理
                    // String result = future.get();
                }
                System.out.println("任务"+i+"已完成，结果已保存到 " + outputDir);
            } finally {
                fetcher.shutdown();
            }
        }

    }
}
