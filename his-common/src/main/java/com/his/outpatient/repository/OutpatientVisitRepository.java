package com.his.outpatient.repository;

import com.his.outpatient.model.OutpatientVisit;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class OutpatientVisitRepository extends BaseRepository {

    public List<OutpatientVisit> findAll() {
        String sql = "SELECT v.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM outpatient_visits v " +
                     "JOIN patients p ON v.patient_id = p.id " +
                     "JOIN doctors d ON v.doctor_id = d.id " +
                     "JOIN departments dep ON v.department_id = dep.id " +
                     "ORDER BY v.id DESC";
        try {
            return queryList(sql, this::mapVisit);
        } catch (Exception e) {
            throw new DatabaseException("查询所有门诊记录失败", e);
        }
    }

    public OutpatientVisit findById(int id) {
        String sql = "SELECT v.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM outpatient_visits v " +
                     "JOIN patients p ON v.patient_id = p.id " +
                     "JOIN doctors d ON v.doctor_id = d.id " +
                     "JOIN departments dep ON v.department_id = dep.id " +
                     "WHERE v.id = ?";
        try {
            return querySingle(sql, this::mapVisit, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询门诊记录失败", e);
        }
    }

    public OutpatientVisit findByRegistrationId(int registrationId) {
        String sql = "SELECT v.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM outpatient_visits v " +
                     "JOIN patients p ON v.patient_id = p.id " +
                     "JOIN doctors d ON v.doctor_id = d.id " +
                     "JOIN departments dep ON v.department_id = dep.id " +
                     "WHERE v.registration_id = ?";
        try {
            return querySingle(sql, this::mapVisit, registrationId);
        } catch (Exception e) {
            throw new DatabaseException("根据挂号ID查询门诊记录失败", e);
        }
    }

    public List<OutpatientVisit> findByPatientId(int patientId) {
        String sql = "SELECT v.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM outpatient_visits v " +
                     "JOIN patients p ON v.patient_id = p.id " +
                     "JOIN doctors d ON v.doctor_id = d.id " +
                     "JOIN departments dep ON v.department_id = dep.id " +
                     "WHERE v.patient_id = ? ORDER BY v.visit_date DESC";
        try {
            return queryList(sql, this::mapVisit, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询门诊记录失败", e);
        }
    }

    public List<OutpatientVisit> findByDoctorAndDate(int doctorId, LocalDate date) {
        String sql = "SELECT v.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM outpatient_visits v " +
                     "JOIN patients p ON v.patient_id = p.id " +
                     "JOIN doctors d ON v.doctor_id = d.id " +
                     "JOIN departments dep ON v.department_id = dep.id " +
                     "WHERE v.doctor_id = ? AND v.visit_date = ? ORDER BY v.id";
        try {
            return queryList(sql, this::mapVisit, doctorId, date);
        } catch (Exception e) {
            throw new DatabaseException("根据医生和日期查询门诊记录失败", e);
        }
    }

    public OutpatientVisit save(OutpatientVisit v) {
        String sql = "INSERT INTO outpatient_visits (registration_id, patient_id, doctor_id, department_id, chief_complaint, present_illness, past_history, physical_exam, diagnosis, diagnosis_name, visit_status, visit_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    v.getRegistrationId(),
                    v.getPatientId(),
                    v.getDoctorId(),
                    v.getDepartmentId(),
                    v.getChiefComplaint(),
                    v.getPresentIllness(),
                    v.getPastHistory(),
                    v.getPhysicalExam(),
                    v.getDiagnosis(),
                    v.getDiagnosisName(),
                    v.getVisitStatus(),
                    v.getVisitDate());
            v.setId(id);
            return v;
        } catch (Exception e) {
            throw new DatabaseException("保存门诊记录失败", e);
        }
    }

    public void update(OutpatientVisit v) {
        String sql = "UPDATE outpatient_visits SET registration_id=?, patient_id=?, doctor_id=?, department_id=?, chief_complaint=?, present_illness=?, past_history=?, physical_exam=?, diagnosis=?, diagnosis_name=?, visit_status=?, visit_date=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try {
            executeUpdate(sql,
                    v.getRegistrationId(),
                    v.getPatientId(),
                    v.getDoctorId(),
                    v.getDepartmentId(),
                    v.getChiefComplaint(),
                    v.getPresentIllness(),
                    v.getPastHistory(),
                    v.getPhysicalExam(),
                    v.getDiagnosis(),
                    v.getDiagnosisName(),
                    v.getVisitStatus(),
                    v.getVisitDate(),
                    v.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新门诊记录失败", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM outpatient_visits WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除门诊记录失败", e);
        }
    }

    private OutpatientVisit mapVisit(ResultSet rs) throws SQLException {
        OutpatientVisit v = new OutpatientVisit();
        v.setId(rs.getInt("id"));
        v.setRegistrationId(rs.getInt("registration_id"));
        v.setPatientId(rs.getInt("patient_id"));
        v.setDoctorId(rs.getInt("doctor_id"));
        v.setDepartmentId(rs.getInt("department_id"));
        v.setChiefComplaint(rs.getString("chief_complaint"));
        v.setPresentIllness(rs.getString("present_illness"));
        v.setPastHistory(rs.getString("past_history"));
        v.setPhysicalExam(rs.getString("physical_exam"));
        v.setDiagnosis(rs.getString("diagnosis"));
        v.setDiagnosisName(rs.getString("diagnosis_name"));
        v.setVisitStatus(rs.getString("visit_status"));
        v.setVisitDate(rs.getDate("visit_date") != null ? rs.getDate("visit_date").toLocalDate() : null);
        v.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        v.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        v.setPatientName(rs.getString("patient_name"));
        v.setDoctorName(rs.getString("doctor_name"));
        v.setDepartmentName(rs.getString("department_name"));
        return v;
    }
}
