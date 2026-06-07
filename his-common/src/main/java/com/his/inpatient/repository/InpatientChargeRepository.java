package com.his.inpatient.repository;

import com.his.inpatient.model.InpatientCharge;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InpatientChargeRepository extends BaseRepository {

    public List<InpatientCharge> findByInpatientId(int inpatientId) {
        String sql = "SELECT * FROM inpatient_charges WHERE admission_id = ? ORDER BY charge_date DESC, id DESC";
        try {
            return queryList(sql, this::mapCharge, inpatientId);
        } catch (Exception e) {
            throw new DatabaseException("查询住院费用失败", e);
        }
    }

    public InpatientCharge findById(int id) {
        String sql = "SELECT * FROM inpatient_charges WHERE id = ?";
        try {
            return querySingle(sql, this::mapCharge, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询住院费用失败", e);
        }
    }

    public InpatientCharge save(InpatientCharge c) {
        String sql = "INSERT INTO inpatient_charges (admission_id, charge_type, item_name, quantity, unit_price, total_price, charge_date, doctor_name, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    c.getInpatientId(),
                    c.getChargeType(),
                    c.getChargeItem(),
                    c.getQuantity(),
                    c.getUnitPrice(),
                    c.getAmount(),
                    c.getChargeDate(),
                    c.getDoctorName(),
                    c.getRemark());
            c.setId(id);
            return c;
        } catch (Exception e) {
            throw new DatabaseException("保存住院费用失败", e);
        }
    }

    public void update(InpatientCharge c) {
        String sql = "UPDATE inpatient_charges SET admission_id=?, charge_type=?, item_name=?, quantity=?, unit_price=?, total_price=?, charge_date=?, doctor_name=?, notes=? WHERE id=?";
        try {
            executeUpdate(sql,
                    c.getInpatientId(),
                    c.getChargeType(),
                    c.getChargeItem(),
                    c.getQuantity(),
                    c.getUnitPrice(),
                    c.getAmount(),
                    c.getChargeDate(),
                    c.getDoctorName(),
                    c.getRemark(),
                    c.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新住院费用失败", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM inpatient_charges WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除住院费用失败", e);
        }
    }

    private InpatientCharge mapCharge(ResultSet rs) throws SQLException {
        InpatientCharge c = new InpatientCharge();
        c.setId(rs.getInt("id"));
        c.setInpatientId(rs.getInt("admission_id"));
        c.setChargeType(rs.getString("charge_type"));
        c.setChargeItem(rs.getString("item_name"));
        c.setQuantity(rs.getInt("quantity"));
        c.setUnitPrice(rs.getBigDecimal("unit_price"));
        c.setAmount(rs.getBigDecimal("total_price"));
        c.setChargeDate(rs.getDate("charge_date") != null ? rs.getDate("charge_date").toLocalDate() : null);
        c.setDoctorName(rs.getString("doctor_name"));
        c.setRemark(rs.getString("notes"));
        c.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return c;
    }
}
