package com.his.inpatient.repository;

import com.his.inpatient.model.InpatientRecord;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InpatientRepository extends BaseRepository {

    public List<InpatientRecord> findAll() {
        String sql = "SELECT ir.*, p.name AS patient_name, dep.name AS department_name, d.name AS doctor_name, b.bed_no " +
                     "FROM inpatient_records ir " +
                     "JOIN patients p ON ir.patient_id = p.id " +
                     "JOIN departments dep ON ir.department_id = dep.id " +
                     "LEFT JOIN doctors d ON ir.doctor_id = d.id " +
                     "LEFT JOIN beds b ON ir.bed_id = b.id " +
                     "ORDER BY ir.id DESC";
        try {
            return queryList(sql, this::mapInpatient);
        } catch (Exception e) {
            throw new DatabaseException("查询所有住院记录失败", e);
        }
    }

    public InpatientRecord findById(int id) {
        String sql = "SELECT ir.*, p.name AS patient_name, dep.name AS department_name, d.name AS doctor_name, b.bed_no " +
                     "FROM inpatient_records ir " +
                     "JOIN patients p ON ir.patient_id = p.id " +
                     "JOIN departments dep ON ir.department_id = dep.id " +
                     "LEFT JOIN doctors d ON ir.doctor_id = d.id " +
                     "LEFT JOIN beds b ON ir.bed_id = b.id " +
                     "WHERE ir.id = ?";
        try {
            return querySingle(sql, this::mapInpatient, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询住院记录失败", e);
        }
    }

    public List<InpatientRecord> findByPatientId(int patientId) {
        String sql = "SELECT ir.*, p.name AS patient_name, dep.name AS department_name, d.name AS doctor_name, b.bed_no " +
                     "FROM inpatient_records ir " +
                     "JOIN patients p ON ir.patient_id = p.id " +
                     "JOIN departments dep ON ir.department_id = dep.id " +
                     "LEFT JOIN doctors d ON ir.doctor_id = d.id " +
                     "LEFT JOIN beds b ON ir.bed_id = b.id " +
                     "WHERE ir.patient_id = ? ORDER BY ir.admission_date DESC";
        try {
            return queryList(sql, this::mapInpatient, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询住院记录失败", e);
        }
    }

    public List<InpatientRecord> findByStatus(String status) {
        String sql = "SELECT ir.*, p.name AS patient_name, dep.name AS department_name, d.name AS doctor_name, b.bed_no " +
                     "FROM inpatient_records ir " +
                     "JOIN patients p ON ir.patient_id = p.id " +
                     "JOIN departments dep ON ir.department_id = dep.id " +
                     "LEFT JOIN doctors d ON ir.doctor_id = d.id " +
                     "LEFT JOIN beds b ON ir.bed_id = b.id " +
                     "WHERE ir.status = ? ORDER BY ir.admission_date";
        try {
            return queryList(sql, this::mapInpatient, status);
        } catch (Exception e) {
            throw new DatabaseException("根据状态查询住院记录失败", e);
        }
    }

    public List<InpatientRecord> findActive() {
        return findByStatus("在院");
    }

    public int getBedOccupationCount() {
        String sql = "SELECT COUNT(*) FROM inpatient_records WHERE status = '在院'";
        try {
            List<Integer> result = queryList(sql, rs -> rs.getInt(1));
            return result.isEmpty() ? 0 : result.get(0);
        } catch (Exception e) {
            throw new DatabaseException("查询床位占用数失败", e);
        }
    }

    public InpatientRecord save(InpatientRecord ir) {
        String sql = "INSERT INTO inpatient_records (admission_no, patient_id, bed_id, department_id, doctor_id, admission_date, discharge_date, admission_reason, admission_diagnosis, discharge_diagnosis, discharge_summary, status, total_cost, paid_amount, deposit_amount) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    ir.getAdmissionNo(),
                    ir.getPatientId(),
                    ir.getBedId(),
                    ir.getDepartmentId(),
                    ir.getAttendingDoctorId(),
                    ir.getAdmissionDate(),
                    ir.getDischargeDate(),
                    ir.getAdmissionReason(),
                    ir.getAdmissionDiagnosis(),
                    ir.getDischargeDiagnosis(),
                    ir.getDischargeSummary(),
                    ir.getStatus(),
                    ir.getTotalCost(),
                    ir.getPaidAmount(),
                    ir.getDepositAmount());
            ir.setId(id);
            return ir;
        } catch (Exception e) {
            throw new DatabaseException("保存住院记录失败", e);
        }
    }

    public void update(InpatientRecord ir) {
        String sql = "UPDATE inpatient_records SET admission_no=?, patient_id=?, bed_id=?, department_id=?, doctor_id=?, admission_date=?, discharge_date=?, admission_reason=?, admission_diagnosis=?, discharge_diagnosis=?, discharge_summary=?, status=?, total_cost=?, paid_amount=?, deposit_amount=? WHERE id=?";
        try {
            executeUpdate(sql,
                    ir.getAdmissionNo(),
                    ir.getPatientId(),
                    ir.getBedId(),
                    ir.getDepartmentId(),
                    ir.getAttendingDoctorId(),
                    ir.getAdmissionDate(),
                    ir.getDischargeDate(),
                    ir.getAdmissionReason(),
                    ir.getAdmissionDiagnosis(),
                    ir.getDischargeDiagnosis(),
                    ir.getDischargeSummary(),
                    ir.getStatus(),
                    ir.getTotalCost(),
                    ir.getPaidAmount(),
                    ir.getDepositAmount(),
                    ir.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新住院记录失败", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM inpatient_records WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除住院记录失败", e);
        }
    }

    // ==================== T-8.1.6 联动：住院→收费 ====================

    /**
     * T-8.1.6 联动：获取住院费用汇总（用于出院结算）
     */
    public java.util.Map<String, Object> getChargeSummary(int inpatientId) {
        String sql = "SELECT COALESCE(SUM(total_price), 0) AS total_charges, COUNT(*) AS item_count " +
                     "FROM inpatient_charges WHERE admission_id = ?";
        try {
            return querySingle(sql, rs -> {
                java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
                summary.put("totalCharges", rs.getBigDecimal("total_charges"));
                summary.put("itemCount", rs.getInt("item_count"));
                return summary;
            }, inpatientId);
        } catch (Exception e) {
            throw new DatabaseException("获取住院费用汇总失败", e);
        }
    }

    /**
     * T-8.1.6 联动：处理出院（更新住院状态、计算住院天数、汇总总费用）
     */
    public void processDischarge(int inpatientId, java.math.BigDecimal finalTotalCost) {
        String sql = "UPDATE inpatient_records SET status = '已出院', discharge_date = CURRENT_DATE, " +
                     "total_cost = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, finalTotalCost, inpatientId);
        } catch (Exception e) {
            throw new DatabaseException("处理出院失败", e);
        }
    }

    /**
     * T-8.1.6 联动：更新住院总费用
     */
    public void updateTotalCost(int inpatientId, java.math.BigDecimal amount) {
        String sql = "UPDATE inpatient_records SET total_cost = COALESCE(total_cost, 0) + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, amount, inpatientId);
        } catch (Exception e) {
            throw new DatabaseException("更新住院总费用失败", e);
        }
    }

    // ==================== RowMapper ====================

    private InpatientRecord mapInpatient(ResultSet rs) throws SQLException {
        InpatientRecord ir = new InpatientRecord();
        ir.setId(rs.getInt("id"));
        ir.setAdmissionNo(rs.getString("admission_no"));
        ir.setPatientId(rs.getInt("patient_id"));
        ir.setBedId(rs.getInt("bed_id"));
        ir.setDepartmentId(rs.getInt("department_id"));
        ir.setAttendingDoctorId(rs.getObject("doctor_id") != null ? rs.getInt("doctor_id") : null);
        ir.setAdmissionDate(rs.getDate("admission_date") != null ? rs.getDate("admission_date").toLocalDate() : null);
        ir.setDischargeDate(rs.getDate("discharge_date") != null ? rs.getDate("discharge_date").toLocalDate() : null);
        ir.setAdmissionReason(rs.getString("admission_reason"));
        ir.setAdmissionDiagnosis(rs.getString("admission_diagnosis"));
        ir.setDischargeDiagnosis(rs.getString("discharge_diagnosis"));
        ir.setDischargeSummary(rs.getString("discharge_summary"));
        ir.setStatus(rs.getString("status"));
        ir.setTotalCost(rs.getBigDecimal("total_cost"));
        ir.setPaidAmount(rs.getBigDecimal("paid_amount"));
        ir.setDepositAmount(rs.getBigDecimal("deposit_amount"));
        try { ir.setRemark(rs.getString("notes")); } catch (SQLException ignore) {}
        try { ir.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null); } catch (SQLException ignore) {}
        try { ir.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null); } catch (SQLException ignore) {}
        ir.setPatientName(rs.getString("patient_name"));
        ir.setDepartmentName(rs.getString("department_name"));
        ir.setDoctorName(rs.getString("doctor_name"));
        ir.setBedNo(rs.getString("bed_no"));
        return ir;
    }
}
