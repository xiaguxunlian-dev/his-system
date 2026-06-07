-- ============================================================
-- HIS 数据库初始化脚本 (Docker PostgreSQL)
-- 在 docker-compose 启动时自动执行
-- ============================================================

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 设置时区
ALTER DATABASE his_db SET timezone TO 'Asia/Shanghai';

-- 授予权限
GRANT ALL PRIVILEGES ON DATABASE his_db TO his_user;

-- 确保 his_user 对 public schema 有权限
GRANT ALL ON SCHEMA public TO his_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO his_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO his_user;

-- 提示信息
DO $$
BEGIN
    RAISE NOTICE 'HIS 数据库初始化完成';
    RAISE NOTICE '  数据库: his_db';
    RAISE NOTICE '  用户:   his_user';
    RAISE NOTICE '  时区:   Asia/Shanghai';
END $$;
