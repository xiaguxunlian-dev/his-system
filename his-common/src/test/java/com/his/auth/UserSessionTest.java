package com.his.auth;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserSession 单例模式单元测试
 * 每个测试前 logout() 清理状态，保证测试隔离
 */
class UserSessionTest {

    private UserSession session;

    @BeforeEach
    void setUp() {
        session = UserSession.getInstance();
        session.logout(); // 清理之前的登录状态
    }

    @AfterEach
    void tearDown() {
        session.logout();
    }

    @Test
    @DisplayName("getInstance - 返回同一个实例")
    void getInstance_sameInstance() {
        UserSession s2 = UserSession.getInstance();
        assertSame(session, s2);
    }

    @Test
    @DisplayName("login - 设置用户信息")
    void login_setsUserInfo() {
        session.login(1, "admin", "管理员", UserRole.管理员, null, null);

        assertTrue(session.isLoggedIn());
        assertEquals(1, session.getUserId());
        assertEquals("admin", session.getUsername());
        assertEquals("管理员", session.getDisplayName());
        assertEquals(UserRole.管理员, session.getRole());
        assertNull(session.getDepartmentId());
        assertNull(session.getDepartmentName());
        assertTrue(session.getLoginTime() > 0);
    }

    @Test
    @DisplayName("login - 带科室信息")
    void login_withDepartment() {
        session.login(2, "doctor", "张医生", UserRole.门诊医生, 10, "内科");

        assertEquals(10, session.getDepartmentId());
        assertEquals("内科", session.getDepartmentName());
    }

    @Test
    @DisplayName("logout - 清除所有信息")
    void logout_clearsAll() {
        session.login(1, "admin", "管理员", UserRole.管理员, null, null);
        assertTrue(session.isLoggedIn());

        session.logout();

        assertFalse(session.isLoggedIn());
        assertEquals(0, session.getUserId());
        assertNull(session.getUsername());
        assertNull(session.getDisplayName());
        assertNull(session.getRole());
    }

    @Test
    @DisplayName("isLoggedIn - 未登录返回 false")
    void isLoggedIn_notLoggedIn() {
        assertFalse(session.isLoggedIn());
    }

    @Test
    @DisplayName("isLoggedIn - 登录后返回 true")
    void isLoggedIn_afterLogin() {
        session.login(1, "admin", "管理员", UserRole.管理员, null, null);
        assertTrue(session.isLoggedIn());
    }

    @Test
    @DisplayName("isAdmin - 管理员返回 true")
    void isAdmin_adminRole() {
        session.login(1, "admin", "管理员", UserRole.管理员, null, null);
        assertTrue(session.isAdmin());
    }

    @Test
    @DisplayName("isAdmin - 非管理员返回 false")
    void isAdmin_nonAdminRole() {
        session.login(2, "doctor", "医生", UserRole.门诊医生, 1, "内科");
        assertFalse(session.isAdmin());
    }

    @Test
    @DisplayName("hasRole - 管理员始终返回 true")
    void hasRole_adminAlwaysTrue() {
        session.login(1, "admin", "管理员", UserRole.管理员, null, null);
        assertTrue(session.hasRole(UserRole.挂号员));
        assertTrue(session.hasRole(UserRole.门诊医生, UserRole.住院医生));
        assertTrue(session.hasRole(UserRole.管理员)); // 自身角色也通过
    }

    @Test
    @DisplayName("hasRole - 匹配角色返回 true")
    void hasRole_matchRole() {
        session.login(2, "doctor", "门诊医生", UserRole.门诊医生, 1, "内科");
        assertTrue(session.hasRole(UserRole.门诊医生));
        assertTrue(session.hasRole(UserRole.门诊医生, UserRole.住院医生));
    }

    @Test
    @DisplayName("hasRole - 不匹配角色返回 false")
    void hasRole_noMatch() {
        session.login(2, "doctor", "门诊医生", UserRole.门诊医生, 1, "内科");
        assertFalse(session.hasRole(UserRole.管理员));
        assertFalse(session.hasRole(UserRole.收费员));
    }

    @Test
    @DisplayName("getDisplayInfo - 未登录")
    void getDisplayInfo_notLoggedIn() {
        assertEquals("未登录", session.getDisplayInfo());
    }

    @Test
    @DisplayName("getDisplayInfo - 已登录")
    void getDisplayInfo_loggedIn() {
        session.login(1, "admin", "系统管理员", UserRole.管理员, null, null);
        assertEquals("系统管理员（管理员）", session.getDisplayInfo());
    }

    @Test
    @DisplayName("login - 覆盖之前的登录信息")
    void login_overwritesPrevious() {
        session.login(1, "admin", "管理员", UserRole.管理员, null, null);
        session.login(2, "doctor", "医生", UserRole.门诊医生, 5, "外科");

        assertEquals(2, session.getUserId());
        assertEquals("doctor", session.getUsername());
        assertEquals(UserRole.门诊医生, session.getRole());
        assertEquals(5, session.getDepartmentId());
    }
}
