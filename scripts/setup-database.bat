@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ============================================================
:: HIS 数据库初始化脚本 (Windows)
:: 
:: 用法:
::   交互模式:  setup-database.bat
::   批处理模式: setup-database.bat --batch [--host HOST] [--port PORT] [--user USER] [--pass PASS]
::   环境变量模式: 设置 HIS_DB_HOST / HIS_DB_PORT / HIS_DB_USER / HIS_DB_PASS
:: ============================================================

set BATCH_MODE=0
set PG_HOST=localhost
set PG_PORT=5432
set PG_USER=postgres
set PG_PASS=
set PGPASSWORD=

:: 解析参数
:parse_args
if "%~1"=="" goto :check_batch
if /i "%~1"=="--batch" (
    set BATCH_MODE=1
    shift
    goto :parse_args
)
if /i "%~1"=="--host" (
    set PG_HOST=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--port" (
    set PG_PORT=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--user" (
    set PG_USER=%~2
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--pass" (
    set PG_PASS=%~2
    shift
    shift
    goto :parse_args
)
shift
goto :parse_args

:check_batch
:: 环境变量覆盖
if not "%HIS_DB_HOST%"=="" set PG_HOST=%HIS_DB_HOST%
if not "%HIS_DB_PORT%"=="" set PG_PORT=%HIS_DB_PORT%
if not "%HIS_DB_USER%"=="" set PG_USER=%HIS_DB_USER%
if not "%HIS_DB_PASS%"=="" set PG_PASS=%HIS_DB_PASS%

:: 密码处理
if not "%PG_PASS%"=="" set PGPASSWORD=%PG_PASS%

echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║   医院信息系统 (HIS) — 数据库安装工具               ║
echo   ╚══════════════════════════════════════════════════╝
echo.

:: ============================================================
:: 第0步：检查 psql 是否可用
:: ============================================================
where psql >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [错误] 未找到 psql 命令
    echo.
    echo   请安装 PostgreSQL 客户端工具，或确保 psql 在 PATH 中：
    echo     - 下载安装包: https://www.postgresql.org/download/windows/
    echo     - 或单独安装 psql: 安装时选择"Command Line Tools"
    echo.
    if "%BATCH_MODE%"=="1" exit /b 1
    pause
    exit /b 1
)

:: 检查 psql 版本
for /f "tokens=3" %%v in ('psql --version 2^>^&1') do set PG_VER=%%v
echo   [√] 检测到 psql v%PG_VER%
echo.

:: ============================================================
:: 非批处理模式：交互式输入
:: ============================================================
if "%BATCH_MODE%"=="0" (
    echo   ┌──────────────────────────────────────────────┐
    echo   │  请配置 PostgreSQL 连接信息                    │
    echo   └──────────────────────────────────────────────┘
    echo.
    set /p "PG_HOST_IN=数据库服务器地址 [%PG_HOST%]: "
    if not "!PG_HOST_IN!"=="" set PG_HOST=!PG_HOST_IN!
    set /p "PG_PORT_IN=数据库端口 [%PG_PORT%]: "
    if not "!PG_PORT_IN!"=="" set PG_PORT=!PG_PORT_IN!
    set /p "PG_USER_IN=超级用户 [%PG_USER%]: "
    if not "!PG_USER_IN!"=="" set PG_USER=!PG_USER_IN!
    set /p "PG_PASS_IN=%PG_USER% 密码: "
    if not "!PG_PASS_IN!"=="" set PGPASSWORD=!PG_PASS_IN!
    echo.
    set /p "CONFIRM=确认连接 %PG_HOST%:%PG_PORT% 并执行初始化？(Y/N): "
    if /i not "!CONFIRM!"=="Y" (
        echo   已取消
        exit /b 0
    )
)

