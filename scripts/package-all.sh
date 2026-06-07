#!/bin/bash
# ============================================================
# HIS 所有模块批量 jpackage 打包 (Linux / macOS)
# 用法: ./scripts/package-all.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST="$PROJECT_DIR/dist"

MODULES=(
    "registration:RegistrationApp"
    "outpatient:OutpatientApp"
    "inpatient:InpatientApp"
    "pharmacy:PharmacyApp"
    "examination:ExaminationApp"
    "emr:EmrApp"
    "billing:BillingApp"
    "statistics:StatisticsApp"
    "admin:AdminApp"
)

echo ""
echo "=== HIS 批量打包 (app-image) ==="
echo ""

mkdir -p "$DIST"

for entry in "${MODULES[@]}"; do
    mod="${entry%%:*}"
    cls="${entry##*:}"
    # Capitalize first letter for display name
    cap="$(echo "$mod" | sed 's/^./\U&/')"
    name="HIS-$cap"

    echo "--- Packaging $name ---"

    rm -rf "$DIST/$name"

    jpackage \
        --name "$name" \
        --type app-image \
        --dest "$DIST" \
        --input "$PROJECT_DIR/his-$mod/target/lib" \
        --main-jar "his-$mod-1.0.0.jar" \
        --main-class "com.his.$mod.ui.$cls" \
        --java-options "--module-path=\$APPDIR/javafx" \
        --java-options "--add-modules=javafx.controls,javafx.fxml" \
        2>&1 | tail -3

    if [ -d "$DIST/$name/bin" ]; then
        echo "  [OK] $name"
    else
        echo "  [FAILED] $name"
    fi
done

echo ""
echo "=== 打包完成, 输出目录: $DIST ==="
