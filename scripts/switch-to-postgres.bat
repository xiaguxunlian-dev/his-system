@echo off
REM ========================================================================
REM HIS - PostgreSQL 模式切换脚本
REM ========================================================================
REM 使用方式：
REM   1. 确保 PostgreSQL 16+ 已安装并运行
REM   2. 双击运行此脚本
REM   3. 按菜单选择操作
REM ========================================================================

REM --- 默认 PostgreSQL 连接参数（可按需修改） ---
set DEFAUT_DB_HOST=localhost
set DEFAUT_DB_PORT=5432
set DEFAUT_DB_NAME=his_db
set DEFAUT_DB_USER=postgres
set DEFAUT_DB_PASS=

cls
echo ===========================================
echo  HIS - PostgreSQL 模式切换工具
echo ===========================================
echo.
echo  当前默认连接参数：
echo    主机: %DEFAUT_DB_HOST%
echo    端口: %DEFAUT_DB_PORT%
echo    数据库: %DEFAUT_DB_NAME%
echo    用户: %DEFAUT_DB_USER%
echo.
echo  如需修改，请编辑此脚本中的 DEFAUT_ 变量。
echo ===========================================
echo.

if "%DEFAUT_DB_PASS%"=="" (
    set /p DEFAUT_DB_PASS="请输入 PostgreSQL 密码: "
)

:menu
cls
echo ===========================================
echo  HIS - PostgreSQL 模式 - 主菜单
echo ===========================================
echo.
echo  [环境变量]
echo    HIS_DB_TYPE    = %HIS_DB_TYPE%
echo    HIS_DB_HOST    = %HIS_DB_HOST%
echo    HIS_DB_PORT    = %HIS_DB_PORT%
echo    HIS_DB_NAME    = %HIS_DB_NAME%
echo    HIS_DB_USER    = %HIS_DB_USER%
echo.
echo  [菜单]
echo    1. 运行 PostgreSQL 连接测试（PostgresConnectionTest）
echo    2. 运行所有测试（PostgreSQL 模式）
echo    3. 启动 his-admin（PostgreSQL 模式）
echo    4. 打包所有模块（PostgreSQL 模式，需先测试通过）
echo    5. 退出
echo.
set /p CHOICE="请选择 (1-5): "

if "%CHOICE%"=="1" goto test
if "%CHOICE%"=="2" goto test_all
if "%CHOICE%"=="3" goto run_admin
if "%CHOICE%"=="4" goto package
if "%CHOICE%"=="5" goto exit
goto menu

:test
echo.
echo 运行 PostgreSQL 连接测试...
set HIS_DB_TYPE=postgresql
set HIS_DB_HOST=%DEFAUT_DB_HOST%
set HIS_DB_PORT=%DEFAUT_DB_PORT%
set HIS_DB_NAME=%DEFAUT_DB_NAME%
set HIS_DB_USER=%DEFAUT_DB_USER%
set HIS_DB_PASS=%DEFAUT_DB_PASS%
cd /d D:\his
call mvn test -pl his-common -Dtest=PostgresConnectionTest
pause
goto menu

:test_all
echo.
echo 运行所有测试（PostgreSQL 模式）...
set HIS_DB_TYPE=postgresql
set HIS_DB_HOST=%DEFAUT_DB_HOST%
set HIS_DB_PORT=%DEFAUT_DB_PORT%
set HIS_DB_NAME=%DEFAUT_DB_NAME%
set HIS_DB_USER=%DEFAUT_DB_USER%
set HIS_DB_PASS=%DEFAUT_DB_PASS%
cd /d D:\his
call mvn test -pl his-common,his-admin
pause
goto menu

:run_admin
echo.
echo 启动 his-admin（PostgreSQL 模式）...
set HIS_DB_TYPE=postgresql
set HIS_DB_HOST=%DEFAUT_DB_HOST%
set HIS_DB_PORT=%DEFAUT_DB_PORT%
set HIS_DB_NAME=%DEFAUT_DB_NAME%
set HIS_DB_USER=%DEFAUT_DB_USER%
set HIS_DB_PASS=%DEFAUT_DB_PASS%
cd /d D:\his\his-admin
call mvn javafx:run
pause
goto menu

:package
echo.
echo 打包所有模块（PostgreSQL 模式）...
echo 注意：打包结果同时包含 H2 和 PostgreSQL 驱动。
set HIS_DB_TYPE=postgresql
set HIS_DB_HOST=%DEFAUT_DB_HOST%
set HIS_DB_PORT=%DEFAUT_DB_PORT%
set HIS_DB_NAME=%DEFAUT_DB_NAME%
set HIS_DB_USER=%DEFAUT_DB_USER%
set HIS_DB_PASS=%DEFAUT_DB_PASS%
cd /d D:\his
call build-all.bat
pause
goto menu

:exit
echo 退出。
exit /b 0
