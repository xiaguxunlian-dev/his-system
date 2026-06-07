@echo off
setlocal enabledelayedexpansion
set MVN=C:\tools\maven\bin\mvn.cmd
set MODULES=registration outpatient inpatient pharmacy examination emr billing statistics admin

for %%M in (%MODULES%) do (
    echo ========================================
    echo Processing his-%%M...
    echo ========================================
    
    cd D:\his\his-%%M
    
    echo [1/3] Copying dependencies...
    call %MVN% dependency:copy-dependencies -DoutputDirectory=target\lib -q
    if errorlevel 1 (
        echo ERROR: Failed to copy dependencies for his-%%M
        exit /b 1
    )
    
    echo [2/3] Moving JavaFX JARs to javafx/ subdirectory...
    if not exist target\lib\javafx mkdir target\lib\javafx
    move target\lib\javafx-*.jar target\lib\javafx\ >nul 2>&1
    
    echo [3/3] Copying main JAR to lib/...
    copy target\his-%%M-1.0.0.jar target\lib\ >nul
    
    echo Done: his-%%M
    echo.
)

echo ========================================
echo All modules processed successfully!
echo ========================================
