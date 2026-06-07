package com.his.pharmacy.repository;

import com.his.pharmacy.model.Drug;
import com.his.pharmacy.model.DrugInventory;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DrugRepository extends BaseRepository {

    public List<Drug> findAll() {
        String sql = "SELECT * FROM drugs WHERE is_active = TRUE ORDER BY id";
        try {
            return queryList(sql, this::mapDrug);
        } catch (Exception e) {
            throw new DatabaseException("查询所有药品失败", e);
        }
    }

    public Drug findById(int id) {
        String sql = "SELECT * FROM drugs WHERE id = ?";
        try {
            return querySingle(sql, this::mapDrug, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询药品失败", e);
        }
    }

    public List<Drug> search(String keyword) {
        String sql = "SELECT * FROM drugs WHERE (generic_name LIKE ? OR drug_code LIKE ? OR manufacturer LIKE ?) AND is_active = TRUE ORDER BY id";
        String likeKeyword = "%" + keyword + "%";
        try {
            return queryList(sql, this::mapDrug, likeKeyword, likeKeyword, likeKeyword);
        } catch (Exception e) {
            throw new DatabaseException("搜索药品失败", e);
        }
    }

    public Drug save(Drug drug) {
        String sql = "INSERT INTO drugs (drug_code, generic_name, trade_name, drug_type, spec, unit, dosage_form, manufacturer, approval_no, is_narcotic, is_psychotropic, is_otc, storage_cond, is_active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    drug.getDrugCode(),
                    drug.getDrugName(),
                    drug.getTradeName(),
                    drug.getDrugType(),
                    drug.getSpecification(),
                    drug.getUnit(),
                    null,  // dosage_form: not in model, use null
                    drug.getManufacturer(),
                    null,  // approval_no: not in model, use null
                    null,  // is_narcotic: not in model, use null
                    null,  // is_psychotropic: not in model, use null
                    drug.isPrescription(),
                    drug.getStorageCondition(),
                    drug.isActive());
            drug.setId(id);

            // Create initial inventory record
            String invSql = "INSERT INTO drug_inventory (drug_id, current_stock, min_stock, max_stock) VALUES (?, 0, 10, 1000) " +
                            "ON CONFLICT (drug_id) DO NOTHING";
            try {
                executeUpdate(invSql, id);
            } catch (Exception ignored) {
                // Inventory may already exist
            }

            return drug;
        } catch (Exception e) {
            throw new DatabaseException("保存药品失败", e);
        }
    }

    public void update(Drug drug) {
        String sql = "UPDATE drugs SET drug_code=?, generic_name=?, trade_name=?, drug_type=?, spec=?, unit=?, dosage_form=?, manufacturer=?, approval_no=?, is_narcotic=?, is_psychotropic=?, is_otc=?, storage_cond=?, is_active=? WHERE id=?";
        try {
            executeUpdate(sql,
                    drug.getDrugCode(),
                    drug.getDrugName(),
                    drug.getTradeName(),
                    drug.getDrugType(),
                    drug.getSpecification(),
                    drug.getUnit(),
                    null,
                    drug.getManufacturer(),
                    null,
                    null,
                    null,
                    drug.isPrescription(),
                    drug.getStorageCondition(),
                    drug.isActive(),
                    drug.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新药品失败", e);
        }
    }

    public void delete(int id) {
        String sql = "UPDATE drugs SET is_active = FALSE WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除药品失败", e);
        }
    }

    public DrugInventory getInventory(int drugId) {
        String sql = "SELECT di.*, d.generic_name AS drug_name " +
                     "FROM drug_inventory di " +
                     "JOIN drugs d ON di.drug_id = d.id " +
                     "WHERE di.drug_id = ?";
        try {
            return querySingle(sql, this::mapInventory, drugId);
        } catch (Exception e) {
            throw new DatabaseException("查询药品库存失败", e);
        }
    }

    public List<DrugInventory> findLowStock() {
        String sql = "SELECT di.*, d.generic_name AS drug_name " +
                     "FROM drug_inventory di " +
                     "JOIN drugs d ON di.drug_id = d.id " +
                     "WHERE di.stock_qty <= di.min_stock_qty AND d.is_active = TRUE " +
                     "ORDER BY di.stock_qty ASC";
        try {
            return queryList(sql, this::mapInventory);
        } catch (Exception e) {
            throw new DatabaseException("查询低库存药品失败", e);
        }
    }

    public void updateStock(int drugId, int quantity, boolean inbound) {
        String sql;
        if (inbound) {
            sql = "UPDATE drug_inventory SET stock_qty = stock_qty + ?, last_updated = CURRENT_TIMESTAMP WHERE drug_id = ?";
        } else {
            sql = "UPDATE drug_inventory SET stock_qty = stock_qty - ?, last_updated = CURRENT_TIMESTAMP WHERE drug_id = ?";
        }
        try {
            executeUpdate(sql, quantity, drugId);
        } catch (Exception e) {
            throw new DatabaseException("更新库存失败", e);
        }
    }

    private Drug mapDrug(ResultSet rs) throws SQLException {
        Drug d = new Drug();
        d.setId(rs.getInt("id"));
        d.setDrugCode(rs.getString("drug_code"));
        d.setDrugName(rs.getString("generic_name"));
        d.setTradeName(rs.getString("trade_name"));
        d.setDrugType(rs.getString("drug_type"));
        d.setSpecification(rs.getString("spec"));
        d.setUnit(rs.getString("unit"));
        d.setManufacturer(rs.getString("manufacturer"));
        d.setPrescription(rs.getBoolean("is_otc"));
        d.setStorageCondition(rs.getString("storage_cond"));
        d.setActive(rs.getBoolean("is_active"));
        d.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return d;
    }

    private DrugInventory mapInventory(ResultSet rs) throws SQLException {
        DrugInventory di = new DrugInventory();
        di.setId(rs.getInt("id"));
        di.setDrugId(rs.getInt("drug_id"));
        di.setCurrentStock(rs.getInt("stock_qty"));
        di.setMinStock(rs.getInt("min_stock_qty"));
        di.setBatchNo(rs.getString("batch_no"));
        di.setExpiryDate(rs.getDate("expiry_date") != null ? rs.getDate("expiry_date").toLocalDate() : null);
        di.setUpdatedAt(rs.getTimestamp("last_updated") != null ? rs.getTimestamp("last_updated").toLocalDateTime() : null);
        try {
            di.setDrugName(rs.getString("drug_name"));
        } catch (SQLException ignored) {
            // drug_name may not be in the result set
        }
        return di;
    }
}
