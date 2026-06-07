@echo off
chcp 65001 >nul
title HIS - 数据库恢复工具

echo.
echo ╔══════════════════════════════════╗
echo ║  HIS 医院信息系统 — 数据库恢复         ║
echo ╚══════════════════════════════════╝
echo.

set /p PG_HOST="数据库主机 [localhost]: "
if "%PG_HOST%"=="" set PG_HOST=localhost

set /p PG_PORT="端口 [5432]: "
if "%PG_PORT%"=="" set PG_PORT=5432

set /p PG_USER="用户 [his_user]: "
if "%PG_USER%"=="" set PG_USER=his_user

set /p PG_DB="目标数据库 [his_db]: "
if "%PG_DB%"=="" set PG_DB=his_db

set /p PGPASSWORD="密码: "

echo.
set /p BACKUP_FILE="备份文件路径: "
if not exist "%BACKUP_FILE%" (
    echo [×] 文件不存在: %BACKUP_FILE%
    pause
    exit /b 1
)

echo.
echo 警告: 此操作将覆盖数据库 %PG_DB% 中的所有数据!
set /p CONFIRM="确认继续？(yes/NO): "
if not "%CONFIRM%"=="yes" (
    echo 已取消
    exit /b 0
)

echo.
echo 正在恢复 %BACKUP_FILE% 到 %PG_DB%@%PG_HOST% ...
echo.

createdb -h %PG_HOST% -p %PG_PORT% -U %PG_USER% %PG_DB%_restore 2>nul || (
    echo 创建临时数据库失败，尝试直接恢复...
)

pg_restore -h %PG_HOST% -p %PG_PORT% -U %PG_USER% -d %PG_DB% -c "%BACKUP_FILE%" 2>&1
if %ERRORLEVEL% equ 0 (
    echo.
    echo ╔══════════════════════════════════╗
    echo ║  [√] 恢复成功!                          ║
    echo ╚══════════════════════════════════╝
) else (
    echo.
    echo ╔══════════════════════════════════╗
    echo ║  [×] 恢复失败                            ║
    echo ╚══════════════════════════════════╝
)
echo.
pause
