package me.yeoc.educlient.object;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class CourseItem {
    @JSONField(name = "jxb_id")
    private String jxbId;

    @JSONField(name = "jxbzls")
    private String jxbzls; // 教学班种类数 (Teaching Class Types Count, e.g. 2 for Lecture+Lab)

    @JSONField(name = "kch_id")
    private String kchId;

    @JSONField(name = "kch")
    private String kch; // 课程代码/编号

    @JSONField(name = "kcmc")
    private String kcmc; // 课程名称

    @JSONField(name = "jxbmc")
    private String jxbmc; // 教学班名称

    @JSONField(name = "xf")
    private String xf; // 学分

    @JSONField(name = "rwzxs")
    private String rwzxs; // 任务总学时
    @JSONField(name = "jxbrl") // 教学班容量 (Used in Detail List)
    private String jxbrl;

    @JSONField(name = "jxbrs") // 教学班人数 (Capacity in Enrolled List / Total Students)
    private String jxbrs; // 教学班容量

    @JSONField(name = "yxzrs")
    private String yxzrs; // 已选总人数

    @JSONField(name = "kklxdm")
    private String kklxdm; // 课程类型代码

    @JSONField(name = "jsxx") // 教师信息
    private String jsxx;

    @JSONField(name = "sksj") // 上课时间
    private String sksj;

    @JSONField(name = "cxbj")
    private String cxbj; // 重修标记?

    @JSONField(name = "fxbj")
    private String fxbj; // 方向标记?

    @JSONField(name = "do_jxb_id")
    private String doJxbId;

    @JSONField(name = "xxkbj")
    private String xxkbj;

    @JSONField(name = "xsdm")
    private String xsdm; // Student Type Code (01=Lecture, 02=Lab etc)

    public String getTeacherName() {
        if (jsxx == null || jsxx.isEmpty()) {
            return "";
        }
        // Format: ID/Name/Title;ID/Name/Title
        StringBuilder names = new StringBuilder();
        String[] teachers = jsxx.split(";");
        for (String teacher : teachers) {
            String[] parts = teacher.split("/");
            if (parts.length > 1 && !"--".equals(parts[1])) {
                if (names.length() > 0)
                    names.append("、");
                names.append(parts[1]);
            }
        }
        return names.length() == 0 ? jsxx : names.toString();
    }
}
