# HIS Windows EXE Installer Builder
# Generates self-extracting EXE installers using 7-Zip SFX

param(
    [string]$SevenZip = "C:\Users\14327\Downloads\MinGW\bin\7z.exe",
    [string]$SfxModule = "C:\Users\14327\Downloads\MinGW\bin\7z.sfx",
    [string]$DistDir = "D:\his\dist",
    [string]$OutDir = "D:\his\installers\windows",
    [string]$WorkDir = "D:\his\installers\_work"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Clean-Dir($path) {
    if (Test-Path $path) {
        $items = Get-ChildItem $path -Recurse -ErrorAction SilentlyContinue
        foreach ($item in $items) {
            if ($item.PSIsContainer) {
                Remove-Item $item.FullName -Force -Recurse -ErrorAction SilentlyContinue
            } else {
                Remove-Item $item.FullName -Force -ErrorAction SilentlyContinue
            }
        }
    }
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  HIS Windows EXE Installer Builder" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan

$allMods = @(
    @{Dir="HIS-Registration"; CN="挂号管理"; Exe="HIS-Registration.exe"},
    @{Dir="HIS-Outpatient";   CN="门诊工作站"; Exe="HIS-Outpatient.exe"},
    @{Dir="HIS-Inpatient";    CN="住院管理";   Exe="HIS-Inpatient.exe"},
    @{Dir="HIS-Pharmacy";     CN="药品管理";   Exe="HIS-Pharmacy.exe"},
    @{Dir="HIS-Examination";  CN="检查检验";   Exe="HIS-Examination.exe"},
    @{Dir="HIS-Emr";          CN="电子病历";   Exe="HIS-Emr.exe"},
    @{Dir="HIS-Billing";      CN="收费管理";   Exe="HIS-Billing.exe"},
    @{Dir="HIS-Statistics";   CN="统计报表";   Exe="HIS-Statistics.exe"},
    @{Dir="HIS-Admin";        CN="系统管理";   Exe="HIS-Admin.exe"}
)

if (-not (Test-Path $SevenZip)) { Write-Host "ERROR: 7z not found at $SevenZip"; exit 1 }
if (-not (Test-Path $SfxModule)) { Write-Host "ERROR: SFX module not found at $SfxModule"; exit 1 }

Clean-Dir $OutDir
Clean-Dir $WorkDir

foreach ($m in $allMods) {
    $src = Join-Path $DistDir $m.Dir
    if (-not (Test-Path $src)) {
        Write-Host "[SKIP] $($m.CN): source not found" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "[BUILD] $($m.CN) ..." -ForegroundColor Green
    
    # Prepare package directory
    $pkg = Join-Path $WorkDir $m.Dir
    New-Item -ItemType Directory -Force -Path $pkg | Out-Null
    Copy-Item -Recurse -Force "$src\*" $pkg
    
    # setup.bat content
    $setupContent = "title HIS $($m.CN)`r`necho.`r`necho ========================================`r`necho   HIS Hospital System - $($m.CN)`r`necho   Version: 1.0.0`r`necho ========================================`r`necho.`r`necho Database: PostgreSQL 16+`r`necho Default: localhost:5432/his_db`r`necho User: his_user / Password: his@2026`r`necho.`r`necho Starting application...`r`nstart `"`" `"%~dp0$($m.Exe)`"`r`nexit"
    $batPath = Join-Path $pkg "setup.bat"
    [System.IO.File]::WriteAllBytes($batPath, [System.Text.Encoding]::UTF8.GetBytes($setupContent))
    
    # SFX config
    $cfgContent = ";!@Install@!UTF-8!`r`nTitle=`"HIS $($m.CN) v1.0.0`"`r`nBeginPrompt=`"HIS Hospital System - $($m.CN). Extract to install.`"`r`nExtractPathText=`"Select install folder:`"`r`nExtractPathTitle=`"HIS $($m.CN)`"`r`nExecuteFile=`"setup.bat`"`r`n;!@InstallEnd@!"
    $cfgPath = Join-Path $pkg "config.txt"
    [System.IO.File]::WriteAllBytes($cfgPath, [System.Text.Encoding]::UTF8.GetBytes($cfgContent))
    
    # Compress with 7z
    $arcPath = Join-Path $WorkDir "$($m.Dir).7z"
    Write-Host "  Compressing..."
    $proc = Start-Process -FilePath $SevenZip -ArgumentList @("a","-t7z","-mx=7","-m0=LZMA2","-mmt=on","-xr!config.txt","-xr!setup.bat",$arcPath,"$pkg\*") -NoNewWindow -Wait -PassThru
    if ($proc.ExitCode -ne 0 -and $proc.ExitCode -ne 1) {
        Write-Host "  WARNING: 7z exit code $($proc.ExitCode)" -ForegroundColor Yellow
    }
    
    if (-not (Test-Path $arcPath) -or (Get-Item $arcPath).Length -lt 10000) {
        Write-Host "  ERROR: Compression failed!" -ForegroundColor Red
        continue
    }
    
    # Create SFX EXE
    $outExe = Join-Path $OutDir "HIS-$($m.CN)-Setup-v1.0.0.exe"
    Write-Host "  Creating installer..."
    
    $sfxBytes = [System.IO.File]::ReadAllBytes($SfxModule)
    $cfgBytes = [System.IO.File]::ReadAllBytes($cfgPath)
    $arcBytes = [System.IO.File]::ReadAllBytes($arcPath)
    $batBytes = [System.IO.File]::ReadAllBytes($batPath)
    
    $allBytes = New-Object byte[] ($sfxBytes.Length + $cfgBytes.Length + $arcBytes.Length + $batBytes.Length)
    [Array]::Copy($sfxBytes, 0, $allBytes, 0, $sfxBytes.Length)
    [Array]::Copy($cfgBytes, 0, $allBytes, $sfxBytes.Length, $cfgBytes.Length)
    [Array]::Copy($arcBytes, 0, $allBytes, $sfxBytes.Length + $cfgBytes.Length, $arcBytes.Length)
    [Array]::Copy($batBytes, 0, $allBytes, $sfxBytes.Length + $cfgBytes.Length + $arcBytes.Length, $batBytes.Length)
    [System.IO.File]::WriteAllBytes($outExe, $allBytes)
    
    if (Test-Path $outExe) {
        $sz = [math]::Round((Get-Item $outExe).Length / 1MB, 1)
        Write-Host "  DONE: HIS-$($m.CN)-Setup-v1.0.0.exe ($sz MB)" -ForegroundColor Green
    } else {
        Write-Host "  FAILED!" -ForegroundColor Red
    }
    
    # Clean per-module
    $items = Get-ChildItem $pkg -Recurse -ErrorAction SilentlyContinue
    foreach ($item in $items) {
        if ($item.PSIsContainer) { Remove-Item $item.FullName -Force -Recurse -ErrorAction SilentlyContinue }
        else { Remove-Item $item.FullName -Force -ErrorAction SilentlyContinue }
    }
    Remove-Item $pkg -Force -ErrorAction SilentlyContinue
    Remove-Item $arcPath -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  All EXE installers built!" -ForegroundColor Cyan
Write-Host "  Output: $OutDir" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Get-ChildItem $OutDir -Filter "*.exe" | ForEach-Object {
    $s = [math]::Round($_.Length / 1MB, 1)
    Write-Host "  $($_.Name) ($s MB)" -ForegroundColor White
}
