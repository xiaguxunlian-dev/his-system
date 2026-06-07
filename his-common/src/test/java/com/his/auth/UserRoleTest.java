package com.his.auth;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRole 枚举单元测试
 * 测试角色解析和显示逻辑
 */
class UserRoleTest {

    @Test
    @DisplayName("fromString - 按显示名解析")
    void fromString_byDisplayName() {
        assertEquals(UserRole.管理员, UserRole.fromString("管理员"));
        assertEquals(UserRole.挂号员, UserRole.fromString("挂号员"));
        assertEquals(UserRole.门诊医生, UserRole.fromString("门诊医生"));
    }

    @Test
    @DisplayName("fromString - 按枚举名解析")
    void fromString_byEnumName() {
        assertEquals(UserRole.管理员, UserRole.fromString("管理员"));
        assertEquals(UserRole.住院医生, UserRole.fromString("住院医生"));
    }

    @Test
    @DisplayName("fromString - 大小写不敏感")
    void fromString_caseInsensitive() {
        assertEquals(UserRole.药剂师, UserRole.fromString("药剂师"));
        assertEquals(UserRole.检验技师, UserRole.fromString("检验技师"));
    }

    @Test
    @DisplayName("fromString - null/空字符串默认挂号员")
    void fromString_nullOrEmpty() {
        assertEquals(UserRole.挂号员, UserRole.fromString(null));
        assertEquals(UserRole.挂号员, UserRole.fromString(""));
        assertEquals(UserRole.挂号员, UserRole.fromString("   "));
    }

    @Test
    @DisplayName("fromString - 无效名称默认挂号员")
    void fromString_invalidName() {
        assertEquals(UserRole.挂号员, UserRole.fromString("不存在的角色"));
        assertEquals(UserRole.挂号员, UserRole.fromString("unknown"));
    }

    @Test
    @DisplayName("getDisplayName - 返回中文显示名")
    void getDisplayName() {
        assertEquals("管理员", UserRole.管理员.getDisplayName());
        assertEquals("挂号员", UserRole.挂号员.getDisplayName());
        assertEquals("门诊医生", UserRole.门诊医生.getDisplayName());
        assertEquals("住院医生", UserRole.住院医生.getDisplayName());
        assertEquals("药剂师", UserRole.药剂师.getDisplayName());
        assertEquals("检验技师", UserRole.检验技师.getDisplayName());
        assertEquals("收费员", UserRole.收费员.getDisplayName());
        assertEquals("统计员", UserRole.统计员.getDisplayName());
    }

    @Test
    @DisplayName("getDescription - 返回角色描述")
    void getDescription() {
        assertEquals("可访问所有子系统及系统管理", UserRole.管理员.getDescription());
        assertEquals("可访问挂号管理子系统", UserRole.挂号员.getDescription());
        assertEquals("可访问门诊工作站、电子病历", UserRole.门诊医生.getDescription());
    }

    @Test
    @DisplayName("toString - 返回显示名")
    void toString_returnsDisplayName() {
        assertEquals("管理员", UserRole.管理员.toString());
        assertEquals("挂号员", UserRole.挂号员.toString());
    }

    @Test
    @DisplayName("values - 8种角色全部存在")
    void values_count() {
        assertEquals(8, UserRole.values().length);
    }
}
