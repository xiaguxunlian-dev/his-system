#!/bin/bash
# ============================================================
# HIS 医院信息系统 — 数据库初始化脚本 (Linux)
# 
# 用法:
#   交互模式:  ./setup-database.sh
#   批处理模式: ./setup-database.sh --batch [--host HOST] [--port PORT] [--user USER] [--pass PASS]
#   环境变量:  设置 HIS_DB_HOST / HIS_DB_PORT / HIS_DB_USER / HIS_DB_PASS
# ============================================================
set -euo pipefail

BATCH_MODE=0
HOST="localhost"
PORT="5432"
USER="postgres"
PASS=""
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
V1_SQL="$SCRIPT_DIR/../his-common/src/main/resources/db/migration/V1__init.sql"

# ============================================================
# 参数解析
# ============================================================
while [[ $# -gt 0 ]]; do
    case $1 in
        --batch) BATCH_MODE=1; shift ;;
        --host)  HOST="$2"; shift 2 ;;
        --port)  PORT="$2"; shift 2 ;;
        --user)  USER="$2"; shift 2 ;;
        --pass)  PASS="$2"; shift 2 ;;
        --v1)    V1_SQL="$2"; shift 2 ;;
        -h|--help)
            echo "用法: $0 [--batch] [--host HOST] [--port PORT] [--user USER] [--pass PASS]"
            echo ""
            echo "选项:"
            echo "  --batch        无交互批处理模式（用于 CI/CD）"
            echo "  --host HOST    数据库服务器地址 (默认: localhost)"
            echo "  --port PORT    数据库端口 (默认: 5432)"
            echo "  --user USER    超级用户 (默认: postgres)"
            echo "  --pass PASS    超级用户密码"
            echo "  --v1  PATH     V1__init.sql 路径"
            echo ""
            echo "环境变量:"
            echo "  HIS_DB_HOST    PostgreSQL 主机"
            echo "  HIS_DB_PORT    PostgreSQL 端口"
            echo "  HIS_DB_USER    超级用户"
            echo "  HIS_DB_PASS    超级用户密码"
            exit 0
            ;;
        *) echo "未知参数: $1 (试试 --help)"; exit 1 ;;
    esac
done

# 环境变量覆盖
[ -n "${HIS_DB_HOST:-}" ] && HOST="$HIS_DB_HOST"
[ -n "${HIS_DB_PORT:-}" ] && PORT="$HIS_DB_PORT"
[ -n "${HIS_DB_USER:-}" ] && USER="$HIS_DB_USER"
[ -n "${HIS_DB_PASS:-}" ] && PASS="$HIS_DB_PASS"

if [ -n "$PASS" ]; then
    export PGPASSWORD="$PASS"
fi

echo ""
echo "  ╔══════════════════════════════════════════════════╗"
echo "  ║   医院信息系统 (HIS) — 数据库安装工具               ║"
echo "  ╚══════════════════════════════════════════════════╝"
echo ""

# ============================================================
# 第0步：检查 psql
# ============================================================
if ! command -v psql &>/dev/null; then
    echo "  [错误] 未找到 psql 命令"
    echo ""
    echo "  请安装 PostgreSQL 客户端:"
    echo "    Ubuntu/Debian:  sudo apt install postgresql-client"
    echo "    CentOS/RHEL:    sudo yum install postgresql"
    echo "    Fedora:         sudo dnf install postgresql"
    echo "    Arch:           sudo pacman -S postgresql-libs"
    echo ""
    [ "$BATCH_MODE" = "1" ] && exit 1
    read -p "  按 Enter 退出..." _
    exit 1
fi

PG_VER=$(psql --version 2>&1 | awk '{print $NF}' || echo "unknown")
echo "  [√] 检测到 psql v$PG_VER"
echo ""

