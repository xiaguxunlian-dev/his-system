#!/usr/bin/env pwsh
# ============================================================
# HIS Release ZIP 打包脚本
# ============================================================
$ErrorActionPreference = "Stop"

$DistDir = "D:\his\dist"
$ReleaseDir = "D:\his\release"
$Version = "1.0.0"

# Clean and create release directory
if (Test-Path $ReleaseDir) { Remove-Item -Recurse -Force $ReleaseDir }
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

Write-Host "=== HIS v$Version Release Packaging ===" -ForegroundColor Cyan

# Individual module ZIPs
$Modules = @(
    "HIS-Registration", "HIS-Outpatient", "HIS-Inpatient", "HIS-Pharmacy",
    "HIS-Examination", "HIS-Emr", "HIS-Billing", "HIS-Statistics", "HIS-Admin"
)

Write-Host "`n--- Creating individual module ZIPs ---" -ForegroundColor Yellow
foreach ($mod in $Modules) {
    $src = Join-Path $DistDir $mod
    $zipName = "${mod}-v${Version}.zip"
    $zipPath = Join-Path $ReleaseDir $zipName
    
    if (Test-Path $src) {
        Write-Host "  [$mod] Compressing..." -NoNewline
        Compress-Archive -Path "$src\*" -DestinationPath $zipPath -Force
        $size = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
        Write-Host " $size MB  [OK]" -ForegroundColor Green
    } else {
        Write-Host "  [$mod] SKIPPED - source not found" -ForegroundColor Red
    }
}

# Create shared README + scripts package (no JRE duplication)
$ScriptsDir = Join-Path $ReleaseDir "his-scripts"
New-Item -ItemType Directory -Force -Path $ScriptsDir | Out-Null
Copy-Item "D:\his\scripts\setup-database.bat" -Destination $ScriptsDir -Force
Copy-Item "D:\his\scripts\setup-database.sh" -Destination $ScriptsDir -Force
Copy-Item "D:\his\docker-compose.yml" -Destination $ScriptsDir -Force

$readme = @"
╔═══════════════════════════════════════════════════════════╗
║        HIS 医院信息系统 v${Version} — Release 分发包        ║
╚═══════════════════════════════════════════════════════════╝

=== 系统要求 ===
- Windows 10/11 64位 (或 Linux x64)
- PostgreSQL 16+
- 2GB+ 可用磁盘空间（每个模块）
- 不需要安装 Java（已内置 JRE）

=== 模块列表 ===
  HIS-Registration   挂号管理
  HIS-Outpatient     门诊医生工作站
  HIS-Inpatient      住院管理
  HIS-Pharmacy       药房管理
  HIS-Examination    检查检验
  HIS-Emr            电子病历
  HIS-Billing        收费管理
  HIS-Statistics     统计报表
  HIS-Admin          系统管理

=== 快速开始 ===

1. 解压你需要的模块 ZIP 文件

2. 设置数据库（首次使用）:
   双击 his-scripts/setup-database.bat 一键初始化
   (或手动运行: docker-compose up -d postgres)

3. 启动应用:
   双击 HIS-XXX.exe 即可运行

=== 数据库默认配置 ===
  主机: localhost:5432
  数据库: his_db
  用户名: his_user
  密码: his@2026

  (可在 app/application.properties 中修改)

=== 技术支持 ===
  版本: v${Version}
  架构: Java 17 + JavaFX 21 + PostgreSQL 16
"@

$readme | Out-File -FilePath (Join-Path $ReleaseDir "README.txt") -Encoding UTF8 -Force

# Zip scripts package
$scriptsZip = Join-Path $ReleaseDir "his-scripts-v${Version}.zip"
Compress-Archive -Path "$ScriptsDir\*" -DestinationPath $scriptsZip -Force
Remove-Item -Recurse -Force $ScriptsDir
Write-Host "  Scripts package: his-scripts-v${Version}.zip  [OK]" -ForegroundColor Green

# Summary
Write-Host "`n=== Release Summary ===" -ForegroundColor Cyan
Write-Host "Location: $ReleaseDir"
Write-Host ""
Get-ChildItem $ReleaseDir -File | ForEach-Object {
    $sz = [math]::Round($_.Length / 1MB, 1)
    Write-Host "  $($_.Name)  —  $sz MB"
}

Write-Host "`n=== Done ===" -ForegroundColor Green
