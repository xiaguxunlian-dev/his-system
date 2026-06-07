package com.his.emr.repository;

import com.his.emr.model.MedicalRecord;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class EmrRepository extends BaseRepository {

    public List<MedicalRecord> findAll() {
        String sql = "SELECT mr.* " +
                     "FROM medical_records mr " +
                     "ORDER BY mr.id DESC";
        try {
            return queryList(sql, this::mapRecord);
        } catch (Exception e) {
            throw new DatabaseException("查询所有病历失败", e);
        }
    }

    public MedicalRecord findById(int id) {
        String sql = "SELECT mr.* " +
                     "FROM medical_records mr " +
                     "WHERE mr.id = ?";
        try {
            return querySingle(sql, this::mapRecord, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询病历失败", e);
        }
    }

    public List<MedicalRecord> findByPatientId(int patientId) {
        String sql = "SELECT mr.* " +
                     "FROM medical_records mr " +
                     "WHERE mr.patient_id = ? ORDER BY mr.record_date DESC";
        try {
            return queryList(sql, this::mapRecord, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询病历失败", e);
        }
    }

    public List<MedicalRecord> findByRecordType(String type) {
        String sql = "SELECT mr.* " +
                     "FROM medical_records mr " +
                     "WHERE mr.record_type = ? ORDER BY mr.record_date DESC";
        try {
            return queryList(sql, this::mapRecord, type);
        } catch (Exception e) {
            throw new DatabaseException("根据病历类型查询失败", e);
        }
    }

    public MedicalRecord save(MedicalRecord mr) {
        String sql = "INSERT INTO medical_records (record_no, patient_id, patient_name, doctor_id, doctor_name, department_name, visit_id, admission_id, record_type, chief_complaint, present_illness, past_history, allergy_history, physical_exam, auxiliary_exam, diagnosis, treatment_plan, record_content, visit_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    mr.getRecordNo(),
                    mr.getPatientId(),
                    mr.getPatientName(),
                    mr.getDoctorId(),
                    mr.getDoctorName(),
                    mr.getDepartmentName(),
                    mr.getVisitId(),
                    mr.getInpatientId(),
                    mr.getRecordType(),
                    mr.getChiefComplaint(),
                    mr.getPresentIllness(),
                    mr.getPastHistory(),
                    mr.getAllergyHistory(),
                    mr.getPhysicalExam(),
                    mr.getAuxiliaryExam(),
                    mr.getDiagnosis(),
                    mr.getTreatmentPlan(),
                    mr.getContent(),
                    mr.getRecordDate());
            mr.setId(id);
            return mr;
        } catch (Exception e) {
            throw new DatabaseException("保存病历失败", e);
        }
    }

    public void update(MedicalRecord mr) {
        String sql = "UPDATE medical_records SET record_no=?, patient_id=?, patient_name=?, doctor_id=?, doctor_name=?, department_name=?, visit_id=?, admission_id=?, record_type=?, chief_complaint=?, present_illness=?, past_history=?, allergy_history=?, physical_exam=?, auxiliary_exam=?, diagnosis=?, treatment_plan=?, record_content=?, visit_date=? WHERE id=?";
        try {
            executeUpdate(sql,
                    mr.getRecordNo(),
                    mr.getPatientId(),
                    mr.getPatientName(),
                    mr.getDoctorId(),
                    mr.getDoctorName(),
                    mr.getDepartmentName(),
                    mr.getVisitId(),
                    mr.getInpatientId(),
                    mr.getRecordType(),
                    mr.getChiefComplaint(),
                    mr.getPresentIllness(),
                    mr.getPastHistory(),
                    mr.getAllergyHistory(),
                    mr.getPhysicalExam(),
                    mr.getAuxiliaryExam(),
                    mr.getDiagnosis(),
                    mr.getTreatmentPlan(),
                    mr.getContent(),
                    mr.getRecordDate(),
                    mr.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新病历失败", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM medical_records WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除病历失败", e);
        }
    }

    private MedicalRecord mapRecord(ResultSet rs) throws SQLException {
        MedicalRecord mr = new MedicalRecord();
        mr.setId(rs.getInt("id"));
        mr.setRecordNo(rs.getString("record_no"));
        mr.setPatientId(rs.getInt("patient_id"));
        mr.setDoctorId(rs.getInt("doctor_id"));
        mr.setVisitId(rs.getObject("visit_id") != null ? rs.getInt("visit_id") : null);
        mr.setInpatientId(rs.getObject("admission_id") != null ? rs.getInt("admission_id") : null);
        mr.setRecordType(rs.getString("record_type"));
        mr.setChiefComplaint(rs.getString("chief_complaint"));
        mr.setPresentIllness(rs.getString("present_illness"));
        mr.setPastHistory(rs.getString("past_history"));
        mr.setPhysicalExam(rs.getString("physical_exam"));
        mr.setAuxiliaryExam(rs.getString("auxiliary_exam"));
        mr.setDiagnosis(rs.getString("diagnosis"));
        mr.setTreatmentPlan(rs.getString("treatment_plan"));
        mr.setContent(rs.getString("record_content"));
        try { mr.setStatus(rs.getString("is_locked")); } catch (SQLException ignore) {}
        mr.setRecordDate(rs.getDate("visit_date") != null ? rs.getDate("visit_date").toLocalDate() : null);
        mr.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        try { mr.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null); } catch (SQLException ignore) {}
        mr.setPatientName(rs.getString("patient_name"));
        mr.setDoctorName(rs.getString("doctor_name"));
        mr.setDepartmentName(rs.getString("department_name"));
        return mr;
    }
}
