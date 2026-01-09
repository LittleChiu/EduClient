package me.yeoc.educlient.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.json";

    public static void saveConfig(String sessionId, String route, boolean remember) {
        if (!remember) {
            new File(CONFIG_FILE).delete();
            return;
        }
        JSONObject json = new JSONObject();
        json.put("sessionId", sessionId);
        json.put("route", route);
        json.put("remember", true);

        try {
            Files.write(new File(CONFIG_FILE).toPath(), json.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists())
            return null;

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return JSON.parseObject(content);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
