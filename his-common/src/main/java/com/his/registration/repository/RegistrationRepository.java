package com.his.registration.repository;

import com.his.registration.model.Registration;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class RegistrationRepository extends BaseRepository {

    public List<Registration> findAll() {
        String sql = "SELECT r.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM registrations r " +
                     "JOIN patients p ON r.patient_id = p.id " +
                     "JOIN doctors d ON r.doctor_id = d.id " +
                     "JOIN departments dep ON r.department_id = dep.id " +
                     "ORDER BY r.id DESC";
        try {
            return queryList(sql, this::mapRegistrationWithJoin);
        } catch (Exception e) {
            throw new DatabaseException("查询所有挂号记录失败", e);
        }
    }

    public Registration findById(int id) {
        String sql = "SELECT r.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM registrations r " +
                     "JOIN patients p ON r.patient_id = p.id " +
                     "JOIN doctors d ON r.doctor_id = d.id " +
                     "JOIN departments dep ON r.department_id = dep.id " +
                     "WHERE r.id = ?";
        try {
            return querySingle(sql, this::mapRegistrationWithJoin, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询挂号记录失败", e);
        }
    }

    public List<Registration> findByPatientId(int patientId) {
        String sql = "SELECT r.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM registrations r " +
                     "JOIN patients p ON r.patient_id = p.id " +
                     "JOIN doctors d ON r.doctor_id = d.id " +
                     "JOIN departments dep ON r.department_id = dep.id " +
                     "WHERE r.patient_id = ? ORDER BY r.visit_date DESC";
        try {
            return queryList(sql, this::mapRegistrationWithJoin, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询挂号记录失败", e);
        }
    }

    public List<Registration> findByDoctorAndDate(int doctorId, LocalDate date) {
        String sql = "SELECT r.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM registrations r " +
                     "JOIN patients p ON r.patient_id = p.id " +
                     "JOIN doctors d ON r.doctor_id = d.id " +
                     "JOIN departments dep ON r.department_id = dep.id " +
                     "WHERE r.doctor_id = ? AND r.visit_date = ? ORDER BY r.id";
        try {
            return queryList(sql, this::mapRegistrationWithJoin, doctorId, date);
        } catch (Exception e) {
            throw new DatabaseException("根据医生和日期查询挂号记录失败", e);
        }
    }

    public List<Registration> findByDate(LocalDate date) {
        String sql = "SELECT r.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM registrations r " +
                     "JOIN patients p ON r.patient_id = p.id " +
                     "JOIN doctors d ON r.doctor_id = d.id " +
                     "JOIN departments dep ON r.department_id = dep.id " +
                     "WHERE r.visit_date = ? ORDER BY r.id";
        try {
            return queryList(sql, this::mapRegistrationWithJoin, date);
        } catch (Exception e) {
            throw new DatabaseException("根据日期查询挂号记录失败", e);
        }
    }

    public Registration save(Registration r) {
        String sql = "INSERT INTO registrations (registration_no, patient_id, patient_name, doctor_id, doctor_name, department_id, department_name, visit_type, visit_date, visit_time_slot, status, registration_fee, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    r.getRegistrationNo(),
                    r.getPatientId(),
                    r.getPatientName(),
                    r.getDoctorId(),
                    r.getDoctorName(),
                    r.getDepartmentId(),
                    r.getDepartmentName(),
                    r.getRegistrationType(),
                    r.getVisitDate(),
                    r.getTimeSlot(),
                    r.getStatus(),
                    r.getFee(),
                    r.getRemark());
            r.setId(id);
            return r;
        } catch (Exception e) {
            throw new DatabaseException("保存挂号记录失败", e);
        }
    }

    public void update(Registration r) {
        String sql = "UPDATE registrations SET registration_no=?, patient_id=?, patient_name=?, doctor_id=?, doctor_name=?, department_id=?, department_name=?, visit_type=?, visit_date=?, visit_time_slot=?, status=?, registration_fee=?, notes=? WHERE id=?";
        try {
            executeUpdate(sql,
                    r.getRegistrationNo(),
                    r.getPatientId(),
                    r.getPatientName(),
                    r.getDoctorId(),
                    r.getDoctorName(),
                    r.getDepartmentId(),
                    r.getDepartmentName(),
                    r.getRegistrationType(),
                    r.getVisitDate(),
                    r.getTimeSlot(),
                    r.getStatus(),
                    r.getFee(),
                    r.getRemark(),
                    r.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新挂号记录失败", e);
        }
    }

    public void updateStatus(int id, String status) {
        String sql = "UPDATE registrations SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, status, id);
        } catch (Exception e) {
            throw new DatabaseException("更新挂号状态失败", e);
        }
    }

    public int getTodayCount() {
        String sql = "SELECT COUNT(*) FROM registrations WHERE visit_date = CURRENT_DATE";
        try {
            List<Integer> result = queryList(sql, rs -> rs.getInt(1));
            return result.isEmpty() ? 0 : result.get(0);
        } catch (Exception e) {
            throw new DatabaseException("查询今日挂号数失败", e);
        }
    }

    /**
     * T-8.1.1 联动：查询待接诊挂号记录
     * 门诊工作站调用此方法获取指定医生当天的待接诊患者列表
     */
    public List<Registration> findPendingVisits(int doctorId, LocalDate date) {
        String sql = "SELECT r.*, p.name AS patient_name, d.name AS doctor_name, dep.name AS department_name " +
                     "FROM registrations r " +
                     "JOIN patients p ON r.patient_id = p.id " +
                     "JOIN doctors d ON r.doctor_id = d.id " +
                     "JOIN departments dep ON r.department_id = dep.id " +
                     "WHERE r.doctor_id = ? AND r.visit_date = ? AND r.status = '待就诊' " +
                     "ORDER BY r.id";
        try {
            return queryList(sql, this::mapRegistrationWithJoin, doctorId, date);
        } catch (Exception e) {
            throw new DatabaseException("查询待接诊挂号记录失败", e);
        }
    }

    /**
     * T-8.1.1 联动：挂号后更新状态为已确认（门诊医生可接诊）
     */
    public void markAsConfirmed(int registrationId) {
        updateStatus(registrationId, "已确认");
    }

    /**
     * T-8.1.1 联动：医生接诊，创建门诊记录，更新挂号状态为已就诊
     */
    public void markAsVisited(int registrationId) {
        updateStatus(registrationId, "已就诊");
    }

    private Registration mapRegistrationWithJoin(ResultSet rs) throws SQLException {
        Registration r = mapBaseFields(rs);
        r.setPatientName(rs.getString("patient_name"));
        r.setDoctorName(rs.getString("doctor_name"));
        r.setDepartmentName(rs.getString("department_name"));
        return r;
    }

    private Registration mapBaseFields(ResultSet rs) throws SQLException {
        Registration r = new Registration();
        r.setId(rs.getInt("id"));
        r.setRegistrationNo(rs.getString("registration_no"));
        r.setPatientId(rs.getInt("patient_id"));
        r.setDoctorId(rs.getInt("doctor_id"));
        r.setDepartmentId(rs.getInt("department_id"));
        r.setRegistrationType(rs.getString("visit_type"));
        r.setStatus(rs.getString("status"));
        r.setVisitDate(rs.getDate("visit_date") != null ? rs.getDate("visit_date").toLocalDate() : null);
        r.setTimeSlot(rs.getString("visit_time_slot"));
        r.setFee(rs.getBigDecimal("registration_fee"));
        r.setRemark(rs.getString("notes"));
        r.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return r;
    }
}
