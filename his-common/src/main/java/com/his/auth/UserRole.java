package com.his.auth;

/**
 * 系统角色枚举
 * 每种角色对应一类医院工作岗位，控制能访问的子系统
 */
public enum UserRole {

    管理员("管理员", "可访问所有子系统及系统管理"),
    挂号员("挂号员", "可访问挂号管理子系统"),
    门诊医生("门诊医生", "可访问门诊工作站、电子病历"),
    住院医生("住院医生", "可访问住院管理、电子病历"),
    药剂师("药剂师", "可访问药品管理子系统"),
    检验技师("检验技师", "可访问检查检验子系统"),
    收费员("收费员", "可访问收费管理子系统"),
    统计员("统计员", "可访问统计报表子系统");

    private final String displayName;
    private final String description;

    UserRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /** 从字符串解析角色（大小写不敏感） */
    public static UserRole fromString(String name) {
        if (name == null) return 挂号员;
        for (UserRole r : values()) {
            if (r.displayName.equalsIgnoreCase(name.trim()) || r.name().equalsIgnoreCase(name.trim())) {
                return r;
            }
        }
        return 挂号员;
    }

    @Override
    public String toString() { return displayName; }
}
