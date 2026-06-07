-- ============================================================
-- V3: 为 drug_inventory 表添加缺失列
-- ============================================================

-- drug_spec: 药品规格（PharmacyView 入库/查询需要）
ALTER TABLE IF EXISTS drug_inventory ADD COLUMN IF NOT EXISTS drug_spec VARCHAR(100);

-- 按规格合并索引，优化查询
CREATE INDEX IF NOT EXISTS idx_drug_inventory_drug_spec ON drug_inventory(drug_spec);
