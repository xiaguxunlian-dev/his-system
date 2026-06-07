#!/bin/bash
# HIS Windows EXE 安装程序打包脚本
# 使用 7-Zip SFX 创建自解压安装程序

SEVENZ="/c/Users/14327/Downloads/MinGW/bin/7z.exe"
SFX="/c/Users/14327/Downloads/MinGW/bin/7z.sfx"
DIST="/d/his/dist"
OUTPUT="/d/his/installers/windows"
WORK="/d/his/installers/_work"

rm -rf "$OUTPUT" "$WORK"
mkdir -p "$OUTPUT" "$WORK"

declare -A NAMES
NAMES=(
    ["HIS-Registration"]="挂号管理"
    ["HIS-Outpatient"]="门诊工作站"
    ["HIS-Inpatient"]="住院管理"
    ["HIS-Pharmacy"]="药品管理"
    ["HIS-Examination"]="检查检验"
    ["HIS-Emr"]="电子病历"
    ["HIS-Billing"]="收费管理"
    ["HIS-Statistics"]="统计报表"
    ["HIS-Admin"]="系统管理"
)

for dir in "$DIST"/HIS-*/; do
    dirname=$(basename "$dir")
    name="${NAMES[$dirname]}"
    exe_name="$dirname.exe"
    
    if [ ! -d "$dir" ]; then
        continue
    fi
    
    echo "[打包] $name ($dirname)..."
    
    pkg="$WORK/$dirname"
    mkdir -p "$pkg"
    
    # 复制 dist 全部内容
    cp -r "$dir"/* "$pkg/"
    
    # 创建 setup.bat
    setup="$pkg/setup.bat"
    cat > "$setup" << BATEOF
@echo off
chcp 65001 >nul
title HIS ${name} 安装程序
echo.
echo ========================================
echo   HIS 医院信息系统 - ${name}
echo   版本: 1.0.0
echo ========================================
echo.
echo 绿色免安装版，直接运行即可。
echo.
echo 数据库要求: PostgreSQL 16+
echo 默认配置:   localhost:5432/his_db
echo             用户: his_user  密码: his@2026
echo.
echo=========================================
echo.
echo 启动中...
start "" "%~dp0${exe_name}"
exit
BATEOF
    unix2dos "$setup" 2>/dev/null || true
    
    # 创建 config.txt
    config="$pkg/config.txt"
    cat > "$config" << CFGEOF
;!@Install@!UTF-8!
Title="HIS ${name} 安装程序 v1.0.0"
BeginPrompt="即将安装 HIS 医院信息系统 - ${name}。建议解压到 D:\HIS\ 目录。是否继续？"
ExtractPathText="请选择安装目录:"
ExtractPathTitle="HIS ${name}"
ExecuteFile="setup.bat"
;!@InstallEnd@!
CFGEOF
    unix2dos "$config" 2>/dev/null || true
    
    # 创建 7z 压缩包 (排除 config.txt 和 setup.bat)
    archive="$WORK/${dirname}.7z"
    echo "  压缩中..."
    "$SEVENZ" a -t7z -mx=7 -m0=LZMA2 -mmt=on \
        -xr!config.txt -xr!setup.bat \
        "$archive" "$pkg"/* > /dev/null 2>&1
    
    if [ ! -f "$archive" ] || [ $(stat -c%s "$archive" 2>/dev/null || echo 0) -lt 1000 ]; then
        echo "  ERROR: 压缩失败"
        continue
    fi
    
    # 创建输出
    output_exe="$OUTPUT/HIS-${name}-Setup-v1.0.0.exe"
    echo "  生成安装程序..."
    
    cat "$SFX" "$config" "$archive" "$setup" > "$output_exe"
    
    if [ -f "$output_exe" ]; then
        size=$(du -h "$output_exe" | cut -f1)
        echo "  OK: HIS-${name}-Setup-v1.0.0.exe ($size)"
    else
        echo "  FAILED"
    fi
done

rm -rf "$WORK"

echo ""
echo "========================================"
echo "  Windows 安装程序打包完成!"
echo "  输出: $OUTPUT"
echo "========================================"
ls -lh "$OUTPUT"/*.exe 2>/dev/null | awk '{print "  " $NF "  (" $5 ")"}'
