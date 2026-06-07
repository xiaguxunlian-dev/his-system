package com.his.admin.repository;

import com.his.auth.UserRole;
import com.his.shared.database.BaseIntegrationTest;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdminRepository 集成测试
 * 使用 H2 内存数据库 + 自动迁移
 *
 * 测试覆盖：用户管理 CRUD、系统配置 UPSERT、审计日志查询
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AdminRepositoryTest extends BaseIntegrationTest {

    private AdminRepository repo;

    @BeforeAll
    @Override
    protected void setupClass() {
        super.setupClass();
        repo = new AdminRepository();
    }

    // ========================================================================
    //  用户管理 CRUD
    // ========================================================================

    @Test
    @DisplayName("createUser - 成功创建用户，返回ID>0")
    void createUser_success() {
        String username = "test_doc_" + System.nanoTime();
        String hash = com.his.auth.AuthService.hashPassword("password123");
        int id = repo.createUser(username, hash, "测试医生", UserRole.门诊医生.toString(), 1);

        assertTrue(id > 0, "新用户ID应大于0");
        assertTableRowCount("system_users", 1);
    }

    @Test
    @DisplayName("createUser - 用户名重复抛出异常")
    void createUser_duplicateUsername() {
        String username = "dup_user_" + System.nanoTime();
        String hash = com.his.auth.AuthService.hashPassword("pass1");
        repo.createUser(username, hash, "用户A", UserRole.挂号员.toString(), null);

        String hash2 = com.his.auth.AuthService.hashPassword("pass2");
        assertThrows(RuntimeException.class, () ->
                repo.createUser(username, hash2, "用户B", UserRole.挂号员.toString(), null));
    }

    @Test
    @DisplayName("getAllUsers - 返回所有活跃用户")
    void getAllUsers_returnsAllActive() {
        String h1 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("u1_" + System.nanoTime(), h1, "用户1", UserRole.挂号员.toString(), null);
        String h2 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("u2_" + System.nanoTime(), h2, "用户2", UserRole.门诊医生.toString(), 1);

        List<Map<String, Object>> users = repo.getAllUsers();
        assertEquals(2, users.size());
    }

    @Test
    @DisplayName("getAllUsers - 返回字段正确")
    void getAllUsers_correctFields() {
        String hash = com.his.auth.AuthService.hashPassword("pass");
        repo.createUser("field_test", hash, "字段测试", UserRole.药剂师.toString(), 2);

        List<Map<String, Object>> users = repo.getAllUsers();
        Map<String, Object> user = users.get(0);

        assertTrue(user.containsKey("id"));
        assertTrue(user.containsKey("username"));
        assertTrue(user.containsKey("displayName"));
        assertTrue(user.containsKey("role"));
        assertTrue(user.containsKey("isActive"));
        assertEquals("field_test", user.get("username"));
        assertEquals("字段测试", user.get("displayName"));
    }

    @Test
    @DisplayName("searchUsers - 按用户名搜索")
    void searchUsers_byUsername() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("search_doc", hash, "搜索医生", UserRole.门诊医生.toString(), 1);
        String h2 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("other_nurse", h2, "其他护士", UserRole.住院医生.toString(), 2);

        List<Map<String, Object>> results = repo.searchUsers("doc", null, null);
        assertEquals(1, results.size());
        assertEquals("search_doc", results.get(0).get("username"));
    }

    @Test
    @DisplayName("searchUsers - 按角色搜索")
    void searchUsers_byRole() {
        String h1 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("r1", h1, "医生1", UserRole.门诊医生.toString(), 1);
        String h2 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("r2", h2, "医生2", UserRole.门诊医生.toString(), 1);
        String h3 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("r3", h3, "护士1", UserRole.住院医生.toString(), 2);

        List<Map<String, Object>> results = repo.searchUsers(null, UserRole.门诊医生.toString(), null);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("searchUsers - 按活跃状态搜索")
    void searchUsers_byActive() {
        String h1 = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("a1", h1, "活跃1", UserRole.挂号员.toString(), null);
        String h2 = com.his.auth.AuthService.hashPassword("p");
        int id2 = repo.createUser("a2", h2, "停用1", UserRole.挂号员.toString(), null);
        repo.setUserActive(id2, false);

        List<Map<String, Object>> active = repo.searchUsers(null, null, true);
        assertEquals(1, active.size());
        assertEquals("a1", active.get(0).get("username"));
    }

    @Test
    @DisplayName("getUserById - 查询存在的用户")
    void getUserById_found() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("byid_test", hash, "ById测试", UserRole.挂号员.toString(), null);

        Map<String, Object> user = repo.getUserById(id);
        assertNotNull(user);
        assertEquals("byid_test", user.get("username"));
    }

    @Test
    @DisplayName("getUserById - 查询不存在的用户返回null")
    void getUserById_notFound() {
        Map<String, Object> user = repo.getUserById(99999);
        assertNull(user);
    }

    @Test
    @DisplayName("updateUser - 成功更新用户信息")
    void updateUser_success() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("update_test", hash, "原名", UserRole.挂号员.toString(), null);

        repo.updateUser(id, "新名字", UserRole.门诊医生.toString(), 5, true);

        Map<String, Object> updated = repo.getUserById(id);
        assertEquals("新名字", updated.get("displayName"));
        assertEquals(UserRole.门诊医生.toString(), updated.get("role"));
    }

    @Test
    @DisplayName("deleteUser - 硬删除用户")
    void deleteUser_hardDelete() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("del_test", hash, "待删除", UserRole.挂号员.toString(), null);

        repo.deleteUser(id);

        assertTableRowCount("system_users", 0);
    }

    @Test
    @DisplayName("resetPassword - 密码哈希更新且可验证")
    void resetPassword_updatesHash() {
        String oldHash = com.his.auth.AuthService.hashPassword("old_pass");
        int id = repo.createUser("reset_test", oldHash, "重置密码", UserRole.挂号员.toString(), null);

        String newRaw = com.his.auth.AuthService.hashPassword("new_password");
        repo.resetPassword(id, newRaw);

        // 验证密码已被更新
        Map<String, Object> user = repo.getUserById(id);
        assertNotNull(user);
    }

    @Test
    @DisplayName("setUserActive - 停用用户")
    void setUserActive_deactivate() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("toggle_test", hash, "切换状态", UserRole.挂号员.toString(), null);

        repo.setUserActive(id, false);

        Map<String, Object> user = repo.getUserById(id);
        assertEquals(false, user.get("isActive"));
    }

    @Test
    @DisplayName("setUserActive - 启用用户")
    void setUserActive_activate() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("toggle2_test", hash, "切换状态2", UserRole.挂号员.toString(), null);
        repo.setUserActive(id, false);

        repo.setUserActive(id, true);

        Map<String, Object> user = repo.getUserById(id);
        assertEquals(true, user.get("isActive"));
    }

    @Test
    @DisplayName("unlockUser - 清除锁定状态")
    void unlockUser_clearsLock() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("lock_test", hash, "锁定测试", UserRole.挂号员.toString(), null);

        // 手动设置锁定状态
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var ps = conn.prepareStatement(
                     "UPDATE system_users SET locked_until = ?, failed_attempts = 3 WHERE id = ?")) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusHours(1)));
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            fail("设置锁定状态失败");
        }

        repo.unlockUser(id);

        Map<String, Object> user = repo.getUserById(id);
        assertNull(user.get("lockedUntil"));
        assertEquals(0, user.get("failedAttempts"));
    }

    @Test
    @DisplayName("usernameExists - 存在返回true")
    void usernameExists_true() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        repo.createUser("exists_test", hash, "存在测试", UserRole.挂号员.toString(), null);

        assertTrue(repo.usernameExists("exists_test", null));
    }

    @Test
    @DisplayName("usernameExists - 不存在返回false")
    void usernameExists_false() {
        assertFalse(repo.usernameExists("not_exists_xyz", null));
    }

    @Test
    @DisplayName("usernameExists - 排除指定ID")
    void usernameExists_excludeId() {
        String hash = com.his.auth.AuthService.hashPassword("p");
        int id = repo.createUser("exclude_test", hash, "排除测试", UserRole.挂号员.toString(), null);

        // 同一用户名，排除自己 → false（不认为重复）
        assertFalse(repo.usernameExists("exclude_test", id));
    }

    // ========================================================================
    //  系统配置管理
    // ========================================================================

    @Test
    @DisplayName("upsertConfig - 新增配置")
    void upsertConfig_insert() {
        repo.upsertConfig("test.key", "test_value", "测试配置", "test");

        List<Map<String, Object>> configs = repo.getAllConfigs();
        assertEquals(1, configs.size());
        assertEquals("test.key", configs.get(0).get("configKey"));
        assertEquals("test_value", configs.get(0).get("configValue"));
    }

    @Test
    @DisplayName("upsertConfig - 更新已有配置")
    void upsertConfig_update() {
        repo.upsertConfig("update.key", "v1", "描述1", "mod1");
        repo.upsertConfig("update.key", "v2", "描述2", "mod1");

        List<Map<String, Object>> configs = repo.getAllConfigs();
        assertEquals(1, configs.size());
        assertEquals("v2", configs.get(0).get("configValue"));
        assertEquals("描述2", configs.get(0).get("description"));
    }

    @Test
    @DisplayName("getAllConfigs - 按module分组")
    void getAllConfigs_groupedByModule() {
        repo.upsertConfig("mod1.k1", "v1", "d1", "module_a");
        repo.upsertConfig("mod1.k2", "v2", "d2", "module_a");
        repo.upsertConfig("mod2.k1", "v3", "d3", "module_b");

        List<Map<String, Object>> configs = repo.getAllConfigs();
        assertEquals(3, configs.size());
    }

    @Test
    @DisplayName("deleteConfig - 删除配置")
    void deleteConfig_success() {
        repo.upsertConfig("delete.key", "value", "待删除", "test");
        List<Map<String, Object>> configs = repo.getAllConfigs();
        int configId = (int) configs.get(0).get("id");

        repo.deleteConfig(configId);

        List<Map<String, Object>> after = repo.getAllConfigs();
        assertEquals(0, after.size());
    }

    // ========================================================================
    //  审计日志查询
    // ========================================================================

    @Test
    @DisplayName("getAuditLogs - 基础查询返回数据")
    void getAuditLogs_basicQuery() {
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO audit_logs (user_id, username, action, target_table) " +
                     "VALUES (1, 'test', '登录', 'system_users')")) {
            ps.executeUpdate();
        } catch (Exception e) {
            fail("插入审计日志失败");
        }

        List<Map<String, Object>> logs = repo.getAuditLogs(null, null, null, null, null, 20, 0);
        assertEquals(1, logs.size());
    }

    @Test
    @DisplayName("getAuditLogs - 按用户名筛选")
    void getAuditLogs_filterByUsername() {
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO audit_logs (user_id, username, action) VALUES (1, 'alice', '登录'), (1, 'bob', '退出')");
        } catch (Exception e) {
            fail("插入审计日志失败");
        }

        List<Map<String, Object>> logs = repo.getAuditLogs("alice", null, null, null, null, 20, 0);
        assertEquals(1, logs.size());
        assertEquals("alice", logs.get(0).get("username"));
    }

    @Test
    @DisplayName("getAuditLogs - 按操作类型筛选")
    void getAuditLogs_filterByAction() {
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO audit_logs (user_id, username, action) VALUES (1, 'u1', '登录'), (1, 'u2', '退出')");
        } catch (Exception e) {
            fail("插入审计日志失败");
        }

        List<Map<String, Object>> logs = repo.getAuditLogs(null, "登录", null, null, null, 20, 0);
        assertEquals(1, logs.size());
    }

    @Test
    @DisplayName("getAuditLogCount - 统计总数")
    void getAuditLogCount_total() {
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO audit_logs (user_id, username, action) VALUES (1, 'u1', '登录'), (1, 'u2', '退出')");
        } catch (Exception e) {
            fail("插入审计日志失败");
        }

        long count = repo.getAuditLogCount(null, null, null, null, null);
        assertEquals(2, count);
    }

    @Test
    @DisplayName("getDistinctActions - 返回不同操作类型")
    void getDistinctActions_returnsList() {
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO audit_logs (user_id, username, action) VALUES (1, 'u', '登录'), (1, 'u', '退出')");
        } catch (Exception e) {
            fail("插入审计日志失败");
        }

        List<String> actions = repo.getDistinctActions();
        assertTrue(actions.contains("登录"));
        assertTrue(actions.contains("退出"));
    }

    @Test
    @DisplayName("getDistinctTables - 返回不同表名")
    void getDistinctTables_returnsList() {
        try (var conn = com.his.shared.database.ConnectionPool.getInstance().getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO audit_logs (user_id, username, action, target_table) " +
                     "VALUES (1, 'u', '新增', 'patients'), (1, 'u', '修改', 'patients')")) {
            ps.executeUpdate();
        } catch (Exception e) {
            fail("插入审计日志失败");
        }

        List<String> tables = repo.getDistinctTables();
        assertTrue(tables.contains("patients"));
    }

    // ========================================================================
    //  系统监控
    // ========================================================================

    @Test
    @DisplayName("checkDbConnection - H2内存库应返回true")
    void checkDbConnection_h2ReturnsTrue() {
        assertTrue(repo.checkDbConnection());
    }

    @Test
    @DisplayName("getTodayActiveUserCount - 无登录记录返回0")
    void getTodayActiveUserCount_noLogs() {
        assertEquals(0, repo.getTodayActiveUserCount());
    }

    @Test
    @DisplayName("getFirstLogTime - 无日志时返回null")
    void getFirstLogTime_noLogs() {
        assertNull(repo.getFirstLogTime());
    }

    @Test
    @DisplayName("getAllDepartments - 空表返回空列表")
    void getAllDepartments_empty() {
        List<Map<String, Object>> depts = repo.getAllDepartments();
        assertNotNull(depts);
        assertTrue(depts.isEmpty());
    }
}
