package com.his.auth;

import com.his.shared.database.ConnectionPool;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * 认证服务
 * 负责用户登录验证、账号锁定、密码验证
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 最大失败尝试次数 */
    private static final int MAX_FAILED_ATTEMPTS = 3;
    /** 账号锁定时长（分钟） */
    private static final int LOCK_MINUTES = 5;

    /**
     * 登录验证结果
     */
    public static class LoginResult {
        public final boolean success;
        public final String  message;
        public final int     userId;
        public final String  displayName;
        public final UserRole role;
        public final Integer departmentId;

        private LoginResult(boolean success, String message, int userId,
                            String displayName, UserRole role, Integer departmentId) {
            this.success      = success;
            this.message      = message;
            this.userId       = userId;
            this.displayName  = displayName;
            this.role         = role;
            this.departmentId = departmentId;
        }

        public static LoginResult fail(String msg)  { return new LoginResult(false, msg, 0, null, null, null); }
        public static LoginResult ok(int id, String name, UserRole r, Integer deptId) {
            return new LoginResult(true, "登录成功", id, name, r, deptId);
        }
    }

    /**
     * 登录认证
     * @param username 用户名
     * @param password 明文密码
     * @return LoginResult
     */
    public LoginResult login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return LoginResult.fail("请输入用户名");
        }
        if (password == null || password.isEmpty()) {
            return LoginResult.fail("请输入密码");
        }

        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            // 查询用户
            String sql = "SELECT id, password_hash, display_name, role, department_id, " +
                         "is_active, failed_attempts, locked_until " +
                         "FROM system_users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("登录失败：用户不存在 [{}]", username);
                        return LoginResult.fail("用户名或密码错误");
                    }

                    int     userId         = rs.getInt("id");
                    String  passwordHash   = rs.getString("password_hash");
                    String  displayName    = rs.getString("display_name");
                    String  roleStr        = rs.getString("role");
                    Integer departmentId   = rs.getObject("department_id") != null ? rs.getInt("department_id") : null;
                    boolean isActive       = rs.getBoolean("is_active");
                    int     failedAttempts = rs.getInt("failed_attempts");
                    Timestamp lockedUntil  = rs.getTimestamp("locked_until");

                    // 检查账号是否启用
                    if (!isActive) {
                        return LoginResult.fail("账号已被停用，请联系管理员");
                    }

                    // 检查账号是否被锁定
                    if (lockedUntil != null && lockedUntil.toLocalDateTime().isAfter(LocalDateTime.now())) {
                        long remaining = (lockedUntil.getTime() - System.currentTimeMillis()) / 60000 + 1;
                        return LoginResult.fail("账号已被锁定，请 " + remaining + " 分钟后再试");
                    }

                    // 验证密码
                    boolean passwordOk = BCrypt.checkpw(password, passwordHash);

                    if (!passwordOk) {
                        int newFailed = failedAttempts + 1;
                        if (newFailed >= MAX_FAILED_ATTEMPTS) {
                            // 锁定账号
                            Timestamp lockTime = Timestamp.valueOf(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                            updateLock(conn, userId, newFailed, lockTime);
                            log.warn("登录失败次数过多，账号已锁定 [{}]", username);
                            return LoginResult.fail("密码错误次数过多，账号已锁定 " + LOCK_MINUTES + " 分钟");
                        } else {
                            updateFailedAttempts(conn, userId, newFailed);
                            int remaining = MAX_FAILED_ATTEMPTS - newFailed;
                            log.warn("登录失败：密码错误 [{}]，剩余尝试次数: {}", username, remaining);
                            return LoginResult.fail("密码错误，还可尝试 " + remaining + " 次");
                        }
                    }

                    // 登录成功，重置失败计数，更新最后登录时间
                    resetFailedAttempts(conn, userId);

                    UserRole role = UserRole.fromString(roleStr);
                    log.info("用户登录成功: {} [{}]", displayName, role);
                    return LoginResult.ok(userId, displayName, role, departmentId);
                }
            }
        } catch (Exception e) {
            log.error("登录认证发生异常: {}", e.getMessage(), e);
            return LoginResult.fail("系统错误，请联系管理员");
        }
    }

    private void updateFailedAttempts(Connection conn, int userId, int failed) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE system_users SET failed_attempts = ? WHERE id = ?")) {
            ps.setInt(1, failed);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private void updateLock(Connection conn, int userId, int failed, Timestamp lockUntil) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE system_users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
            ps.setInt(1, failed);
            ps.setTimestamp(2, lockUntil);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    private void resetFailedAttempts(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE system_users SET failed_attempts = 0, locked_until = NULL, " +
                "last_login = CURRENT_TIMESTAMP WHERE id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /**
     * 验证密码（用于敏感操作的二次确认）
     */
    public boolean verifyCurrentUserPassword(String password) {
        UserSession session = UserSession.getInstance();
        if (!session.isLoggedIn()) return false;

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password_hash FROM system_users WHERE username = ?")) {
            ps.setString(1, session.getUsername());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return BCrypt.checkpw(password, rs.getString("password_hash"));
                }
            }
        } catch (Exception e) {
            log.error("密码验证异常: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * 生成BCrypt密码Hash（用于初始化/重置密码）
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }
}
