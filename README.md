# HIS 医院信息系统 v1.0

> **Hospital Information System** — Java 17 + JavaFX 21 + PostgreSQL 16，9 个独立子系统模块

---

## 系统概述

本系统是一套基于 Java 的医院信息管理系统，覆盖挂号、门诊、住院、药品、检查、病历、收费、统计及系统管理九大业务模块。每个模块为独立子系统，拥有自己的窗口界面，共用同一 PostgreSQL 数据库。

### 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                       HIS 医院信息系统                            │
├─────────────────────────────────────────────────────────────────┤
│  挂号 │ 门诊 │ 住院 │ 药品 │ 检查 │ 病历 │ 收费 │ 统计 │ 管理   │
├─────────────────────────────────────────────────────────────────┤
│                    his-common（共享层）                            │
│    认证服务 │ 数据库迁移 │ 用户管理 │ 通用 UI │ 工具类            │
├─────────────────────────────────────────────────────────────────┤
│                    PostgreSQL 16                                 │
│              数据库迁移：V1 → V2 → V3 ...                         │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 LTS |
| UI 框架 | JavaFX 21 (OpenJFX) |
| 数据库 | PostgreSQL 16 |
| 连接池 | HikariCP |
| 密码加密 | BCrypt (jbcrypt 0.4) |
| 构建工具 | Maven 多模块 |
| 打包工具 | jpackage (JDK 23) |
| 运行环境 | Windows 10/11, Ubuntu 22.04+ |

---

## 模块列表

| 序号 | 模块 | 主类 | 功能 |
|:---:|------|------|------|
| 1 | 挂号管理 (HIS-Registration) | `com.his.registration.ui.RegistrationApp` | 患者建档/挂号/预约 |
| 2 | 门诊管理 (HIS-Outpatient) | `com.his.outpatient.ui.OutpatientApp` | 门诊医生工作站 |
| 3 | 住院管理 (HIS-Inpatient) | `com.his.inpatient.ui.InpatientApp` | 住院登记/床位管理 |
| 4 | 药品管理 (HIS-Pharmacy) | `com.his.pharmacy.ui.PharmacyApp` | 药房库存/发药管理 |
| 5 | 检查管理 (HIS-Examination) | `com.his.examination.ui.ExaminationApp` | 检验/检查申请与报告 |
| 6 | 病历管理 (HIS-Emr) | `com.his.emr.ui.EmrApp` | 电子病历管理 |
| 7 | 收费管理 (HIS-Billing) | `com.his.billing.ui.BillingApp` | 费用结算/收退费 |
| 8 | 统计分析 (HIS-Statistics) | `com.his.statistics.ui.StatisticsApp` | 业务数据统计报表 |
| 9 | 系统管理 (HIS-Admin) | `com.his.admin.ui.AdminApp` | 用户/角色/权限管理 |

---

## 快速开始

### 1. 环境要求

- **JDK 17+** （推荐 JDK 23）
- **Maven 3.8+**
- **PostgreSQL 16**（或 Docker）

### 2. 启动数据库

**方式 A：Docker（推荐）**
```bash
docker compose up -d
```

**方式 B：本地安装**
```bash
# Windows
scripts\setup-database.bat

# Linux/Mac
bash scripts/setup-database.sh
```

### 3. 编译项目

```bash
# 全项目编译（跳过测试）
mvn clean package -DskipTests

# 单模块编译
mvn clean package -pl his-registration -am -DskipTests
```

### 4. 运行模块

**Windows：**
```shell
# 开发模式
cd his-registration
mvn javafx:run
```

**Linux：**
```bash
# 设置数据库环境变量
export HIS_DB_TYPE=postgresql
export HIS_DB_HOST=127.0.0.1
export HIS_DB_PORT=5432
export HIS_DB_NAME=hisdb
export HIS_DB_USER=hisuser
export HIS_DB_PASS=his123

# 运行
java --module-path /path/to/javafx \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
     -cp his-registration-1.0.0.jar \
     com.his.registration.ui.RegistrationApp
```

