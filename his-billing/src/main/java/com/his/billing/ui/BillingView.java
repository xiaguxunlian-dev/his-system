package com.his.billing.ui;

import com.his.auth.AuditService;
import com.his.auth.UserSession;
import com.his.shared.database.ConnectionPool;
import com.his.shared.exception.BusinessException;
import com.his.shared.ui.AsyncUIUtil;
import com.his.shared.validation.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 收费管理视图 - 生产级
 * T-3.7: 完善收费管理
 * 功能：门诊收费、住院收费、医保结算、发票管理、审计日志
 */
public class BillingView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(BillingView.class);
    private final AuditService audit = AuditService.getInstance();
    private final UserSession session = UserSession.getInstance();

    // ========== Tab1: 门诊收费 ==========
    private TableView<Object[]> outpatientTable;
    private ObservableList<Object[]> outpatientData = FXCollections.observableArrayList();
    private DatePicker opStartDate, opEndDate;
    private Label opTotalLabel;

    // ========== Tab2: 住院收费 ==========
    private TableView<Object[]> inpatientTable;
    private ObservableList<Object[]> inpatientData = FXCollections.observableArrayList();
    private TextField inpatientSearchField;
    private Label inpatientTotalLabel;

    public BillingView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab opTab = new Tab("门诊收费");
        opTab.setContent(buildOutpatientTab());
        opTab.setClosable(false);

        Tab ipTab = new Tab("住院收费");
        ipTab.setContent(buildInpatientTab());
        ipTab.setClosable(false);

        getTabs().addAll(opTab, ipTab);
    }

    // ========================================================================
    // Tab1: 门诊收费
    // ========================================================================

    private VBox buildOutpatientTab() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(0));

        // ---- 收费表单 ----
        HBox formRow = new HBox(10);
        formRow.setAlignment(Pos.CENTER_LEFT);
        TextField patientField = new TextField();
        patientField.setPromptText("患者姓名/病历号");
        patientField.setPrefWidth(180);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("挂号费", "药品费", "检查费", "治疗费", "其他");
        typeCombo.setValue("药品费");
        typeCombo.setPrefWidth(120);
        TextField itemField = new TextField();
        itemField.setPromptText("收费项目");
        itemField.setPrefWidth(150);
        TextField amountField = new TextField();
        amountField.setPromptText("金额");
        amountField.setPrefWidth(100);
        ComboBox<String> payCombo = new ComboBox<>();
        payCombo.getItems().addAll("现金", "医保", "银行卡", "微信", "支付宝");
        payCombo.setValue("现金");
        payCombo.setPrefWidth(100);
        Button chargeBtn = new Button("收费");
        chargeBtn.getStyleClass().add("btn-primary");
        formRow.getChildren().addAll(
                new Label("患者:"), patientField, new Label("类型:"), typeCombo,
                new Label("项目:"), itemField, new Label("金额¥:"), amountField,
                new Label("支付:"), payCombo, chargeBtn);

        // ---- 收费记录表格 ----
        outpatientTable = new TableView<>();
        outpatientTable.getStyleClass().add("table-view");
        outpatientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] cols = {"ID", "票据号", "患者", "类型", "总金额", "医保支付", "自费", "已付", "支付方式", "状态", "日期"};
        int[] widths = {40, 120, 100, 80, 80, 80, 80, 80, 80, 70, 90};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(widths[i]);
            outpatientTable.getColumns().add(c);
        }
        outpatientTable.setItems(outpatientData);
        VBox.setVgrow(outpatientTable, Priority.ALWAYS);

        // ---- 日期筛选 ----
        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        opStartDate = new DatePicker(LocalDate.now().minusDays(7));
        opStartDate.setPrefWidth(120);
        opEndDate = new DatePicker(LocalDate.now());
        opEndDate.setPrefWidth(120);
        Button filterBtn = new Button("查询");
        filterBtn.getStyleClass().add("btn-outline");
        opTotalLabel = new Label("合计: ¥0.00");
        opTotalLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        filterRow.getChildren().addAll(new Label("日期范围:"), opStartDate, new Label("至"), opEndDate, filterBtn, opTotalLabel);

        root.getChildren().addAll(formRow, filterRow, outpatientTable);

        // ---- 事件绑定 ----
        chargeBtn.setOnAction(e -> createOutpatientBill(patientField, typeCombo, itemField, amountField, payCombo));
        filterBtn.setOnAction(e -> loadOutpatientBills());

        // 初始加载
        loadOutpatientBills();

        return root;
    }

    private void createOutpatientBill(TextField patientField, ComboBox<String> typeCombo,
                                   TextField itemField, TextField amountField, ComboBox<String> payCombo) {
        String patientStr = patientField.getText().trim();
        if (patientStr.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "请输入患者信息").showAndWait();
            return;
        }
        String amountStr = amountField.getText().trim();
        if (amountStr.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "请输入金额").showAndWait();
            return;
        }
        BigDecimal amount = new BigDecimal(amountStr);
        String paymentMethod = payCombo.getValue();

        AsyncUIUtil.executeAsync(outpatientTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement psP = conn.prepareStatement(
                        "SELECT id, name FROM patients WHERE name=? OR patient_no=? LIMIT 1");
                psP.setString(1, patientStr); psP.setString(2, patientStr);
                ResultSet rsP = psP.executeQuery();
                if (!rsP.next()) throw new BusinessException("未找到患者");
                int pId = rsP.getInt(1);
                String pName = rsP.getString(2);

                String billNo = "BILL" + System.currentTimeMillis() % 100000000;
                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO billing_records (bill_no, patient_id, patient_name, bill_type, " +
                        "total_amount, paid_amount, payment_method, status, operator_id, operator_name) " +
                        "VALUES (?,?,?,'门诊',?,?,'待缴费',?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                psIns.setString(1, billNo);
                psIns.setInt(2, pId);
                psIns.setString(3, pName);
                psIns.setBigDecimal(4, amount);
                psIns.setBigDecimal(5, amount);
                psIns.setString(6, paymentMethod);
                psIns.setInt(7, (int) session.getUserId());
                psIns.setString(8, session.getUsername());
                psIns.executeUpdate();

                ResultSet keys = psIns.getGeneratedKeys();
                if (keys.next()) {
                    int billId = keys.getInt(1);
                    audit.log("CREATE", "billing_records", String.valueOf(billId),
                            "门诊收费: billNo=" + billNo + ", amount=" + amount);
                }
                return billNo;
            }
        }, billNo -> {
            new Alert(Alert.AlertType.INFORMATION, "收费成功！票据号: " + billNo).showAndWait();
            amountField.clear();
            itemField.clear();
            loadOutpatientBills();
        });
    }

    private void loadOutpatientBills() {
        Date startDate = Date.valueOf(opStartDate.getValue());
        Date endDate = Date.valueOf(opEndDate.getValue());
        String sql = """
                SELECT br.id, br.bill_no, p.name as pname, br.bill_type, br.total_amount,
                       br.insurance_amount, br.self_pay_amount, br.paid_amount,
                       br.payment_method, br.status, br.bill_date
                FROM billing_records br
                JOIN patients p ON br.patient_id = p.id
                WHERE br.bill_date BETWEEN ? AND ? AND br.bill_type = '门诊'
                ORDER BY br.id DESC LIMIT 100""";

        AsyncUIUtil.executeAsync(outpatientTable, () -> {
            List<Object[]> results = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, startDate);
                ps.setDate(2, endDate);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    BigDecimal amt = rs.getBigDecimal("total_amount");
                    total = total.add(amt != null ? amt : BigDecimal.ZERO);
                    results.add(new Object[]{
                            rs.getInt("id"), rs.getString("bill_no"),
                            rs.getString("pname"), rs.getString("bill_type"),
                            amt, rs.getBigDecimal("insurance_amount"),
                            rs.getBigDecimal("self_pay_amount"), rs.getBigDecimal("paid_amount"),
                            rs.getString("payment_method"), rs.getString("status"),
                            rs.getDate("bill_date").toLocalDate()
                    });
                }
            }
            return new Object[]{results, total};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<Object[]> data = (List<Object[]>) result[0];
            BigDecimal total = (BigDecimal) result[1];
            outpatientData.clear();
            outpatientData.addAll(data);
            opTotalLabel.setText("合计: ¥" + total.toString());
        });
    }

    // ========================================================================
    // Tab2: 住院收费
    // ========================================================================

    private VBox buildInpatientTab() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(0));

        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        inpatientSearchField = new TextField();
        inpatientSearchField.setPromptText("患者姓名/病历号...");
        inpatientSearchField.setPrefWidth(200);
        Button searchBtn = new Button("查询费用");
        searchBtn.getStyleClass().add("btn-primary");
        Button settleBtn = new Button("出院结算");
        settleBtn.getStyleClass().add("btn-success");
        searchRow.getChildren().addAll(new Label("住院患者:"), inpatientSearchField, searchBtn, settleBtn);

        // ---- 费用明细表 ----
        inpatientTable = new TableView<>();
        inpatientTable.getStyleClass().add("table-view");
        inpatientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] cols = {"ID", "费用类型", "收费项目", "数量", "单价", "总金额", "日期", "医生"};
        int[] widths = {40, 100, 150, 70, 80, 80, 90, 100};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(widths[i]);
            inpatientTable.getColumns().add(c);
        }
        inpatientTable.setItems(inpatientData);
        VBox.setVgrow(inpatientTable, Priority.ALWAYS);

        inpatientTotalLabel = new Label("住院总费用: ¥0.00");
        inpatientTotalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        VBox.setVgrow(inpatientTable, Priority.ALWAYS);

        root.getChildren().addAll(searchRow, inpatientTable, inpatientTotalLabel);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadInpatientCharges());
        settleBtn.setOnAction(e -> settleInpatient());

        return root;
    }

    private void loadInpatientCharges() {
        String kw = inpatientSearchField.getText().trim();
        if (kw.isEmpty()) return;
        String likeKw = "%" + kw + "%";

        AsyncUIUtil.executeAsync(inpatientTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement psIP = conn.prepareStatement(
                        "SELECT id FROM inpatient_records WHERE patient_id IN " +
                        "(SELECT id FROM patients WHERE name LIKE ? OR patient_no LIKE ? LIMIT 1) " +
                        "AND status='在院' ORDER BY id DESC LIMIT 1");
                psIP.setString(1, likeKw); psIP.setString(2, likeKw);
                ResultSet rsIP = psIP.executeQuery();
                if (!rsIP.next()) {
                    throw new BusinessException("未找到在院患者");
                }
                int ipId = rsIP.getInt(1);

                List<Object[]> results = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                PreparedStatement psC = conn.prepareStatement(
                        "SELECT id, charge_type, item_name, quantity, unit_price, total_price, charge_date, doctor_name " +
                        "FROM inpatient_charges WHERE admission_id=? ORDER BY charge_date");
                psC.setInt(1, ipId);
                ResultSet rsC = psC.executeQuery();
                while (rsC.next()) {
                    BigDecimal amt = rsC.getBigDecimal("total_price");
                    total = total.add(amt != null ? amt : BigDecimal.ZERO);
                    results.add(new Object[]{
                            rsC.getInt("id"), rsC.getString("charge_type"),
                            rsC.getString("item_name"), rsC.getBigDecimal("quantity"),
                            rsC.getBigDecimal("unit_price"), amt,
                            rsC.getDate("charge_date").toLocalDate(),
                            rsC.getString("doctor_name")
                    });
                }
                return new Object[]{results, total};
            }
        }, result -> {
            @SuppressWarnings("unchecked")
            List<Object[]> data = (List<Object[]>) result[0];
            BigDecimal total = (BigDecimal) result[1];
            inpatientData.clear();
            inpatientData.addAll(data);
            inpatientTotalLabel.setText("住院总费用: ¥" + total.toString());
        });
    }

    private void settleInpatient() {
        String kw = inpatientSearchField.getText().trim();
        if (kw.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "请先查询患者").showAndWait();
            return;
        }
        String likeKw = "%" + kw + "%";

        AsyncUIUtil.executeAsync(inpatientTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement psIP = conn.prepareStatement(
                        "SELECT ir.id, ir.patient_id, ir.bed_id, p.name " +
                        "FROM inpatient_records ir " +
                        "JOIN patients p ON ir.patient_id = p.id " +
                        "WHERE p.name LIKE ? OR p.patient_no LIKE ? " +
                        "AND ir.status='在院' ORDER BY ir.id DESC LIMIT 1");
                psIP.setString(1, likeKw); psIP.setString(2, likeKw);
                ResultSet rsIP = psIP.executeQuery();
                if (!rsIP.next()) {
                    throw new BusinessException("未找到在院患者");
                }
                int ipId = rsIP.getInt(1);
                int pId = rsIP.getInt(2);
                int bedId = rsIP.getInt(3);
                String pName = rsIP.getString(4);

                PreparedStatement psTotal = conn.prepareStatement(
                        "SELECT COALESCE(SUM(total_price),0) FROM inpatient_charges WHERE admission_id=?");
                psTotal.setInt(1, ipId);
                ResultSet rsTotal = psTotal.executeQuery();
                BigDecimal totalCost = rsTotal.next() ? rsTotal.getBigDecimal(1) : BigDecimal.ZERO;

                conn.setAutoCommit(false);
                try {
                    String billNo = "BILL" + System.currentTimeMillis() % 100000000;
                    PreparedStatement psBill = conn.prepareStatement(
                            "INSERT INTO billing_records (bill_no, patient_id, patient_name, bill_type, " +
                            "admission_id, total_amount, paid_amount, payment_method, status, operator_id, operator_name) " +
                            "VALUES (?,?,?,'住院',?,?,0,'现金','待缴费',?,?)");
                    psBill.setString(1, billNo);
                    psBill.setInt(2, pId);
                    psBill.setString(3, pName);
                    psBill.setInt(4, ipId);
                    psBill.setBigDecimal(5, totalCost);
                    psBill.setInt(6, (int) session.getUserId());
                    psBill.setString(7, session.getUsername());
                    psBill.executeUpdate();

                    audit.log("CREATE", "billing_records", "?", "出院结算: billNo=" + billNo + ", total=" + totalCost);
                    conn.commit();
                    return new Object[]{billNo, totalCost};
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        }, result -> {
            String billNo = (String) result[0];
            BigDecimal totalCost = (BigDecimal) result[1];
            new Alert(Alert.AlertType.INFORMATION,
                    "出院结算单已创建！\n总费用: ¥" + totalCost + "\n票据号: " + billNo).showAndWait();
            inpatientData.clear();
            inpatientTotalLabel.setText("住院总费用: ¥0.00");
        });
    }
}
