#!/usr/bin/env python3
"""HIS Windows EXE Installer Builder using 7-Zip SFX"""

import os
import subprocess
import sys
import shutil

SEVENZIP = r"C:\Users\14327\Downloads\MinGW\bin\7z.exe"
SFX_MODULE = r"C:\Users\14327\Downloads\MinGW\bin\7z.sfx"
DIST_DIR = r"D:\his\dist"
OUT_DIR = r"D:\his\installers\windows"
WORK_DIR = r"D:\his\installers\_work"

MODULES = [
    ("HIS-Registration", "挂号管理", "HIS-Registration.exe"),
    ("HIS-Outpatient", "门诊工作站", "HIS-Outpatient.exe"),
    ("HIS-Inpatient", "住院管理", "HIS-Inpatient.exe"),
    ("HIS-Pharmacy", "药品管理", "HIS-Pharmacy.exe"),
    ("HIS-Examination", "检查检验", "HIS-Examination.exe"),
    ("HIS-Emr", "电子病历", "HIS-Emr.exe"),
    ("HIS-Billing", "收费管理", "HIS-Billing.exe"),
    ("HIS-Statistics", "统计报表", "HIS-Statistics.exe"),
    ("HIS-Admin", "系统管理", "HIS-Admin.exe"),
]

def clean_dir(path):
    if os.path.exists(path):
        shutil.rmtree(path)
    os.makedirs(path)

def main():
    print("=" * 50)
    print("  HIS Windows EXE Installer Builder")
    print("=" * 50)
    
    if not os.path.exists(SEVENZIP):
        print(f"ERROR: 7z not found at {SEVENZIP}")
        sys.exit(1)
    if not os.path.exists(SFX_MODULE):
        print(f"ERROR: SFX module not found at {SFX_MODULE}")
        sys.exit(1)
    
    # Only clean work dir, preserve existing output
    if os.path.exists(WORK_DIR):
        def remove_readonly(func, path, excinfo):
            import stat
            os.chmod(path, stat.S_IWRITE)
            func(path)
        try:
            shutil.rmtree(WORK_DIR, onerror=remove_readonly)
        except PermissionError:
            print("WARNING: Could not clean work dir, continuing anyway")
    os.makedirs(WORK_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    
    for dir_name, cn_name, exe_name in MODULES:
        src = os.path.join(DIST_DIR, dir_name)
        if not os.path.exists(src):
            print(f"[SKIP] {cn_name}: source not found")
            continue
        
        # Skip if already built
        output_exe = os.path.join(OUT_DIR, f"HIS-{cn_name}-Setup-v1.0.0.exe")
        if os.path.exists(output_exe) and os.path.getsize(output_exe) > 1000000:
            size_mb = os.path.getsize(output_exe) / (1024 * 1024)
            print(f"[SKIP] {cn_name}: already built ({size_mb:.1f} MB)")
            continue
        
        print(f"\n[BUILD] {cn_name} ({dir_name})")
        
        # Prepare package directory
        pkg = os.path.join(WORK_DIR, dir_name)
        if os.path.exists(pkg):
            shutil.rmtree(pkg)
        
        print("  Copying files...")
        shutil.copytree(src, pkg)
        
        # Create setup.bat
        setup_content = f"""@echo off
title HIS {cn_name}
echo.
echo ========================================
echo   HIS Hospital System - {cn_name}
echo   Version: 1.0.0
echo ========================================
echo.
echo Database: PostgreSQL 16+
echo Default: localhost:5432/his_db
echo User: his_user / Password: his@2026
echo.
echo Starting application...
start "" "%~dp0{exe_name}"
exit
"""
        bat_path = os.path.join(pkg, "setup.bat")
        with open(bat_path, "w", encoding="gbk") as f:
            f.write(setup_content)
        
        # Create SFX config.txt
        cfg_content = f""";!@Install@!UTF-8!
Title="HIS {cn_name} v1.0.0"
BeginPrompt="HIS Hospital System - {cn_name}. Extract to install."
ExtractPathText="Select install folder:"
ExtractPathTitle="HIS {cn_name}"
ExecuteFile="setup.bat"
;!@InstallEnd@!
"""
        cfg_path = os.path.join(pkg, "config.txt")
        with open(cfg_path, "w", encoding="utf-8") as f:
            f.write(cfg_content)
        
        # Compress with 7z
        arc_path = os.path.join(WORK_DIR, f"{dir_name}.7z")
        print("  Compressing...")
        cmd = [
            SEVENZIP, "a", "-t7z", "-mx=7", "-m0=LZMA2", "-mmt=on",
            "-xr!config.txt", "-xr!setup.bat",
            arc_path, f"{pkg}\\*"
        ]
        result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        
        if not os.path.exists(arc_path) or os.path.getsize(arc_path) < 10000:
            print("  ERROR: Compression failed!")
            continue
        
        # Create SFX EXE
        output_exe = os.path.join(OUT_DIR, f"HIS-{cn_name}-Setup-v1.0.0.exe")
        print("  Creating installer...")
        
        with open(SFX_MODULE, "rb") as f:
            sfx_data = f.read()
        with open(cfg_path, "rb") as f:
            cfg_data = f.read()
        with open(arc_path, "rb") as f:
            arc_data = f.read()
        with open(bat_path, "rb") as f:
            bat_data = f.read()
        
        with open(output_exe, "wb") as f:
            f.write(sfx_data)
            f.write(cfg_data)
            f.write(arc_data)
            f.write(bat_data)
        
        if os.path.exists(output_exe):
            size_mb = os.path.getsize(output_exe) / (1024 * 1024)
            print(f"  DONE: HIS-{cn_name}-Setup-v1.0.0.exe ({size_mb:.1f} MB)")
        else:
            print("  FAILED!")
        
        # Clean up per-module (handle locked files gracefully)
        def remove_readonly(func, path, excinfo):
            import stat
            os.chmod(path, stat.S_IWRITE)
            func(path)
        try:
            shutil.rmtree(pkg, onerror=remove_readonly)
        except PermissionError:
            print("  WARNING: Could not clean up temp files")
        try:
            os.remove(arc_path)
        except OSError:
            pass
    
    print()
    print("=" * 50)
    print("  All EXE installers built!")
    print(f"  Output: {OUT_DIR}")
    print("=" * 50)
    
    for f in sorted(os.listdir(OUT_DIR)):
        if f.endswith(".exe"):
            path = os.path.join(OUT_DIR, f)
            size_mb = os.path.getsize(path) / (1024 * 1024)
            print(f"  {f} ({size_mb:.1f} MB)")

if __name__ == "__main__":
    main()
