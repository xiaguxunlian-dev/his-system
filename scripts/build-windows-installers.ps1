# HIS Windows .exe 安装程序打包
# 使用 7-Zip SFX 创建自解压安装程序

$ErrorActionPreference = "Stop"
$sevenZip = "C:\Users\14327\Downloads\MinGW\bin\7z.exe"
$sfxModule = "C:\Users\14327\Downloads\MinGW\bin\7z.sfx"

$modules = @(
    @{id="registration";   dir="HIS-Registration";   name="挂号管理"}
    @{id="outpatient";     dir="HIS-Outpatient";     name="门诊工作站"}
    @{id="inpatient";      dir="HIS-Inpatient";      name="住院管理"}
    @{id="pharmacy";       dir="HIS-Pharmacy";       name="药品管理"}
    @{id="examination";    dir="HIS-Examination";    name="检查检验"}
    @{id="emr";            dir="HIS-Emr";            name="电子病历"}
    @{id="billing";        dir="HIS-Billing";        name="收费管理"}
    @{id="statistics";     dir="HIS-Statistics";     name="统计报表"}
    @{id="admin";          dir="HIS-Admin";          name="系统管理"}
)

$distDir = "D:\his\dist"
$outputDir = "D:\his\installers\windows"
$workDir = "D:\his\installers\_work"

if (Test-Path $outputDir) { Remove-Item -Recurse -Force $outputDir }
if (Test-Path $workDir) { Remove-Item -Recurse -Force $workDir }
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  HIS Windows .exe 安装程序打包" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

foreach ($m in $modules) {
    $srcDir = Join-Path $distDir $m.dir
    if (-not (Test-Path $srcDir)) {
        Write-Host "[跳过] $($m.name): 目录不存在" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "[打包] $($m.name)..." -ForegroundColor Green
    
    $pkgDir = Join-Path $workDir $m.id
    New-Item -ItemType Directory -Force -Path $pkgDir | Out-Null
    
    # 复制 app-image 全部内容
    Copy-Item -Recurse -Force "$srcDir\*" $pkgDir
    
    # 创建 setup.bat — 安装/运行脚本
    $exeName = "HIS-$($m.id.substring(0,1).ToUpper()+$m.id.substring(1)).exe"
    $setupBat = @"
@echo off
chcp 65001 >nul
title HIS $($m.name) 安装程序
echo ========================================
echo   HIS 医院信息系统
echo   $($m.name) - 版本 1.0.0
echo ========================================
echo.
echo 本程序为绿色免安装版，直接运行即可。
echo 建议将本目录复制到合适位置后使用。
echo.
echo 数据库要求: PostgreSQL 16+
echo 默认配置: localhost:5432/his_db
echo             用户: his_user  密码: his@2026
echo.
echo ========================================
echo.
echo 启动方式:
echo   双击运行: $exeName
echo   或运行:   启动.bat
echo.
echo 数据库初始化:
echo   运行 scripts\setup-database.bat
echo.
echo ========================================
start "" "%~dp0$exeName"
"@
    
    $batFile = Join-Path $pkgDir "setup.bat"
    [System.IO.File]::WriteAllText($batFile, $setupBat, [System.Text.Encoding]::UTF8)
    
    # 创建 SFX 配置文件
    $configContent = @"
;!@Install@!UTF-8!
Title="HIS $($m.name) 安装程序 v1.0.0"
BeginPrompt="即将解压 HIS 医院信息系统 - $($m.name)`r`n`r`n版本: 1.0.0`r`n`r`n解压后即可运行，建议解压到 D:\HIS\ 目录。`r`n`r`n是否继续？"
ExtractPathText="请选择安装目录:"
ExtractPathTitle="HIS $($m.name)"
ExecuteFile="setup.bat"
;!@InstallEnd@!
"@
    
    $configFile = Join-Path $pkgDir "config.txt"
    [System.IO.File]::WriteAllText($configFile, $configContent, [System.Text.Encoding]::UTF8)
    
    # 创建 7z 压缩包
    $archiveFile = Join-Path $workDir "$($m.id).7z"
    $excludeConfig = Join-Path $workDir "exclude_$($m.id).txt"
    # 排除 config.txt 和 setup.bat 从被打包的文件中，它们需要单独处理
    
    Write-Host "  压缩文件中..."
    & $sevenZip a -t7z -mx=7 -m0=LZMA2 -mmt=on "-xr!config.txt" "-xr!setup.bat" $archiveFile "$pkgDir\*" 2>&1 | Select-Object -Last 3
    
    if (-not (Test-Path $archiveFile) -or (Get-Item $archiveFile).Length -lt 1024) {
        Write-Host "  错误: 压缩失败" -ForegroundColor Red
        continue
    }
    
    # 拼接 SFX + config + archive = .exe
    $outputExe = Join-Path $outputDir "HIS-$($m.name)-Setup-v1.0.0.exe"
    
    $tempDir = Join-Path $workDir "tmp_$($m.id)"
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
    
    Copy-Item $sfxModule $tempDir
    Copy-Item $configFile $tempDir
    Copy-Item $archiveFile $tempDir
    Copy-Item $batFile $tempDir
    
    # 拼接
    $sfxPath = Join-Path $tempDir (Split-Path $sfxModule -Leaf)
    $cfgPath = Join-Path $tempDir "config.txt"
    $arcPath = Join-Path $tempDir "$($m.id).7z"
    $setupPath = Join-Path $tempDir "setup.bat"
    
    $bytes = [System.IO.File]::ReadAllBytes($sfxPath)
    $bytes += [System.IO.File]::ReadAllBytes($cfgPath)
    $bytes += [System.IO.File]::ReadAllBytes($arcPath)
    $bytes += [System.IO.File]::ReadAllBytes($setupPath)
    [System.IO.File]::WriteAllBytes($outputExe, $bytes)
    
    if (Test-Path $outputExe) {
        $sizeMB = [math]::Round((Get-Item $outputExe).Length / 1MB, 1)
        Write-Host "  OK: HIS-$($m.name)-Setup-v1.0.0.exe ($sizeMB MB)" -ForegroundColor Green
    } else {
        Write-Host "  失败!" -ForegroundColor Red
    }
}

# 清理
Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  打包完成! 输出: $outputDir" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Get-ChildItem $outputDir -Filter "*.exe" | ForEach-Object {
    $s = [math]::Round($_.Length / 1MB, 1)
    Write-Host "  $($_.Name)  ($s MB)" -ForegroundColor White
}
