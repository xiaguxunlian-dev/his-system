# HIS 医院信息系统 v1.0

[![Build Status](https://github.com/xiaguxunlian-dev/his-system/actions/workflows/build.yml/badge.svg)](https://github.com/xiaguxunlian-dev/his-system/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/xiaguxunlian-dev/his-system)](https://github.com/xiaguxunlian-dev/his-system/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **Hospital Information System** — Java 17 + JavaFX 21 + PostgreSQL 16，9 个独立子系统模块

---

## 系统概述

本系统是一套基于 Java 的医院信息管理系统，覆盖挂号、门诊、住院、药品、检查、病历、收费、统计及系统管理九大业务模块。每个模块为独立子系统，拥有自己的窗口界面，共用同一 PostgreSQL 数据库。

### 技术架构

```
┌───────────────────────────────────────────────────────────┐
│                       HIS 医院信息系统                            │
├───────────────────────────────────────────────────────────┤
│  挂号 │ 门诊 │ 住院 │ 药品 │ 检查 │ 病历 │ 收费 │ 统计 │ 管理   │
├───────────────────────────────────────────────────────────┤
│                    his-common（共享层）                            │
│    认证服务 │ 数据库迁移 │ 用户管理 │ 通用 UI │ 工具类            │
├───────────────────────────────────────────────────────────┤
│                    PostgreSQL 16                                 │
│              数据库迁移：V1 → V2 → V3 ...                         │
└───────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 LTS |
| UI 框架 | JavaFX 21 (OpenJFX) |
| 数据库 | PostgreSQL 16 |
| 连接池 | HikariCP 5.x |
| 密码加密 | BCrypt (jbcrypt 0.4) |
| 构建工具 | Maven 多模块 |
| 打包工具 | jpackage (JDK 23) |
| 运行环境 | Windows 10/11, Ubuntu 22.04+ |

---

## 下载安装

### 直接下载（推荐）

前往 [GitHub Releases](https://github.com/xiaguxunlian-dev/his-system/releases) 下载最新版本：

| 平台 | 安装包格式 | 说明 |
|------|-----------|------|
| **Windows** | `.exe` 自解压安装程序 | 双击运行，选择解压目录即可，含内置 JRE |
| **Linux** | `.deb` 安装包 | `sudo dpkg -i xxx.deb`，含内置 JRE |

每个模块独立安装，按需下载：

| 模块 | Windows 安装包 | Linux 安装包 |
|------|:---:|:---:|
| 挂号管理 | `HIS-Registration-Setup-v1.0.0.exe` (~81 MB) | `his-registration_1.0-1_amd64.deb` (~84 MB) |
| 门诊工作站 | `HIS-Outpatient-Setup-v1.0.0.exe` (~81 MB) | `his-outpatient_1.0-1_amd64.deb` (~84 MB) |
| 住院管理 | `HIS-Inpatient-Setup-v1.0.0.exe` (~81 MB) | `his-inpatient_1.0-1_amd64.deb` (~84 MB) |
| 药品管理 | `HIS-Pharmacy-Setup-v1.0.0.exe` (~81 MB) | `his-pharmacy_1.0-1_amd64.deb` (~84 MB) |
| 检查检验 | `HIS-Examination-Setup-v1.0.0.exe` (~81 MB) | `his-examination_1.0-1_amd64.deb` (~84 MB) |
| 电子病历 | `HIS-Emr-Setup-v1.0.0.exe` (~81 MB) | `his-emr_1.0-1_amd64.deb` (~84 MB) |
| 收费管理 | `HIS-Billing-Setup-v1.0.0.exe` (~81 MB) | `his-billing_1.0-1_amd64.deb` (~84 MB) |
| 统计报表 | `HIS-Statistics-Setup-v1.0.0.exe` (~81 MB) | `his-statistics_1.0-1_amd64.deb` (~84 MB) |
| 系统管理 | `HIS-Admin-Setup-v1.0.0.exe` (~81 MB) | `his-admin_1.0-1_amd64.deb` (~84 MB) |

### 安装步骤

**Windows：**
1. 下载对应模块的 `.exe` 安装程序
2. 双击运行，选择解压目录（建议 `D:\HIS\`）
3. 首次运行前，确保 PostgreSQL 数据库已启动
4. 双击桌面快捷方式启动

**Linux (Ubuntu/Debian)：**
```bash
# 安装
sudo dpkg -i his-registration_1.0-1_amd64.deb

# 启动
his-registration

# 卸载
sudo dpkg -r his-registration
```

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

- **JDK 17+**（推荐 JDK 23）
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

### 4. 运行模块（开发模式）

**Windows：**
```shell
# 已打包的模块（推荐）
dist\HIS-Registration\HIS-Registration.exe

# 开发模式
cd his-registration
mvn javafx:run
```

**Linux：**
```bash
# 已安装模块
his-registration

# 开发模式（需 JavaFX 21+）
export PATH_TO_FX=/path/to/javafx-sdk-21/lib
java --module-path $PATH_TO_FX \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
     -jar his-registration/target/his-registration-1.0.0.jar
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
├── LICENSE                     # MIT 许可证
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
    ├── build-exe-installers.ps1 # Windows EXE 安装包生成
    ├── setup-database.bat/.sh  # 数据库初始化
    └── init-postgres.sql       # PostgreSQL 初始化 SQL
```

---

## 打包部署

### Windows（jpackage + 7z SFX）

```powershell
# 生成 app-image（dist/ 目录）
powershell -File scripts\package-all-fixed.ps1

# 生成 .exe 自解压安装包（installers/windows/ 目录）
python scripts\build_exe_installers.py
```

生成产物：
- `dist/HIS-<Module>/` — 自包含应用目录（含 JRE，~214 MB/模块）
- `installers/windows/HIS-<中文名>-Setup-v1.0.0.exe` — 自解压安装程序（~81 MB/模块）

### Linux（jpackage + dpkg-deb）

```bash
# 在 Linux 环境执行（需 JDK 23 + jpackage）
bash scripts/package-all.sh

# 输出到 Linux-deb/ 目录
ls Linux-deb/
# his-registration_1.0-1_amd64.deb  ...
```

---

## 常见问题

<details>
<summary><b>登录失败（密码错误）</b></summary>

密码使用 BCrypt 加密存储。如果遇到"用户名或密码错误"：
1. 确认已执行 V2 迁移（V2__fix_password.sql）
2. 默认账号：`admin` / `admin123`
3. 可运行 `scripts\setup-database.bat` 重置数据库
</details>

<details>
<summary><b>启动 EXE 无反应或闪退</b></summary>

请检查：
1. 解压目录不能有中文或空格
2. 检查 `app/logs/` 下的日志文件
3. 确认数据库连接配置正确（`app/application.properties`）
4. 防火墙是否阻止了 5432 端口
</details>

<details>
<summary><b>数据库连接失败</b></summary>

默认连接配置：
- 地址：`localhost:5432`
- 数据库：`his_db`
- 用户：`his_user`
- 密码：`his@2026`

可在 `app/application.properties` 中修改。
</details>

<details>
<summary><b>修改共享代码后模块未更新</b></summary>

各模块使用 Fat JAR（maven-assembly-plugin），his-common 的类被打包进每个模块 JAR。
修改 his-common 后，需要**全量重新编译**：
```bash
mvn clean package -DskipTests
```
仅编译 his-common 不够，每个模块也需要重新打包。
</details>

<details>
<summary><b>Linux 无桌面环境运行</b></summary>

需要安装虚拟显示：
```bash
sudo apt install xvfb
xvfb-run his-registration
```
</details>

<details>
<summary><b>JavaFX 模块加载失败</b></summary>

错误：`Module javafx.base not found`

原因：`app/javafx/` 目录为空或 `app.cfg` 中 `--module-path` 路径错误。
修复：
1. 确认 `app/javafx/` 下存在 `javafx-*.jar`
2. `app/HIS-<Module>.cfg` 中 `--module-path=$APPDIR\javafx`
</details>

---

## 数据库迁移机制

项目启动时自动执行 `db/migration/` 下的 `V*.sql` 文件，按版本号递增：

| 版本 | 文件 | 说明 |
|:---:|------|------|
| V1 | `V1__init.sql` | 建表 + 种子数据（6 个默认用户、基础字典） |
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
