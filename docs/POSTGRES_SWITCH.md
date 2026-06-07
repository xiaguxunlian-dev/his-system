# PostgreSQL 切换验证指南

## 1. 概述

HIS 系统支持两种数据库模式：
- **H2 内存模式**（`db.type=h2`）— 开发/测试默认，数据重启丢失
- **PostgreSQL 模式**（`db.type=postgresql`）— 生产部署，数据持久化

切换通过修改 `application.properties` 或设置环境变量实现，无需修改代码。

---

## 2. PostgreSQL 16 安装（Windows）

### 2.1 下载安装包

官网：https://www.postgresql.org/download/windows/

推荐：EnterpriseDB 安装包（包含 pgAdmin 4）

- 版本选择：**PostgreSQL 16.x**（HIS 要求 16+）
- 架构：x86_64

### 2.2 安装步骤

1. 运行安装包，按向导操作
2. 设置 `postgres` 用户密码（请牢记，后续配置需要）
3. 端口默认 `5432`（HIS 默认配置也是 5432，无需修改）
4. 区域设置：默认即可
5. 取消勾选 "Stack Builder"（不需要额外工具）

### 2.3 验证安装

```powershell
# 添加到 PATH（默认安装路径）
$env:Path += ";C:\Program Files\PostgreSQL\16\bin"

# 验证版本
pg_config --version
# 预期输出：PostgreSQL 16.x

# 验证服务状态
Get-Service -Name "postgresql-x64-16" | Select-Object Status
# 预期输出：Running
```

---

## 3. 数据库准备

### 3.1 创建数据库和用户（推荐）

```sql
-- 以 postgres 超级用户连接
psql -U postgres

-- 创建数据库
CREATE DATABASE his_db
  WITH ENCODING 'UTF8'
       LC_COLLATE='zh_CN.UTF-8'
       LC_CTYPE='zh_CN.UTF-8'
       TEMPLATE=template0;

-- 创建专用用户（可选，也可以用 postgres）
CREATE USER his_user WITH PASSWORD 'his_password123';
GRANT ALL PRIVILEGES ON DATABASE his_db TO his_user;

-- 连接到 his_db，设置 search_path
\c his_db
ALTER DATABASE his_db SET search_path TO public;
```

### 3.2 快速测试连接

```powershell
# 测试连接（用 postgres 用户）
psql -U postgres -d his_db -h localhost -p 5432

# 或在 HIS 中使用专用用户
psql -U his_user -d his_db -h localhost -p 5432
```

---

## 4. HIS 配置切换

### 4.1 方式一：修改 `application.properties`（永久切换）

编辑 `his-common/src/main/resources/application.properties`：

```properties
# 修改这一行
db.type=postgresql

# 确认 PostgreSQL 连接参数（默认如下，可按需修改）
db.host=localhost
db.port=5432
db.name=his_db
db.username=his_user
db.password=his_password123
```

### 4.2 方式二：环境变量覆盖（推荐，不影响配置文件）

设置环境变量后启动 HIS，配置自动覆盖：

```powershell
# Windows PowerShell
$env:HIS_DB_TYPE="postgresql"
$env:HIS_DB_HOST="localhost"
$env:HIS_DB_PORT="5432"
$env:HIS_DB_NAME="his_db"
$env:HIS_DB_USER="his_user"
$env:HIS_DB_PASS="his_password123"

# 启动任意模块（例如 his-admin）
cd D:\his\his-admin
mvn javafx:run
```

```bash
# Linux/macOS
export HIS_DB_TYPE=postgresql
export HIS_DB_HOST=localhost
export HIS_DB_PORT=5432
export HIS_DB_NAME=his_db
export HIS_DB_USER=his_user
export HIS_DB_PASS=his_password123

# 启动
cd /path/to/his/his-admin
mvn javafx:run
```

---

## 5. 运行测试

### 5.1 H2 模式测试（默认，无需 PostgreSQL）

```powershell
cd D:\his
mvn test -pl his-common,his-admin
```

### 5.2 PostgreSQL 模式测试（需要运行中的 PostgreSQL）

```powershell
# 设置环境变量
$env:HIS_DB_TYPE="postgresql"
$env:HIS_DB_HOST="localhost"
$env:HIS_DB_PORT="5432"
$env:HIS_DB_NAME="his_test"   # 建议使用独立的测试数据库
$env:HIS_DB_USER="postgres"
$env:HIS_DB_PASS="your_password"

# 运行 PostgreSQL 连接验证测试
mvn test -pl his-common -Dtest=PostgresConnectionTest

# 运行所有测试（PostgreSQL 模式）
mvn test -pl his-common,his-admin
```

> **注意**：测试前需创建 `his_test` 数据库（见 3.1 节），测试会执行 `V1__init.sql` 迁移并清理数据。

---

## 6. 打包部署

### 6.1 打包命令

