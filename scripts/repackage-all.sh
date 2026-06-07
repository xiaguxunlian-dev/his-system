#!/bin/bash
# ============================================================
# HIS T-10 修复版重打包 (含 JavaFX module path)
# 适用于 Linux / macOS / Windows (Git Bash)
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST="$PROJECT_DIR/dist"

# 检测平台
case "$(uname -s)" in
    Linux*)   PLATFORM="linux" ;;
    Darwin*)  PLATFORM="macos" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM="windows" ;;
    *)        PLATFORM="unknown" ;;
esac

MODULES=(
  "registration:Registration:RegistrationApp"
  "outpatient:Outpatient:OutpatientApp"
  "inpatient:Inpatient:InpatientApp"
  "pharmacy:Pharmacy:PharmacyApp"
  "examination:Examination:ExaminationApp"
  "emr:Emr:EmrApp"
  "billing:Billing:BillingApp"
  "statistics:Statistics:StatisticsApp"
  "admin:Admin:AdminApp"
)

echo ""
echo "=== HIS 重打包 ($PLATFORM) ==="
echo ""

mkdir -p "$DIST"

for entry in "${MODULES[@]}"; do
  IFS=':' read -r mod cap cls <<< "$entry"
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
    --java-options '--module-path=$APPDIR/javafx' \
    --java-options '--add-modules=javafx.controls,javafx.fxml' \
    2>&1 | tail -5

  # Verify
  if [ "$PLATFORM" = "windows" ]; then
    if [ -f "$DIST/$name/$name.exe" ]; then
      echo "  [OK] $name (exe exists)"
    else
      echo "  [WARN] $name - exe not found, check build log"
    fi
  else
    if [ -f "$DIST/$name/bin/$name" ]; then
      echo "  [OK] $name (binary exists)"
    else
      echo "  [WARN] $name - binary not found, check build log"
    fi
  fi
done

echo ""
echo "=== ALL DONE ==="
