package com.his.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * 应用配置管理 (单例模式)
 * 支持 application.properties + 环境变量覆盖
 * 环境变量优先级高于配置文件
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static volatile AppConfig instance;

    private final Properties props;

    // 数据库配置
    private final String dbType;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUsername;
    private final String dbPassword;
    private final int dbPoolSize;
    private final int dbPoolMinIdle;
    private final long dbPoolTimeout;
    private final long dbIdleTimeout;
    private final long dbMaxLifetime;
    private final long dbKeepaliveTime;

    // 应用配置
    private final String appName;
    private final String appVersion;
    private final String locale;
    private final String hospitalName;
    private final String hospitalAddress;
    private final String hospitalPhone;

    private AppConfig() {
        props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is == null) {
                throw new RuntimeException("找不到配置文件 application.properties");
            }
            props.load(is);
        } catch (Exception e) {
            throw new RuntimeException("加载配置文件失败", e);
        }

        // 环境变量优先覆盖配置文件
        applyEnvOverrides();

        // 加载数据库配置
        this.dbType     = get("db.type", "h2");
        this.dbHost     = get("db.host", "localhost");
        this.dbPort     = getInt("db.port", 5432);
        this.dbName     = get("db.name", "his_db");
        this.dbUsername = get("db.username", "sa");
        this.dbPassword = get("db.password", "");

        // 连接池
        this.dbPoolSize     = getInt("db.pool.size", 20);
        this.dbPoolMinIdle  = getInt("db.pool.min.idle", 5);
        this.dbPoolTimeout  = getLong("db.pool.timeout", 30000L);
        this.dbIdleTimeout  = getLong("db.pool.idle.timeout", 600000L);
        this.dbMaxLifetime  = getLong("db.pool.max.lifetime", 1800000L);
        this.dbKeepaliveTime = getLong("db.pool.keepalive.time", 60000L);

        // 应用配置
        this.appName      = get("app.name", "医院信息管理系统");
        this.appVersion   = get("app.version", "1.0.0");
        this.locale       = get("app.locale", "zh_CN");
        this.hospitalName    = get("hospital.name", "XX医院");
        this.hospitalAddress = get("hospital.address", "");
        this.hospitalPhone   = get("hospital.phone", "");

        log.info("配置加载完成: {} v{} | 数据库类型: {} | {}:{}/{}",
                appName, appVersion, dbType, dbHost, dbPort, dbName);
    }

    /**
     * 将环境变量覆盖到 props 中（环境变量优先级更高）
     */
    private void applyEnvOverrides() {
        overrideIfSet("HIS_DB_TYPE",  "db.type");
        overrideIfSet("HIS_DB_HOST",  "db.host");
        overrideIfSet("HIS_DB_PORT",  "db.port");
        overrideIfSet("HIS_DB_NAME",  "db.name");
        overrideIfSet("HIS_DB_USER",  "db.username");
        overrideIfSet("HIS_DB_PASS",  "db.password");
        overrideIfSet("HIS_POOL_SIZE","db.pool.size");
    }

    private void overrideIfSet(String envKey, String propKey) {
        String val = System.getenv(envKey);
        if (val != null && !val.trim().isEmpty()) {
            props.setProperty(propKey, val.trim());
            log.debug("环境变量覆盖配置: {}={}", propKey, envKey.contains("PASS") ? "***" : val.trim());
        }
    }

    public static void init() {
        getInstance();
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    // ===== JDBC URL 构建 =====

    public String getJdbcUrl() {
        if ("h2".equalsIgnoreCase(dbType)) {
            // H2内存模式，PostgreSQL兼容
            return "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        }
        // PostgreSQL
        return String.format("jdbc:postgresql://%s:%d/%s", dbHost, dbPort, dbName);
    }

    public boolean isH2Mode() {
        return "h2".equalsIgnoreCase(dbType);
    }

    public boolean isPostgresMode() {
        return "postgresql".equalsIgnoreCase(dbType);
    }

    // ===== 工具方法 =====

    private String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    private int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 格式错误，使用默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }

    private long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 格式错误，使用默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }

    // ===== Getters =====

    public String getDbType()          { return dbType; }
    public String getDbHost()          { return dbHost; }
    public int    getDbPort()          { return dbPort; }
    public String getDbName()          { return dbName; }
    public String getDbUsername()      { return dbUsername; }
    public String getDbPassword()      { return dbPassword; }
    public int    getDbPoolSize()      { return dbPoolSize; }
    public int    getDbPoolMinIdle()   { return dbPoolMinIdle; }
    public long   getDbPoolTimeout()   { return dbPoolTimeout; }
    public long   getDbIdleTimeout()   { return dbIdleTimeout; }
    public long   getDbMaxLifetime()   { return dbMaxLifetime; }
    public long   getDbKeepaliveTime() { return dbKeepaliveTime; }
    public String getAppName()         { return appName; }
    public String getAppVersion()      { return appVersion; }
    public String getLocale()          { return locale; }
    public String getHospitalName()    { return hospitalName; }
    public String getHospitalAddress() { return hospitalAddress; }
    public String getHospitalPhone()   { return hospitalPhone; }
}
