package com.his.admin.repository;

import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;
import com.his.auth.UserRole;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统管理数据访问层
 * <p>负责用户管理、审计日志查询、系统配置管理的数据操作</p>
 */
public class AdminRepository extends BaseRepository {

    // ========================================================================
    //  用户管理
    // ========================================================================

    /**
     * 查询所有用户（含科室名称）
     */
    public List<Map<String, Object>> getAllUsers() {
        String sql = "SELECT u.id, u.username, u.display_name, u.role, " +
                "u.department_id, d.name AS dept_name, u.is_active, " +
                "u.failed_attempts, u.locked_until, u.last_login, u.created_at " +
                "FROM system_users u " +
                "LEFT JOIN departments d ON u.department_id = d.id " +
                "ORDER BY u.id";
        try {
            return queryList(sql, this::mapUser);
        } catch (Exception e) {
            throw new DatabaseException("查询用户列表失败", e);
        }
    }

    /**
     * 根据 ID 查询单个用户
     */
    public Map<String, Object> getUserById(int id) {
        String sql = "SELECT u.id, u.username, u.display_name, u.role, " +
                "u.department_id, d.name AS dept_name, u.is_active, " +
                "u.failed_attempts, u.locked_until, u.last_login, u.created_at " +
                "FROM system_users u " +
                "LEFT JOIN departments d ON u.department_id = d.id " +
                "WHERE u.id = ?";
        try {
            return querySingle(sql, this::mapUser, id);
        } catch (Exception e) {
            throw new DatabaseException("查询用户失败", e);
        }
    }