### 5. 登录账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | 系统管理员 |
| `guahao` | `admin123` | 挂号员 |
| `doctor` | `admin123` | 医生 |
| `nurse` | `admin123` | 护士 |
| `pharmacy` | `admin123` | 药师 |
| `cashier` | `admin123` | 收费员 |

---

## 项目结构

```
his-system/
├── pom.xml                     # 父 POM（多模块聚合）
├── docker-compose.yml          # Docker 数据库环境
├── README.md                   # 本文件
├── FAQ.md                      # 常见问题
├── his-common/                 # 共享模块
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/his/
│       │   ├── auth/           # 登录认证
│       │   ├── config/         # 配置管理
│       │   ├── model/          # 数据模型
│       │   ├── service/        # 业务服务
│       │   ├── shared/database/ # 数据库迁移
│       │   └── ui/             # 通用 UI 组件
│       └── resources/db/migration/
│           ├── V1__init.sql    # 初始建表 + 种子数据
│           ├── V2__fix_password.sql
│           └── V3__add_drug_inventory_columns.sql
├── his-registration/           # 挂号管理子系统
├── his-outpatient/             # 门诊管理子系统
├── his-inpatient/              # 住院管理子系统
├── his-pharmacy/               # 药品管理子系统
├── his-examination/            # 检查管理子系统
├── his-emr/                    # 病历管理子系统
├── his-billing/                # 收费管理子系统
├── his-statistics/             # 统计分析子系统
├── his-admin/                  # 系统管理子系统
└── scripts/                    # 打包/部署脚本
    ├── package-all-fixed.ps1   # Windows 批量打包
    ├── setup-database.bat/.sh  # 数据库初始化
    └── init-postgres.sql       # PostgreSQL 初始化 SQL
```

---

## 打包部署

### Windows

使用 JDK 23 的 jpackage + 后处理脚本：

```shell
# 全量打包
powershell -File scripts\package-all-fixed.ps1
```

**关键注意事项：**
- JavaFX JAR 放在 `app\javafx\` 子目录
- `app.cfg` 配置：`--module-path=$APPDIR\javafx`
- 每个模块约 217 MB（含 JRE）
- 双击 `启动.bat` 即可运行

### Linux

```bash
# 需在 Linux 环境执行（不支持交叉编译）
bash scripts/package-all.sh

# 输出 .deb 安装包
Linux-deb/his-registration_1.0-1_amd64.deb
```

---

## 常见问题

详见 [FAQ.md](./FAQ.md)，覆盖以下问题：

- 登录失败（密码哈希不匹配）
- 药品管理 `drug_spec` 列缺失
- 登录界面顶部乱码
- EXE 双击无反应
- JavaFX 模块加载失败
- 数据库连接配置
- Linux 无头运行
- 修改 his-common 后子系统无变化
- 多模块同时运行

---

## 数据库迁移机制

项目启动时自动执行 `db/migration/` 下的 `V*.sql` 文件，按版本号递增：

| 版本 | 文件 | 说明 |
|:---:|------|------|
| V1 | `V1__init.sql` | 建表 + 种子数据（6个默认用户、基础字典） |
| V2 | `V2__fix_password.sql` | 修复 BCrypt 密码哈希 |
| V3 | `V3__add_drug_inventory_columns.sql` | 补充 drug_inventory 表字段 |

添加新迁移只需新建 `V4__your_migration.sql` 并在 `MigrationRunner.java` 中注册。

---

## 开发约定

- **数据库迁移**：所有 SQL 变更通过 `db/migration/V*.sql` 管理，自动递增执行
- **密码加密**：使用 BCrypt（10 rounds），`his-common` 提供 `PasswordUtil`
- **异步加载**：所有模块数据加载使用 `AsyncUIUtil`，避免阻塞 UI 线程
- **编码规范**：UTF-8 编码，微软雅黑 UI 字体
- **模块独立性**：每个模块可独立编译和运行，通过 `his-common` 共享代码
- **Fat JAR**：使用 `maven-assembly-plugin` + `jar-with-dependencies`，his-common 类被打包进每个模块

---

## License

[MIT License](LICENSE)

---

**有问题联系：1432758432@qq.com**
