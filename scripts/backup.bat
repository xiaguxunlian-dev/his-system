@echo off
chcp 65001 >nul
title HIS - 数据库备份工具

echo.
echo ╔══════════════════════════════════════╗
echo ║  HIS 医院信息系统 — 数据库备份         ║
echo ╚══════════════════════════════════════╝
echo.

set /p PG_HOST="数据库主机 [localhost]: "
if "%PG_HOST%"=="" set PG_HOST=localhost

set /p PG_PORT="端口 [5432]: "
if "%PG_PORT%"=="" set PG_PORT=5432

set /p PG_USER="用户 [his_user]: "
if "%PG_USER%"=="" set PG_USER=his_user

set /p PG_DB="数据库 [his_db]: "
if "%PG_DB%"=="" set PG_DB=his_db

set /p PGPASSWORD="密码: "

echo.
echo 正在备份 %PG_DB%@%PG_HOST%:%PG_PORT% ...
echo.

set BACKUP_DIR=backups
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

set TIMESTAMP=%DATE:~0,4%%DATE:~5,2%%DATE:~8,2%_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

set BACKUP_FILE=%BACKUP_DIR%\his_db_%TIMESTAMP%.sql

pg_dump -h %PG_HOST% -p %PG_PORT% -U %PG_USER% -d %PG_DB% -F c -f "%BACKUP_FILE%" 2>&1
if %ERRORLEVEL% equ 0 (
    echo.
    echo ╔══════════════════════════════════════╗
    echo ║  [√] 备份成功!                          ║
    echo ║  文件: %BACKUP_FILE%       ║
    echo ╚══════════════════════════════════════╝
    dir "%BACKUP_FILE%" | findstr /r "."
) else (
    echo.
    echo ╔══════════════════════════════════════╗
    echo ║  [×] 备份失败                            ║
    echo ╚══════════════════════════════════════╝
)
echo.
pause
