package me.yeoc.educlient.object;

import lombok.Data;

@Data
public class UserInfo {
    private String xqhId;      // 校区ID
    private String jgId;       // 学院ID
    private String njdmId;     // 年级代码
    private String zyhId;      // 专业ID
    private String zyfxId;     // 专业方向
    private String bhId;       // 班号ID
    private String xbm;        // 性别码
    private String xslbdm;     // 学生类别代码
    private String mzm;        // 民族码
    private String xz;         // 学制
    private String ccdm;       // 层次代码（如本科）
    private String xsbj;       // 学生标记
    private String njdmId_xs;  // 学生年级
    private String zyhId_xs;   // 学生专业
    private String xkxnm;      // 学年
    private String xkxqm;      // 学期
}