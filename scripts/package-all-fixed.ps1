$ErrorActionPreference = "Stop"

$JavaHome = "C:\Program Files\Java\jdk-23"
$DistDir = "D:\his\dist"
$ProjDir = "D:\his"
$M2Repo = "$env:USERPROFILE\.m2\repository"

# JavaFX win JAR path
$JavaFxBase = "$M2Repo\org\openjfx\javafx-base\21.0.2\javafx-base-21.0.2-win.jar"
$JavaFxControls = "$M2Repo\org\openjfx\javafx-controls\21.0.2\javafx-controls-21.0.2-win.jar"
$JavaFxFxml = "$M2Repo\org\openjfx\javafx-fxml\21.0.2\javafx-fxml-21.0.2-win.jar"
$JavaFxGraphics = "$M2Repo\org\openjfx\javafx-graphics\21.0.2\javafx-graphics-21.0.2-win.jar"

# Test dependency patterns to exclude
$TestPatterns = "*junit*", "*mockito*", "*opentest4j*", "*apiguardian*", "*byte-buddy-agent*", "*objenesis*"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  HIS 9 Modules Re-Packaging" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Clear dist
if (Test-Path $DistDir) {
    Remove-Item -Recurse -Force "$DistDir\*"
}

$modules = @()

$modules += @{name="HIS-Registration"; dir="his-registration"; jar="his-registration-1.0.0.jar"; cls="com.his.registration.ui.RegistrationApp"; desc="HIS Registration"}
$modules += @{name="HIS-Outpatient";   dir="his-outpatient";    jar="his-outpatient-1.0.0.jar";    cls="com.his.outpatient.ui.OutpatientApp";       desc="HIS Outpatient"}
$modules += @{name="HIS-Inpatient";    dir="his-inpatient";     jar="his-inpatient-1.0.0.jar";     cls="com.his.inpatient.ui.InpatientApp";         desc="HIS Inpatient"}
$modules += @{name="HIS-Pharmacy";     dir="his-pharmacy";      jar="his-pharmacy-1.0.0.jar";      cls="com.his.pharmacy.ui.PharmacyApp";           desc="HIS Pharmacy"}
$modules += @{name="HIS-Examination";  dir="his-examination";   jar="his-examination-1.0.0.jar";   cls="com.his.examination.ui.ExaminationApp";     desc="HIS Examination"}
$modules += @{name="HIS-Emr";          dir="his-emr";           jar="his-emr-1.0.0.jar";           cls="com.his.emr.ui.EmrApp";                     desc="HIS EMR"}
$modules += @{name="HIS-Billing";      dir="his-billing";       jar="his-billing-1.0.0.jar";       cls="com.his.billing.ui.BillingApp";             desc="HIS Billing"}
$modules += @{name="HIS-Statistics";   dir="his-statistics";    jar="his-statistics-1.0.0.jar";    cls="com.his.statistics.ui.StatisticsApp";       desc="HIS Statistics"}
$modules += @{name="HIS-Admin";        dir="his-admin";         jar="his-admin-1.0.0.jar";         cls="com.his.admin.ui.AdminApp";                 desc="HIS Admin"}

$failed = @()
$total = $modules.Count
$idx = 0

