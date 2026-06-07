package com.his.outpatient.repository;

import com.his.outpatient.model.Prescription;
import com.his.outpatient.model.PrescriptionItem;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionRepository extends BaseRepository {

    public List<Prescription> findAll() {
        String sql = "SELECT pr.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM prescriptions pr " +
                     "JOIN patients p ON pr.patient_id = p.id " +
                     "JOIN doctors d ON pr.doctor_id = d.id " +
                     "ORDER BY pr.id DESC";
        try {
            return queryList(sql, this::mapPrescription);
        } catch (Exception e) {
            throw new DatabaseException("查询所有处方失败", e);
        }
    }

    public Prescription findById(int id) {
        String sql = "SELECT pr.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM prescriptions pr " +
                     "JOIN patients p ON pr.patient_id = p.id " +
                     "JOIN doctors d ON pr.doctor_id = d.id " +
                     "WHERE pr.id = ?";
        try {
            Prescription pres = querySingle(sql, this::mapPrescription, id);
            if (pres != null) {
                pres.setItems(findItemsByPrescriptionId(id));
            }
            return pres;
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询处方失败", e);
        }
    }

    public List<Prescription> findByVisitId(int visitId) {
        String sql = "SELECT pr.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM prescriptions pr " +
                     "JOIN patients p ON pr.patient_id = p.id " +
                     "JOIN doctors d ON pr.doctor_id = d.id " +
                     "WHERE pr.visit_id = ? ORDER BY pr.id";
        try {
            return queryList(sql, this::mapPrescription, visitId);
        } catch (Exception e) {
            throw new DatabaseException("根据就诊ID查询处方失败", e);
        }
    }

    public List<Prescription> findByPatientId(int patientId) {
        String sql = "SELECT pr.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM prescriptions pr " +
                     "JOIN patients p ON pr.patient_id = p.id " +
                     "JOIN doctors d ON pr.doctor_id = d.id " +
                     "WHERE pr.patient_id = ? ORDER BY pr.prescribe_date DESC";
        try {
            return queryList(sql, this::mapPrescription, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询处方失败", e);
        }
    }

    public Prescription save(Prescription p) {
        String sql = "INSERT INTO prescriptions (prescription_no, visit_id, patient_id, doctor_id, prescription_type, status, total_amount, diagnosis, remark, prescribe_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    p.getPrescriptionNo(),
                    p.getVisitId(),
                    p.getPatientId(),
                    p.getDoctorId(),
                    p.getPrescriptionType(),
                    p.getStatus(),
                    p.getTotalAmount(),
                    p.getDiagnosis(),
                    p.getRemark(),
                    p.getPrescribeDate());
            p.setId(id);
            return p;
        } catch (Exception e) {
            throw new DatabaseException("保存处方失败", e);
        }
    }

    public Prescription saveWithItems(Prescription p) {
        try {
            executeInTransaction(conn -> {
                // Insert prescription
                String sql = "INSERT INTO prescriptions (prescription_no, visit_id, patient_id, doctor_id, prescription_type, status, total_amount, diagnosis, remark, prescribe_date) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, p.getPrescriptionNo());
                    ps.setInt(2, p.getVisitId());
                    ps.setInt(3, p.getPatientId());
                    ps.setInt(4, p.getDoctorId());
                    ps.setString(5, p.getPrescriptionType());
                    ps.setString(6, p.getStatus());
                    ps.setBigDecimal(7, p.getTotalAmount());
                    ps.setString(8, p.getDiagnosis());
                    ps.setString(9, p.getRemark());
                    ps.setDate(10, p.getPrescribeDate() != null ? Date.valueOf(p.getPrescribeDate()) : null);
                    ps.executeUpdate();
                    ResultSet rs = ps.getGeneratedKeys();
                    if (rs.next()) {
                        p.setId(rs.getInt(1));
                    }
                }

                // Insert items
                if (p.getItems() != null && !p.getItems().isEmpty()) {
                    String itemSql = "INSERT INTO prescription_items (prescription_id, drug_id, drug_name, drug_spec, dosage, frequency, days, quantity, unit, unit_price, total_price, notes) " +
                                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS)) {
                        for (PrescriptionItem item : p.getItems()) {
                            ps.setInt(1, p.getId());
                            ps.setInt(2, item.getDrugId());
                            ps.setString(3, item.getDrugName());
                            ps.setString(4, item.getSpecification());
                            ps.setString(5, item.getDosage());
                            ps.setString(6, item.getFrequency());
                            ps.setInt(7, item.getDays());
                            ps.setInt(8, item.getQuantity());
                            ps.setString(9, item.getUnit());
                            ps.setBigDecimal(10, item.getUnitPrice());
                            ps.setBigDecimal(11, item.getSubtotal());
                            ps.setString(12, item.getRemark());
                            ps.executeUpdate();
                            ResultSet rs2 = ps.getGeneratedKeys();
                            if (rs2.next()) {
                                item.setId(rs2.getInt(1));
                            }
                        }
                    }
                }
            });
            return p;
        } catch (Exception e) {
            throw new DatabaseException("保存处方及明细失败", e);
        }
    }

    public void update(Prescription p) {
        String sql = "UPDATE prescriptions SET prescription_no=?, visit_id=?, patient_id=?, doctor_id=?, prescription_type=?, status=?, total_amount=?, diagnosis=?, remark=?, prescribe_date=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try {
            executeUpdate(sql,
                    p.getPrescriptionNo(),
                    p.getVisitId(),
                    p.getPatientId(),
                    p.getDoctorId(),
                    p.getPrescriptionType(),
                    p.getStatus(),
                    p.getTotalAmount(),
                    p.getDiagnosis(),
                    p.getRemark(),
                    p.getPrescribeDate(),
                    p.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新处方失败", e);
        }
    }

    public void delete(int id) {
        try {
            executeInTransaction(conn -> {
                try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM prescription_items WHERE prescription_id = ?")) {
                    ps1.setInt(1, id);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM prescriptions WHERE id = ?")) {
                    ps2.setInt(1, id);
                    ps2.executeUpdate();
                }
            });
        } catch (Exception e) {
            throw new DatabaseException("删除处方失败", e);
        }
    }

    // ==================== T-8.1.5 联动：收费→药房 ====================

    /**
     * T-8.1.5 联动：查询待发药处方（状态为'已缴费'的处方）
     * 药房工作站调用此方法获取需要发药的处方列表
     */
    public List<Prescription> findPendingDispensing() {
        String sql = "SELECT p.*, p2.name AS patient_name, d.name AS doctor_name " +
                     "FROM prescriptions p " +
                     "JOIN patients p2 ON p.patient_id = p2.id " +
                     "JOIN doctors d ON p.doctor_id = d.id " +
                     "WHERE p.status = '已缴费' " +
                     "ORDER BY p.created_at";
        try {
            return queryList(sql, this::mapPrescription, (Object[]) null);
        } catch (Exception e) {
            throw new DatabaseException("查询待发药处方失败", e);
        }
    }

    /**
     * T-8.1.5 联动：更新处方状态
     */
    public void updateStatus(int prescriptionId, String status) {
        String sql = "UPDATE prescriptions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, status, prescriptionId);
        } catch (Exception e) {
            throw new DatabaseException("更新处方状态失败", e);
        }
    }

    /**
     * T-8.1.5 联动：发药完成，更新处方状态为'已发药'
     */
    public void markAsDispensed(int prescriptionId) {
        updateStatus(prescriptionId, "已发药");
    }

    // ==================== 其他查询 ====================

    public List<PrescriptionItem> findItemsByPrescriptionId(int prescriptionId) {
        String sql = "SELECT * FROM prescription_items WHERE prescription_id = ? ORDER BY id";
        try {
            return queryList(sql, this::mapItem, prescriptionId);
        } catch (Exception e) {
            throw new DatabaseException("查询处方明细失败", e);
        }
    }

    private Prescription mapPrescription(ResultSet rs) throws SQLException {
        Prescription p = new Prescription();
        p.setId(rs.getInt("id"));
        p.setPrescriptionNo(rs.getString("prescription_no"));
        p.setVisitId(rs.getInt("visit_id"));
        p.setPatientId(rs.getInt("patient_id"));
        p.setDoctorId(rs.getInt("doctor_id"));
        p.setPrescriptionType(rs.getString("prescription_type"));
        p.setStatus(rs.getString("status"));
        p.setTotalAmount(rs.getBigDecimal("total_amount"));
        p.setDiagnosis(rs.getString("diagnosis"));
        p.setRemark(rs.getString("remark"));
        p.setPrescribeDate(rs.getDate("prescribe_date") != null ? rs.getDate("prescribe_date").toLocalDate() : null);
        p.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        p.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        p.setPatientName(rs.getString("patient_name"));
        p.setDoctorName(rs.getString("doctor_name"));
        return p;
    }

    private PrescriptionItem mapItem(ResultSet rs) throws SQLException {
        PrescriptionItem item = new PrescriptionItem();
        item.setId(rs.getInt("id"));
        item.setPrescriptionId(rs.getInt("prescription_id"));
        item.setDrugId(rs.getInt("drug_id"));
        item.setDrugName(rs.getString("drug_name"));
        item.setSpecification(rs.getString("drug_spec"));
        item.setDosage(rs.getString("dosage"));
        item.setFrequency(rs.getString("frequency"));
        item.setDays(rs.getInt("days"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnit(rs.getString("unit"));
        item.setUnitPrice(rs.getBigDecimal("unit_price"));
        item.setSubtotal(rs.getBigDecimal("total_price"));
        item.setRemark(rs.getString("notes"));
        return item;
    }
}
