package com.his.inpatient.ui;

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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 住院管理视图 - 生产级
 * T-3.3: 完善住院管理
 * 功能：入院登记、在院管理、床位管理、护理记录、手术记录、费用明细、审计日志
 */
public class InpatientView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(InpatientView.class);
    private final AuditService audit = AuditService.getInstance();
    private final UserSession session = UserSession.getInstance();

    // ========== Tab1: 入院管理 ==========
    private TableView<Object[]> admissionTable;
    private ObservableList<Object[]> admissionData = FXCollections.observableArrayList();
    private TextField admitSearchField;
    private ComboBox<String> admitStatusCombo;
    private int currentAdmissionId = -1;

    // 入院表单
    private TextField admitPatientField;
    private ComboBox<String> admitDeptCombo;
    private ComboBox<String> admitDoctorCombo;
    private ComboBox<String> admitBedCombo;
    private TextArea admitReasonArea;
    private TextArea admitDiagnosisArea;
    private CheckBox admitCriticalCb;

    // ========== Tab2: 护理记录 ==========
    private TableView<Object[]> nursingTable;
    private ObservableList<Object[]> nursingData = FXCollections.observableArrayList();
    private ComboBox<String> nursingTypeCombo;
    private TextArea nursingContentArea;
    private TextField nursingTempField;
    private TextField nursingPulseField;
    private TextField nursingBpHighField;
    private TextField nursingBpLowField;
    private TextField nursingSpo2Field;

    // ========== Tab3: 手术记录 ==========
    private TableView<Object[]> operationTable;
    private ObservableList<Object[]> operationData = FXCollections.observableArrayList();
    private TextField opNameField;
    private TextField opCodeField;
    private ComboBox<String> opSurgeonCombo;
    private TextField opAssistantsField;
    private ComboBox<String> opAnesthesiaCombo;
    private TextField opAnesthetistField;
    private DatePicker opDatePicker;
    private TextField opStartField;
    private TextField opEndField;

    // ========== Tab4: 费用明细 ==========
    private TableView<Object[]> costTable;
    private ObservableList<Object[]> costData = FXCollections.observableArrayList();
    private Label totalCostLabel;

    public InpatientView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab admitTab = new Tab("入院管理");
        admitTab.setContent(buildAdmissionTab());
        admitTab.setClosable(false);

        Tab nursingTab = new Tab("护理记录");
        nursingTab.setContent(buildNursingTab());
        nursingTab.setClosable(false);

        Tab opTab = new Tab("手术记录");
        opTab.setContent(buildOperationTab());
        opTab.setClosable(false);

        Tab costTab = new Tab("费用管理");
        costTab.setContent(buildCostTab());
        costTab.setClosable(false);

        getTabs().addAll(admitTab, nursingTab, opTab, costTab);
    }

    // ========================================================================
    // Tab1: 入院管理
    // ========================================================================

    private VBox buildAdmissionTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        admitSearchField = new TextField();
        admitSearchField.setPromptText("患者姓名/住院号...");
        admitSearchField.setPrefWidth(200);
        admitStatusCombo = new ComboBox<>();
        admitStatusCombo.getItems().addAll("全部", "在院", "已出院", "已取消");
        admitStatusCombo.setValue("全部");
        admitStatusCombo.setPrefWidth(120);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button newAdmitBtn = new Button("新入院");
        newAdmitBtn.getStyleClass().add("btn-success");
        searchRow.getChildren().addAll(new Label("搜索:"), admitSearchField,
                new Label("状态:"), admitStatusCombo, searchBtn, refreshBtn, newAdmitBtn);

        // ---- 住院记录表格 ----
        admissionTable = new TableView<>();
        admissionTable.getStyleClass().add("table-view");
        admissionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] admitCols = {"ID", "住院号", "患者", "科室", "床号", "主管医生", "入院日期", "状态", "危重"};
        for (int i = 0; i < admitCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(admitCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 0 ? 40 : i == 1 ? 110 : i == 2 ? 100 : i == 8 ? 50 : 80);
            admissionTable.getColumns().add(c);
        }
        admissionTable.setItems(admissionData);
        admissionTable.setPrefHeight(250);
        VBox.setVgrow(admissionTable, Priority.ALWAYS);

        // 双击加载详情
        admissionTable.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Object[] selected = row.getItem();
                    if (selected != null) {
                        currentAdmissionId = (int) selected[0];
                        loadAdmissionDetail(currentAdmissionId);
                    }
                }
            });
            return row;
        });

        // ---- 入院表单 ----
        Label formTitle = new Label("入院登记 / 详情");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(8);

        admitPatientField = new TextField();
        admitPatientField.setPromptText("患者姓名或病历号");
        admitPatientField.setPrefWidth(200);
        Button selectPatientBtn = new Button("选择患者");
        selectPatientBtn.getStyleClass().add("btn-outline");

        admitDeptCombo = new ComboBox<>();
        admitDeptCombo.setPrefWidth(150);
        admitDoctorCombo = new ComboBox<>();
        admitDoctorCombo.setPrefWidth(150);
        admitBedCombo = new ComboBox<>();
        admitBedCombo.setPrefWidth(150);

        // 加载科室
        loadDepartments(admitDeptCombo);
        admitDeptCombo.setOnAction(e -> loadDoctors(admitDeptCombo, admitDoctorCombo));

        admitReasonArea = new TextArea();
        admitReasonArea.setPromptText("入院原因");
        admitReasonArea.setPrefRowCount(2);
        admitDiagnosisArea = new TextArea();
        admitDiagnosisArea.setPromptText("入院诊断");
        admitDiagnosisArea.setPrefRowCount(2);
        admitCriticalCb = new CheckBox("危重患者");

        Button saveAdmitBtn = new Button("保存");
        saveAdmitBtn.getStyleClass().add("btn-primary");
        Button dischargeBtn = new Button("办理出院");
        dischargeBtn.getStyleClass().add("btn-warning");
        Button cancelAdmitBtn = new Button("取消入院");
        cancelAdmitBtn.getStyleClass().add("btn-danger");
        Button transferBtn = new Button("转床");
        transferBtn.getStyleClass().add("btn-outline");

        form.add(new Label("患者:"), 0, 0);
        form.add(admitPatientField, 1, 0);
        form.add(selectPatientBtn, 2, 0);
        form.add(new Label("科室:"), 3, 0);
        form.add(admitDeptCombo, 4, 0);
        form.add(new Label("主管医生:"), 5, 0);
        form.add(admitDoctorCombo, 6, 0);
        form.add(new Label("床位:"), 0, 1);
        form.add(admitBedCombo, 1, 1);
        form.add(transferBtn, 2, 1);
        form.add(new Label("入院原因:"), 0, 2);
        form.add(admitReasonArea, 1, 2, 6, 1);
        form.add(new Label("入院诊断:"), 0, 3);
        form.add(admitDiagnosisArea, 1, 3, 6, 1);
        form.add(admitCriticalCb, 0, 4);
        form.add(new HBox(10, saveAdmitBtn, dischargeBtn, cancelAdmitBtn), 0, 5, 7, 1);

        root.getChildren().addAll(searchRow, admissionTable, formTitle, form);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadAdmissions());
        refreshBtn.setOnAction(e -> loadAdmissions());
        newAdmitBtn.setOnAction(e -> showNewAdmissionDialog());
        selectPatientBtn.setOnAction(e -> showPatientSelectDialog(admitPatientField));
        saveAdmitBtn.setOnAction(e -> saveAdmission());
        dischargeBtn.setOnAction(e -> dischargePatient());
        cancelAdmitBtn.setOnAction(e -> cancelAdmission());
        transferBtn.setOnAction(e -> showTransferBedDialog());

        // 初始加载
        loadAdmissions();

        return root;
    }

    private void loadAdmissions() {
        String kw = admitSearchField.getText().trim();
        String status = admitStatusCombo.getValue();
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT ir.id, ir.admission_no, p.name, d.name, ir.bed_no, doc.name, ir.admission_date, ir.status, ir.is_critical " +
                    "FROM inpatient_records ir " +
                    "JOIN patients p ON ir.patient_id = p.id " +
                    "JOIN departments d ON ir.department_id = d.id " +
                    "LEFT JOIN doctors doc ON ir.doctor_id = doc.id " +
                    "WHERE 1=1 ");
            if (!kw.isEmpty()) {
                sql.append("AND (p.name LIKE ? OR p.patient_no LIKE ? OR ir.admission_no LIKE ?) ");
            }
            if (!"全部".equals(status)) {
                sql.append("AND ir.status = ? ");
            }
            sql.append("ORDER BY ir.id DESC LIMIT 200");

            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(idx++, likeKw);
                    ps.setString(idx++, likeKw);
                    ps.setString(idx++, likeKw);
                }
                if (!"全部".equals(status)) {
                    ps.setString(idx++, status);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("ir.id"), rs.getString("ir.admission_no"),
                            rs.getString("p.name"), rs.getString("d.name"),
                            rs.getString("ir.bed_no"),
                            rs.getString("doc.name") != null ? rs.getString("doc.name") : "",
                            rs.getDate("ir.admission_date").toLocalDate(),
                            rs.getString("ir.status"),
                            rs.getBoolean("ir.is_critical") ? "是" : "否"
                    });
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return data;
        }, data -> {
            admissionData.setAll(data);
        });
    }

    private void loadAdmissionDetail(int admissionId) {
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ir.patient_id, ir.department_id, ir.doctor_id, ir.bed_id, " +
                        "ir.admission_reason, ir.admission_diagnosis, ir.is_critical " +
                        "FROM inpatient_records ir WHERE ir.id = ?")) {
                    ps.setInt(1, admissionId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) return new ArrayList<>();

                    List<Object> result = new ArrayList<>();
                    int patientId = rs.getInt("patient_id");
                    int deptId = rs.getInt("department_id");
                    int doctorId = rs.getInt("doctor_id");
                    int bedId = rs.getInt("bed_id");
                    String reason = rs.getString("admission_reason");
                    String diagnosis = rs.getString("admission_diagnosis");
                    boolean isCritical = rs.getBoolean("is_critical");

                    result.add(patientId);
                    result.add(deptId);
                    result.add(doctorId);
                    result.add(bedId);
                    result.add(reason);
                    result.add(diagnosis);
                    result.add(isCritical);

                    // 加载患者信息
                    try (PreparedStatement psP = conn.prepareStatement(
                            "SELECT name, patient_no FROM patients WHERE id = ?")) {
                        psP.setInt(1, patientId);
                        ResultSet rsP = psP.executeQuery();
                        if (rsP.next()) {
                            result.add(rsP.getString("name") + " (" + rsP.getString("patient_no") + ")");
                        } else {
                            result.add("");
                        }
                    }

                    // 加载医生列表
                    List<String> doctorItems = new ArrayList<>();
                    try (PreparedStatement psDoc = conn.prepareStatement(
                            "SELECT id, name FROM doctors WHERE department_id = ? ORDER BY name")) {
                        psDoc.setInt(1, deptId);
                        ResultSet rsDoc = psDoc.executeQuery();
                        while (rsDoc.next()) {
                            doctorItems.add(rsDoc.getInt("id") + "|" + rsDoc.getString("name"));
                        }
                    }
                    result.add(doctorItems);

                    // 加载床位列表
                    List<String> bedItems = new ArrayList<>();
                    try (PreparedStatement psBed = conn.prepareStatement(
                            "SELECT id, bed_no, ward_name FROM beds WHERE department_id = ? AND status = '空闲' ORDER BY bed_no")) {
                        psBed.setInt(1, deptId);
                        ResultSet rsBed = psBed.executeQuery();
                        while (rsBed.next()) {
                            bedItems.add(rsBed.getInt("id") + "|" + rsBed.getString("bed_no") + " (" + rsBed.getString("ward_name") + ")");
                        }
                    }
                    result.add(bedItems);

                    return result;
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, result -> {
            if (result.isEmpty()) return;
            int deptId = (int) result.get(1);
            int doctorId = (int) result.get(2);
            int bedId = (int) result.get(3);
            String reason = (String) result.get(4);
            String diagnosis = (String) result.get(5);
            boolean isCritical = (boolean) result.get(6);
            String patientText = (String) result.get(7);
            @SuppressWarnings("unchecked")
            List<String> doctorItems = (List<String>) result.get(8);
            @SuppressWarnings("unchecked")
            List<String> bedItems = (List<String>) result.get(9);

            admitPatientField.setText(patientText);
            selectComboBoxItem(admitDeptCombo, deptId);

            admitDoctorCombo.getItems().setAll(doctorItems);
            selectComboBoxItem(admitDoctorCombo, doctorId);

            admitBedCombo.getItems().setAll(bedItems);
            selectComboBoxItem(admitBedCombo, bedId);

            admitReasonArea.setText(reason);
            admitDiagnosisArea.setText(diagnosis);
            admitCriticalCb.setSelected(isCritical);
        });
    }

    private void showNewAdmissionDialog() {
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("新入院登记");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField patientField = new TextField();
        patientField.setPromptText("患者姓名或病历号");
        Button selectBtn = new Button("选择");
        ComboBox<String> deptCombo = new ComboBox<>();
        deptCombo.setPrefWidth(150);
        ComboBox<String> doctorCombo = new ComboBox<>();
        doctorCombo.setPrefWidth(150);
        ComboBox<String> bedCombo = new ComboBox<>();
        bedCombo.setPrefWidth(150);

        loadDepartments(deptCombo);
        deptCombo.setOnAction(e -> {
            loadDoctors(deptCombo, doctorCombo);
            loadBeds(deptCombo, bedCombo);
        });

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("入院原因");
        reasonArea.setPrefRowCount(2);
        TextArea diagnosisArea = new TextArea();
        diagnosisArea.setPromptText("入院诊断");
        diagnosisArea.setPrefRowCount(2);
        CheckBox criticalCb = new CheckBox("危重患者");

        grid.add(new Label("患者:"), 0, 0);
        grid.add(patientField, 1, 0);
        grid.add(selectBtn, 2, 0);
        grid.add(new Label("科室:"), 0, 1);
        grid.add(deptCombo, 1, 1);
        grid.add(new Label("医生:"), 2, 1);
        grid.add(doctorCombo, 3, 1);
        grid.add(new Label("床位:"), 0, 2);
        grid.add(bedCombo, 1, 2);
        grid.add(new Label("原因:"), 0, 3);
        grid.add(reasonArea, 1, 3, 3, 1);
        grid.add(new Label("诊断:"), 0, 4);
        grid.add(diagnosisArea, 1, 4, 3, 1);
        grid.add(criticalCb, 0, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                // 验证并返还结果
                String patientStr = patientField.getText().trim();
                if (patientStr.isEmpty()) return null;
                int patientId = parseIdFromCombo(patientStr);
                if (patientId <= 0) return null;
                int deptId = parseIdFromCombo(deptCombo.getValue());
                int doctorId = parseIdFromCombo(doctorCombo.getValue());
                int bedId = parseIdFromCombo(bedCombo.getValue());
                return new int[]{patientId, deptId, doctorId, bedId};
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            int[] ids = result.get();
            createAdmission(ids[0], ids[1], ids[2], ids[3],
                    reasonArea.getText(), diagnosisArea.getText(), criticalCb.isSelected());
        }
    }

    private void createAdmission(int patientId, int deptId, int doctorId, int bedId,
                                String reason, String diagnosis, boolean critical) {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 获取患者、科室、医生、床位信息
                String patientName, deptName, doctorName, bedNo, wardName;
                try (PreparedStatement psP = conn.prepareStatement(
                        "SELECT name FROM patients WHERE id = ?")) {
                    psP.setInt(1, patientId);
                    ResultSet rsP = psP.executeQuery();
                    if (!rsP.next()) throw new BusinessException("患者不存在");
                    patientName = rsP.getString(1);
                }
                try (PreparedStatement psD = conn.prepareStatement(
                        "SELECT name FROM departments WHERE id = ?")) {
                    psD.setInt(1, deptId);
                    ResultSet rsD = psD.executeQuery();
                    if (!rsD.next()) throw new BusinessException("科室不存在");
                    deptName = rsD.getString(1);
                }
                try (PreparedStatement psDoc = conn.prepareStatement(
                        "SELECT name FROM doctors WHERE id = ?")) {
                    psDoc.setInt(1, doctorId);
                    ResultSet rsDoc = psDoc.executeQuery();
                    if (!rsDoc.next()) throw new BusinessException("医生不存在");
                    doctorName = rsDoc.getString(1);
                }
                try (PreparedStatement psB = conn.prepareStatement(
                        "SELECT bed_no, ward_name FROM beds WHERE id = ? AND status = '空闲'")) {
                    psB.setInt(1, bedId);
                    ResultSet rsB = psB.executeQuery();
                    if (!rsB.next()) throw new BusinessException("床位不可用");
                    bedNo = rsB.getString(1);
                    wardName = rsB.getString(2);
                }

                String admissionNo = "IP" + System.currentTimeMillis() % 1000000000;
                try (PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO inpatient_records (admission_no, patient_id, patient_name, " +
                        "department_id, department_name, doctor_id, doctor_name, bed_id, bed_no, ward_name, " +
                        "admission_date, admission_reason, admission_diagnosis, is_critical, status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_DATE,?,?,?,'在院')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    psIns.setString(1, admissionNo);
                    psIns.setInt(2, patientId);
                    psIns.setString(3, patientName);
                    psIns.setInt(4, deptId);
                    psIns.setString(5, deptName);
                    psIns.setInt(6, doctorId);
                    psIns.setString(7, doctorName);
                    psIns.setInt(8, bedId);
                    psIns.setString(9, bedNo);
                    psIns.setString(10, wardName);
                    psIns.setString(11, reason);
                    psIns.setString(12, diagnosis);
                    psIns.setBoolean(13, critical);
                    psIns.executeUpdate();

                    ResultSet keys = psIns.getGeneratedKeys();
                    if (keys.next()) currentAdmissionId = keys.getInt(1);
                }

                // 更新床位状态
                try (PreparedStatement psBed = conn.prepareStatement(
                        "UPDATE beds SET status='占用', current_patient_id=? WHERE id=?")) {
                    psBed.setInt(1, patientId);
                    psBed.setInt(2, bedId);
                    psBed.executeUpdate();
                }

                conn.commit();

                audit.log("CREATE", "inpatient_records", String.valueOf(currentAdmissionId),
                        "新入院: " + admissionNo + ", patient=" + patientName);

                new Alert(Alert.AlertType.INFORMATION, "入院登记成功！住院号: " + admissionNo).showAndWait();
                loadAdmissions();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            log.error("入院登记失败", ex);
            new Alert(Alert.AlertType.ERROR, "入院失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void saveAdmission() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择住院记录").showAndWait();
            return;
        }
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE inpatient_records SET admission_reason=?, admission_diagnosis=?, is_critical=? WHERE id=?");
            ps.setString(1, admitReasonArea.getText());
            ps.setString(2, admitDiagnosisArea.getText());
            ps.setBoolean(3, admitCriticalCb.isSelected());
            ps.setInt(4, currentAdmissionId);
            ps.executeUpdate();

            audit.log("UPDATE", "inpatient_records", String.valueOf(currentAdmissionId), "更新住院信息");
            new Alert(Alert.AlertType.INFORMATION, "保存成功").showAndWait();
            loadAdmissions();
        } catch (SQLException ex) {
            log.error("保存住院信息失败", ex);
            new Alert(Alert.AlertType.ERROR, "保存失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void dischargePatient() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择住院记录").showAndWait();
            return;
        }
        // 计算总费用和住院天数
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            PreparedStatement psInfo = conn.prepareStatement(
                    "SELECT ir.bed_id, ir.admission_date, p.name " +
                    "FROM inpatient_records ir JOIN patients p ON ir.patient_id = p.id WHERE ir.id = ?");
            psInfo.setInt(1, currentAdmissionId);
            ResultSet rsInfo = psInfo.executeQuery();
            if (!rsInfo.next()) throw new BusinessException("记录不存在");
            int bedId = rsInfo.getInt(1);
            Date admitDate = rsInfo.getDate(2);
            String patientName = rsInfo.getString(3);
            long days = ChronoUnit.DAYS.between(admitDate.toLocalDate(), LocalDate.now());

            // 计算总费用
            PreparedStatement psTotal = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_price),0) FROM inpatient_charges WHERE admission_id = ?");
            psTotal.setInt(1, currentAdmissionId);
            ResultSet rsTotal = psTotal.executeQuery();
            BigDecimal totalCost = rsTotal.next() ? rsTotal.getBigDecimal(1) : BigDecimal.ZERO;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "确认办理出院？\n患者: " + patientName + "\n住院天数: " + days + "\n总费用: ¥" + totalCost,
                    ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            conn.setAutoCommit(false);
            try {
                // 更新住院记录
                PreparedStatement psDisc = conn.prepareStatement(
                        "UPDATE inpatient_records SET status='已出院', discharge_date=CURRENT_DATE, " +
                        "days_of_stay=?, total_cost=? WHERE id=?");
                psDisc.setLong(1, days);
                psDisc.setBigDecimal(2, totalCost);
                psDisc.setInt(3, currentAdmissionId);
                psDisc.executeUpdate();

                // 释放床位
                PreparedStatement psBed = conn.prepareStatement(
                        "UPDATE beds SET status='空闲', current_patient_id=NULL WHERE id=?");
                psBed.setInt(1, bedId);
                psBed.executeUpdate();

                conn.commit();

                audit.log("UPDATE", "inpatient_records", String.valueOf(currentAdmissionId),
                        "办理出院: patient=" + patientName + ", totalCost=" + totalCost);
                new Alert(Alert.AlertType.INFORMATION, "出院办理成功！").showAndWait();
                loadAdmissions();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            log.error("办理出院失败", ex);
            new Alert(Alert.AlertType.ERROR, "出院失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void cancelAdmission() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择住院记录").showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认取消入院？", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            // 获取床位ID
            PreparedStatement psInfo = conn.prepareStatement(
                    "SELECT bed_id FROM inpatient_records WHERE id = ?");
            psInfo.setInt(1, currentAdmissionId);
            ResultSet rsInfo = psInfo.executeQuery();
            if (!rsInfo.next()) throw new BusinessException("记录不存在");
            int bedId = rsInfo.getInt(1);

            conn.setAutoCommit(false);
            try {
                // 更新状态
                PreparedStatement psUpd = conn.prepareStatement(
                        "UPDATE inpatient_records SET status='已取消' WHERE id=?");
                psUpd.setInt(1, currentAdmissionId);
                psUpd.executeUpdate();

                // 释放床位
                PreparedStatement psBed = conn.prepareStatement(
                        "UPDATE beds SET status='空闲', current_patient_id=NULL WHERE id=?");
                psBed.setInt(1, bedId);
                psBed.executeUpdate();

                conn.commit();
                audit.log("UPDATE", "inpatient_records", String.valueOf(currentAdmissionId), "取消入院");
                new Alert(Alert.AlertType.INFORMATION, "入院已取消").showAndWait();
                loadAdmissions();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            log.error("取消入院失败", ex);
            new Alert(Alert.AlertType.ERROR, "操作失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void showTransferBedDialog() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择住院记录").showAndWait();
            return;
        }
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("转床");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        HBox content = new HBox(10);
        content.setPadding(new Insets(20));
        ComboBox<String> newBedCombo = new ComboBox<>();
        newBedCombo.setPrefWidth(200);
        // 加载同科室的空床位
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT ir.department_id FROM inpatient_records ir WHERE ir.id = ?")) {
            ps.setInt(1, currentAdmissionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                loadBedsByDept(rs.getInt(1), newBedCombo);
            }
        } catch (SQLException ex) {
            log.error("加载床位失败", ex);
        }
        content.getChildren().addAll(new Label("新床位:"), newBedCombo);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ?
                parseIdFromCombo(newBedCombo.getValue()) : null);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() > 0) {
            transferBed(result.get());
        }
    }

    private void transferBed(int newBedId) {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 获取旧床位和患者信息
                PreparedStatement psOld = conn.prepareStatement(
                        "SELECT ir.bed_id, ir.patient_id FROM inpatient_records ir WHERE ir.id = ?");
                psOld.setInt(1, currentAdmissionId);
                ResultSet rsOld = psOld.executeQuery();
                if (!rsOld.next()) throw new BusinessException("记录不存在");
                int oldBedId = rsOld.getInt(1);
                int patientId = rsOld.getInt(2);

                // 检查新床位是否可用
                PreparedStatement psNew = conn.prepareStatement(
                        "SELECT bed_no FROM beds WHERE id = ? AND status = '空闲'");
                psNew.setInt(1, newBedId);
                ResultSet rsNew = psNew.executeQuery();
                if (!rsNew.next()) throw new BusinessException("新床位不可用");

                // 更新住院记录床位
                PreparedStatement psUpd = conn.prepareStatement(
                        "UPDATE inpatient_records SET bed_id=?, bed_no=(SELECT bed_no FROM beds WHERE id=?), ward_name=(SELECT ward_name FROM beds WHERE id=?) WHERE id=?");
                psUpd.setInt(1, newBedId);
                psUpd.setInt(2, newBedId);
                psUpd.setInt(3, newBedId);
                psUpd.setInt(4, currentAdmissionId);
                psUpd.executeUpdate();

                // 释放旧床位
                PreparedStatement psOldBed = conn.prepareStatement(
                        "UPDATE beds SET status='空闲', current_patient_id=NULL WHERE id=?");
                psOldBed.setInt(1, oldBedId);
                psOldBed.executeUpdate();

                // 占用新床位
                PreparedStatement psNewBed = conn.prepareStatement(
                        "UPDATE beds SET status='占用', current_patient_id=? WHERE id=?");
                psNewBed.setInt(1, patientId);
                psNewBed.setInt(2, newBedId);
                psNewBed.executeUpdate();

                conn.commit();
                audit.log("UPDATE", "inpatient_records", String.valueOf(currentAdmissionId),
                        "转床: oldBed=" + oldBedId + ", newBed=" + newBedId);
                new Alert(Alert.AlertType.INFORMATION, "转床成功").showAndWait();
                loadAdmissions();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            log.error("转床失败", ex);
            new Alert(Alert.AlertType.ERROR, "转床失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // Tab2: 护理记录
    // ========================================================================

    private VBox buildNursingTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部：选择住院记录 ----
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        TextField admField = new TextField();
        admField.setPromptText("住院号或患者姓名...");
        Button loadAdmBtn = new Button("加载");
        loadAdmBtn.getStyleClass().add("btn-primary");
        topRow.getChildren().addAll(new Label("住院记录:"), admField, loadAdmBtn);

        // ---- 护理记录表格 ----
        nursingTable = new TableView<>();
        nursingTable.getStyleClass().add("table-view");
        nursingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] ncCols = {"ID", "类型", "内容", "体温", "脉搏", "血压", "SpO2", "时间"};
        for (int i = 0; i < ncCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(ncCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 2 ? 200 : i == 7 ? 120 : 80);
            nursingTable.getColumns().add(c);
        }
        nursingTable.setItems(nursingData);
        nursingTable.setPrefHeight(250);
        VBox.setVgrow(nursingTable, Priority.ALWAYS);

        // ---- 护理记录表单 ----
        Label formTitle = new Label("新增护理记录");
        formTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(8);

        nursingTypeCombo = new ComboBox<>();
        nursingTypeCombo.getItems().addAll("体温", "血压", "脉搏", "血氧", "一般护理", "特殊护理");
        nursingTypeCombo.setValue("一般护理");
        nursingTypeCombo.setPrefWidth(120);
        nursingContentArea = new TextArea();
        nursingContentArea.setPromptText("护理内容");
        nursingContentArea.setPrefRowCount(2);
        nursingTempField = new TextField();
        nursingTempField.setPromptText("体温℃");
        nursingTempField.setPrefWidth(80);
        nursingPulseField = new TextField();
        nursingPulseField.setPromptText("脉搏");
        nursingPulseField.setPrefWidth(80);
        nursingBpHighField = new TextField();
        nursingBpHighField.setPromptText("收缩压");
        nursingBpHighField.setPrefWidth(80);
        nursingBpLowField = new TextField();
        nursingBpLowField.setPromptText("舒张压");
        nursingBpLowField.setPrefWidth(80);
        nursingSpo2Field = new TextField();
        nursingSpo2Field.setPromptText("SpO2%");
        nursingSpo2Field.setPrefWidth(80);

        Button saveNursingBtn = new Button("保存记录");
        saveNursingBtn.getStyleClass().add("btn-primary");

        form.add(new Label("类型:"), 0, 0);
        form.add(nursingTypeCombo, 1, 0);
        form.add(new Label("体温:"), 2, 0);
        form.add(nursingTempField, 3, 0);
        form.add(new Label("脉搏:"), 4, 0);
        form.add(nursingPulseField, 5, 0);
        form.add(new Label("血压:"), 0, 1);
        form.add(nursingBpHighField, 1, 1);
        form.add(new Label("/"), 2, 1);
        form.add(nursingBpLowField, 3, 1);
        form.add(new Label("SpO2:"), 4, 1);
        form.add(nursingSpo2Field, 5, 1);
        form.add(new Label("内容:"), 0, 2);
        form.add(nursingContentArea, 1, 2, 5, 1);
        form.add(saveNursingBtn, 0, 3, 6, 1);

        root.getChildren().addAll(topRow, nursingTable, formTitle, form);

        // ---- 事件绑定 ----
        loadAdmBtn.setOnAction(e -> {
            currentAdmissionId = -1;
            nursingData.clear();
            loadAdmissionForNursing(admField.getText().trim());
        });
        saveNursingBtn.setOnAction(e -> saveNursingRecord());

        return root;
    }

    private void loadAdmissionForNursing(String keyword) {
        if (keyword.isEmpty()) return;
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id FROM inpatient_records WHERE admission_no LIKE ? OR id IN " +
                         "(SELECT id FROM inpatient_records WHERE patient_id IN " +
                         "(SELECT id FROM patients WHERE name LIKE ? OR patient_no LIKE ?)) " +
                         "AND status = '在院' ORDER BY id DESC LIMIT 1")) {
                String likeKw = "%" + keyword + "%";
                ps.setString(1, likeKw); ps.setString(2, likeKw); ps.setString(3, likeKw);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return -1;
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, admissionId -> {
            if (admissionId > 0) {
                currentAdmissionId = admissionId;
                loadNursingRecords(currentAdmissionId);
            }
        });
    }

    private void loadNursingRecords(int admissionId) {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, nursing_type, content, vitals_temp, vitals_pulse, " +
                         "vitals_bp_high, vitals_bp_low, vitals_spo2, record_time " +
                         "FROM nursing_records WHERE admission_id = ? ORDER BY record_time DESC LIMIT 100")) {
                ps.setInt(1, admissionId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("nursing_type"), rs.getString("content"),
                            rs.getBigDecimal("vitals_temp"), rs.getInt("vitals_pulse"),
                            rs.getInt("vitals_bp_high") + "/" + rs.getInt("vitals_bp_low"),
                            rs.getInt("vitals_spo2"), rs.getTimestamp("record_time").toLocalDateTime()
                    });
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return data;
        }, data -> {
            nursingData.setAll(data);
        });
    }

    private void saveNursingRecord() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先加载住院记录").showAndWait();
            return;
        }
        try {
            String type = ValidationUtil.requireNonBlank(nursingTypeCombo.getValue(), "护理类型");
            String content = ValidationUtil.requireNonBlank(nursingContentArea.getText(), "护理内容");
            BigDecimal temp = nursingTempField.getText().isEmpty() ? null :
                    new BigDecimal(nursingTempField.getText());
            Integer pulse = nursingPulseField.getText().isEmpty() ? null :
                    Integer.parseInt(nursingPulseField.getText());
            Integer bpHigh = nursingBpHighField.getText().isEmpty() ? null :
                    Integer.parseInt(nursingBpHighField.getText());
            Integer bpLow = nursingBpLowField.getText().isEmpty() ? null :
                    Integer.parseInt(nursingBpLowField.getText());
            Integer spo2 = nursingSpo2Field.getText().isEmpty() ? null :
                    Integer.parseInt(nursingSpo2Field.getText());

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                // 获取患者ID和护士信息
                PreparedStatement psInfo = conn.prepareStatement(
                        "SELECT patient_id FROM inpatient_records WHERE id = ?");
                psInfo.setInt(1, currentAdmissionId);
                ResultSet rsInfo = psInfo.executeQuery();
                if (!rsInfo.next()) throw new BusinessException("住院记录不存在");
                int patientId = rsInfo.getInt(1);

                Integer nurseId = session.getUserId() > 0 ? (int) session.getUserId() : null;
                String nurseName = session.getUsername();

                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO nursing_records (admission_id, patient_id, nurse_id, nurse_name, " +
                        "nursing_type, content, vitals_temp, vitals_pulse, vitals_bp_high, vitals_bp_low, " +
                        "vitals_spo2, record_time) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)");
                psIns.setInt(1, currentAdmissionId);
                psIns.setInt(2, patientId);
                if (nurseId != null) psIns.setInt(3, nurseId); else psIns.setNull(3, Types.INTEGER);
                psIns.setString(4, nurseName);
                psIns.setString(5, type);
                psIns.setString(6, content);
                psIns.setBigDecimal(7, temp);
                psIns.setObject(8, pulse);
                psIns.setObject(9, bpHigh);
                psIns.setObject(10, bpLow);
                psIns.setObject(11, spo2);
                psIns.executeUpdate();

                audit.log("CREATE", "nursing_records", String.valueOf(currentAdmissionId),
                        "护理记录: type=" + type + ", content=" + content.substring(0, Math.min(50, content.length())));
                new Alert(Alert.AlertType.INFORMATION, "护理记录已保存").showAndWait();
                loadNursingRecords(currentAdmissionId);

                // 清空表单
                nursingContentArea.clear();
                nursingTempField.clear();
                nursingPulseField.clear();
                nursingBpHighField.clear();
                nursingBpLowField.clear();
                nursingSpo2Field.clear();
            }
        } catch (Exception ex) {
            log.error("保存护理记录失败", ex);
            new Alert(Alert.AlertType.ERROR, "保存失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // Tab3: 手术记录
    // ========================================================================

    private VBox buildOperationTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部：选择住院记录 ----
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        TextField admField = new TextField();
        admField.setPromptText("住院号或患者姓名...");
        Button loadAdmBtn = new Button("加载");
        loadAdmBtn.getStyleClass().add("btn-primary");
        topRow.getChildren().addAll(new Label("住院记录:"), admField, loadAdmBtn);

        // ---- 手术记录表格 ----
        operationTable = new TableView<>();
        operationTable.getStyleClass().add("table-view");
        operationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] opCols = {"ID", "手术名称", "手术编码", "主刀医生", "麻醉方式", "手术日期", "状态"};
        for (int i = 0; i < opCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(opCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 1 ? 150 : 100);
            operationTable.getColumns().add(c);
        }
        operationTable.setItems(operationData);
        operationTable.setPrefHeight(200);
        VBox.setVgrow(operationTable, Priority.ALWAYS);

        // ---- 手术记录表单 ----
        Label formTitle = new Label("手术记录");
        formTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(8);

        opNameField = new TextField();
        opNameField.setPromptText("手术名称");
        opNameField.setPrefWidth(250);
        opCodeField = new TextField();
        opCodeField.setPromptText("手术编码");
        opCodeField.setPrefWidth(120);
        opSurgeonCombo = new ComboBox<>();
        opSurgeonCombo.setPrefWidth(150);
        opAssistantsField = new TextField();
        opAssistantsField.setPromptText("助手姓名(逗号分隔)");
        opAssistantsField.setPrefWidth(200);
        opAnesthesiaCombo = new ComboBox<>();
        opAnesthesiaCombo.getItems().addAll("全身麻醉", "局部麻醉", "椎管内麻醉", "神经阻滞麻醉");
        opAnesthesiaCombo.setPrefWidth(120);
        opAnesthetistField = new TextField();
        opAnesthetistField.setPromptText("麻醉师");
        opAnesthetistField.setPrefWidth(120);
        opDatePicker = new DatePicker(LocalDate.now());
        opDatePicker.setPrefWidth(130);
        opStartField = new TextField();
        opStartField.setPromptText("开始时间 HH:mm");
        opStartField.setPrefWidth(100);
        opEndField = new TextField();
        opEndField.setPromptText("结束时间 HH:mm");
        opEndField.setPrefWidth(100);
        TextArea opNotesArea = new TextArea();
        opNotesArea.setPromptText("手术备注");
        opNotesArea.setPrefRowCount(2);

        Button saveOpBtn = new Button("保存手术记录");
        saveOpBtn.getStyleClass().add("btn-primary");

        form.add(new Label("手术名称:"), 0, 0);
        form.add(opNameField, 1, 0, 3, 1);
        form.add(new Label("编码:"), 4, 0);
        form.add(opCodeField, 5, 0);
        form.add(new Label("主刀:"), 0, 1);
        form.add(opSurgeonCombo, 1, 1);
        form.add(new Label("助手:"), 2, 1);
        form.add(opAssistantsField, 3, 1, 3, 1);
        form.add(new Label("麻醉:"), 0, 2);
        form.add(opAnesthesiaCombo, 1, 2);
        form.add(new Label("麻醉师:"), 2, 2);
        form.add(opAnesthetistField, 3, 2);
        form.add(new Label("日期:"), 4, 2);
        form.add(opDatePicker, 5, 2);
        form.add(new Label("开始:"), 0, 3);
        form.add(opStartField, 1, 3);
        form.add(new Label("结束:"), 2, 3);
        form.add(opEndField, 3, 3);
        form.add(new Label("备注:"), 0, 4);
        form.add(opNotesArea, 1, 4, 5, 1);
        form.add(saveOpBtn, 0, 5, 6, 1);

        root.getChildren().addAll(topRow, operationTable, formTitle, form);

        // ---- 事件绑定 ----
        loadAdmBtn.setOnAction(e -> {
            currentAdmissionId = -1;
            operationData.clear();
            loadAdmissionForOperation(admField.getText().trim());
        });
        saveOpBtn.setOnAction(e -> saveOperationRecord());

        return root;
    }

    private void loadAdmissionForOperation(String keyword) {
        if (keyword.isEmpty()) return;
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, patient_id FROM inpatient_records WHERE admission_no LIKE ? OR id IN " +
                         "(SELECT id FROM inpatient_records WHERE patient_id IN " +
                         "(SELECT id FROM patients WHERE name LIKE ? OR patient_no LIKE ?)) " +
                         "AND status = '在院' ORDER BY id DESC LIMIT 1")) {
                String likeKw = "%" + keyword + "%";
                ps.setString(1, likeKw); ps.setString(2, likeKw); ps.setString(3, likeKw);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new int[]{rs.getInt(1), rs.getInt(2)};
                }
                return new int[]{-1, -1};
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, result -> {
            if (result[0] > 0) {
                currentAdmissionId = result[0];
                loadOperationRecords(currentAdmissionId);
            }
        });
    }

    private void loadOperationRecords(int admissionId) {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, operation_name, operation_code, surgeon_name, anesthesia_type, " +
                         "operation_date, status " +
                         "FROM operation_records WHERE admission_id = ? ORDER BY operation_date DESC")) {
                ps.setInt(1, admissionId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("operation_name"), rs.getString("operation_code"),
                            rs.getString("surgeon_name"), rs.getString("anesthesia_type"),
                            rs.getDate("operation_date").toLocalDate(), rs.getString("status")
                    });
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return data;
        }, data -> {
            operationData.setAll(data);
        });
    }

    private void saveOperationRecord() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先加载住院记录").showAndWait();
            return;
        }
        try {
            String opName = ValidationUtil.requireNonBlank(opNameField.getText(), "手术名称");
            String opCode = opCodeField.getText();
            int surgeonId = parseIdFromCombo(opSurgeonCombo.getValue());
            String surgeonName = opSurgeonCombo.getValue() != null ?
                    opSurgeonCombo.getValue().substring(opSurgeonCombo.getValue().indexOf("]") + 1).trim() : "";
            String assistants = opAssistantsField.getText();
            String anesthesia = opAnesthesiaCombo.getValue();
            String anesthetist = opAnesthetistField.getText();
            LocalDate opDate = opDatePicker.getValue();

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement psInfo = conn.prepareStatement(
                        "SELECT patient_id, patient_name FROM inpatient_records WHERE id = ?");
                psInfo.setInt(1, currentAdmissionId);
                ResultSet rsInfo = psInfo.executeQuery();
                if (!rsInfo.next()) throw new BusinessException("住院记录不存在");
                int patientId = rsInfo.getInt(1);
                String patientName = rsInfo.getString(2);

                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO operation_records (admission_id, patient_id, patient_name, " +
                        "operation_name, operation_code, surgeon_id, surgeon_name, assistant_names, " +
                        "anesthesia_type, anesthetist_name, operation_date, status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,'已完成')",
                        Statement.RETURN_GENERATED_KEYS);
                psIns.setInt(1, currentAdmissionId);
                psIns.setInt(2, patientId);
                psIns.setString(3, patientName);
                psIns.setString(4, opName);
                psIns.setString(5, opCode.isEmpty() ? null : opCode);
                if (surgeonId > 0) psIns.setInt(6, surgeonId); else psIns.setNull(6, Types.INTEGER);
                psIns.setString(7, surgeonName);
                psIns.setString(8, assistants.isEmpty() ? null : assistants);
                psIns.setString(9, anesthesia);
                psIns.setString(10, anesthetist.isEmpty() ? null : anesthetist);
                psIns.setDate(11, Date.valueOf(opDate));
                psIns.executeUpdate();

                audit.log("CREATE", "operation_records", "?", "手术记录: " + opName);
                new Alert(Alert.AlertType.INFORMATION, "手术记录已保存").showAndWait();
                loadOperationRecords(currentAdmissionId);
            }
        } catch (Exception ex) {
            log.error("保存手术记录失败", ex);
            new Alert(Alert.AlertType.ERROR, "保存失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // Tab4: 费用管理
    // ========================================================================

    private VBox buildCostTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部：选择住院记录 ----
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        TextField admField = new TextField();
        admField.setPromptText("住院号或患者姓名...");
        Button loadAdmBtn = new Button("加载");
        loadAdmBtn.getStyleClass().add("btn-primary");
        Button addChargeBtn = new Button("添加费用");
        addChargeBtn.getStyleClass().add("btn-success");
        totalCostLabel = new Label("总费用: ¥0.00");
        totalCostLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        topRow.getChildren().addAll(new Label("住院记录:"), admField, loadAdmBtn, addChargeBtn, totalCostLabel);

        // ---- 费用明细表格 ----
        costTable = new TableView<>();
        costTable.getStyleClass().add("table-view");
        costTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] costCols = {"ID", "费用类型", "收费项目", "数量", "单价", "总价", "日期", "医生"};
        for (int i = 0; i < costCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(costCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 2 ? 150 : 80);
            costTable.getColumns().add(c);
        }
        costTable.setItems(costData);
        costTable.setPrefHeight(300);
        VBox.setVgrow(costTable, Priority.ALWAYS);

        root.getChildren().addAll(topRow, costTable);

        // ---- 事件绑定 ----
        loadAdmBtn.setOnAction(e -> {
            currentAdmissionId = -1;
            costData.clear();
            loadAdmissionForCost(admField.getText().trim());
        });
        addChargeBtn.setOnAction(e -> showAddChargeDialog());

        return root;
    }

    private void loadAdmissionForCost(String keyword) {
        if (keyword.isEmpty()) return;
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id FROM inpatient_records WHERE admission_no LIKE ? OR id IN " +
                         "(SELECT id FROM inpatient_records WHERE patient_id IN " +
                         "(SELECT id FROM patients WHERE name LIKE ? OR patient_no LIKE ?)) " +
                         "ORDER BY id DESC LIMIT 1")) {
                String likeKw = "%" + keyword + "%";
                ps.setString(1, likeKw); ps.setString(2, likeKw); ps.setString(3, likeKw);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return -1;
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, admissionId -> {
            if (admissionId > 0) {
                currentAdmissionId = admissionId;
                loadCostRecords(currentAdmissionId);
            }
        });
    }

    private void loadCostRecords(int admissionId) {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, charge_type, item_name, quantity, unit_price, total_price, charge_date, doctor_name " +
                         "FROM inpatient_charges WHERE admission_id = ? ORDER BY charge_date DESC LIMIT 200")) {
                ps.setInt(1, admissionId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    BigDecimal amt = rs.getBigDecimal("total_price");
                    total = total.add(amt != null ? amt : BigDecimal.ZERO);
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("charge_type"), rs.getString("item_name"),
                            rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                            amt, rs.getDate("charge_date").toLocalDate(), rs.getString("doctor_name")
                    });
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return new Object[]{data, total};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<Object[]> data = (List<Object[]>) ((Object[]) result)[0];
            BigDecimal total = (BigDecimal) ((Object[]) result)[1];
            costData.setAll(data);
            totalCostLabel.setText("总费用: ¥" + total.toString());
        });
    }

    private void showAddChargeDialog() {
        if (currentAdmissionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先加载住院记录").showAndWait();
            return;
        }
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("添加费用");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("床位费", "诊疗费", "检查费", "药品费", "手术费", "护理费等");
        typeCombo.setValue("药品费");
        TextField itemField = new TextField();
        itemField.setPromptText("收费项目");
        itemField.setPrefWidth(200);
        TextField qtyField = new TextField();
        qtyField.setPromptText("数量");
        qtyField.setText("1");
        TextField priceField = new TextField();
        priceField.setPromptText("单价");

        grid.add(new Label("类型:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("项目:"), 0, 1);
        grid.add(itemField, 1, 1, 2, 1);
        grid.add(new Label("数量:"), 0, 2);
        grid.add(qtyField, 1, 2);
        grid.add(new Label("单价:"), 2, 2);
        grid.add(priceField, 3, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? new int[]{0} : null);

        var result = dialog.showAndWait();
        if (result.isPresent()) {
            addChargeRecord(typeCombo.getValue(), itemField.getText(), qtyField.getText(), priceField.getText());
        }
    }

    private void addChargeRecord(String type, String item, String qtyStr, String priceStr) {
        try {
            String itemName = ValidationUtil.requireNonBlank(item, "收费项目");
            BigDecimal qty = new BigDecimal(ValidationUtil.requireNonBlank(qtyStr, "数量"));
            BigDecimal price = new BigDecimal(ValidationUtil.requireNonBlank(priceStr, "单价"));
            BigDecimal totalPrice = qty.multiply(price);

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement psInfo = conn.prepareStatement(
                        "SELECT patient_id FROM inpatient_records WHERE id = ?");
                psInfo.setInt(1, currentAdmissionId);
                ResultSet rsInfo = psInfo.executeQuery();
                if (!rsInfo.next()) throw new BusinessException("住院记录不存在");
                int patientId = rsInfo.getInt(1);

                String doctorName = session.getUsername();

                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO inpatient_charges (admission_id, patient_id, charge_type, item_name, " +
                        "quantity, unit_price, total_price, charge_date, doctor_name) " +
                        "VALUES (?,?,?,?,?,?,?,CURRENT_DATE,?)");
                psIns.setInt(1, currentAdmissionId);
                psIns.setInt(2, patientId);
                psIns.setString(3, type);
                psIns.setString(4, itemName);
                psIns.setBigDecimal(5, qty);
                psIns.setBigDecimal(6, price);
                psIns.setBigDecimal(7, totalPrice);
                psIns.setString(8, doctorName);
                psIns.executeUpdate();

                audit.log("CREATE", "inpatient_charges", String.valueOf(currentAdmissionId),
                        "添加费用: " + type + " - " + itemName + ", amount=" + totalPrice);
                loadCostRecords(currentAdmissionId);
            }
        } catch (Exception ex) {
            log.error("添加费用失败", ex);
            new Alert(Alert.AlertType.ERROR, "添加失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private void loadDepartments(ComboBox<String> combo) {
        combo.getItems().clear();
        AsyncUIUtil.executeAsync(() -> {
            List<String> items = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, name FROM departments ORDER BY name")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(rs.getInt("id") + "|" + rs.getString("name"));
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return items;
        }, items -> {
            combo.getItems().setAll(items);
        });
    }

    private void loadDoctors(ComboBox<String> deptCombo, ComboBox<String> doctorCombo) {
        doctorCombo.getItems().clear();
        if (deptCombo.getValue() == null) return;
        int deptId = parseIdFromCombo(deptCombo.getValue());
        AsyncUIUtil.executeAsync(() -> {
            List<String> items = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, name FROM doctors WHERE department_id = ? ORDER BY name")) {
                ps.setInt(1, deptId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(rs.getInt("id") + "|" + rs.getString("name"));
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return items;
        }, items -> {
            doctorCombo.getItems().setAll(items);
        });
    }

    private void loadBeds(ComboBox<String> deptCombo, ComboBox<String> bedCombo) {
        bedCombo.getItems().clear();
        if (deptCombo.getValue() == null) return;
        int deptId = parseIdFromCombo(deptCombo.getValue());
        loadBedsByDept(deptId, bedCombo);
    }

    private void loadBedsByDept(int deptId, ComboBox<String> bedCombo) {
        bedCombo.getItems().clear();
        AsyncUIUtil.executeAsync(() -> {
            List<String> items = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, bed_no, ward_name FROM beds WHERE department_id = ? AND status = '空闲' ORDER BY bed_no")) {
                ps.setInt(1, deptId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(rs.getInt("id") + "|" + rs.getString("bed_no") + " (" + rs.getString("ward_name") + ")");
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return items;
        }, items -> {
            bedCombo.getItems().setAll(items);
        });
    }

    private int parseIdFromCombo(String value) {
        if (value == null || value.isEmpty()) return -1;
        int idx = value.indexOf("|");
        return idx > 0 ? Integer.parseInt(value.substring(0, idx)) : -1;
    }

    private void selectComboBoxItem(ComboBox<String> combo, int id) {
        for (String item : combo.getItems()) {
            if (item.startsWith(id + "|")) {
                combo.setValue(item);
                return;
            }
        }
    }

    private void showPatientSelectDialog(TextField targetField) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("选择患者");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        TextField searchField = new TextField();
        searchField.setPromptText("搜索患者姓名或病历号...");
        TableView<Object[]> table = new TableView<>();
        table.getStyleClass().add("table-view");
        String[] cols = {"ID", "姓名", "病历号", "身份证"};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 0 ? 40 : 100);
            table.getColumns().add(c);
        }
        ObservableList<Object[]> data = FXCollections.observableArrayList();
        table.setItems(data);
        table.setPrefHeight(200);

        Button searchBtn = new Button("搜索");
        searchBtn.getStyleClass().add("btn-primary");
        searchBtn.setOnAction(e -> {
            data.clear();
            String kw = searchField.getText().trim();
            if (kw.isEmpty()) return;
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, name, patient_no, id_card FROM patients WHERE name LIKE ? OR patient_no LIKE ? OR id_card LIKE ? LIMIT 50")) {
                String likeKw = "%" + kw + "%";
                ps.setString(1, likeKw); ps.setString(2, likeKw); ps.setString(3, likeKw);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{rs.getInt("id"), rs.getString("name"),
                            rs.getString("patient_no"), rs.getString("id_card")});
                }
            } catch (SQLException ex) {
                log.error("搜索患者失败", ex);
            }
        });

        content.getChildren().addAll(new HBox(10, searchField, searchBtn), table);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Object[] selected = table.getSelectionModel().getSelectedItem();
                return selected != null ? selected[1] + " (" + selected[2] + ")" : null;
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            targetField.setText(result.get());
        }
    }
}
