package com.his.shared.database;

import com.his.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 数据库连接池管理 (HikariCP)
 * 单例模式，支持自动重连、健康检查、连接丢失通知 UI
 * T-10.2.1 连接失败友好提示 | T-10.2.2 网络重连机制
 */
public class ConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);
    private static volatile ConnectionPool instance;

    private HikariDataSource dataSource;
    private ScheduledExecutorService healthScheduler;
    private volatile boolean connectionLost = false;
    private String lastError = "";

    // UI 回调：连接丢失 / 连接恢复
    private Consumer<String> onConnectionLostCallback;
    private Consumer<String> onConnectionRestoredCallback;

    private ConnectionPool() {}

    public static ConnectionPool getInstance() {
        if (instance == null) {
            synchronized (ConnectionPool.class) {
                if (instance == null) {
                    instance = new ConnectionPool();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化连接池
     */
    public void initialize() {
        AppConfig config = AppConfig.getInstance();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getDbUsername());
        hikariConfig.setPassword(config.getDbPassword());

        hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
        hikariConfig.setMinimumIdle(config.getDbPoolMinIdle());

        hikariConfig.setConnectionTimeout(config.getDbPoolTimeout());
        hikariConfig.setIdleTimeout(config.getDbIdleTimeout());
        hikariConfig.setMaxLifetime(config.getDbMaxLifetime());

        if (config.isPostgresMode()) {
            hikariConfig.setKeepaliveTime(config.getDbKeepaliveTime());
        }

        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setPoolName("HIS-Pool");
        hikariConfig.setLeakDetectionThreshold(30000);

        // 连接失败时不崩溃，记录错误并通知 UI
        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            log.info("数据库连接池初始化成功 [{}模式] | 最大连接数: {} | URL: {}",
                    config.getDbType().toUpperCase(),
                    config.getDbPoolSize(),
                    config.getJdbcUrl());
            connectionLost = false;
            startHealthCheck();
        } catch (Exception e) {
            log.error("数据库连接池初始化失败: {}", e.getMessage(), e);
            connectionLost = true;
            lastError = e.getMessage();
            notifyConnectionLost("数据库连接失败: " + e.getMessage());
            // 不抛异常，允许 UI 继续运行（显示离线模式提示）
        }
    }

    /**
     * 启动健康检查定时任务（每 30 秒）
     */
    private void startHealthCheck() {
        if (healthScheduler != null) healthScheduler.shutdownNow();
        healthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HIS-DB-Health");
            t.setDaemon(true);
            return t;
        });
        healthScheduler.scheduleAtFixedRate(() -> {
            if (dataSource == null || dataSource.isClosed()) {
                tryReconnect();
                return;
            }
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(3)) {
                    if (connectionLost) {
                        connectionLost = false;
                        lastError = "";
                        log.info("数据库连接已恢复");
                        notifyConnectionRestored();
                    }
                } else {
                    handleConnectionLost("连接健康检查: isValid=false");
                }
            } catch (SQLException e) {
                handleConnectionLost("连接健康检查失败: " + e.getMessage());
            } catch (Exception e) {
                log.warn("健康检查异常: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 手动触发重新连接（UI 调用）
     */
    public void reconnect() {
        tryReconnect();
    }

    /**
     * 尝试重新连接（内部方法）
     */
    private void tryReconnect() {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                connectionLost = false;
                lastError = "";
                log.info("数据库重连成功");
                notifyConnectionRestored();
            }
        } catch (SQLException e) {
            log.debug("重连失败（正常）: {}", e.getMessage());
        }
    }

    /**
     * 处理连接丢失
     */
    private void handleConnectionLost(String reason) {
        if (!connectionLost) {
            connectionLost = true;
            lastError = reason;
            log.warn("数据库连接丢失: {}", reason);
            notifyConnectionLost(reason);
        }
    }

    /**
     * 通知 UI：连接丢失（JavaFX Application Thread）
     */
    private void notifyConnectionLost(String reason) {
        if (onConnectionLostCallback != null) {
            Platform.runLater(() -> onConnectionLostCallback.accept(reason));
        }
    }

    /**
     * 通知 UI：连接恢复
     */
    private void notifyConnectionRestored() {
        if (onConnectionRestoredCallback != null) {
            Platform.runLater(() -> onConnectionRestoredCallback.accept("数据库连接已恢复"));
        }
    }

    /**
     * 注册 UI 回调
     */
    public void setOnConnectionLost(Consumer<String> callback) {
        this.onConnectionLostCallback = callback;
    }

    public void setOnConnectionRestored(Consumer<String> callback) {
        this.onConnectionRestoredCallback = callback;
    }

    /**
     * 获取数据库连接（若连接丢失则抛异常，由调用方处理）
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            if (connectionLost) {
                throw new SQLException("数据库连接已断开: " + lastError);
            }
            throw new IllegalStateException("连接池未初始化，请先调用 initialize()");
        }
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("连接池未初始化");
        }
        return dataSource;
    }

    public void shutdown() {
        if (healthScheduler != null) {
            healthScheduler.shutdownNow();
            healthScheduler = null;
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("数据库连接池已关闭");
        }
    }

    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) return false;
        if (connectionLost) return false;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isConnectionLost() {
        return connectionLost;
    }

    public String getLastError() {
        return lastError;
    }
}
