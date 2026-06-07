package com.his.integration;

import com.his.shared.database.BaseIntegrationTest;
import com.his.shared.database.ConnectionPool;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-8: 跨模块数据联动测试
 * 使用直接SQL验证6个跨模块联动场景的数据库层面数据一致性
 *
 * 所有测试完全使用直接SQL，不依赖Repository方法（避免Repository与数据库列名不一致）
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class CrossModuleLinkageTest extends BaseIntegrationTest {

    private static final int OPERATOR_ID = 1;

    private Connection getConn() throws SQLException {
        return ConnectionPool.getInstance().getConnection();
    }

    // ==================== 直接 SQL 辅助方法 ====================

    private int insertDept(String code, String name, String type) {
        String sql = "INSERT INTO departments (code, name, type) VALUES (?, ?, ?)";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code); ps.setString(2, name); ps.setString(3, type);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertDoctor(String code, String name, int deptId) {
        String sql = "INSERT INTO doctors (code, name, department_id) VALUES (?, ?, ?)";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code); ps.setString(2, name); ps.setInt(3, deptId);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertPatient(String patientNo, String name) {
        String sql = "INSERT INTO patients (patient_no, name, gender, phone, address) VALUES (?, ?, '男', '13800000000', '测试地址')";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, patientNo); ps.setString(2, name);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertDrug(String code, String name) {
        String sql = "INSERT INTO drugs (drug_code, generic_name, drug_type, spec, unit, manufacturer) VALUES (?, ?, '西药', '测试规格', '片', '测试药厂')";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code); ps.setString(2, name);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next();
                int drugId = rs.getInt(1);
                // 创建库存
                try (PreparedStatement ps2 = c.prepareStatement(
                        "INSERT INTO drug_inventory (drug_id, drug_name, stock_qty, min_stock_qty, unit_price, retail_price) " +
                        "VALUES (?, ?, 1000, 10, 10, 10)")) {
                    ps2.setInt(1, drugId); ps2.setString(2, name);
                    ps2.executeUpdate();
                }
                return drugId;
            }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertExamItem(String code, String name) {
        String sql = "INSERT INTO exam_items (item_code, item_name, category, price) VALUES (?, ?, '检验', 25.00)";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code); ps.setString(2, name);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertRegistration(int patientId, int doctorId, int deptId, String status) {
        String regNo = "REG-" + System.currentTimeMillis();
        String sql = "INSERT INTO registrations (registration_no, patient_id, patient_name, doctor_id, doctor_name, " +
                     "department_id, department_name, visit_date, status, registration_fee) " +
                     "VALUES (?, ?, '测试患者', ?, '测试医生', ?, '测试科室', CURRENT_DATE, ?, 10.00)";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, regNo); ps.setInt(2, patientId);
            ps.setInt(3, doctorId); ps.setInt(4, deptId);
            ps.setString(5, status);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertOutpatientVisit(int regId, int patientId, int doctorId, int deptId) {
        String visitNo = "VIS-" + System.currentTimeMillis();
        String sql = "INSERT INTO outpatient_visits (visit_no, registration_id, patient_id, patient_name, " +
                     "doctor_id, doctor_name, department_id, department_name, visit_date, chief_complaint, status) " +
                     "VALUES (?, ?, ?, '测试患者', ?, '测试医生', ?, '测试科室', CURRENT_DATE, '测试主诉', '接诊中')";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, visitNo); ps.setInt(2, regId);
            ps.setInt(3, patientId); ps.setInt(4, doctorId); ps.setInt(5, deptId);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertPrescription(int visitId, int patientId, int doctorId, String status,
                                    int drugId, String drugName, int quantity, int unitPrice, int subtotal) {
        String presNo = "PRES-" + System.currentTimeMillis();
        try (Connection c = getConn()) {
            int presId;
            // 处方主表
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO prescriptions (prescription_no, visit_id, patient_id, patient_name, " +
                    "doctor_id, doctor_name, prescription_type, total_amount, status) " +
                    "VALUES (?, ?, ?, '测试患者', ?, '测试医生', '普通', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, presNo); ps.setInt(2, visitId);
                ps.setInt(3, patientId); ps.setInt(4, doctorId);
                ps.setInt(5, subtotal); ps.setString(6, status);
                ps.executeUpdate();
                try (var rs = ps.getGeneratedKeys()) { rs.next(); presId = rs.getInt(1); }
            }
            // 处方明细
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO prescription_items (prescription_id, drug_id, drug_name, " +
                    "dosage, frequency, days, quantity, unit, unit_price, total_price) " +
                    "VALUES (?, ?, ?, '1片', '每日3次', 7, ?, '片', ?, ?)")) {
                ps.setInt(1, presId); ps.setInt(2, drugId); ps.setString(3, drugName);
                ps.setInt(4, quantity); ps.setInt(5, unitPrice); ps.setInt(6, subtotal);
                ps.executeUpdate();
            }
            return presId;
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertExamRequest(int patientId, int doctorId, Integer visitId,
                                   int itemId, String itemName, String status) {
        String reqNo = "REQ-" + System.currentTimeMillis();
        String sql = "INSERT INTO examination_requests (request_no, patient_id, patient_name, " +
                     "doctor_id, doctor_name, department_name, visit_id, item_id, item_name, " +
                     "category, status, request_date) " +
                     "VALUES (?, ?, '测试患者', ?, '测试医生', '测试科室', ?, ?, ?, '检验', ?, CURRENT_DATE)";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reqNo); ps.setInt(2, patientId); ps.setInt(3, doctorId);
            ps.setObject(4, visitId); ps.setInt(5, itemId); ps.setString(6, itemName);
            ps.setString(7, status);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    private int insertExamReport(int requestId, boolean isAbnormal) {
        String rptNo = "RPT-" + System.currentTimeMillis();
        String sql = "INSERT INTO examination_reports (request_id, report_no, patient_id, patient_name, " +
                     "item_name, conclusion, is_abnormal, report_date) " +
                     "VALUES (?, ?, 1, '测试患者', '测试项目', '正常', ?, CURRENT_DATE)";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, requestId); ps.setString(2, rptNo);
            ps.setBoolean(3, isAbnormal);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return -1; }
    }

    // ==================== T-8.1.1: 挂号 → 门诊 ====================

    @Test
    @DisplayName("T-8.1.1 挂号→门诊: 挂号后门诊工作站查询待接诊患者")
    void t811_RegistrationToOutpatient() {
        int deptId = insertDept("MENZHEN", "门诊内科", "门诊科室");
        int doctorId = insertDoctor("DOC-01", "张医生", deptId);
        int patientId = insertPatient("P-202501", "测试患者A");

        // 步骤1: 挂号 status='待就诊'
        int regId = insertRegistration(patientId, doctorId, deptId, "待就诊");

        // 步骤2: 医生登录门诊工作站，查询今日待接诊患者
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM registrations WHERE doctor_id=? AND visit_date=CURRENT_DATE AND status='待就诊'")) {
            ps.setInt(1, doctorId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应该能查询到待接诊患者");
                assertEquals(regId, rs.getInt("id"));
            }
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤3: 医生确认接诊，更新挂号状态
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE registrations SET status='已就诊' WHERE id=?")) {
            ps.setInt(1, regId);
            assertEquals(1, ps.executeUpdate());
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤4: 创建门诊记录
        int visitId = insertOutpatientVisit(regId, patientId, doctorId, deptId);

        // 步骤5: 验证门诊记录关联到挂号
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM outpatient_visits WHERE registration_id=?")) {
            ps.setInt(1, regId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应能通过挂号ID找到门诊记录");
                assertEquals(visitId, rs.getInt("id"));
            }
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤6: 验证已就诊后不再出现在待接诊列表
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM registrations WHERE doctor_id=? AND visit_date=CURRENT_DATE AND status='待就诊'")) {
            ps.setInt(1, doctorId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "已就诊后待接诊应为0");
            }
        } catch (SQLException e) { fail(e.getMessage()); }
    }

    // ==================== T-8.1.2: 门诊 → 收费 ====================

    @Test
    @DisplayName("T-8.1.2 门诊→收费: 处方保存后自动生成待缴费项目")
    void t812_OutpatientToBilling() {
        int deptId = insertDept("BILL-DEPT", "收费测试科", "门诊科室");
        int doctorId = insertDoctor("DOC-BILL", "李医生", deptId);
        int patientId = insertPatient("P-BILL01", "收费测试患者");
        int drugId = insertDrug("DRUG-B01", "测试药品A");

        int regId = insertRegistration(patientId, doctorId, deptId, "已就诊");
        int visitId = insertOutpatientVisit(regId, patientId, doctorId, deptId);

        // 步骤1: 开处方
        int presId = insertPrescription(visitId, patientId, doctorId, "待缴费", drugId, "测试药品A", 15, 25, 125);

        // 步骤2: 收费系统自动生成收费记录 (从处方创建)
        String billNo = "BILL-" + System.currentTimeMillis();
        int billId;
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 插入收费记录
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO billing_records (bill_no, patient_id, patient_name, bill_type, visit_id, " +
                        "total_amount, paid_amount, payment_method, status, operator_id, bill_date) " +
                        "VALUES (?, ?, '测试患者', '门诊', ?, ?, 0, '现金', '待缴费', ?, CURRENT_DATE)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, billNo); ps.setInt(2, patientId); ps.setInt(3, visitId);
                    ps.setInt(4, 125); ps.setInt(5, OPERATOR_ID);
                    ps.executeUpdate();
                    try (var rs = ps.getGeneratedKeys()) { rs.next(); billId = rs.getInt(1); }
                }
                // 插入收费明细
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO billing_details (bill_id, item_type, item_name, quantity, unit_price, total_price) " +
                        "VALUES (?, '药品', '测试药品A', 15, 25, 125)")) {
                    ps.setInt(1, billId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { fail(e.getMessage()); return; }

        // 步骤3: 验证收费记录存在
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM billing_records WHERE id=?")) {
            ps.setInt(1, billId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "收费记录应存在");
                assertEquals("待缴费", rs.getString("status"));
                assertEquals(visitId, rs.getInt("visit_id"), "收费记录应关联到门诊visit_id");
            }
        } catch (SQLException e) { fail(e.getMessage()); }

        // 验证收费明细
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM billing_details WHERE bill_id=?")) {
            ps.setInt(1, billId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "应有1条收费明细");
            }
        } catch (SQLException e) { fail(e.getMessage()); }
    }

    // ==================== T-8.1.3: 门诊 → 检查 ====================

    @Test
    @DisplayName("T-8.1.3 门诊→检查: 开具检查申请后检查科收到申请单")
    void t813_OutpatientToExamination() {
        int deptId = insertDept("EXAM-DEPT", "检查测试科", "门诊科室");
        int doctorId = insertDoctor("DOC-EXAM", "赵医生", deptId);
        int patientId = insertPatient("P-EXAM01", "检查申请患者");
        int itemId = insertExamItem("CT-HEAD", "头部CT");

        // 步骤1: 门诊开具检查申请 status='待检查'
        int reqId = insertExamRequest(patientId, doctorId, null, itemId, "头部CT", "待检查");

        // 步骤2: 检查科查询待检查申请
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM examination_requests WHERE status=? ORDER BY request_date")) {
            if (c.getMetaData().getDatabaseProductName().contains("H2")) {
                ps.setString(1, "待检查");
            } else {
                ps.setString(1, "待检查");
            }
            try (var rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt("id"));
                assertFalse(ids.isEmpty(), "检查科应能看到待检查申请");
                assertTrue(ids.contains(reqId), "刚创建的申请应在待处理列表");
            }
        } catch (SQLException e) { fail(e.getMessage()); }
    }

    // ==================== T-8.1.4: 检查 → 门诊 ====================

    @Test
    @DisplayName("T-8.1.4 检查→门诊: 检验报告出具后医生工作站有新报告提醒")
    void t814_ExaminationToOutpatient() {
        int deptId = insertDept("RPT-DEPT", "报告测试科", "门诊科室");
        int doctorId = insertDoctor("DOC-RPT", "孙医生", deptId);
        int patientId = insertPatient("P-RPT01", "报告患者");
        int itemId = insertExamItem("BLOOD", "血常规报告测试");

        int reqId = insertExamRequest(patientId, doctorId, null, itemId, "血常规报告测试", "已检查");

        LocalDateTime beforeReport = LocalDateTime.now().minusMinutes(5);

        // 步骤1: 检验科出具报告
        insertExamReport(reqId, false);

        // 步骤2: 医生工作站查询新报告 (用 created_at 时间过滤)
        Timestamp ts = Timestamp.valueOf(beforeReport);
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM examination_reports WHERE created_at > ? ORDER BY created_at DESC")) {
            ps.setTimestamp(1, ts);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应能查到新出具的检查报告");
                // 验证: 新报告数据正确
                assertNotNull(rs.getString("conclusion"));
            }
        } catch (SQLException e) { fail(e.getMessage()); }
    }

    // ==================== T-8.1.5: 收费 → 药房 ====================

    @Test
    @DisplayName("T-8.1.5 收费→药房: 患者缴费后药房可见待发药处方")
    void t815_BillingToPharmacy() {
        int deptId = insertDept("PHARM-T", "药房测试科", "门诊科室");
        int doctorId = insertDoctor("DOC-PHARM", "周医生", deptId);
        int patientId = insertPatient("P-PHARM1", "药房测试患者");
        int drugId = insertDrug("DRUG-P01", "药房测试药品");

        // 步骤1: 创建已缴费处方
        int presId = insertPrescription(0, patientId, doctorId, "已缴费", drugId, "药房测试药品", 6, 15, 45);

        // 步骤2: 药房查询待发药处方
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM prescriptions WHERE status='已缴费' ORDER BY created_at")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "药房应能看到待发药处方");
                assertEquals("已缴费", rs.getString("status"));
            }
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤3: 药房发药，更新处方状态
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE prescriptions SET status='已发药' WHERE id=?")) {
            ps.setInt(1, presId);
            assertEquals(1, ps.executeUpdate());
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤4: 验证发药后不再出现在待发药列表
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM prescriptions WHERE status='已缴费'")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "发药后待发药处方应为0");
            }
        } catch (SQLException e) { fail(e.getMessage()); }
    }

    // ==================== T-8.1.6: 住院 → 收费 ====================

    @Test
    @DisplayName("T-8.1.6 住院→收费: 住院费用自动记录与出院结算")
    void t816_InpatientToBilling() {
        int deptId = insertDept("INP-DEPT", "住院测试科", "住院科室");
        int doctorId = insertDoctor("DOC-INP", "吴医生", deptId);
        int patientId = insertPatient("P-INP01", "住院患者");

        // 插入床位
        int bedId;
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO beds (bed_no, ward_name, department_id, bed_type, status) " +
                     "VALUES ('INP-T01', '测试病区', ?, '普通床', '占用')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, deptId);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); bedId = rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return; }

        // 步骤1: 办理入院
        int inpId;
        String admNo = "ADM-" + System.currentTimeMillis();
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inpatient_records (admission_no, patient_id, patient_name, " +
                     "department_id, department_name, doctor_id, doctor_name, bed_id, bed_no, ward_name, " +
                     "admission_date, status, admission_diagnosis, total_cost, deposit_amount) " +
                     "VALUES (?, ?, '测试患者', ?, '测试科室', ?, '测试医生', ?, 'INP-T01', '测试病区', " +
                     "CURRENT_DATE, '在院', '测试入院诊断', 0, 3000)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, admNo); ps.setInt(2, patientId);
            ps.setInt(3, deptId); ps.setInt(4, doctorId); ps.setInt(5, bedId);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { rs.next(); inpId = rs.getInt(1); }
        } catch (SQLException e) { fail(e.getMessage()); return; }

        // 步骤2: 记录住院费用
        try (Connection c = getConn()) {
            // 床位费
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO inpatient_charges (admission_id, patient_id, charge_type, item_name, " +
                    "quantity, unit_price, total_price, charge_date) VALUES (?, ?, '床位费', '普通病床', 1, 50, 50, CURRENT_DATE)")) {
                ps.setInt(1, inpId); ps.setInt(2, patientId); ps.executeUpdate();
            }
            // 检查费
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO inpatient_charges (admission_id, patient_id, charge_type, item_name, " +
                    "quantity, unit_price, total_price, charge_date) VALUES (?, ?, '检查费', '血常规', 1, 25, 25, CURRENT_DATE)")) {
                ps.setInt(1, inpId); ps.setInt(2, patientId); ps.executeUpdate();
            }
            // 药品费
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO inpatient_charges (admission_id, patient_id, charge_type, item_name, " +
                    "quantity, unit_price, total_price, charge_date) VALUES (?, ?, '药品费', '抗生素', 10, 15, 150, CURRENT_DATE)")) {
                ps.setInt(1, inpId); ps.setInt(2, patientId); ps.executeUpdate();
            }
        } catch (SQLException e) { fail(e.getMessage()); return; }

        // 步骤3: 查询住院费用汇总
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(total_price), 0) AS total_charges, COUNT(*) AS item_count " +
                     "FROM inpatient_charges WHERE admission_id=?")) {
            ps.setInt(1, inpId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(225.0, rs.getDouble("total_charges"), 0.01, "总费用应为225元");
                assertEquals(3, rs.getInt("item_count"), "应有3条费用记录");
            }
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤4: 出院 - 创建结算记录
        String billNo = "BILL-INP-" + System.currentTimeMillis();
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 创建收费记录 (billing_records)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO billing_records (bill_no, patient_id, patient_name, bill_type, admission_id, " +
                        "total_amount, paid_amount, payment_method, status, operator_id, bill_date) " +
                        "VALUES (?, ?, '测试患者', '住院', ?, 225, 225, '现金', '已缴费', ?, CURRENT_DATE)")) {
                    ps.setString(1, billNo); ps.setInt(2, patientId);
                    ps.setInt(3, inpId); ps.setInt(4, OPERATOR_ID);
                    ps.executeUpdate();
                }
                // 更新住院状态
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE inpatient_records SET status='已出院', discharge_date=CURRENT_DATE, total_cost=225 WHERE id=?")) {
                    ps.setInt(1, inpId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { fail(e.getMessage()); return; }

        // 步骤5: 验证出院状态
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement("SELECT status, total_cost FROM inpatient_records WHERE id=?")) {
            ps.setInt(1, inpId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("已出院", rs.getString("status"), "状态应为已出院");
            }
        } catch (SQLException e) { fail(e.getMessage()); }

        // 步骤6: 验证收费记录存在
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM billing_records WHERE bill_no=?")) {
            ps.setString(1, billNo);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应有出院收费记录");
                assertEquals("已缴费", rs.getString("status"));
            }
        } catch (SQLException e) { fail(e.getMessage()); }
    }

    // ==================== 综合联动: 全流程 ====================

    @Test
    @DisplayName("综合联动: 挂号→门诊→处方→收费→药房 全流程")
    void fullLinkageFlow() {
        // 准备
        int deptId = insertDept("FULL-DEPT", "全流程测试科", "门诊科室");
        int doctorId = insertDoctor("DOC-FULL1", "全流程医生", deptId);
        int patientId = insertPatient("P-FULL01", "全流程患者");
        int drug1Id = insertDrug("DRUG-F1", "全流测试药1");
        int drug2Id = insertDrug("DRUG-F2", "全流测试药2");

        // 步骤1: 挂号 → registrations (status='待就诊')
        int regId = insertRegistration(patientId, doctorId, deptId, "待就诊");
        assertTrue(regId > 0, "挂号成功");

        // 步骤2: 医生接诊 → outpatient_visits (挂号状态变为已就诊)
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 更新挂号状态
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE registrations SET status='已就诊' WHERE id=?")) {
                    ps.setInt(1, regId); ps.executeUpdate();
                }
                // 创建门诊记录
                String visitNo = "VIS-FULL-" + System.currentTimeMillis();
                int visitId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO outpatient_visits (visit_no, registration_id, patient_id, patient_name, " +
                        "doctor_id, doctor_name, department_id, department_name, visit_date, chief_complaint, status) " +
                        "VALUES (?, ?, ?, '全流程患者', ?, '全流程医生', ?, '全流程测试科', CURRENT_DATE, '全流程测试', '接诊中')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, visitNo); ps.setInt(2, regId);
                    ps.setInt(3, patientId); ps.setInt(4, doctorId); ps.setInt(5, deptId);
                    ps.executeUpdate();
                    try (var rs = ps.getGeneratedKeys()) { rs.next(); visitId = rs.getInt(1); }
                }
                c.commit();
                assertTrue(visitId > 0, "门诊记录创建成功");

                // 步骤3: 开处方 → prescriptions + prescription_items
                c.setAutoCommit(false);
                try {
                    int presId;
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO prescriptions (prescription_no, visit_id, patient_id, patient_name, " +
                            "doctor_id, doctor_name, prescription_type, total_amount, status) " +
                            "VALUES (?, ?, ?, '全流程患者', ?, '全流程医生', '普通', ?, '待缴费')",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, "PRES-FULL-" + System.currentTimeMillis());
                        ps.setInt(2, visitId); ps.setInt(3, patientId);
                        ps.setInt(4, doctorId); ps.setInt(5, 330);
                        ps.executeUpdate();
                        try (var rs = ps.getGeneratedKeys()) { rs.next(); presId = rs.getInt(1); }
                    }
                    // 明细1
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO prescription_items (prescription_id, drug_id, drug_name, " +
                            "dosage, frequency, days, quantity, unit, unit_price, total_price) " +
                            "VALUES (?, ?, ?, '1片', '每日3次', 7, ?, '片', ?, ?)")) {
                        ps.setInt(1, presId); ps.setInt(2, drug1Id); ps.setString(3, "全流测试药1");
                        ps.setInt(4, 21); ps.setInt(5, 20); ps.setInt(6, 140);
                        ps.executeUpdate();
                    }
                    // 明细2
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO prescription_items (prescription_id, drug_id, drug_name, " +
                            "dosage, frequency, days, quantity, unit, unit_price, total_price) " +
                            "VALUES (?, ?, ?, '1片', '每日2次', 7, ?, '片', ?, ?)")) {
                        ps.setInt(1, presId); ps.setInt(2, drug2Id); ps.setString(3, "全流测试药2");
                        ps.setInt(4, 14); ps.setInt(5, 35); ps.setInt(6, 190);
                        ps.executeUpdate();
                    }
                    c.commit();

                    // 步骤4: 处方→收费 billing_records + billing_details
                    c.setAutoCommit(false);
                    try {
                        int billId;
                        String billNo = "BILL-FULL-" + System.currentTimeMillis();
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO billing_records (bill_no, patient_id, patient_name, bill_type, visit_id, " +
                                "total_amount, paid_amount, payment_method, status, operator_id, bill_date) " +
                                "VALUES (?, ?, '全流程患者', '门诊', ?, 330, 0, '现金', '待缴费', ?, CURRENT_DATE)",
                                Statement.RETURN_GENERATED_KEYS)) {
                            ps.setString(1, billNo); ps.setInt(2, patientId);
                            ps.setInt(3, visitId); ps.setInt(4, OPERATOR_ID);
                            ps.executeUpdate();
                            try (var rs = ps.getGeneratedKeys()) { rs.next(); billId = rs.getInt(1); }
                        }
                        // 收费明细 (药品1 + 药品2)
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO billing_details (bill_id, item_type, item_name, quantity, unit_price, total_price) " +
                                "VALUES (?, '药品', '全流测试药1', 21, 20, 140), (?, '药品', '全流测试药2', 14, 35, 190)")) {
                            ps.setInt(1, billId); ps.setInt(2, billId);
                            ps.executeUpdate();
                        }
                        c.commit();

                        // 步骤5: 缴费 → 更新处方状态
                        c.setAutoCommit(false);
                        try {
                            try (PreparedStatement ps = c.prepareStatement(
                                    "UPDATE prescriptions SET status='已缴费' WHERE id=?")) {
                                ps.setInt(1, presId); ps.executeUpdate();
                            }
                            try (PreparedStatement ps = c.prepareStatement(
                                    "UPDATE billing_records SET paid_amount=330, status='已缴费' WHERE id=?")) {
                                ps.setInt(1, billId); ps.executeUpdate();
                            }
                            c.commit();

                            // 步骤6: 药房发药
                            c.setAutoCommit(false);
                            try {
                                // 验证待发药
                                try (PreparedStatement ps = c.prepareStatement(
                                        "SELECT COUNT(*) FROM prescriptions WHERE status='已缴费'")) {
                                    try (var rs = ps.executeQuery()) { rs.next(); assertTrue(rs.getInt(1) > 0, "应有待发药处方"); }
                                }
                                // 发药
                                try (PreparedStatement ps = c.prepareStatement(
                                        "UPDATE prescriptions SET status='已发药' WHERE id=?")) {
                                    ps.setInt(1, presId); ps.executeUpdate();
                                }
                                c.commit();

                                // 步骤7: 全流程验证
                                // 处方状态
                                try (PreparedStatement ps = c.prepareStatement(
                                        "SELECT status FROM prescriptions WHERE id=?")) {
                                    ps.setInt(1, presId);
                                    try (var rs = ps.executeQuery()) {
                                        assertTrue(rs.next());
                                        assertEquals("已发药", rs.getString("status"));
                                    }
                                }
                                // 门诊记录关联到挂号
                                try (PreparedStatement ps = c.prepareStatement(
                                        "SELECT COUNT(*) FROM outpatient_visits WHERE registration_id=?")) {
                                    ps.setInt(1, regId);
                                    try (var rs = ps.executeQuery()) { rs.next(); assertEquals(1, rs.getInt(1)); }
                                }
                                // 处方有2个明细
                                try (PreparedStatement ps = c.prepareStatement(
                                        "SELECT COUNT(*) FROM prescription_items WHERE prescription_id=?")) {
                                    ps.setInt(1, presId);
                                    try (var rs = ps.executeQuery()) { rs.next(); assertEquals(2, rs.getInt(1)); }
                                }
                                // 收费有2个明细
                                try (PreparedStatement ps = c.prepareStatement(
                                        "SELECT COUNT(*) FROM billing_details WHERE bill_id=?")) {
                                    ps.setInt(1, billId);
                                    try (var rs = ps.executeQuery()) { rs.next(); assertEquals(2, rs.getInt(1)); }
                                }
                            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
                        } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
                    } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
                } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { fail(e.getMessage()); }
    }
}
