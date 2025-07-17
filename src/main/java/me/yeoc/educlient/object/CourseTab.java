package me.yeoc.educlient.object;

import lombok.Data;

@Data
public class CourseTab {
    private String name;      // 课程分类名称（如 主修课程）
    private String kklxdm;    // 课程类型代码
    private String xkkzId;    // 选课控制ID
    private String njdmId;    // 年级代码
    private String zyhId;     // 专业ID
}
