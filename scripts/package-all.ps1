# Package all HIS modules as app-image (JavaFX-aware)
$ErrorActionPreference = "Stop"

$modules = @{
    "registration" = "RegistrationApp"
    "outpatient"   = "OutpatientApp"
    "inpatient"    = "InpatientApp"
    "pharmacy"     = "PharmacyApp"
    "examination"  = "ExaminationApp"
    "emr"          = "EmrApp"
    "billing"      = "BillingApp"
    "statistics"   = "StatisticsApp"
    "admin"        = "AdminApp"
}

$distDir = "D:\his\dist"
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory $distDir -Force | Out-Null }

foreach ($mod in $modules.Keys) {
    $className = $modules[$mod]
    $appName = "HIS-$mod"
    $appNameCapitalized = (Get-Culture).TextInfo.ToTitleCase($appName.Replace("-", " "))
    $appNameCapitalized = [char]::ToUpper($mod[0]) + $mod.Substring(1)
    $appDir = "HIS-$appNameCapitalized"
    
    # jpackage appimage name uses the --name parameter (hyphens are OK)
    $jpackageName = "HIS-$appNameCapitalized"
    
    Write-Host "=== Packaging $jpackageName ==="
    
    $targetDir = Join-Path $distDir $jpackageName
    if (Test-Path $targetDir) {
        Remove-Item -Recurse -Force $targetDir
    }
    
    $inputDir = "D:\his\his-$mod\target\lib"
    $mainJar = "his-$mod-1.0.0.jar"
    $mainClass = "com.his.$mod.ui.$className"
    
    $args = @(
        "--name", $jpackageName,
        "--type", "app-image",
        "--dest", $distDir,
        "--input", $inputDir,
        "--main-jar", $mainJar,
        "--main-class", $mainClass,
        "--java-options", "--module-path=`$APPDIR/javafx",
        "--java-options", "--add-modules=javafx.controls,javafx.fxml"
    )
    
    $proc = Start-Process -FilePath "jpackage.exe" -ArgumentList $args -Wait -NoNewWindow -PassThru
    if ($proc.ExitCode -ne 0) {
        Write-Host "ERROR: Failed to package $jpackageName (exit code: $($proc.ExitCode))"
    } else {
        Write-Host "OK: $jpackageName"
    }
}

Write-Host "All packages done!"