foreach ($m in $modules) {
    $idx++
    $modName = $m.name
    $modDir = Join-Path $ProjDir $m.dir
    $targetDir = Join-Path $modDir "target"
    $libDir = Join-Path $targetDir "jpackage-lib"
    $mainJar = Join-Path $targetDir $m.jar
    $inputDir = Join-Path $targetDir "jpackage-input"
    $outputDir = Join-Path $DistDir $modName

    Write-Host "[$idx/$total] $modName ..." -ForegroundColor Yellow

    # Step 1: Prepare clean input directory
    Write-Host "  [1/5] Prepare input dir ..." -NoNewline
    if (Test-Path $inputDir) { Remove-Item -Recurse -Force $inputDir }
    New-Item -ItemType Directory -Force -Path $inputDir | Out-Null

    if (-not (Test-Path $mainJar)) {
        Write-Host " [FAIL] Main JAR missing: $mainJar" -ForegroundColor Red
        $failed += $modName
        continue
    }
    Copy-Item $mainJar -Destination $inputDir -Force

    $jarCount = 0
    if (Test-Path $libDir) {
        Get-ChildItem "$libDir\*.jar" | ForEach-Object {
            $skip = $false
            if ($_.Name -like "javafx-*") { $skip = $true }
            foreach ($p in $TestPatterns) {
                if ($_.Name -like $p) { $skip = $true }
            }
            if (-not $skip) {
                Copy-Item $_.FullName -Destination $inputDir -Force
                $jarCount++
            }
        }
    }
    $jarCount++
    Write-Host " $jarCount JARs [OK]" -ForegroundColor Green

    # Step 2: jpackage app-image
    Write-Host "  [2/5] jpackage app-image ..." -NoNewline
    $jpArgs = @(
        "--name", $modName,
        "--description", $m.desc,
        "--vendor", "HIS Team",
        "--app-version", "1.0.0",
        "--input", $inputDir,
        "--main-jar", $m.jar,
        "--main-class", $m.cls,
        "--type", "app-image",
        "--dest", $DistDir
    )
    $jpOut = & "$JavaHome\bin\jpackage.exe" $jpArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host " [FAIL]" -ForegroundColor Red
        Write-Host "    Error: $jpOut" -ForegroundColor Red
        $failed += $modName
        continue
    }
    Write-Host " [OK]" -ForegroundColor Green

    # Step 3: Copy JavaFX JARs
    Write-Host "  [3/5] Copy JavaFX ..." -NoNewline
    $javafxDir = Join-Path $outputDir "app\javafx"
    New-Item -ItemType Directory -Force -Path $javafxDir | Out-Null
    Copy-Item $JavaFxBase -Destination $javafxDir -Force
    Copy-Item $JavaFxControls -Destination $javafxDir -Force
    Copy-Item $JavaFxFxml -Destination $javafxDir -Force
    Copy-Item $JavaFxGraphics -Destination $javafxDir -Force
    Write-Host " [OK]" -ForegroundColor Green

    # Step 4: Fix cfg
    Write-Host "  [4/5] Fix startup cfg ..." -NoNewline
    $cfgPath = Join-Path $outputDir "app\$modName.cfg"
    if (Test-Path $cfgPath) {
        $cfg = Get-Content $cfgPath -Raw -Encoding UTF8
        if ($cfg -notmatch "--module-path") {
            $javaFxOpts = @"

java-options=--module-path=`$APPDIR\app\javafx
java-options=--add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.base
"@
            $cfg = $cfg -replace "(\[JavaOptions\])", "`$1$javaFxOpts"
            [System.IO.File]::WriteAllText($cfgPath, $cfg, [System.Text.UTF8Encoding]::new($false))
            Write-Host " [OK]" -ForegroundColor Green
        } else {
            Write-Host " [skip] already configured" -ForegroundColor DarkGray
        }
    } else {
        Write-Host " [warn] no cfg" -ForegroundColor DarkYellow
    }

    # Step 5: Create run.bat
    Write-Host "  [5/5] Create run.bat ..." -NoNewline
    $batPath = Join-Path $outputDir "启动.bat"
    $batContent = "@echo off`r`n" +
        "title $modName - $($m.desc)`r`n" +
        'cd /d "%~dp0"' + "`r`n" +
        'set PATH=%~dp0runtime\bin;%PATH%' + "`r`n" +
        'start javaw --module-path "%~dp0app\javafx" --add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.base -cp "%~dp0app\*" ' + $m.cls + "`r`n"
    [System.IO.File]::WriteAllText($batPath, $batContent, [System.Text.Encoding]::ASCII)
    Write-Host " [OK]" -ForegroundColor Green

    # Report size
    if (Test-Path $outputDir) {
        $sz = (Get-ChildItem $outputDir -Recurse -File | Measure-Object -Property Length -Sum).Sum
        Write-Host "  => $modName done ($([math]::Round($sz/1MB, 1)) MB)" -ForegroundColor Green
    }
    Write-Host ""
}

# Summary
Write-Host "========================================" -ForegroundColor Cyan
if ($failed.Count -eq 0) {
    Write-Host "  ALL $total MODULES SUCCESS!" -ForegroundColor Green
} else {
    Write-Host "  OK: $($total - $failed.Count), FAILED: $($failed -join ', ')" -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nModule Sizes:" -ForegroundColor Yellow
Get-ChildItem $DistDir -Directory | ForEach-Object {
    $sz = (Get-ChildItem $_.FullName -Recurse -File | Measure-Object -Property Length -Sum).Sum
    Write-Host "  $($_.Name): $([math]::Round($sz/1MB, 1)) MB"
}

Write-Host "`nHow to launch:" -ForegroundColor Cyan
Write-Host "  1. Double-click '启动.bat' in module folder" -ForegroundColor White
Write-Host "  2. Or run <ModuleName>.exe" -ForegroundColor White
Write-Host "`n=== Done ===" -ForegroundColor Green
