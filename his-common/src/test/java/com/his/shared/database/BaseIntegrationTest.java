package com.his.shared.database;

import com.his.config.AppConfig;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试基类
 * 使用 H2 内存数据库（默认）或 PostgreSQL（设置环境变量 HIS_DB_TYPE=postgresql）
 * 每个测试类只初始化一次数据库，每个测试方法重新建表
 *
 * 使用方式：
 *   # H2 模式（默认）
 *   mvn test -pl his-admin
 *
 *   # PostgreSQL 模式（需要本地 PostgreSQL 和运行中的数据库）
 *   set HIS_DB_TYPE=postgresql
 *   set HIS_DB_HOST=localhost
 *   set HIS_DB_PORT=5432
 *   set HIS_DB_NAME=his_test
 *   set HIS_DB_USER=postgres
 *   set HIS_DB_PASS=your_password
 *   mvn test -pl his-admin
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    /**
     * 类级别一次性初始化：
     * 1. 加载测试配置 (application.properties → H2 内存库)
     * 2. 初始化连接池
     * 3. 执行数据库迁移 (V1__init.sql)
     */
    @BeforeAll
    protected void setupClass() {
        // 强制重新初始化 AppConfig（防止缓存的旧实例）
        // 通过设置系统属性让 AppConfig 读取测试配置
        AppConfig.init();

        // 初始化连接池
        ConnectionPool.getInstance().initialize();

        // 执行数据库迁移
        MigrationRunner.run();
    }

    /**
     * 每个测试方法前：清理所有表数据（保持表结构）
     * 保证测试隔离
     *
     * H2 模式：使用 SET REFERENTIAL_INTEGRITY FALSE 禁用外键约束后 DELETE
     * PostgreSQL 模式：使用 SET session_replication_role = replica 跳过外键约束后 DELETE
     */
    @BeforeEach
    protected void cleanupBeforeTest() {
        boolean isPostgres = AppConfig.getInstance().isPostgresMode();
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            if (isPostgres) {
                // PostgreSQL: 设置 session_replication_role = replica 以跳过外键检查
                stmt.execute("SET session_replication_role = replica");
            } else {
                // H2: 禁用外键约束
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            }

            // 清理所有表（保持迁移后的表结构）
            // 注意：表名在 H2 (DATABASE_TO_UPPER=false) 和 PostgreSQL 中都是小写
            String[] tables = {
                "audit_logs", "system_configs", "system_users",
                "departments", "doctors", "patients",
                "registrations", "appointment_slots", "outpatient_visits",
                "prescriptions", "prescription_items",
                "beds", "inpatient_records", "inpatient_charges",
                "operation_records", "nursing_records",
                "drugs", "drug_inventory", "drug_purchase_orders",
                "exam_items", "examination_requests", "examination_reports",
                "medical_records", "billing_records", "billing_details",
                "icd10_codes"
            };
            for (String table : tables) {
                try {
                    stmt.execute("DELETE FROM " + table);
                } catch (Exception e) {
                    // 表可能不存在（迁移未创建），忽略
                }
            }

            if (isPostgres) {
                // PostgreSQL: 恢复默认行为
                stmt.execute("SET session_replication_role = origin");
            } else {
                // H2: 重新启用外键约束
                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
        } catch (Exception e) {
            fail("测试数据清理失败: " + e.getMessage());
        }
    }

    /**
     * 类级别清理：关闭连接池
     */
    @AfterAll
    protected void teardownClass() {
        ConnectionPool.getInstance().shutdown();
    }

    // ===== 辅助断言方法 =====

    /** 断言表中有指定行数 */
    protected void assertTableRowCount(String tableName, int expectedCount) {
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next(), "查询表 " + tableName + " 失败");
            assertEquals(expectedCount, rs.getInt(1),
                    "表 " + tableName + " 行数不符合预期");
        } catch (Exception e) {
            fail("断言表行数失败: " + e.getMessage());
        }
    }

    /** 向 system_users 插入一个测试管理员用户（返回用户ID） */
    protected int insertTestAdmin(String username, String password) {
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO system_users (username, password_hash, display_name, role, is_active) " +
                     "VALUES (?, ?, ?, '管理员', TRUE)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, "测试管理员");
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        } catch (Exception e) {
            fail("插入测试管理员失败: " + e.getMessage());
            return -1;
        }
    }

    /** 向 system_users 插入一个普通用户 */
    protected int insertTestUser(String username, String password, String role) {
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO system_users (username, password_hash, display_name, role, is_active) " +
                     "VALUES (?, ?, ?, ?, TRUE)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, "测试用户");
            ps.setString(4, role);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        } catch (Exception e) {
            fail("插入测试用户失败: " + e.getMessage());
            return -1;
        }
    }
}
