#!/usr/bin/env pwsh
# ============================================================
# HIS 打包脚本 — 生成 app-image 自包含应用 (PowerShell)
# ============================================================
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"

$Modules = @(
    @{name="HisRegistration"; dir="his-registration"; jar="his-registration-1.0.0.jar"; cls="com.his.registration.ui.RegistrationApp"; desc="HIS Registration"},
    @{name="HisOutpatient";   dir="his-outpatient";    jar="his-outpatient-1.0.0.jar";    cls="com.his.outpatient.ui.OutpatientApp";       desc="HIS Outpatient"},
    @{name="HisInpatient";    dir="his-inpatient";     jar="his-inpatient-1.0.0.jar";     cls="com.his.inpatient.ui.InpatientApp";         desc="HIS Inpatient"},
    @{name="HisPharmacy";     dir="his-pharmacy";      jar="his-pharmacy-1.0.0.jar";      cls="com.his.pharmacy.ui.PharmacyApp";           desc="HIS Pharmacy"},
    @{name="HisExamination";  dir="his-examination";   jar="his-examination-1.0.0.jar";   cls="com.his.examination.ui.ExaminationApp";     desc="HIS Examination"},
    @{name="HisEmr";          dir="his-emr";           jar="his-emr-1.0.0.jar";           cls="com.his.emr.ui.EmrApp";                     desc="HIS EMR"},
    @{name="HisBilling";      dir="his-billing";       jar="his-billing-1.0.0.jar";       cls="com.his.billing.ui.BillingApp";             desc="HIS Billing"},
    @{name="HisStatistics";   dir="his-statistics";    jar="his-statistics-1.0.0.jar";    cls="com.his.statistics.ui.StatisticsApp";       desc="HIS Statistics"},
    @{name="HisAdmin";        dir="his-admin";         jar="his-admin-1.0.0.jar";         cls="com.his.admin.ui.AdminApp";                 desc="HIS Admin"}
)

$Failed = @()
$Total = $Modules.Count

foreach ($i in 0..($Total-1)) {
    $m = $Modules[$i]
    $idx = $i + 1
    Write-Host "[$idx/$Total] Building $($m.name) ..."
    
    $result = & jpackage `
        --name $m.name `
        --description $m.desc `
        --vendor "HIS Team" `
        --app-version "1.0.0" `
        --input "D:\his\$($m.dir)\target" `
        --main-jar $m.jar `
        --main-class $m.cls `
        --type app-image `
        --dest "D:\his\dist" `
        2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [√] $($m.name) OK"
    } else {
        Write-Host "  [×] $($m.name) FAILED"
        $Failed += $m.name
    }
}

if ($Failed.Count -eq 0) {
    Write-Host "`n=== ALL $Total MODULES SUCCESS ==="
} else {
    Write-Host "`n=== FAILED: $($Failed -join ', ') ==="
}

# Summary
Write-Host "`n=== Output sizes ==="
Get-ChildItem D:\his\dist -Directory | ForEach-Object {
    $size = (Get-ChildItem $_.FullName -Recurse -File | Measure-Object -Property Length -Sum).Sum
    Write-Host "  $($_.Name): $([math]::Round($size/1MB, 1)) MB"
}

Write-Host "`n=== Done ==="
