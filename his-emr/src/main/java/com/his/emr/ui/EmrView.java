package com.his.emr.ui;

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

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 电子病历视图 - 生产级
 * T-3.6: 完善电子病历
 * 功能：病历书写、病历查询、ICD-10诊断编码、过敏史、记录锁定、审计日志
 */
public class EmrView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(EmrView.class);
    private final AuditService audit = AuditService.getInstance();
    private final UserSession session = UserSession.getInstance();

    // ========== Tab1: 病历书写 ==========
    private TextField patientSearchField;
    private TextArea chiefComplaint, presentIllness, pastHistory, allergyHistory, physicalExam, auxiliaryExam, diagnosis, treatmentPlan;
    private ComboBox<String> recordTypeCombo;
    private TableView<Object[]> recordsTable;
    private ObservableList<Object[]> recordsData = FXCollections.observableArrayList();
    private int currentRecordId = -1;
    private boolean recordLocked = false;

    // ========== Tab2: 病历查询 ==========
    private TableView<Object[]> queryTable;
    private ObservableList<Object[]> queryData = FXCollections.observableArrayList();

    // ========== ICD-10 对话框 ==========
    private Dialog<Object[]> icdDialog;
    private TableView<Object[]> icdTable;
    private ObservableList<Object[]> icdData = FXCollections.observableArrayList();
    private TextField diagnosisCodeField; // 用于回写诊断编码

    public EmrView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab writeTab = new Tab("病历书写");
        writeTab.setContent(buildWriteTab());
        writeTab.setClosable(false);

        Tab queryTab = new Tab("病历查询");
        queryTab.setContent(buildQueryTab());
        queryTab.setClosable(false);

        getTabs().addAll(writeTab, queryTab);
    }

    // ========================================================================
    // Tab1: 病历书写
    // ========================================================================

    private VBox buildWriteTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部：患者搜索 ----
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        patientSearchField = new TextField();
        patientSearchField.setPromptText("输入患者姓名或病历号...");
        patientSearchField.setPrefWidth(250);
        Button searchBtn = new Button("查询患者");
        searchBtn.getStyleClass().add("btn-primary");
        Button resetBtn = new Button("新建病历");
        resetBtn.getStyleClass().add("btn-success");
        Button lockBtn = new Button("锁定病历");
        lockBtn.getStyleClass().add("btn-warning");
        topRow.getChildren().addAll(new Label("患者:"), patientSearchField, searchBtn, resetBtn, lockBtn);

        // ---- 病历类型 ----
        HBox typeRow = new HBox(10);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        recordTypeCombo = new ComboBox<>();
        recordTypeCombo.getItems().addAll("门诊病历", "入院记录", "病程记录", "出院小结", "手术记录");
        recordTypeCombo.setValue("门诊病历");
        recordTypeCombo.setPrefWidth(180);
        typeRow.getChildren().addAll(new Label("病历类型:"), recordTypeCombo);

        // ---- 病历表单 ----
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(10);

        chiefComplaint = createTextArea("主诉");
        presentIllness = createTextArea("现病史");
        pastHistory = createTextArea("既往史");
        allergyHistory = createTextArea("过敏史");
        physicalExam = createTextArea("体格检查");
        auxiliaryExam = createTextArea("辅助检查");
        diagnosis = createTextArea("诊断");
        treatmentPlan = createTextArea("治疗计划");

        // 诊断编码选择按钮
        HBox diagnosisRow = new HBox(10);
        diagnosisCodeField = new TextField();
        diagnosisCodeField.setPromptText("ICD-10编码 (点击选择)");
        diagnosisCodeField.setPrefWidth(200);
        diagnosisCodeField.setEditable(false);
        Button selectIcdBtn = new Button("选择ICD-10");
        selectIcdBtn.getStyleClass().add("btn-outline");
        diagnosisRow.getChildren().addAll(new Label("ICD编码:"), diagnosisCodeField, selectIcdBtn);

        TextArea[] areas = {chiefComplaint, presentIllness, pastHistory, allergyHistory, physicalExam, auxiliaryExam, diagnosis, treatmentPlan};
        String[] labels = {"主诉:", "现病史:", "既往史:", "过敏史:", "体格检查:", "辅助检查:", "诊断:", "治疗计划:"};

        for (int i = 0; i < labels.length; i++) {
            Label l = new Label(labels[i]);
            l.getStyleClass().add("form-label");
            form.add(l, 0, i);
            form.add(areas[i], 1, i);
            GridPane.setHgrow(areas[i], Priority.ALWAYS);
        }
        // 在诊断行下面添加ICD编码行
        form.add(diagnosisRow, 1, 6);

        // ---- 保存按钮 ----
        Button saveBtn = new Button("保存病历");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setPrefWidth(120);

        // ---- 已有记录表 ----
        recordsTable = createRecordsTable();
        recordsTable.setItems(recordsData);
        recordsTable.setPrefHeight(150);

        VBox.setVgrow(form, Priority.ALWAYS);
        root.getChildren().addAll(topRow, typeRow, form, saveBtn, new Label("该患者历史病历:"), recordsTable);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadPatientRecords(patientSearchField.getText()));
        resetBtn.setOnAction(e -> clearForm());
        saveBtn.setOnAction(e -> saveRecord());
        lockBtn.setOnAction(e -> lockRecord());
        selectIcdBtn.setOnAction(e -> showIcd10Dialog(diagnosisCodeField));

        return root;
    }

    private TextArea createTextArea(String prompt) {
        TextArea ta = new TextArea();
        ta.setPromptText(prompt);
        ta.setPrefRowCount(2);
        ta.setPrefWidth(500);
        return ta;
    }

    private TableView<Object[]> createRecordsTable() {
        TableView<Object[]> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] cols = {"ID", "病历编号", "类型", "诊断", "日期", "状态", "锁定"};
        int[] widths = {40, 120, 100, 200, 90, 70, 50};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(widths[i]);
            table.getColumns().add(c);
        }
        // 双击加载
        table.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Object[] selected = row.getItem();
                    if (selected != null) {
                        currentRecordId = (int) selected[0];
                        loadRecordDetail(currentRecordId);
                    }
                }
            });
            return row;
        });
        return table;
    }

    private void loadPatientRecords(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;
        AsyncUIUtil.executeAsync(recordsTable, () -> {
            List<Object[]> results = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, record_no, record_type, diagnosis, visit_date, " +
                         "CASE WHEN is_locked THEN '已锁定' ELSE '可编辑' END as lock_status " +
                         "FROM medical_records " +
                         "WHERE patient_id = (SELECT id FROM patients WHERE name=? OR patient_no=? LIMIT 1) " +
                         "ORDER BY id DESC LIMIT 20")) {
                ps.setString(1, keyword);
                ps.setString(2, keyword);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(new Object[]{
                            rs.getInt("id"), rs.getString("record_no"),
                            rs.getString("record_type"), rs.getString("diagnosis"),
                            rs.getDate("visit_date").toLocalDate(),
                            rs.getString("lock_status")
                    });
                }
            }
            return results;
        }, results -> {
            recordsData.clear();
            recordsData.addAll(results);
        });
    }

    private void loadRecordDetail(int recordId) {
        AsyncUIUtil.executeAsync(recordsTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT record_no, record_type, chief_complaint, present_illness, past_history, " +
                         "allergy_history, physical_exam, auxiliary_exam, diagnosis, treatment_plan, " +
                         "is_locked, visit_date " +
                         "FROM medical_records WHERE id = ?")) {
                ps.setInt(1, recordId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new Object[]{
                            rs.getString("record_type"),
                            rs.getString("chief_complaint"),
                            rs.getString("present_illness"),
                            rs.getString("past_history"),
                            rs.getString("allergy_history"),
                            rs.getString("physical_exam"),
                            rs.getString("auxiliary_exam"),
                            rs.getString("diagnosis"),
                            rs.getString("treatment_plan"),
                            rs.getBoolean("is_locked")
                    };
                }
            }
            return null;
        }, result -> {
            if (result == null) return;
            recordTypeCombo.setValue((String) result[0]);
            chiefComplaint.setText((String) result[1]);
            presentIllness.setText((String) result[2]);
            pastHistory.setText((String) result[3]);
            allergyHistory.setText((String) result[4]);
            physicalExam.setText((String) result[5]);
            auxiliaryExam.setText((String) result[6]);
            diagnosis.setText((String) result[7]);
            treatmentPlan.setText((String) result[8]);
            recordLocked = (boolean) result[9];
            if (recordLocked) {
                new Alert(Alert.AlertType.WARNING, "该病历已锁定，仅供查看").showAndWait();
            }
        });
    }

    private void clearForm() {
        for (TextArea ta : new TextArea[]{chiefComplaint, presentIllness, pastHistory,
                allergyHistory, physicalExam, auxiliaryExam, diagnosis, treatmentPlan}) {
            ta.clear();
        }
        diagnosisCodeField.clear();
        patientSearchField.clear();
        recordsData.clear();
        currentRecordId = -1;
        recordLocked = false;
    }

    private void saveRecord() {
        if (recordLocked) {
            new Alert(Alert.AlertType.WARNING, "病历已锁定，无法保存").showAndWait();
            return;
        }
        String patient = patientSearchField.getText().trim();
        if (patient.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "请输入患者信息").showAndWait();
            return;
        }
        String type = recordTypeCombo.getValue();
        String cc = chiefComplaint.getText();
        String pi = presentIllness.getText();
        String ph = pastHistory.getText();
        String ah = allergyHistory.getText();
        String pe = physicalExam.getText();
        String ae = auxiliaryExam.getText();
        String dx = diagnosis.getText();
        String tp = treatmentPlan.getText();
        final int[] saveRecordId = {currentRecordId};

        AsyncUIUtil.executeAsync(recordsTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                // 查找患者
                PreparedStatement psP = conn.prepareStatement(
                        "SELECT id, name FROM patients WHERE name=? OR patient_no=? LIMIT 1");
                psP.setString(1, patient);
                psP.setString(2, patient);
                ResultSet rsP = psP.executeQuery();
                if (!rsP.next()) throw new BusinessException("未找到患者");
                int pId = rsP.getInt(1);
                String pName = rsP.getString(2);

                int dId = (int) session.getUserId();
                String dName = session.getUsername();
                String deptName = session.getDepartmentName();

                String recordNo;
                if (saveRecordId[0] <= 0) {
                    recordNo = "EMR" + System.currentTimeMillis() % 100000000;
                    PreparedStatement psIns = conn.prepareStatement(
                            "INSERT INTO medical_records (record_no, patient_id, patient_name, " +
                            "record_type, department_name, doctor_id, doctor_name, " +
                            "chief_complaint, present_illness, past_history, allergy_history, " +
                            "physical_exam, auxiliary_exam, diagnosis, treatment_plan, visit_date) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_DATE)",
                            Statement.RETURN_GENERATED_KEYS);
                    psIns.setString(1, recordNo);
                    psIns.setInt(2, pId);
                    psIns.setString(3, pName);
                    psIns.setString(4, type);
                    psIns.setString(5, deptName);
                    psIns.setInt(6, dId);
                    psIns.setString(7, dName);
                    psIns.setString(8, cc);
                    psIns.setString(9, pi);
                    psIns.setString(10, ph);
                    psIns.setString(11, ah);
                    psIns.setString(12, pe);
                    psIns.setString(13, ae);
                    psIns.setString(14, dx);
                    psIns.setString(15, tp);
                    psIns.executeUpdate();
                    ResultSet keys = psIns.getGeneratedKeys();
                    if (keys.next()) saveRecordId[0] = keys.getInt(1);
                } else {
                    PreparedStatement psUpd = conn.prepareStatement(
                            "UPDATE medical_records SET record_type=?, chief_complaint=?, present_illness=?, " +
                            "past_history=?, allergy_history=?, physical_exam=?, auxiliary_exam=?, " +
                            "diagnosis=?, treatment_plan=? WHERE id=? AND is_locked=FALSE");
                    psUpd.setString(1, type);
                    psUpd.setString(2, cc);
                    psUpd.setString(3, pi);
                    psUpd.setString(4, ph);
                    psUpd.setString(5, ah);
                    psUpd.setString(6, pe);
                    psUpd.setString(7, ae);
                    psUpd.setString(8, dx);
                    psUpd.setString(9, tp);
                    psUpd.setInt(10, saveRecordId[0]);
                    int updated = psUpd.executeUpdate();
                    if (updated == 0) throw new BusinessException("病历已锁定或不存在");
                }
                audit.log("UPDATE", "medical_records", String.valueOf(saveRecordId[0]), "保存病历: type=" + type);
                return saveRecordId[0];
            }
        }, newId -> {
            currentRecordId = newId;
            new Alert(Alert.AlertType.INFORMATION, "病历保存成功！").showAndWait();
            loadPatientRecords(patientSearchField.getText());
        });
    }

    private void lockRecord() {
        if (currentRecordId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择或保存病历").showAndWait();
            return;
        }
        AsyncUIUtil.executeAsync(recordsTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE medical_records SET is_locked=TRUE, locked_at=CURRENT_TIMESTAMP WHERE id=? AND is_locked=FALSE");
                ps.setInt(1, currentRecordId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new BusinessException("病历已锁定或不存在");
                }
                audit.log("UPDATE", "medical_records", String.valueOf(currentRecordId), "锁定病历");
            }
            return true;
        }, ok -> {
            recordLocked = true;
            new Alert(Alert.AlertType.INFORMATION, "病历已锁定").showAndWait();
        });
    }

    // ========================================================================
    // Tab2: 病历查询
    // ========================================================================

    private VBox buildQueryTab() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(0));

        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        TextField keywordField = new TextField();
        keywordField.setPromptText("患者姓名或病历类型...");
        keywordField.setPrefWidth(250);
        Button searchBtn = new Button("搜索");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        searchRow.getChildren().addAll(new Label("搜索:"), keywordField, searchBtn, refreshBtn);

        queryTable = new TableView<>();
        queryTable.getStyleClass().add("table-view");
        queryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] cols = {"ID", "病历编号", "患者", "医生", "类型", "诊断", "日期", "状态", "锁定"};
        int[] widths = {40, 110, 100, 80, 100, 200, 90, 70, 50};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(widths[i]);
            queryTable.getColumns().add(c);
        }

        queryTable.setItems(queryData);
        VBox.setVgrow(queryTable, Priority.ALWAYS);

        root.getChildren().addAll(searchRow, queryTable);

        Runnable loadAll = () -> {
            String sql = """
                    SELECT m.id, m.record_no, p.name as pname, d.name as dname,
                           m.record_type, m.diagnosis, m.visit_date,
                           CASE WHEN m.is_locked THEN '已锁定' ELSE '可编辑' END as lock_status
                    FROM medical_records m
                    JOIN patients p ON m.patient_id = p.id
                    JOIN doctors d ON m.doctor_id = d.id
                    ORDER BY m.id DESC LIMIT 100""";
            AsyncUIUtil.executeAsync(queryTable, () -> {
                List<Object[]> results = new ArrayList<>();
                try (Connection conn = ConnectionPool.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        results.add(new Object[]{
                                rs.getInt("id"), rs.getString("record_no"),
                                rs.getString("pname"), rs.getString("dname"),
                                rs.getString("record_type"), rs.getString("diagnosis"),
                                rs.getDate("visit_date").toLocalDate(),
                                rs.getString("lock_status")
                        });
                    }
                }
                return results;
            }, results -> {
                queryData.clear();
                queryData.addAll(results);
            });
        };

        searchBtn.setOnAction(e -> {
            String kw = "%" + keywordField.getText() + "%";
            AsyncUIUtil.executeAsync(queryTable, () -> {
                List<Object[]> results = new ArrayList<>();
                try (Connection conn = ConnectionPool.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT m.id, m.record_no, p.name as pname, d.name as dname, " +
                             "m.record_type, m.diagnosis, m.visit_date, " +
                             "CASE WHEN m.is_locked THEN '已锁定' ELSE '可编辑' END as lock_status " +
                             "FROM medical_records m JOIN patients p ON m.patient_id=p.id " +
                             "JOIN doctors d ON m.doctor_id=d.id " +
                             "WHERE p.name LIKE ? OR m.record_type LIKE ? " +
                             "ORDER BY m.id DESC LIMIT 100")) {
                    ps.setString(1, kw);
                    ps.setString(2, kw);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        results.add(new Object[]{
                                rs.getInt("id"), rs.getString("record_no"),
                                rs.getString("pname"), rs.getString("dname"),
                                rs.getString("record_type"), rs.getString("diagnosis"),
                                rs.getDate("visit_date").toLocalDate(),
                                rs.getString("lock_status")
                        });
                    }
                }
                return results;
            }, results -> {
                queryData.clear();
                queryData.addAll(results);
            });
        });

        refreshBtn.setOnAction(e -> loadAll.run());
        loadAll.run();

        return root;
    }

    // ========================================================================
    // ICD-10 选择对话框
    // ========================================================================

    private void showIcd10Dialog(TextField targetField) {
        if (icdDialog == null) {
            icdDialog = new Dialog<>();
            icdDialog.setTitle("选择ICD-10诊断编码");
            icdDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            TextField searchIcd = new TextField();
            searchIcd.setPromptText("搜索ICD-10编码或名称...");
            Button searchBtn = new Button("搜索");
            searchBtn.getStyleClass().add("btn-primary");

            icdTable = new TableView<>();
            icdTable.getStyleClass().add("table-view");
            String[] cols = {"编码", "名称", "分类"};
            int[] widths = {80, 200, 150};
            for (int i = 0; i < cols.length; i++) {
                TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
                final int idx = i;
                c.setCellValueFactory(d -> {
                    Object v = d.getValue()[idx];
                    return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
                });
                c.setPrefWidth(widths[i]);
                icdTable.getColumns().add(c);
            }
            icdTable.setItems(icdData);
            icdTable.setPrefHeight(300);

            searchBtn.setOnAction(e -> {
                String kw = searchIcd.getText().trim();
                if (kw.isEmpty()) return;
                AsyncUIUtil.executeAsync(icdTable, () -> {
                    List<Object[]> results = new ArrayList<>();
                    try (Connection conn = ConnectionPool.getInstance().getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "SELECT code, name, category FROM icd10_codes WHERE code LIKE ? OR name LIKE ? LIMIT 100")) {
                        String likeKw = "%" + kw + "%";
                        ps.setString(1, likeKw);
                        ps.setString(2, likeKw);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            results.add(new Object[]{rs.getString("code"), rs.getString("name"), rs.getString("category")});
                        }
                    }
                    return results;
                }, results -> {
                    icdData.clear();
                    icdData.addAll(results);
                });
            });

            content.getChildren().addAll(new HBox(10, searchIcd, searchBtn), icdTable);
            icdDialog.getDialogPane().setContent(content);
            icdDialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    Object[] selected = icdTable.getSelectionModel().getSelectedItem();
                    return selected != null ? selected : null;
                }
                return null;
            });
        }

        var result = icdDialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            Object[] selected = result.get();
            targetField.setText((String) selected[0] + " " + (String) selected[1]);
        }
    }
}
