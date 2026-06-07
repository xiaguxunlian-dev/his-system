package com.his.auth;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthService 单元测试
 * 测试密码哈希和 LoginResult 内部类
 * 注意：login() 方法依赖数据库连接，集成测试中测试
 */
class AuthServiceTest {

    @Test
    @DisplayName("hashPassword - 每次哈希值不同（随机盐）")
    void hashPassword_differentEachTime() {
        String hash1 = AuthService.hashPassword("password123");
        String hash2 = AuthService.hashPassword("password123");

        assertNotEquals(hash1, hash2); // 随机盐，不同
        assertTrue(hash1.startsWith("$2a$10$")); // BCrypt 格式
        assertTrue(hash2.startsWith("$2a$10$"));
    }

    @Test
    @DisplayName("hashPassword - 相同密码可验证")
    void hashPassword_verifySame() {
        String hash = AuthService.hashPassword("MySecret@2024");
        assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw("MySecret@2024", hash));
    }

    @Test
    @DisplayName("hashPassword - 不同密码不可验证")
    void hashPassword_verifyDifferent() {
        String hash = AuthService.hashPassword("password123");
        assertFalse(org.mindrot.jbcrypt.BCrypt.checkpw("password124", hash));
    }

    @Test
    @DisplayName("hashPassword - 空密码")
    void hashPassword_empty() {
        String hash = AuthService.hashPassword("");
        assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw("", hash));
    }

    @Test
    @DisplayName("LoginResult.fail - success=false, message 正确")
    void loginResult_fail() {
        AuthService.LoginResult result = AuthService.LoginResult.fail("用户名错误");

        assertFalse(result.success);
        assertEquals("用户名错误", result.message);
        assertEquals(0, result.userId);
        assertNull(result.displayName);
        assertNull(result.role);
        assertNull(result.departmentId);
    }

    @Test
    @DisplayName("LoginResult.ok - success=true, 字段正确")
    void loginResult_ok() {
        AuthService.LoginResult result = AuthService.LoginResult.ok(
                42, "张三", UserRole.门诊医生, 10);

        assertTrue(result.success);
        assertEquals("登录成功", result.message);
        assertEquals(42, result.userId);
        assertEquals("张三", result.displayName);
        assertEquals(UserRole.门诊医生, result.role);
        assertEquals(10, result.departmentId);
    }
}