# ============================================================
# 非批处理模式：交互式输入
# ============================================================
if [ "$BATCH_MODE" = "0" ]; then
    echo "  ┌──────────────────────────────────────────────┐"
    echo "  │  请配置 PostgreSQL 连接信息                    │"
    echo "  └──────────────────────────────────────────────┘"
    echo ""
    read -p "  数据库服务器地址 [$HOST]: " INPUT
    [ -n "$INPUT" ] && HOST="$INPUT"
    read -p "  数据库端口 [$PORT]: " INPUT
    [ -n "$INPUT" ] && PORT="$INPUT"
    read -p "  超级用户 [$USER]: " INPUT
    [ -n "$INPUT" ] && USER="$INPUT"
    
    if [ -z "$PGPASSWORD" ]; then
        read -sp "  $USER 密码: " PGPASSWORD
        export PGPASSWORD
        echo ""
    fi
    
    echo ""
    echo "  数据库服务器: $HOST:$PORT"
    echo "  超级用户:     $USER"
    echo ""
    read -p "  确认连接并执行初始化？(y/N): " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[yY]$ ]]; then
        echo "  已取消"
        exit 0
    fi
fi

# ============================================================
# 连接测试
# ============================================================
echo "  ── 测试数据库连接..."
if ! psql -h "$HOST" -p "$PORT" -U "$USER" -d postgres -c "SELECT 1;" >/dev/null 2>&1; then
    echo "  [×] 无法连接到 PostgreSQL，请检查："
    echo "     - 服务器是否在运行 (systemctl status postgresql)"
    echo "     - 主机/端口/用户名/密码是否正确"
    echo "     - pg_hba.conf 是否允许此连接"
    [ "$BATCH_MODE" = "1" ] && exit 1
    read -p "  按 Enter 退出..." _
    exit 1
fi
echo "  [√] 连接成功"
echo ""

# ============================================================
# 第1步：创建数据库用户 his_user
# ============================================================
echo "  ── 创建数据库用户 his_user..."
psql -h "$HOST" -p "$PORT" -U "$USER" -d postgres -c \
    "DO \$\$ BEGIN CREATE ROLE his_user WITH LOGIN PASSWORD 'his@2026'; EXCEPTION WHEN duplicate_object THEN RAISE NOTICE '用户已存在'; END \$\$;" >/dev/null 2>&1 || {
    echo "  [×] 创建用户失败"
    exit 1
}
echo "  [√] 用户 his_user 就绪"
echo ""

# ============================================================
# 第2步：创建数据库 his_db
# ============================================================
echo "  ── 创建数据库 his_db..."
psql -h "$HOST" -p "$PORT" -U "$USER" -d postgres -c \
    "CREATE DATABASE his_db WITH OWNER his_user ENCODING 'UTF8' LC_COLLATE 'zh_CN.UTF-8' LC_CTYPE 'zh_CN.UTF-8' TEMPLATE template0;" 2>/dev/null || true
echo "  [√] 数据库 his_db 就绪"
echo ""

# ============================================================
# 第3步：授权
# ============================================================
echo "  ── 授权 his_user..."
psql -h "$HOST" -p "$PORT" -U "$USER" -d his_db -c \
    "GRANT ALL PRIVILEGES ON DATABASE his_db TO his_user; GRANT ALL ON SCHEMA public TO his_user;" >/dev/null 2>&1
echo "  [√] 权限已授权"
echo ""

# ============================================================
# 第4步：定位并执行 V1__init.sql
# ============================================================
if [ ! -f "$V1_SQL" ]; then
    V1_SQL="/opt/his/his-common/src/main/resources/db/migration/V1__init.sql"
fi
if [ ! -f "$V1_SQL" ]; then
    if [ "$BATCH_MODE" = "1" ]; then
        echo "  [×] 找不到 V1__init.sql"
        exit 1
    fi
    read -p "  V1__init.sql 完整路径: " V1_SQL
    if [ ! -f "$V1_SQL" ]; then
        echo "  [×] 文件不存在"
        exit 1
    fi
fi

echo "  ── 执行数据库迁移 ($V1_SQL)..."
psql -h "$HOST" -p "$PORT" -U his_user -d his_db -f "$V1_SQL" 2>&1 || {
    echo "  [×] 迁移执行失败"
    exit 1
}
echo "  [√] 表结构迁移完成"
echo ""

# ============================================================
# 第5步：导入种子数据
# ============================================================
DO_SEED="y"
if [ "$BATCH_MODE" = "0" ]; then
    read -p "  是否导入示例数据（科室、医生、管理员账号）？(Y/n) [Y]: " INPUT
    [ "$INPUT" = "n" ] || [ "$INPUT" = "N" ] && DO_SEED="n"
