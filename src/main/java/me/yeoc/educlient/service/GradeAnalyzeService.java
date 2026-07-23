package me.yeoc.educlient.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import me.yeoc.educlient.object.GradeRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GradeAnalyzeService {
    public List<GradeRecord> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        JSONObject root = JSON.parseObject(json);
        JSONArray items = root == null ? null : root.getJSONArray("items");
        if (items == null) {
            return Collections.emptyList();
        }

        List<GradeRecord> records = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            GradeRecord record = new GradeRecord();
            record.setAcademicYear(text(item, "xnmmc", "xnm"));
            record.setSemester(text(item, "xqmmc", "xqm"));
            record.setCourseCode(text(item, "kch", "kch_id"));
            record.setCourseName(text(item, "kcmc"));
            record.setCourseNature(text(item, "kcxzmc"));
            record.setCredits(text(item, "xf"));
            record.setScore(text(item, "cj", "bfzcj"));
            record.setGradePoint(text(item, "jd"));
            record.setScoreNature(text(item, "cjxzmc"));
            record.setDegreeCourse(text(item, "sfxwkc"));
            record.setCollege(text(item, "kkbmmc", "kkxy"));
            record.setTeacher(text(item, "jsxm"));
            record.setAssessmentMethod(text(item, "khfsmc"));
            records.add(record);
        }
        return records;
    }

    public double averageNumericScore(List<GradeRecord> records) {
        double sum = 0;
        int count = 0;
        for (GradeRecord record : records) {
            try {
                sum += Double.parseDouble(record.getScore());
                count++;
            } catch (Exception ignored) {
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    public double weightedGpa(List<GradeRecord> records) {
        double weighted = 0;
        double credits = 0;
        for (GradeRecord record : records) {
            try {
                double credit = Double.parseDouble(record.getCredits());
                double point = Double.parseDouble(record.getGradePoint());
                weighted += credit * point;
                credits += credit;
            } catch (Exception ignored) {
            }
        }
        return credits == 0 ? Double.NaN : weighted / credits;
    }

    private String text(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
