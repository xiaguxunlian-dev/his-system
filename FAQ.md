# HIS 医院信息系统 — 常见问题解答 (FAQ)

---

## 目录

1. [登录相关](#登录相关)
2. [启动相关](#启动相关)
3. [数据库相关](#数据库相关)
4. [打包部署相关](#打包部署相关)
5. [模块运行相关](#模块运行相关)
6. [Linux 部署相关](#linux-部署相关)

---

## 登录相关

### Q1: 所有子系统都登录不进去，提示"用户名或密码错误"

**原因**：`V1__init.sql` 中的 BCrypt 密码哈希与明文 `admin123` 不匹配。

**解决方案**：
1. 数据库迁移会自动执行 `V2__fix_password.sql`，更新所有默认用户密码哈希
2. 如果是全新安装，`V1__init.sql` 已包含正确的哈希值
3. 手动修复：连接数据库执行：
```sql
UPDATE system_users SET password_hash = '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le'
WHERE username IN ('admin','guahao','doctor','nurse','pharmacy','cashier');
```

### Q2: 登录界面顶部显示乱码 "XXåå..."

**原因**：`AppConfig.getHospitalName()` 默认值 "XX医院" 在某些编码环境下显示为乱码。

**解决方案**（已在 v1.0 修复）：
- `LoginDialog.java` 顶部标签已改为固定文字 `"有问题联系1432758432@qq.com"`
- 如果仍需自定义，修改 `his-common/src/main/java/com/his/auth/LoginDialog.java` 第 67 行
- **重要**：修改后需要**全项目重新编译**（因为每个模块的 fat JAR 都包含 his-common 类）

---

## 启动相关

### Q3: Windows EXE 双击无反应，没有任何错误提示

**原因**：`jpackage --type app-image` 生成的 exe 在 JVM 初始化阶段静默失败，常见原因：
1. JavaFX 模块未正确加入运行时
2. `app.cfg` 中 `--module-path` 路径错误
3. 打包的 `input` 目录包含了编译产物而非纯 JAR

**解决方案**：
- **推荐**：双击 `启动.bat`，使用 bundled JRE 的 javaw 启动
- **命令行调试**（用系统 JDK 直接启动定位问题）：
```shell
java --module-path "app\javafx" \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
     -cp "app\*" \
     com.his.registration.ui.RegistrationApp
```
- **根本修复**（重新打包）：确保 jpackage 的 `--input` 目录只包含 JAR，JavaFX 放 `app/javafx/`

### Q4: 启动报 "Module javafx.controls not found"

**原因**：JavaFX 模块 JAR 不在 module-path 上。

**解决方案**：
1. 确认 `app/javafx/` 目录包含 4 个 JavaFX 模块 JAR：
   - `javafx-base-21.0.2-win.jar`
   - `javafx-controls-21.0.2-win.jar`
   - `javafx-fxml-21.0.2-win.jar`
   - `javafx-graphics-21.0.2-win.jar`
2. Maven 仓库下载：
```bash
mvn dependency:copy -Dartifact=org.openjfx:javafx-controls:21.0.2:win
mvn dependency:copy -Dartifact=org.openjfx:javafx-fxml:21.0.2:win
mvn dependency:copy -Dartifact=org.openjfx:javafx-graphics:21.0.2:win
mvn dependency:copy -Dartifact=org.openjfx:javafx-base:21.0.2:win
```

### Q5: 启动报 "Missing JavaFX application class"

**原因**：主应用 JAR 包含了 `javafx/` 包下的类文件（fat JAR 用 assembly 插件打包），与 JavaFX 模块冲突。

**解决方案**：
1. JavaFX JAR **必须**放在独立的 `app/javafx/` 目录
2. JavaFX 用 `--module-path` 加载
3. 主应用 JAR 用 `-cp` 加载
4. **不要**把 JavaFX JAR 混在主应用 classpath 中

---

## 数据库相关

### Q6: 药品管理子系统报 "Column di.drug_spec not found"

**原因**：`drug_inventory` 表缺少 `drug_spec` 列，但代码中多处查询引用了它。

**解决方案**（已在 v1.0 修复）：
- `V3__add_drug_inventory_columns.sql` 会自动添加缺失列
- `V1__init.sql` 的 `drug_inventory` 表定义已包含此列
- 手动修复：
```sql
ALTER TABLE drug_inventory ADD COLUMN IF NOT EXISTS drug_spec VARCHAR(100);
```

### Q7: 药品管理报 "Column di.id not found"

**原因**：JDBC ResultSet 获取列值时使用了表别名前缀 `rs.getString("di.drug_name")`，但 PostgreSQL 返回的列标签不带前缀。

**解决方案**（已在 v1.0 修复）：
- 所有 `rs.getXxx("di.xxx")` 改为 `rs.getXxx("xxx")`
- 即：`rs.getString("drug_name")` 而非 `rs.getString("di.drug_name")`

### Q8: 数据库连接失败

**检查步骤**：
1. 确认 PostgreSQL 运行中：`sudo systemctl status postgresql`（Linux）或 `pg_isready`（Windows）
2. 确认环境变量设置正确：`HIS_DB_TYPE`, `HIS_DB_HOST`, `HIS_DB_PORT`, `HIS_DB_NAME`, `HIS_DB_USER`, `HIS_DB_PASS`
3. 确认数据库和用户存在：
```sql
CREATE USER hisuser WITH PASSWORD 'his123';
CREATE DATABASE hisdb OWNER hisuser;
GRANT ALL PRIVILEGES ON DATABASE hisdb TO hisuser;
```

### Q9: Docker 数据库怎么连接

```bash
docker compose up -d
```
连接信息：Host `localhost`, Port `5432`, Database `his_db`, User `postgres`, Password `postgres123`

---

## 打包部署相关

### Q10: jpackage 生成的 MSI 安装包不可用

**原因**：JDK 23 的 jpackage 与 WiX 3.11 不兼容（`light.exe` 退出码 216）。

**解决方案**：使用 `--type app-image` 代替 `--type msi`，生成自包含目录通过 ZIP 分发。

### Q11: 修改 his-common 后子系统没有变化

**原因**：每个子系统的 JAR 是 fat JAR（`maven-assembly-plugin` + `jar-with-dependencies`），his-common 的类会被打包进每个模块的 JAR。

**解决方案**：
```bash
# 必须全项目重新编译
mvn clean package -DskipTests
# 然后重新打包或替换 dist 中的 JAR
```

### Q12: 如何减小打包体积

当前每个模块约 217 MB（含完整 JRE 和 JavaFX）。优化方案：
1. 去掉 bundled JRE，要求用户系统安装 Java
2. 使用 jlink 生成精简 JRE
3. 9 个模块共享同一 JRE（通过 `--runtime-image` 参数）

---

## 模块运行相关

### Q13: Linux 下如何无头运行（无显示器）

```bash
xvfb-run -a java --module-path ~/his-apps/javafx \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
  -cp his-registration.jar com.his.registration.ui.RegistrationApp
```

### Q14: Linux 上 JavaFX 模块在哪

系统安装（Ubuntu）：`sudo apt install openjfx`，JAR 位置：`/usr/share/java/javafx-*.jar`

Maven 依赖：`~/.m2/repository/org/openjfx/javafx-*/21.0.2/javafx-*-21.0.2-linux.jar`

### Q15: 多模块如何同时运行

每个模块是独立的 Java 进程，打开多个终端分别运行即可。所有模块共用同一数据库，操作实时同步。

---

## Linux 部署相关

### Q16: Ubuntu 安装 deb 包后如何运行

```bash
# 1. 安装
sudo dpkg -i his-registration_1.0-1_amd64.deb
sudo apt install -f

# 2. 设置环境变量
export HIS_DB_TYPE=postgresql HIS_DB_HOST=127.0.0.1 HIS_DB_PORT=5432
export HIS_DB_NAME=hisdb HIS_DB_USER=hisuser HIS_DB_PASS=his123

# 3. 运行
/opt/his-registration/bin/his-registration
```

---

## 环境变量参考

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `HIS_DB_TYPE` | `h2` | 数据库类型（`h2` 或 `postgresql`） |
| `HIS_DB_HOST` | `127.0.0.1` | 数据库主机 |
| `HIS_DB_PORT` | `5432` | 数据库端口 |
| `HIS_DB_NAME` | `hisdb` | 数据库名称 |
| `HIS_DB_USER` | `hisuser` | 数据库用户 |
| `HIS_DB_PASS` | `his123` | 数据库密码 |

> 使用 H2 内嵌模式时不需设置环境变量，应用启动时自动创建内存数据库。

---

**有问题联系：1432758432@qq.com**