fi

if [ "$DO_SEED" != "n" ]; then
    echo "  ── 导入种子数据..."
    
    # 科室数据 (列名: code, name, type, location)
    psql -h "$HOST" -p "$PORT" -U his_user -d his_db -c "
        INSERT INTO departments (code, name, type, location) VALUES 
            ('NK','内科','临床科室','1号楼A区'),
            ('WK','外科','临床科室','1号楼B区'),
            ('EK','儿科','临床科室','2号楼A区'),
            ('FCK','妇产科','临床科室','2号楼B区'),
            ('JYK','检验科','医技科室','3号楼A区'),
            ('YXK','影像科','医技科室','3号楼B区'),
            ('YJK','药剂科','医技科室','1号楼C区'),
            ('JZK','急诊科','临床科室','1号楼D区'),
            ('MZK','门诊科','临床科室','1号楼E区')
        ON CONFLICT(code) DO NOTHING;
    " >/dev/null 2>&1

    # 医生数据 (列名: code, name, gender, title, department_id, phone)
    psql -h "$HOST" -p "$PORT" -U his_user -d his_db -c "
        INSERT INTO doctors (code, name, gender, title, department_id, phone) VALUES 
            ('D001','张伟','男','主任医师',1,'13800000001'),
            ('D002','李芳','女','副主任医师',2,'13800000002'),
            ('D003','王磊','男','主治医师',3,'13800000003'),
            ('D004','陈静','女','主任医师',4,'13800000004'),
            ('D005','赵明','男','检验师',5,'13800000005')
        ON CONFLICT(code) DO NOTHING;
    " >/dev/null 2>&1

    # 管理员账号 (admin / his@2026)
    # 列名: username, password_hash, display_name, role, is_active
    psql -h "$HOST" -p "$PORT" -U his_user -d his_db -c "
        INSERT INTO system_users (username, password_hash, display_name, role, is_active) VALUES 
            ('admin','\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','系统管理员','系统管理员',true)
        ON CONFLICT(username) DO NOTHING;
    " >/dev/null 2>&1

    echo "  [√] 种子数据已导入"
fi
echo ""

# ============================================================
# 第6步：配置 application.properties
# ============================================================
CONFIG_FILE="$SCRIPT_DIR/../his-common/src/main/resources/application.properties"
if [ -f "$CONFIG_FILE" ]; then
    echo "  ── 配置 application.properties..."
    
    # 备份
    [ ! -f "$CONFIG_FILE.bak" ] && cp "$CONFIG_FILE" "$CONFIG_FILE.bak"
    
    # 用 sed 更新配置
    sed -i "s/^db\.type=.*/db.type=postgresql/" "$CONFIG_FILE"
    sed -i "s/^db\.host=.*/db.host=$HOST/" "$CONFIG_FILE"
    sed -i "s/^db\.port=.*/db.port=$PORT/" "$CONFIG_FILE"
    sed -i "s/^db\.name=.*/db.name=his_db/" "$CONFIG_FILE"
    sed -i "s/^db\.username=.*/db.username=his_user/" "$CONFIG_FILE"
    sed -i "s/^db\.password=.*/db.password=his@2026/" "$CONFIG_FILE"
    
    echo "  [√] 配置文件已更新为 PostgreSQL 连接"
fi

echo ""
echo "  ╔══════════════════════════════════════════════════╗"
echo "  ║             数据库初始化完成!                       ║"
echo "  ╠══════════════════════════════════════════════════╣"
echo "  ║  主机: $HOST                                     ║"
echo "  ║  端口: $PORT                                     ║"
echo "  ║  数据库: his_db                                  ║"
echo "  ║  用户:   his_user                                 ║"
echo "  ║  密码:   his@2026                                ║"
echo "  ║  管理员: admin / his@2026 (首次登录需修改)         ║"
echo "  ╚══════════════════════════════════════════════════╝"
echo ""
echo "  提示:"
echo "  - 已自动修改 application.properties 为 PostgreSQL 配置"
echo "  - 如需恢复：$CONFIG_FILE.bak"
echo ""

if [ "$BATCH_MODE" = "0" ]; then
    read -p "  按 Enter 退出..." _
fi
