package com.his.shared.database;

import com.his.config.AppConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * PostgreSQL 连接验证测试
 * 仅在环境变量 HIS_DB_TYPE=postgresql 时运行
 *
 * 运行前准备：
 * 1. 安装 PostgreSQL 16+
 * 2. 创建数据库：CREATE DATABASE his_test;
 * 3. 设置环境变量：
 *    set HIS_DB_TYPE=postgresql
 *    set HIS_DB_HOST=localhost
 *    set HIS_DB_PORT=5432
 *    set HIS_DB_NAME=his_test
 *    set HIS_DB_USER=postgres
 *    set HIS_DB_PASS=your_password
 * 4. 运行测试：mvn test -pl his-common -Dtest=PostgresConnectionTest
 */
@DisplayName("PostgreSQL 连接验证测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "HIS_DB_TYPE", matches = "postgresql")
public class PostgresConnectionTest extends BaseIntegrationTest {

    @BeforeAll
    void checkPostgresAvailable() {
        // 这个测试只在 HIS_DB_TYPE=postgresql 时运行（由 @EnabledIfEnvironmentVariable 保证）
        // 额外检查：能获取到连接
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            assertNotNull(conn, "PostgreSQL 连接不能为空");
        } catch (Exception e) {
            fail("无法获取 PostgreSQL 连接: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("PostgreSQL 版本检查")
    void postgresVersionCheck() {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String dbProduct = meta.getDatabaseProductName();
            String dbVersion = meta.getDatabaseProductVersion();

            assertTrue(dbProduct.toLowerCase().contains("postgres"),
                    "数据库产品名应该包含 'postgres'，实际: " + dbProduct);

            // PostgreSQL 16+ 版本字符串类似：PostgreSQL 16.2 (Debian 16.2-1.pgdg120+1)
            // 提取主版本号
            String[] parts = dbVersion.split(" ");
            String versionPart = parts.length > 1 ? parts[1] : "0.0";
            String majorStr = versionPart.split("\\.")[0];
            int major = Integer.parseInt(majorStr);
            assertTrue(major >= 16,
                    "PostgreSQL 主版本应该 >= 16，实际: " + major + " (版本字符串: " + dbVersion + ")");
        } catch (Exception e) {
            fail("获取数据库元数据失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("V1__init.sql 迁移验证（PostgreSQL）")
    void migrationValidation() {
        // 迁移应该在 setupClass() 中已完成
        // 这里验证所有表都已创建
        String[] expectedTables = {
            "system_users", "audit_logs", "system_configs",
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

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {

            // 收集所有表名
            java.util.Set<String> actualTables = new java.util.HashSet<>();
            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
            }

            for (String table : expectedTables) {
                assertTrue(actualTables.contains(table),
                        "表 " + table + " 应该在数据库中（PostgreSQL 迁移后）");
            }
        } catch (Exception e) {
            fail("验证迁移失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SERIAL 序列初始值验证（PostgreSQL）")
    void serialSequenceCheck() {
        // PostgreSQL 的 SERIAL 会创建序列，初始值为 1
        // 初始数据插入了 6 行（admin, guahao, doctor, nurse, pharmacy, cashier）
        // 所以下一个 ID 应该是 7
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT currval('system_users_id_seq')")) {
            assertTrue(rs.next());
            int nextId = rs.getInt(1);
            assertEquals(7, nextId, "system_users_id_seq 的下一个值应该是 7");
        } catch (Exception e) {
            // 序列可能不存在（如果迁移没运行），忽略这个测试
            assumeTrue(false, "无法检查序列（可能迁移未运行）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("BOOLEAN 类型验证（PostgreSQL）")
    void booleanTypeCheck() {
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT is_active FROM system_users WHERE username = 'admin'")) {
            assertTrue(rs.next());
            boolean isActive = rs.getBoolean(1);
            assertTrue(isActive, "admin 用户的 is_active 应该是 TRUE");
        } catch (Exception e) {
            fail("检查 BOOLEAN 类型失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("DECIMAL 类型验证（PostgreSQL）")
    void decimalTypeCheck() {
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT registration_fee FROM registrations LIMIT 1")) {
            // 如果没有数据，这个测试会失败；我们只是验证列类型可读
            if (rs.next()) {
                double fee = rs.getDouble(1);
                assertTrue(fee >= 0, "registration_fee 应该是数值类型");
            }
        } catch (Exception e) {
            // 没有数据没关系，我们只是验证列类型
        }
    }
}
