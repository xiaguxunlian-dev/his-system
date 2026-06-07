package com.his.statistics.repository;

import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * 统计报表数据访问层
 */
public class StatisticsRepository extends BaseRepository {

    /**
     * 挂号统计 - 按日期分组
     */
    public List<Map<String, Object>> getRegistrationStats(LocalDate start, LocalDate end) {
        String sql = "SELECT visit_date AS date, COUNT(*) AS count " +
                     "FROM registrations " +
                     "WHERE visit_date BETWEEN ? AND ? " +
                     "GROUP BY visit_date ORDER BY visit_date";
        try {
            return queryList(sql, this::mapDateCount, start, end);
        } catch (Exception e) {
            throw new DatabaseException("查询挂号统计失败", e);
        }
    }

    /**
     * 科室就诊统计 - 按科室分组
     */
    public List<Map<String, Object>> getDepartmentVisitStats(LocalDate start, LocalDate end) {
        String sql = "SELECT d.name AS dept_name, COUNT(*) AS count " +
                     "FROM outpatient_visits ov " +
                     "JOIN departments d ON ov.department_id = d.id " +
                     "WHERE ov.visit_date BETWEEN ? AND ? " +
                     "GROUP BY d.name ORDER BY count DESC";
        try {
            return queryList(sql, this::mapDeptCount, start, end);
        } catch (Exception e) {
            throw new DatabaseException("查询科室就诊统计失败", e);
        }
    }

    /**
     * 收入统计 - 按收费类型分组
     */
    public List<Map<String, Object>> getRevenueStats(int year, int month) {
        String sql = "SELECT bill_type AS category, COALESCE(SUM(total_amount), 0) AS total " +
                     "FROM billing_records " +
                     "WHERE EXTRACT(YEAR FROM bill_date) = ? AND EXTRACT(MONTH FROM bill_date) = ? " +
                     "GROUP BY bill_type";
        try {
            return queryList(sql, this::mapCategoryTotal, year, month);
        } catch (Exception e) {
            throw new DatabaseException("查询收入统计失败", e);
        }
    }

    /**
     * 药品消耗统计 - Top 20
     */
    public List<Map<String, Object>> getDrugConsumptionStats() {
        String sql = "SELECT drug_name AS drug_name, SUM(quantity) AS quantity " +
                     "FROM prescription_items " +
                     "GROUP BY drug_name " +
                     "ORDER BY quantity DESC LIMIT 20";
        try {
            return queryList(sql, this::mapDrugQuantity);
        } catch (Exception e) {
            throw new DatabaseException("查询药品消耗统计失败", e);
        }
    }

    private Map<String, Object> mapDateCount(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", rs.getDate("date") != null ? rs.getDate("date").toLocalDate() : null);
        map.put("count", rs.getInt("count"));
        return map;
    }

    private Map<String, Object> mapDeptCount(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deptName", rs.getString("dept_name"));
        map.put("count", rs.getInt("count"));
        return map;
    }

    private Map<String, Object> mapCategoryTotal(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("category", rs.getString("category"));
        map.put("total", rs.getBigDecimal("total"));
        return map;
    }

    private Map<String, Object> mapDrugQuantity(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("drugName", rs.getString("drug_name"));
        map.put("quantity", rs.getInt("quantity"));
        return map;
    }
}