:: ============================================================
:: 连接测试
:: ============================================================
echo   ── 测试数据库连接...
psql -h %PG_HOST% -p %PG_PORT% -U %PG_USER% -d postgres -c "SELECT 1;" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [×] 无法连接到 PostgreSQL，请检查：
    echo      - 服务器是否在运行
    echo      - 主机/端口/用户名/密码是否正确
    echo      - pg_hba.conf 是否允许此连接
    if "%BATCH_MODE%"=="1" exit /b 1
    pause
    exit /b 1
)
echo   [√] 连接成功
echo.

:: ============================================================
:: 第1步：创建数据库用户 his_user
:: ============================================================
echo   ── 创建数据库用户 his_user...
psql -h %PG_HOST% -p %PG_PORT% -U %PG_USER% -d postgres -c "DO \$\$ BEGIN CREATE ROLE his_user WITH LOGIN PASSWORD 'his@2026'; EXCEPTION WHEN duplicate_object THEN RAISE NOTICE '用户已存在'; END \$\$;" >nul 2>&1
echo   [√] 用户 his_user 就绪
echo.

:: ============================================================
:: 第2步：创建数据库 his_db
:: ============================================================
echo   ── 创建数据库 his_db...
psql -h %PG_HOST% -p %PG_PORT% -U %PG_USER% -d postgres -c "CREATE DATABASE his_db WITH OWNER his_user ENCODING 'UTF8' LC_COLLATE 'zh_CN.UTF-8' LC_CTYPE 'zh_CN.UTF-8' TEMPLATE template0;" 2>nul
REM 数据库已存在时报错，忽略
echo   [√] 数据库 his_db 就绪
echo.

:: ============================================================
:: 第3步：授权
:: ============================================================
echo   ── 授权 his_user...
psql -h %PG_HOST% -p %PG_PORT% -U %PG_USER% -d his_db -c "GRANT ALL PRIVILEGES ON DATABASE his_db TO his_user; GRANT ALL ON SCHEMA public TO his_user;" >nul 2>&1
echo   [√] 权限已授权
echo.

:: ============================================================
:: 第4步：定位并执行 V1__init.sql
:: ============================================================
set SCRIPT_DIR=%~dp0
set V1_SQL=%SCRIPT_DIR%..\his-common\src\main\resources\db\migration\V1__init.sql
:: 规范化路径
for %%i in ("%V1_SQL%") do set V1_SQL=%%~fi

if not exist "%V1_SQL%" (
    set V1_SQL=D:\his\his-common\src\main\resources\db\migration\V1__init.sql
)
if not exist "%V1_SQL%" (
    if "%BATCH_MODE%"=="1" (
        echo   [×] 找不到 V1__init.sql
        exit /b 1
    )
    set /p "V1_SQL=请输入 V1__init.sql 完整路径: "
    if not exist "!V1_SQL!" (
        echo   [×] 文件不存在
        pause
        exit /b 1
    )
)

echo   ── 执行数据库迁移 (%V1_SQL%)...
psql -h %PG_HOST% -p %PG_PORT% -U his_user -d his_db -f "%V1_SQL%" 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [×] 迁移执行失败，请检查上方错误信息
    if "%BATCH_MODE%"=="1" exit /b 1
    pause
    exit /b 1
)
echo   [√] 表结构迁移完成
echo.

:: ============================================================
:: 第5步：导入种子数据
:: ============================================================
set DO_SEED=Y
if "%BATCH_MODE%"=="0" (
    set /p "DO_SEED=是否导入示例数据（科室、医生、管理员账号）？(Y/N) [Y]: "
    if "!DO_SEED!"=="" set DO_SEED=Y
)

