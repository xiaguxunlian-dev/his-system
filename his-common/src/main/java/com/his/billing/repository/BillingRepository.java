package com.his.billing.repository;

import com.his.billing.model.BillingDetail;
import com.his.billing.model.BillingRecord;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BillingRepository extends BaseRepository {

    // ==================== BillingRecord ====================

    public List<BillingRecord> findAllRecords() {
        String sql = "SELECT br.*, p.name AS patient_name " +
                     "FROM billing_records br " +
                     "JOIN patients p ON br.patient_id = p.id " +
                     "ORDER BY br.id DESC";
        try {
            return queryList(sql, this::mapRecord);
        } catch (Exception e) {
            throw new DatabaseException("查询所有收费记录失败", e);
        }
    }

    public BillingRecord findRecordById(int id) {
        String sql = "SELECT br.*, p.name AS patient_name " +
                     "FROM billing_records br " +
                     "JOIN patients p ON br.patient_id = p.id " +
                     "WHERE br.id = ?";
        try {
            return querySingle(sql, this::mapRecord, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询收费记录失败", e);
        }
    }

    public List<BillingRecord> findRecordsByPatientId(int patientId) {
        String sql = "SELECT br.*, p.name AS patient_name " +
                     "FROM billing_records br " +
                     "JOIN patients p ON br.patient_id = p.id " +
                     "WHERE br.patient_id = ? ORDER BY br.bill_date DESC";
        try {
            return queryList(sql, this::mapRecord, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询收费记录失败", e);
        }
    }

    public List<BillingRecord> findRecordsByDateRange(LocalDate start, LocalDate end) {
        String sql = "SELECT br.*, p.name AS patient_name " +
                     "FROM billing_records br " +
                     "JOIN patients p ON br.patient_id = p.id " +
                     "WHERE br.bill_date >= ? AND br.bill_date <= ? ORDER BY br.bill_date DESC";
        try {
            return queryList(sql, this::mapRecord, start, end);
        } catch (Exception e) {
            throw new DatabaseException("根据日期范围查询收费记录失败", e);
        }
    }

    public BillingRecord saveRecord(BillingRecord br) {
        String sql = "INSERT INTO billing_records (bill_no, patient_id, bill_type, visit_id, admission_id, total_amount, paid_amount, payment_method, status, operator_id, bill_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    br.getBillNo(),
                    br.getPatientId(),
                    br.getBillType(),
                    br.getVisitId(),
                    br.getInpatientId(),
                    br.getTotalAmount(),
                    br.getPaidAmount(),
                    br.getPaymentMethod(),
                    br.getPaymentStatus(),
                    br.getOperatorId(),
                    br.getBillDate());
            br.setId(id);
            return br;
        } catch (Exception e) {
            throw new DatabaseException("保存收费记录失败", e);
        }
    }

    public void updateRecord(BillingRecord br) {
        String sql = "UPDATE billing_records SET bill_no=?, patient_id=?, bill_type=?, visit_id=?, admission_id=?, total_amount=?, paid_amount=?, payment_method=?, status=?, operator_id=?, bill_date=? WHERE id=?";
        try {
            executeUpdate(sql,
                    br.getBillNo(),
                    br.getPatientId(),
                    br.getBillType(),
                    br.getVisitId(),
                    br.getInpatientId(),
                    br.getTotalAmount(),
                    br.getPaidAmount(),
                    br.getPaymentMethod(),
                    br.getPaymentStatus(),
                    br.getOperatorId(),
                    br.getBillDate(),
                    br.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新收费记录失败", e);
        }
    }

    public void deleteRecord(int id) {
        String sql = "DELETE FROM billing_records WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除收费记录失败", e);
        }
    }

    public BigDecimal getTodayTotal() {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM billing_records WHERE bill_date = CURRENT_DATE";
        try {
            List<BigDecimal> result = queryList(sql, rs -> rs.getBigDecimal(1));
            return result.isEmpty() ? BigDecimal.ZERO : (result.get(0) != null ? result.get(0) : BigDecimal.ZERO);
        } catch (Exception e) {
            throw new DatabaseException("查询今日收费总额失败", e);
        }
    }

    public BigDecimal getMonthlyTotal(int year, int month) {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM billing_records WHERE EXTRACT(YEAR FROM bill_date) = ? AND EXTRACT(MONTH FROM bill_date) = ?";
        try {
            List<BigDecimal> result = queryList(sql, rs -> rs.getBigDecimal(1), year, month);
            return result.isEmpty() ? BigDecimal.ZERO : (result.get(0) != null ? result.get(0) : BigDecimal.ZERO);
        } catch (Exception e) {
            throw new DatabaseException("查询月收费总额失败", e);
        }
    }

    // ==================== T-8.1.2 联动：门诊→收费 ====================

    /**
     * T-8.1.2 联动：根据处方ID自动创建收费记录
     * 处方保存后调用此方法，自动在 billing_records 创建记录，在 billing_details 创建明细
     */
    public BillingRecord createFromPrescription(int prescriptionId, int operatorId) {
        try {
            final int[] billingRecordId = new int[1];
            executeInTransaction(conn -> {
                // 1. 查询处方及明细
                String presSql = "SELECT p.*, pi.drug_name, pi.quantity, pi.unit_price, pi.total_price " +
                                 "FROM prescriptions p " +
                                 "LEFT JOIN prescription_items pi ON p.id = pi.prescription_id " +
                                 "WHERE p.id = ?";
                PrescriptionData presData;
                try (PreparedStatement ps = conn.prepareStatement(presSql)) {
                    ps.setInt(1, prescriptionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        presData = null;
                        while (rs.next()) {
                            if (presData == null) {
                                presData = new PrescriptionData();
                                presData.billNo = "BILL-PRES-" + System.currentTimeMillis();
                                presData.patientId = rs.getInt("patient_id");
                                presData.visitId = rs.getObject("visit_id") != null ? rs.getInt("visit_id") : null;
                                presData.totalAmount = rs.getBigDecimal("total_amount");
                                presData.prescriptionNo = rs.getString("prescription_no");
                            }
                            if (rs.getString("drug_name") != null) {
                                presData.items.add(new PrescriptionItemData(
                                    rs.getString("drug_name"),
                                    rs.getInt("quantity"),
                                    rs.getBigDecimal("unit_price"),
                                    rs.getBigDecimal("total_price")
                                ));
                            }
                        }
                    }
                }
                if (presData == null) throw new SQLException("处方不存在: " + prescriptionId);

                // 2. 插入 billing_records
                String insertBillSql = "INSERT INTO billing_records (bill_no, patient_id, bill_type, visit_id, " +
                                     "total_amount, paid_amount, payment_method, status, operator_id, bill_date) " +
                                     "VALUES (?, ?, '门诊', ?, ?, 0, '现金', '待缴费', ?, CURRENT_DATE)";
                try (PreparedStatement ps = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, presData.billNo);
                    ps.setInt(2, presData.patientId);
                    ps.setObject(3, presData.visitId);
                    ps.setBigDecimal(4, presData.totalAmount);
                    ps.setInt(5, operatorId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) billingRecordId[0] = rs.getInt(1);
                    }
                }

                // 3. 插入 billing_details
                if (!presData.items.isEmpty()) {
                    String insertDetailSql = "INSERT INTO billing_details (bill_id, item_type, item_name, quantity, unit_price, total_price) " +
                                           "VALUES (?, '药品', ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertDetailSql)) {
                        for (PrescriptionItemData item : presData.items) {
                            ps.setInt(1, billingRecordId[0]);
                            ps.setString(2, item.drugName);
                            ps.setInt(3, item.quantity);
                            ps.setBigDecimal(4, item.unitPrice);
                            ps.setBigDecimal(5, item.subtotal);
                            ps.executeUpdate();
                        }
                    }
                }
            });
            // 返回创建的收费记录
            return findRecordById(billingRecordId[0]);
        } catch (Exception e) {
            throw new DatabaseException("根据处方创建收费记录失败", e);
        }
    }

    /**
     * T-8.1.2 联动：根据检查申请ID自动创建收费记录
     */
    public BillingRecord createFromExamination(int requestId, int operatorId) {
        try {
            final int[] billingRecordId = new int[1];
            executeInTransaction(conn -> {
                // 1. 查询检查申请
                String reqSql = "SELECT er.*, ei.item_name, ei.price " +
                                 "FROM examination_requests er " +
                                 "JOIN exam_items ei ON er.item_id = ei.id " +
                                 "WHERE er.id = ?";
                ExaminationRequestData reqData;
                try (PreparedStatement ps = conn.prepareStatement(reqSql)) {
                    ps.setInt(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("检查申请不存在: " + requestId);
                        reqData = new ExaminationRequestData();
                        reqData.billNo = "BILL-EXAM-" + System.currentTimeMillis();
                        reqData.patientId = rs.getInt("patient_id");
                        reqData.visitId = rs.getObject("visit_id") != null ? rs.getInt("visit_id") : null;
                        reqData.itemName = rs.getString("item_name");
                        reqData.price = rs.getBigDecimal("price");
                        reqData.requestNo = rs.getString("request_no");
                    }
                }

                // 2. 插入 billing_records
                String insertBillSql = "INSERT INTO billing_records (bill_no, patient_id, bill_type, visit_id, " +
                                     "total_amount, paid_amount, payment_method, status, operator_id, bill_date) " +
                                     "VALUES (?, ?, '门诊', ?, ?, 0, '现金', '待缴费', ?, CURRENT_DATE)";
                try (PreparedStatement ps = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, reqData.billNo);
                    ps.setInt(2, reqData.patientId);
                    ps.setObject(3, reqData.visitId);
                    ps.setBigDecimal(4, reqData.price);
                    ps.setInt(5, operatorId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) billingRecordId[0] = rs.getInt(1);
                    }
                }

                // 3. 插入 billing_details
                    String insertDetailSql = "INSERT INTO billing_details (bill_id, item_type, item_name, quantity, unit_price, total_price) " +
                                           "VALUES (?, '检查', ?, 1, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertDetailSql)) {
                    ps.setInt(1, billingRecordId[0]);
                    ps.setString(2, reqData.itemName);
                    ps.setBigDecimal(3, reqData.price);
                    ps.setBigDecimal(4, reqData.price);
                    ps.executeUpdate();
                }
            });
            return findRecordById(billingRecordId[0]);
        } catch (Exception e) {
            throw new DatabaseException("根据检查申请创建收费记录失败", e);
        }
    }

    // ==================== 内部数据结构（用于跨表查询） ====================
    private static class PrescriptionData {
        String billNo;
        int patientId;
        Integer visitId;
        BigDecimal totalAmount;
        String prescriptionNo;
        List<PrescriptionItemData> items = new ArrayList<>();
    }
    private static class PrescriptionItemData {
        String drugName;
        int quantity;
        BigDecimal unitPrice;
        BigDecimal subtotal;
        PrescriptionItemData(String drugName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
            this.drugName = drugName; this.quantity = quantity;
            this.unitPrice = unitPrice; this.subtotal = subtotal;
        }
    }
    private static class ExaminationRequestData {
        String billNo;
        int patientId;
        Integer visitId;
        String itemName;
        BigDecimal price;
        String requestNo;
    }

    // ==================== BillingDetail ====================

    public List<BillingDetail> findDetailsByBillId(int billId) {
        String sql = "SELECT * FROM billing_details WHERE bill_id = ? ORDER BY id";
        try {
            return queryList(sql, this::mapDetail, billId);
        } catch (Exception e) {
            throw new DatabaseException("查询收费明细失败", e);
        }
    }

    public BillingDetail saveDetail(BillingDetail bd) {
        String sql = "INSERT INTO billing_details (bill_id, item_type, item_name, quantity, unit_price, total_price) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    bd.getBillId(),
                    bd.getItemType(),
                    bd.getItemName(),
                    bd.getQuantity(),
                    bd.getUnitPrice(),
                    bd.getSubtotal());
            bd.setId(id);
            return bd;
        } catch (Exception e) {
            throw new DatabaseException("保存收费明细失败", e);
        }
    }

    public void updateDetail(BillingDetail bd) {
        String sql = "UPDATE billing_details SET bill_id=?, item_type=?, item_name=?, quantity=?, unit_price=?, total_price=? WHERE id=?";
        try {
            executeUpdate(sql,
                    bd.getBillId(),
                    bd.getItemType(),
                    bd.getItemName(),
                    bd.getQuantity(),
                    bd.getUnitPrice(),
                    bd.getSubtotal(),
                    bd.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新收费明细失败", e);
        }
    }

    public void deleteDetail(int id) {
        String sql = "DELETE FROM billing_details WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除收费明细失败", e);
        }
    }

    // ==================== RowMappers ====================

    private BillingRecord mapRecord(ResultSet rs) throws SQLException {
        BillingRecord br = new BillingRecord();
        br.setId(rs.getInt("id"));
        br.setBillNo(rs.getString("bill_no"));
        br.setPatientId(rs.getInt("patient_id"));
        br.setBillType(rs.getString("bill_type"));
        br.setVisitId(rs.getObject("visit_id") != null ? rs.getInt("visit_id") : null);
        br.setInpatientId(rs.getObject("admission_id") != null ? rs.getInt("admission_id") : null);
        br.setTotalAmount(rs.getBigDecimal("total_amount"));
        br.setPaidAmount(rs.getBigDecimal("paid_amount"));
        br.setPaymentMethod(rs.getString("payment_method"));
        br.setPaymentStatus(rs.getString("status"));
        br.setOperatorId(rs.getObject("operator_id") != null ? rs.getInt("operator_id") : null);
        br.setBillDate(rs.getDate("bill_date") != null ? rs.getDate("bill_date").toLocalDate() : null);
        br.setPatientName(rs.getString("patient_name"));
        try { br.setRemark(rs.getString("notes")); } catch (SQLException ignore) {}
        return br;
    }

    private BillingDetail mapDetail(ResultSet rs) throws SQLException {
        BillingDetail bd = new BillingDetail();
        bd.setId(rs.getInt("id"));
        bd.setBillId(rs.getInt("bill_id"));
        bd.setItemType(rs.getString("item_type"));
        bd.setItemId(rs.getInt("item_id"));
        bd.setItemName(rs.getString("item_name"));
        bd.setQuantity(rs.getInt("quantity"));
        bd.setUnitPrice(rs.getBigDecimal("unit_price"));
        bd.setSubtotal(rs.getBigDecimal("total_price"));
        bd.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return bd;
    }
}
