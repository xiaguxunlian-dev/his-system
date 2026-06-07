@echo off
chcp 65001 >nul
setlocal

echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║   WiX Toolset v3.11.2 — 安装配置                   ║
echo   ╚══════════════════════════════════════════════════╝
echo.

set WIX_ZIP=%~dp0..\wix311-binaries.zip
set WIX_DIR=C:\wix311
set WIX_BIN=%WIX_DIR%\bin

if not exist "%WIX_ZIP%" (
    echo   [错误] 找不到 %WIX_ZIP%
    echo   请先下载 WiX 3.11.2 到 scripts 目录
    pause
    exit /b 1
)

echo   [1/3] 解压 WiX 到 %WIX_DIR%...

if exist "%WIX_DIR%" (
    echo   目录已存在，是否覆盖？ (Y/N)
    set /p OVERWRITE="  "
    if /i "!OVERWRITE!"=="Y" (
        rmdir /s /q "%WIX_DIR%"
    ) else (
        goto :set_path
    )
)

:: 用 PowerShell 解压（Windows 10+ 自带）
powershell -NoProfile -Command ^
    "Expand-Archive -Path '%WIX_ZIP%' -DestinationPath '%WIX_DIR%' -Force" 2>&1

if %ERRORLEVEL% neq 0 (
    echo   [×] 解压失败，请检查 ZIP 文件是否完整
    pause
    exit /b 1
)
echo   [√] 解压完成

:set_path
echo.
echo   [2/3] 检查 candle.exe 和 light.exe...
if not exist "%WIX_BIN%\candle.exe" (
    echo   [×] 找不到 candle.exe
    pause
    exit /b 1
)
echo   [√] WiX 工具链就绪

echo.
echo   [3/3] 添加到当前会话 PATH...
set "PATH=%WIX_BIN%;%PATH%"

echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║   WiX Toolset 配置完成!                           ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║  如果要永久添加到系统 PATH:                        ║
echo   ║    1. Win+R → sysdm.cpl → 高级 → 环境变量        ║
echo   ║    2. 在 Path 中添加: %WIX_BIN%                    ║
echo   ║  或运行:                                           ║
echo   ║    setx PATH "%%PATH%%;%WIX_BIN%" /M              ║
echo   ╚══════════════════════════════════════════════════╝
echo.

:: 验证版本
echo   ── 验证 WiX 版本...
"%WIX_BIN%\candle.exe" -? 2>&1 | findstr /i "version" 
echo.

:: 顺便运行 build-all.bat 测试 MSI 打包
echo   ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
echo   ── 即将运行 build-all.bat 生成 MSI 安装包         ──
echo   ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──
echo.
set /p "DO_BUILD=是否立即构建 MSI 安装包？(Y/N): "
if /i "%DO_BUILD%"=="Y" (
    call "%~dp0..\build-all.bat"
)

endlocal
pause
