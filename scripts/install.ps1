# HIS 子系统安装脚本
# 用法: powershell -ExecutionPolicy Bypass -File install.ps1 -ModuleName "Registration" -DisplayName "挂号管理"

param(
    [Parameter(Mandatory=$true)]
    [string]$ModuleName,
    [Parameter(Mandatory=$true)]
    [string]$DisplayName
)

$ErrorActionPreference = "Stop"
$host.UI.RawUI.WindowTitle = "HIS $DisplayName 安装程序"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  HIS 医院信息系统 - $DisplayName" -ForegroundColor Cyan
Write-Host "  版本: 1.0.0" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 安装目录
$installRoot = "$env:ProgramFiles\HIS"
$installDir = "$installRoot\$ModuleName"

# 检查是否已安装
if (Test-Path $installDir) {
    $choice = Read-Host "检测到已有安装 [$installDir]，是否覆盖安装？(Y/N)"
    if ($choice -ne 'Y' -and $choice -ne 'y') {
        Write-Host "安装已取消。" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
        exit 0
    }
    Write-Host "正在卸载旧版本..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $installDir -ErrorAction SilentlyContinue
}

Write-Host "正在安装到: $installDir" -ForegroundColor Green

# 复制文件
$sourceDir = Join-Path $PSScriptRoot "app"
if (-not (Test-Path $sourceDir)) {
    Write-Host "错误: 找不到应用文件目录！" -ForegroundColor Red
    Start-Sleep -Seconds 5
    exit 1
}

New-Item -ItemType Directory -Force -Path $installDir | Out-Null
Copy-Item -Recurse -Force "$sourceDir\*" $installDir

# 创建启动脚本
$exeName = "HIS-$ModuleName.exe"
$batContent = @"
@echo off
start "" "$installDir\$exeName"
"@
$batContent | Out-File -FilePath "$installDir\启动.bat" -Encoding Default

# 创建卸载脚本
$uninstallContent = @"
@echo off
chcp 65001 >nul
echo ========================================
echo   HIS $DisplayName 卸载程序
echo ========================================
echo.
echo 即将卸载 HIS $DisplayName...
echo 卸载目录: $installDir
echo.
set /p confirm="确认卸载？(Y/N): "
if /i not "%confirm%"=="Y" (
    echo 卸载已取消。
    pause
    exit /b 0
)
echo 正在卸载...
taskkill /f /im "$exeName" 2>nul
rd /s /q "%~dp0"
echo 卸载完成！
echo.
echo 注意: 数据库数据不会被删除。
pause
"@
$uninstallContent | Out-File -FilePath "$installDir\卸载.bat" -Encoding Default

# 创建桌面快捷方式
$desktop = [Environment]::GetFolderPath("Desktop")
$shortcutPath = "$desktop\HIS-$DisplayName.lnk"
$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut($shortcutPath)
$Shortcut.TargetPath = "$installDir\$exeName"
$Shortcut.WorkingDirectory = $installDir
$Shortcut.Description = "HIS 医院信息系统 - $DisplayName"
$Shortcut.Save()

# 创建开始菜单快捷方式
$startMenu = [Environment]::GetFolderPath("StartMenu")
$startMenuDir = "$startMenu\Programs\HIS 医院信息系统"
New-Item -ItemType Directory -Force -Path $startMenuDir | Out-Null

$startShortcut = "$startMenuDir\HIS-$DisplayName.lnk"
$Shortcut2 = $WshShell.CreateShortcut($startShortcut)
$Shortcut2.TargetPath = "$installDir\$exeName"
$Shortcut2.WorkingDirectory = $installDir
$Shortcut2.Description = "HIS 医院信息系统 - $DisplayName"
$Shortcut2.Save()

# 开始菜单卸载快捷方式
$uninstallShortcut = "$startMenuDir\卸载-$DisplayName.lnk"
$Shortcut3 = $WshShell.CreateShortcut($uninstallShortcut)
$Shortcut3.TargetPath = "$installDir\卸载.bat"
$Shortcut3.WorkingDirectory = $installDir
$Shortcut3.Description = "卸载 HIS $DisplayName"
$Shortcut3.Save()

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  安装完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  安装位置: $installDir" -ForegroundColor White
Write-Host "  桌面快捷方式已创建" -ForegroundColor White
Write-Host "  开始菜单快捷方式已创建" -ForegroundColor White
Write-Host "  卸载: 开始菜单 > HIS 医院信息系统 > 卸载-$DisplayName" -ForegroundColor White
Write-Host ""
Write-Host "  数据库配置请参考: $installDir\application.properties" -ForegroundColor Yellow
Write-Host ""

$launch = Read-Host "是否立即启动 $DisplayName？(Y/N)"
if ($launch -eq 'Y' -or $launch -eq 'y') {
    Start-Process "$installDir\$exeName"
}

Start-Sleep -Seconds 3
