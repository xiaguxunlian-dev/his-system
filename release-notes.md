## 安装包

### Windows（.exe 自解压安装程序）
双击运行，选择解压目录即可，含内置 JRE（约 81 MB/模块）

### Linux（.deb 安装包）
```bash
sudo dpkg -i his-xxx_1.0-1_amd64.deb
```
含内置 JRE（约 84 MB/模块）

---

## 模块下载

| 模块 | Windows | Linux |
|------|---------|-------|
| 挂号管理 | `HIS-Registration-Setup-v1.0.0.exe` | `his-registration_1.0-1_amd64.deb` |
| 门诊工作站 | `HIS-Outpatient-Setup-v1.0.0.exe` | `his-outpatient_1.0-1_amd64.deb` |
| 住院管理 | `HIS-Inpatient-Setup-v1.0.0.exe` | `his-inpatient_1.0-1_amd64.deb` |
| 药品管理 | `HIS-Pharmacy-Setup-v1.0.0.exe` | `his-pharmacy_1.0-1_amd64.deb` |
| 检查检验 | `HIS-Examination-Setup-v1.0.0.exe` | `his-examination_1.0-1_amd64.deb` |
| 电子病历 | `HIS-Emr-Setup-v1.0.0.exe` | `his-emr_1.0-1_amd64.deb` |
| 收费管理 | `HIS-Billing-Setup-v1.0.0.exe` | `his-billing_1.0-1_amd64.deb` |
| 统计报表 | `HIS-Statistics-Setup-v1.0.0.exe` | `his-statistics_1.0-1_amd64.deb` |
| 系统管理 | `HIS-Admin-Setup-v1.0.0.exe` | `his-admin_1.0-1_amd64.deb` |

---

## 默认登录账户

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `admin123` | 系统管理员 |
| `doctor` | `admin123` | 医生 |
| `guahao` | `admin123` | 挂号员 |

---

## 数据库配置

- 数据库：PostgreSQL 16+
- 默认地址：`localhost:5432`
- 数据库名：`his_db`
- 用户名：`his_user`
- 密码：`his@2026`

首次运行需要安装并启动 PostgreSQL，可使用项目自带的 `scripts/setup-database.bat`（Windows）或 `scripts/setup-database.sh`（Linux）初始化数据库。

---

## 问题反馈

有问题联系 1432758432@qq.com
