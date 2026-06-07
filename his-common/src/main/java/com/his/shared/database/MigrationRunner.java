package com.his.shared.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 数据库迁移工具
 * 启动时自动执行 SQL 迁移脚本（按版本顺序执行）
 */
public class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    /** 所有迁移脚本（按版本顺序添加） */
    private static final String[] MIGRATION_FILES = {
            "db/migration/V1__init.sql",
            "db/migration/V2__fix_password.sql",
            "db/migration/V3__add_drug_inventory_columns.sql"
    };

    /**
     * 执行数据库迁移
     */
    public static void run() {
        log.info("开始执行数据库迁移...");

        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            conn.setAutoCommit(true);

            for (String migrationFile : MIGRATION_FILES) {
                log.info(">>> 处理迁移文件: {}", migrationFile);
                runSingleMigration(conn, migrationFile);
            }

            log.info("数据库迁移完成");
        } catch (Exception e) {
            log.error("数据库迁移失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行单个迁移文件
     */
    private static void runSingleMigration(Connection conn, String migrationFile) {
        try (InputStream is = MigrationRunner.class.getClassLoader().getResourceAsStream(migrationFile)) {

            if (is == null) {
                log.warn("未找到迁移文件: {}", migrationFile);
                return;
            }

            String sql = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // 按分号分割 SQL 语句（简单分割，不处理引号内的分号）
            String[] statements = sql.split(";(?=(?:[^']*'[^']*')*[^']*$)");
            log.info("  SQL分割完成，共 {} 条语句", statements.length);

            for (String stmt : statements) {
                // 去除行注释后再判断是否为空
                String cleaned = removeLineComments(stmt);
                if (cleaned.isEmpty()) {
                    continue;
                }
                try (Statement s = conn.createStatement()) {
                    s.execute(cleaned + ";");
                    String prefix = cleaned.length() > 60 ? cleaned.substring(0, 60).replace("\n", " ") : cleaned.replace("\n", " ");
                    log.info("  SQL OK: {}", prefix);
                } catch (Exception e) {
                    // 幂等迁移中某些语句失败是正常的（如 CREATE TABLE IF NOT EXISTS 后的重复 INSERT 等）
                    // 但记录警告以便排查
                    log.warn("  SQL执行失败（跳过）: {} | SQL前60字: {}", e.getMessage(),
                            cleaned.length() > 60 ? cleaned.substring(0, 60).replace("\n", " ") : cleaned.replace("\n", " "));
                }
            }

        } catch (Exception e) {
            log.warn("迁移文件 {} 读取或执行失败: {}", migrationFile, e.getMessage());
        }
    }

    /**
     * 去除SQL行注释（-- 到行尾），不处理字符串内的--
     */
    private static String removeLineComments(String sql) {
        StringBuilder sb = new StringBuilder();
        String[] lines = sql.split("\n");
        for (String line : lines) {
            int ci = line.indexOf("--");
            if (ci >= 0) line = line.substring(0, ci);
            if (!line.trim().isEmpty()) sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
}
