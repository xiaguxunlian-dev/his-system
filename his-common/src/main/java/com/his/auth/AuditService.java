package com.his.auth;

import com.his.shared.database.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 操作审计服务
 * 异步记录所有数据写操作到 audit_logs 表
 */
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** 单例 */
    private static volatile AuditService instance;

    /** 异步线程池（单线程，保证顺序写入） */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-writer");
        t.setDaemon(true);
        return t;
    });

    private AuditService() {}

    public static AuditService getInstance() {
        if (instance == null) {
            synchronized (AuditService.class) {
                if (instance == null) {
                    instance = new AuditService();
                }
            }
        }
        return instance;
    }

    /**
     * 记录操作（异步，不阻塞业务）
     *
     * @param action      操作描述，如 "新增患者"、"修改挂号"、"删除处方"
     * @param targetTable 目标数据表
     * @param targetId    目标记录ID
     * @param detail      操作详情（可以是JSON或摘要文本）
     */
    public void log(String action, String targetTable, String targetId, String detail) {
        UserSession session = UserSession.getInstance();
        int    userId  = session.getUserId();
        String username = session.getUsername();

        executor.submit(() -> writeLog(userId, username, action, targetTable, targetId, detail));
    }

    /**
     * 记录操作（带显式IP）
     */
    public void log(String action, String targetTable, String targetId, String detail, String ipAddress) {
        UserSession session = UserSession.getInstance();
        executor.submit(() -> writeLogWithIp(
                session.getUserId(), session.getUsername(),
                action, targetTable, targetId, detail, ipAddress));
    }

    private void writeLog(int userId, String username, String action,
                          String targetTable, String targetId, String detail) {
        writeLogWithIp(userId, username, action, targetTable, targetId, detail, "localhost");
    }

    private void writeLogWithIp(int userId, String username, String action,
                                String targetTable, String targetId, String detail, String ip) {
        String sql = "INSERT INTO audit_logs (user_id, username, action, target_table, " +
                     "target_id, detail, ip_address, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, username);
            ps.setString(3, action);
            ps.setString(4, targetTable);
            ps.setString(5, targetId);
            ps.setString(6, detail);
            ps.setString(7, ip);
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (Exception e) {
            // 审计失败不应影响主业务
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    /** 关闭线程池（应用退出时调用） */
    public void shutdown() {
        executor.shutdown();
    }

    // ===== 常用操作常量 =====
    public static final String ACTION_CREATE  = "新增";
    public static final String ACTION_UPDATE  = "修改";
    public static final String ACTION_DELETE  = "删除";
    public static final String ACTION_LOGIN   = "登录";
    public static final String ACTION_LOGOUT  = "退出";
    public static final String ACTION_QUERY   = "查询";
    public static final String ACTION_PRINT   = "打印";
    public static final String ACTION_EXPORT  = "导出";
}