```powershell
# 全量打包（所有模块）
cd D:\his
.\build-all.bat

# 单模块打包（例如 his-admin）
cd D:\his
.\build-module.bat his-admin
```

### 6.2 部署机配置

部署机上**不需要安装 PostgreSQL 客户端**，但需要：
1. 网络能访问 PostgreSQL 服务器
2. `application.properties` 配置正确的数据库连接参数（或设置环境变量）

示例 `application.properties`（生产环境）：

```properties
db.type=postgresql
db.host=192.168.1.100
db.port=5432
db.name=his_db
db.username=his_app
db.password=SecurePass123!
db.pool.size=50
db.pool.min.idle=10
```

---

## 7. 数据库迁移（V1__init.sql）

`V1__init.sql` 位于 `his-common/src/main/resources/db/migration/V1__init.sql`。

### 7.1 迁移机制

HIS 启动时自动执行 `MigrationRunner.run()`，它会：
1. 检查 `schema_version` 表（记录已执行迁移）
2. 执行未应用的迁移 SQL 文件
3. 记录迁移历史

### 7.2 手动执行迁移（调试用）

```powershell
# 连接到目标数据库
psql -U his_user -d his_db -h localhost

# 执行迁移 SQL（注意：会清空并重建所有表）
\i D:/his/his-common/src/main/resources/db/migration/V1__init.sql
```

### 7.3 PostgreSQL 兼容性说明

`V1__init.sql` 设计时已考虑 PostgreSQL 兼容性：
- 使用 `SERIAL PRIMARY KEY`（PostgreSQL 10+ 仍支持）
- 使用 `BOOLEAN DEFAULT TRUE/FALSE`
- 使用 `DECIMAL(10,2)` 数值类型
- 使用 `TEXT` 大文本类型
- 使用 `CURRENT_TIMESTAMP` 默认当前时间
- 使用 `CREATE INDEX IF NOT EXISTS`（PostgreSQL 9.5+ 支持）

如果你修改 `V1__init.sql`，请在 PostgreSQL 16 上测试兼容性。

---

## 8. 常见问题

### Q1: 启动时提示 "password authentication failed"

**原因**：密码错误，或 `pg_hba.conf` 认证方式配置不正确。

**解决**：
1. 确认 `db.password` 正确
2. 检查 `C:\Program Files\PostgreSQL\16\data\pg_hba.conf`，确保有：
   ```
   # IPv4 local connections
   host    all    all    127.0.0.1/32    scram-sha-256
   # 或（较宽松）
   host    all    all    127.0.0.1/32    md5
   ```
3. 重启 PostgreSQL 服务：`Restart-Service -Name "postgresql-x64-16"`

### Q2: 启动时提示 "database does not exist"

**原因**：数据库 `his_db` 不存在。

**解决**：按 3.1 节创建数据库。

### Q3: 迁移失败 "relation already exists"

**原因**：`schema_version` 表记录了迁移历史，但实际的表结构不一致。

**解决**（开发环境）：
```sql
DROP DATABASE his_db;
CREATE DATABASE his_db;
-- 重新运行迁移（HIS 启动时自动执行）
```

### Q4: 生产环境如何升级数据库 schema？

**方案**：新建 `V2__xxx.sql`、`V3__xxx.sql` 等迁移文件，`MigrationRunner` 会自动执行未应用的迁移。

**注意**：不要修改已有的迁移文件（如 `V1__init.sql`），生产环境应创建新的迁移版本。

### Q5: 如何备份 PostgreSQL 数据？

```powershell
# 逻辑备份（推荐）
pg_dump -U his_user -d his_db -f his_db_backup.sql

# 压缩备份
pg_dump -U his_user -d his_db | gzip > his_db_backup.sql.gz

# 恢复
psql -U his_user -d his_db -f his_db_backup.sql
```

---

## 9. 性能优化建议（PostgreSQL）

1. **连接池配置**（`application.properties`）：
   ```properties
   db.pool.size=50        # 最大连接数（按并发用户数调整）
   db.pool.min.idle=10    # 最小空闲连接
   db.pool.timeout=30000  # 连接超时（毫秒）
   ```

2. **PostgreSQL 服务器端配置**（`postgresql.conf`）：
   ```
   max_connections = 200
   shared_buffers = 256MB
   effective_cache_size = 2GB
   maintenance_work_mem = 64MB
   checkpoint_completion_target = 0.9
   wal_buffers = 16MB
   default_statistics_target = 500
   random_page_cost = 1.1
   effective_io_concurrency = 200
   work_mem = 10MB
   min_wal_size = 2GB
   max_wal_size = 8GB
   ```

3. **定期 VACUUM/ANALYZE**：
   ```sql
   VACUUM ANALYZE;
   ```

---

## 10. 联系与支持

如有 PostgreSQL 切换问题，请联系 HIS 开发团队。

**文档版本**：1.0 | **更新日期**：2026-06-06