    /**
     * 搜索用户（支持按用户名/角色/状态筛选）
     */
    public List<Map<String, Object>> searchUsers(String username, String role, Boolean isActive) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.id, u.username, u.display_name, u.role, " +
                "u.department_id, d.name AS dept_name, u.is_active, " +
                "u.failed_attempts, u.locked_until, u.last_login, u.created_at " +
                "FROM system_users u " +
                "LEFT JOIN departments d ON u.department_id = d.id " +
                "WHERE 1=1 ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (username != null && !username.isEmpty()) {
            sql.append("AND u.username ILIKE ? ");
            params.add("%" + username.trim() + "%");
        }
        if (role != null && !role.isEmpty()) {
            sql.append("AND u.role = ? ");
            params.add(role);
        }
        if (isActive != null) {
            sql.append("AND u.is_active = ? ");
            params.add(isActive);
        }

        sql.append("ORDER BY u.id");
        try {
            return queryList(sql.toString(), this::mapUser, params.toArray());
        } catch (Exception e) {
            throw new com.his.shared.exception.DatabaseException("搜索用户失败", e);
        }
    }

    // ========================================================================
    //  用户管理（续）
    // ========================================================================

    /**
     * 创建新用户（不检查重复）
     */
    public int createUser(String username, String passwordHash, String displayName,
                          String role, Integer departmentId) {
        String sql = "INSERT INTO system_users " +
                "(username, password_hash, display_name, role, department_id, " +
                "is_active, failed_attempts, locked_until, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, TRUE, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try {
            return executeInsert(sql, username.trim(), passwordHash,
                    displayName.trim(), role, departmentId);
        } catch (Exception e) {
            throw new DatabaseException("创建用户失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新用户信息（不含密码）
     */
    public void updateUser(int id, String displayName, String role,
                           Integer departmentId, boolean isActive) {
        String sql = "UPDATE system_users SET display_name = ?, role = ?, " +
                "department_id = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ?";
        try {
            executeUpdate(sql, displayName.trim(), role,
                    departmentId, isActive, id);
        } catch (Exception e) {
            throw new DatabaseException("更新用户失败", e);
        }
    }

    /**
     * 重置用户密码
     */
    public void resetPassword(int id, String passwordHash) {
        String sql = "UPDATE system_users SET password_hash = ?, " +
                "failed_attempts = 0, locked_until = NULL, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, passwordHash, id);
        } catch (Exception e) {
            throw new DatabaseException("重置密码失败", e);
        }
    }

    /**
     * 设置用户启用/停用状态
     */
    public void setUserActive(int id, boolean isActive) {
        String sql = "UPDATE system_users SET is_active = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, isActive, id);
        } catch (Exception e) {
            throw new DatabaseException("设置用户状态失败", e);
        }
    }

    /**
     * 解锁用户账号
     */
    public void unlockUser(int id) {
        String sql = "UPDATE system_users SET failed_attempts = 0, " +
                "locked_until = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("解锁用户失败", e);
        }
    }

    /**
     * 删除用户（硬删除）
     */
    public void deleteUser(int id) {
        String sql = "DELETE FROM system_users WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除用户失败", e);
        }
    }

    /**
     * 检查用户名是否存在
     */
    public boolean usernameExists(String username, Integer excludeId) {
        String sql;
        if (excludeId != null) {
            sql = "SELECT COUNT(*) FROM system_users WHERE username = ? AND id != ?";
            try {
                Integer cnt = querySingle(sql, rs -> rs.getInt(1), username.trim(), excludeId);
                return cnt != null && cnt > 0;
            } catch (Exception e) {
                throw new DatabaseException("检查用户名失败", e);
            }
        } else {
            sql = "SELECT COUNT(*) FROM system_users WHERE username = ?";
            try {
                Integer cnt = querySingle(sql, rs -> rs.getInt(1), username.trim());
                return cnt != null && cnt > 0;
            } catch (Exception e) {
                throw new DatabaseException("检查用户名失败", e);
            }
        }
    }

    // ========================================================================
    //  审计日志
    // ========================================================================

    /**
     * 查询审计日志（支持分页和筛选）
     */
    public List<Map<String, Object>> getAuditLogs(String username, String action,
                                                  String targetTable, LocalDate startDate,
                                                  LocalDate endDate, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, username, action, target_table, " +
                "target_id, detail, ip_address, created_at " +
                "FROM audit_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (username != null && !username.isEmpty()) {
            sql.append("AND username ILIKE ? ");
            params.add("%" + username.trim() + "%");
        }
        if (action != null && !action.isEmpty()) {
            sql.append("AND action = ? ");
            params.add(action);
        }
        if (targetTable != null && !targetTable.isEmpty()) {
            sql.append("AND target_table = ? ");
            params.add(targetTable);
        }
        if (startDate != null) {
            sql.append("AND created_at >= ? ");
            params.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            sql.append("AND created_at < ? ");
            params.add(endDate.plusDays(1).atStartOfDay());
        }

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try {
            return queryList(sql.toString(), this::mapAuditLog, params.toArray());
        } catch (Exception e) {
            throw new DatabaseException("查询审计日志失败", e);
        }
    }

    /**
     * 统计审计日志总数（用于分页）
     */
    public int getAuditLogCount(String username, String action,
                                String targetTable, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (username != null && !username.isEmpty()) {
            sql.append("AND username ILIKE ? ");
            params.add("%" + username.trim() + "%");
        }
        if (action != null && !action.isEmpty()) {
            sql.append("AND action = ? ");
            params.add(action);
        }
        if (targetTable != null && !targetTable.isEmpty()) {
            sql.append("AND target_table = ? ");
            params.add(targetTable);
        }
        if (startDate != null) {
            sql.append("AND created_at >= ? ");
            params.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            sql.append("AND created_at < ? ");
            params.add(endDate.plusDays(1).atStartOfDay());
        }

        try {
            return querySingle(sql.toString(), rs -> rs.getInt(1), params.toArray());
        } catch (Exception e) {
            throw new DatabaseException("统计审计日志失败", e);
        }
    }

    /**
     * 获取所有不同的 action 类型（用于筛选下拉框）
     */
    public List<String> getDistinctActions() {
        String sql = "SELECT DISTINCT action FROM audit_logs ORDER BY action";
        try {
            return queryList(sql, rs -> rs.getString(1));
        } catch (Exception e) {
            throw new DatabaseException("查询操作类型失败", e);
        }
    }

    /**
     * 获取所有不同的 target_table 类型
     */
    public List<String> getDistinctTables() {
        String sql = "SELECT DISTINCT target_table FROM audit_logs ORDER BY target_table";
        try {
            return queryList(sql, rs -> rs.getString(1));
        } catch (Exception e) {
            throw new DatabaseException("查询操作表失败", e);
        }
    }

    // ========================================================================
    //  系统监控辅助方法（供 AdminView 调用）
    // ========================================================================

    /**
     * 检测数据库连接是否正常
     */
    public boolean checkDbConnection() {
        try (var conn = getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (Exception e) {
            log.warn("数据库连接检测失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取今日活跃用户数（今日有登录记录的不同用户名数量）
     */
    public int getTodayActiveUserCount() {
        String sql = "SELECT COUNT(DISTINCT username) FROM audit_logs " +
                "WHERE action = '登录' AND created_at >= CURRENT_DATE";
        try {
            return querySingle(sql, rs -> rs.getInt(1));
        } catch (Exception e) {
            throw new DatabaseException("查询今日活跃用户数失败", e);
        }
    }

    /**
     * 获取系统首次运行时间（最早一条审计日志记录时间）
     */
    public Timestamp getFirstLogTime() {
        String sql = "SELECT MIN(created_at) FROM audit_logs";
        try {
            return querySingle(sql, rs -> rs.getTimestamp(1));
        } catch (Exception e) {
            throw new DatabaseException("查询首次运行时间失败", e);
        }
    }

    // ========================================================================
    //  系统配置管理
    // ========================================================================

    /**
     * 查询所有系统配置
     */
    public List<Map<String, Object>> getAllConfigs() {
        String sql = "SELECT id, config_key, config_value, description, module, " +
                "updated_at FROM system_configs ORDER BY module, config_key";
        try {
            return queryList(sql, this::mapConfig);
        } catch (Exception e) {
            throw new DatabaseException("查询系统配置失败", e);
        }
    }

    /**
     * 根据 Key 查询配置值
     */
    public String getConfigValue(String key) {
        String sql = "SELECT config_value FROM system_configs WHERE config_key = ?";
        try {
            String val = querySingle(sql, rs -> rs.getString(1), key);
            return val;
        } catch (Exception e) {
            throw new DatabaseException("查询配置值失败: " + key, e);
        }
    }

    /**
     * 新增或更新系统配置（UPSERT）
     */
    public void upsertConfig(String key, String value, String description, String module) {
        // 先检查是否存在
        String checkSql = "SELECT COUNT(*) FROM system_configs WHERE config_key = ?";
        Integer cnt;
        try {
            cnt = querySingle(checkSql, rs -> rs.getInt(1), key);
        } catch (Exception e) {
            throw new DatabaseException("检查配置失败", e);
        }

        if (cnt != null && cnt > 0) {
            // UPDATE
            String sql = "UPDATE system_configs SET config_value = ?, description = ?, " +
                    "module = ?, updated_at = CURRENT_TIMESTAMP WHERE config_key = ?";
            try {
                executeUpdate(sql, value, description, module, key);
            } catch (Exception e) {
                throw new DatabaseException("更新系统配置失败", e);
            }
        } else {
            // INSERT
            String sql = "INSERT INTO system_configs " +
                    "(config_key, config_value, description, module, updated_at) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try {
                executeInsert(sql, key, value, description, module);
            } catch (Exception e) {
                throw new DatabaseException("新增系统配置失败", e);
            }
        }
    }

    /**
     * 删除系统配置
     */
    public void deleteConfig(int id) {
        String sql = "DELETE FROM system_configs WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除系统配置失败", e);
        }
    }

    // ========================================================================
    //  通用工具（科室查询，供 UI 使用）
    // ========================================================================

    /**
     * 查询所有科室（用于下拉框选择）
     */
    public List<Map<String, Object>> getAllDepartments() {
        String sql = "SELECT id, name, type FROM departments WHERE is_active = TRUE ORDER BY name";
        try {
            return queryList(sql, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("name", rs.getString("name"));
                m.put("type", rs.getString("type"));
                return m;
            });
        } catch (Exception e) {
            throw new DatabaseException("查询科室失败", e);
        }
    }

    // ========================================================================
    //  私有映射方法
    // ========================================================================

    private Map<String, Object> mapUser(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("username", rs.getString("username"));
        m.put("displayName", rs.getString("display_name"));
        m.put("role", rs.getString("role"));
        m.put("departmentId", rs.getObject("department_id") != null ? rs.getInt("department_id") : null);
        m.put("deptName", rs.getString("dept_name"));
        m.put("isActive", rs.getBoolean("is_active"));
        m.put("failedAttempts", rs.getInt("failed_attempts"));
        m.put("lockedUntil", rs.getTimestamp("locked_until"));
        m.put("lastLogin", rs.getTimestamp("last_login"));
        m.put("createdAt", rs.getTimestamp("created_at"));
        return m;
    }

    private Map<String, Object> mapAuditLog(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("userId", rs.getObject("user_id") != null ? rs.getInt("user_id") : null);
        m.put("username", rs.getString("username"));
        m.put("action", rs.getString("action"));
        m.put("targetTable", rs.getString("target_table"));
        m.put("targetId", rs.getString("target_id"));
        m.put("detail", rs.getString("detail"));
        m.put("ipAddress", rs.getString("ip_address"));
        m.put("createdAt", rs.getTimestamp("created_at"));
        return m;
    }

    private Map<String, Object> mapConfig(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("configKey", rs.getString("config_key"));
        m.put("configValue", rs.getString("config_value"));
        m.put("description", rs.getString("description"));
        m.put("module", rs.getString("module"));
        m.put("updatedAt", rs.getTimestamp("updated_at"));
        return m;
    }

    /**
     * 生成 BCrypt 密码哈希（供 AdminView 调用）
     */
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /**
     * 验证 BCrypt 密码是否正确（供 AdminView 调用）
     */
    public static boolean checkPassword(String plainPassword, String hash) {
        return BCrypt.checkpw(plainPassword, hash);
    }
}
