package com.his.ui.statistics;

import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsRepository extends BaseRepository {

    public List<Map<String, Object>> getRegistrationStats(LocalDate start, LocalDate end) {
        String sql = "SELECT visit_date, COUNT(*) AS count " +
                     "FROM registrations " +
                     "WHERE visit_date >= ? AND visit_date <= ? " +
                     "GROUP BY visit_date ORDER BY visit_date";
        try {
            return queryList(sql, rs -> {
                Map<String, Object> map = new HashMap<>();
                map.put("date", rs.getDate("visit_date") != null ? rs.getDate("visit_date").toLocalDate() : null);
                map.put("count", rs.getInt("count"));
                return map;
            }, start, end);
        } catch (Exception e) {
            throw new DatabaseException("查询挂号统计失败", e);
        }
    }

    public List<Map<String, Object>> getDepartmentVisitStats(LocalDate start, LocalDate end) {
        String sql = "SELECT dep.name AS dept_name, COUNT(*) AS count " +
                     "FROM outpatient_visits v " +
                     "JOIN departments dep ON v.department_id = dep.id " +
                     "WHERE v.visit_date >= ? AND v.visit_date <= ? " +
                     "GROUP BY dep.name ORDER BY count DESC";
        try {
            return queryList(sql, rs -> {
                Map<String, Object> map = new HashMap<>();
                map.put("dept_name", rs.getString("dept_name"));
                map.put("count", rs.getInt("count"));
                return map;
            }, start, end);
        } catch (Exception e) {
            throw new DatabaseException("查询科室就诊统计失败", e);
        }
    }

    public List<Map<String, Object>> getRevenueStats(int year, int month) {
        String sql = "SELECT bi.item_type AS category, SUM(bi.subtotal) AS total " +
                     "FROM billing_details bi " +
                     "JOIN billing_records br ON bi.bill_id = br.id " +
                     "WHERE EXTRACT(YEAR FROM br.bill_date) = ? AND EXTRACT(MONTH FROM br.bill_date) = ? " +
                     "GROUP BY bi.item_type ORDER BY total DESC";
        try {
            return queryList(sql, rs -> {
                Map<String, Object> map = new HashMap<>();
                map.put("category", rs.getString("category"));
                map.put("total", rs.getBigDecimal("total"));
                return map;
            }, year, month);
        } catch (Exception e) {
            throw new DatabaseException("查询收入统计失败", e);
        }
    }

    public List<Map<String, Object>> getDailyRevenue(LocalDate start, LocalDate end) {
        String sql = "SELECT br.bill_date, SUM(br.total_amount) AS total " +
                     "FROM billing_records br " +
                     "WHERE br.bill_date >= ? AND br.bill_date <= ? " +
                     "GROUP BY br.bill_date ORDER BY br.bill_date";
        try {
            return queryList(sql, rs -> {
                Map<String, Object> map = new HashMap<>();
                map.put("date", rs.getDate("bill_date") != null ? rs.getDate("bill_date").toLocalDate() : null);
                map.put("total", rs.getBigDecimal("total") != null ? rs.getBigDecimal("total") : BigDecimal.ZERO);
                return map;
            }, start, end);
        } catch (Exception e) {
            throw new DatabaseException("查询每日收入统计失败", e);
        }
    }

    public List<Map<String, Object>> getDrugConsumptionStats() {
        String sql = "SELECT d.drug_name AS drug_name, SUM(dd.quantity) AS quantity " +
                     "FROM drug_dispense dd " +
                     "JOIN drugs d ON dd.drug_id = d.id " +
                     "GROUP BY d.drug_name ORDER BY quantity DESC";
        try {
            return queryList(sql, rs -> {
                Map<String, Object> map = new HashMap<>();
                map.put("drug_name", rs.getString("drug_name"));
                map.put("quantity", rs.getInt("quantity"));
                return map;
            });
        } catch (Exception e) {
            throw new DatabaseException("查询药品消耗统计失败", e);
        }
    }
}
