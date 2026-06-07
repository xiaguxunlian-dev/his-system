package com.his.config.repository;

import com.his.config.model.Doctor;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DoctorRepository extends BaseRepository {

    public List<Doctor> findAll() {
        String sql = "SELECT d.*, dep.name AS department_name " +
                     "FROM doctors d " +
                     "LEFT JOIN departments dep ON d.department_id = dep.id " +
                     "ORDER BY d.id";
        try {
            return queryList(sql, this::mapDoctor);
        } catch (Exception e) {
            throw new DatabaseException("查询所有医生失败", e);
        }
    }

    public Doctor findById(int id) {
        String sql = "SELECT d.*, dep.name AS department_name " +
                     "FROM doctors d " +
                     "LEFT JOIN departments dep ON d.department_id = dep.id " +
                     "WHERE d.id = ?";
        try {
            return querySingle(sql, this::mapDoctor, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询医生失败", e);
        }
    }

    public List<Doctor> findByDepartment(int deptId) {
        String sql = "SELECT d.*, dep.name AS department_name " +
                     "FROM doctors d " +
                     "LEFT JOIN departments dep ON d.department_id = dep.id " +
                     "WHERE d.department_id = ? AND d.is_active = TRUE ORDER BY d.id";
        try {
            return queryList(sql, this::mapDoctor, deptId);
        } catch (Exception e) {
            throw new DatabaseException("根据科室查询医生失败", e);
        }
    }

    public Doctor save(Doctor doctor) {
        String sql = "INSERT INTO doctors (code, name, gender, title, department_id, phone, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    doctor.getEmployeeId(),
                    doctor.getName(),
                    doctor.getGender(),
                    doctor.getTitle(),
                    doctor.getDepartmentId(),
                    doctor.getPhone(),
                    doctor.isActive());
            doctor.setId(id);
            return doctor;
        } catch (Exception e) {
            throw new DatabaseException("保存医生失败", e);
        }
    }

    public void update(Doctor doctor) {
        String sql = "UPDATE doctors SET code=?, name=?, gender=?, title=?, department_id=?, phone=?, is_active=? WHERE id=?";
        try {
            executeUpdate(sql,
                    doctor.getEmployeeId(),
                    doctor.getName(),
                    doctor.getGender(),
                    doctor.getTitle(),
                    doctor.getDepartmentId(),
                    doctor.getPhone(),
                    doctor.isActive(),
                    doctor.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新医生失败", e);
        }
    }

    public void delete(int id) {
        String sql = "UPDATE doctors SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除医生失败", e);
        }
    }

    private Doctor mapDoctor(ResultSet rs) throws SQLException {
        Doctor d = new Doctor();
        d.setId(rs.getInt("id"));
        d.setEmployeeId(rs.getString("code"));
        d.setName(rs.getString("name"));
        d.setGender(rs.getString("gender"));
        d.setTitle(rs.getString("title"));
        d.setDepartmentId(rs.getInt("department_id"));
        d.setPhone(rs.getString("phone"));
        d.setActive(rs.getBoolean("is_active"));
        try { d.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null); } catch (SQLException ignore) {}
        try { d.setDepartmentName(rs.getString("department_name")); } catch (SQLException ignore) {}
        return d;
    }
}
