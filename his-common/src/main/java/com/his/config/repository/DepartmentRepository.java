package com.his.config.repository;

import com.his.config.model.Department;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DepartmentRepository extends BaseRepository {

    public List<Department> findAll() {
        String sql = "SELECT * FROM departments ORDER BY id";
        try {
            return queryList(sql, this::mapDepartment);
        } catch (Exception e) {
            throw new DatabaseException("查询所有科室失败", e);
        }
    }

    public Department findById(int id) {
        String sql = "SELECT * FROM departments WHERE id = ?";
        try {
            return querySingle(sql, this::mapDepartment, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询科室失败", e);
        }
    }

    public List<Department> findActive() {
        String sql = "SELECT * FROM departments WHERE is_active = TRUE ORDER BY id";
        try {
            return queryList(sql, this::mapDepartment);
        } catch (Exception e) {
            throw new DatabaseException("查询活跃科室失败", e);
        }
    }

    public Department save(Department dept) {
        String sql = "INSERT INTO departments (code, name, type, location, phone, is_active) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    dept.getCode(),
                    dept.getName(),
                    dept.getCategory(),
                    dept.getLocation(),
                    dept.getPhone(),
                    dept.isActive());
            dept.setId(id);
            return dept;
        } catch (Exception e) {
            throw new DatabaseException("保存科室失败", e);
        }
    }

    public void update(Department dept) {
        String sql = "UPDATE departments SET code=?, name=?, type=?, location=?, phone=?, is_active=? WHERE id=?";
        try {
            executeUpdate(sql,
                    dept.getCode(),
                    dept.getName(),
                    dept.getCategory(),
                    dept.getLocation(),
                    dept.getPhone(),
                    dept.isActive(),
                    dept.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新科室失败", e);
        }
    }

    public void delete(int id) {
        String sql = "UPDATE departments SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除科室失败", e);
        }
    }

    private Department mapDepartment(ResultSet rs) throws SQLException {
        Department d = new Department();
        d.setId(rs.getInt("id"));
        d.setCode(rs.getString("code"));
        d.setName(rs.getString("name"));
        d.setCategory(rs.getString("type"));
        d.setLocation(rs.getString("location"));
        d.setPhone(rs.getString("phone"));
        d.setActive(rs.getBoolean("is_active"));
        try { d.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null); } catch (SQLException ignore) {}
        try { d.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null); } catch (SQLException ignore) {}
        return d;
    }
}