if /i "%DO_SEED%"=="Y" (
    echo   ── 导入种子数据...

    REM 1. 科室数据 (列名: code, name, type, location)
    psql -h %PG_HOST% -p %PG_PORT% -U his_user -d his_db -c ^
    "INSERT INTO departments (code, name, type, location) VALUES ^
     ('NK','内科','临床科室','1号楼A区'),^
     ('WK','外科','临床科室','1号楼B区'),^
     ('EK','儿科','临床科室','2号楼A区'),^
     ('FCK','妇产科','临床科室','2号楼B区'),^
     ('JYK','检验科','医技科室','3号楼A区'),^
     ('YXK','影像科','医技科室','3号楼B区'),^
     ('YJK','药剂科','医技科室','1号楼C区'),^
     ('JZK','急诊科','临床科室','1号楼D区'),^
     ('MZK','门诊科','临床科室','1号楼E区')^
     ON CONFLICT(code) DO NOTHING;" >nul 2>&1

    REM 2. 医生数据 (列名: code, name, gender, title, department_id, phone)
    psql -h %PG_HOST% -p %PG_PORT% -U his_user -d his_db -c ^
    "INSERT INTO doctors (code, name, gender, title, department_id, phone) VALUES ^
     ('D001','张伟','男','主任医师',1,'13800000001'),^
     ('D002','李芳','女','副主任医师',2,'13800000002'),^
     ('D003','王磊','男','主治医师',3,'13800000003'),^
     ('D004','陈静','女','主任医师',4,'13800000004'),^
     ('D005','赵明','男','检验师',5,'13800000005')^
     ON CONFLICT(code) DO NOTHING;" >nul 2>&1

    REM 3. 管理员账号 (admin / his@2026)
    REM 列名: username, password_hash, display_name, role, department_id, is_active
    psql -h %PG_HOST% -p %PG_PORT% -U his_user -d his_db -c ^
    "INSERT INTO system_users (username, password_hash, display_name, role, is_active) VALUES ^
     ('admin','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','系统管理员','系统管理员',true)^
     ON CONFLICT(username) DO NOTHING;" >nul 2>&1

    echo   [√] 种子数据已导入
)
echo.

:: ============================================================
:: 第6步：生成配置文件
:: ============================================================
set CONFIG_DIR=%SCRIPT_DIR%..\his-common\src\main\resources
for %%i in ("%CONFIG_DIR%") do set CONFIG_DIR=%%~fi

if exist "%CONFIG_DIR%\application.properties" (
    echo   ── 配置 application.properties...

    REM 备份原文件
    if not exist "%CONFIG_DIR%\application.properties.bak" (
        copy "%CONFIG_DIR%\application.properties" "%CONFIG_DIR%\application.properties.bak" >nul 2>&1
    )

    REM 更新 PostgreSQL 配置
    powershell -NoProfile -Command ^
    "$f='%CONFIG_DIR%\application.properties';" ^
    "$c=(Get-Content $f -Raw);" ^
    "$c=$c -replace 'db\.type=.*','db.type=postgresql';" ^
    "$c=$c -replace 'db\.host=.*','db.host=%PG_HOST%';" ^
    "$c=$c -replace 'db\.port=.*','db.port=%PG_PORT%';" ^
    "$c=$c -replace 'db\.name=.*','db.name=his_db';" ^
    "$c=$c -replace 'db\.username=.*','db.username=his_user';" ^
    "$c=$c -replace 'db\.password=.*','db.password=his@2026';" ^
    "[System.IO.File]::WriteAllText($f, $c)" >nul 2>&1

    echo   [√] 配置文件已更新为 PostgreSQL 连接
)

echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║             数据库初始化完成!                       ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║  主机: %PG_HOST%                                     ║
echo   ║  端口: %PG_PORT%                                     ║
echo   ║  数据库: his_db                                  ║
echo   ║  用户: his_user                                 ║
echo   ║  密码: his@2026                                 ║
echo   ║  管理员: admin / his@2026 (首次登录需修改)         ║
echo   ╚══════════════════════════════════════════════════╝
echo.
echo   提示:
echo   - 已自动修改 application.properties 为 PostgreSQL 配置
echo   - 如需恢复：备份文件在同目录下的 application.properties.bak
echo.
if "%BATCH_MODE%"=="0" pause
exit /b 0
