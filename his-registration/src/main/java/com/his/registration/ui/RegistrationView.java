package com.his.registration.ui;

import com.his.auth.AuditService;
import com.his.auth.UserSession;
import com.his.shared.database.ConnectionPool;
import com.his.shared.exception.BusinessException;
import com.his.shared.exception.ValidationException;
import com.his.shared.ui.AsyncUIUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 挂号管理视图（生产级）
 *
 * 功能：
 * - 患者管理：新增/编辑/删除患者，支持身份证18位校验
 * - 号源管理：按医生+日期+时段控制号源配额
 * - 挂号操作：普通号/专家号/急诊绿色通道
 * - 退号处理：已挂号→已退号状态流转
 * - 多条件组合查询：日期/科室/状态/患者名/挂号类型
 * - 操作审计：所有写操作异步记录
 * - 排队号自动生成
 */
public class RegistrationView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(RegistrationView.class);

    // ───── 表格数据 ─────
    private TableView<Object[]> patientTable;
    private TableView<Object[]> registrationTable;
    private final ObservableList<Object[]> patientData      = FXCollections.observableArrayList();
    private final ObservableList<Object[]> registrationData = FXCollections.observableArrayList();
    private final ObservableList<Object[]> slotData         = FXCollections.observableArrayList();

    // ───── 缓存 ─────
    private final AuditService audit = AuditService.getInstance();

    /** 身份证权重因子 */
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK  = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    public RegistrationView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab patientTab = new Tab("患者管理");
        patientTab.setContent(buildPatientTab());
        patientTab.setClosable(false);

        Tab regTab = new Tab("挂号管理");
        regTab.setContent(buildRegistrationTab());
        regTab.setClosable(false);

        Tab slotTab = new Tab("号源管理");
        slotTab.setContent(buildSlotTab());
        slotTab.setClosable(false);

        getTabs().addAll(patientTab, regTab, slotTab);

        // 初始加载
        refreshPatientTable();
    }

    // ═══════════════════════════════════════════════════════════
    //  患者管理
    // ═══════════════════════════════════════════════════════════

    private VBox buildPatientTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(0));

        // 搜索栏
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("输入姓名/电话/身份证号搜索患者...");
        searchField.setPrefWidth(300);

        Button searchBtn  = new Button("搜索");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button addBtn     = new Button("+ 新增患者");
        addBtn.getStyleClass().add("btn-success");

        searchBar.getChildren().addAll(searchField, searchBtn, refreshBtn, addBtn);

        // 表格
        patientTable = createPatientTable();
        patientTable.setItems(patientData);
        VBox.setVgrow(patientTable, Priority.ALWAYS);

        // 操作按钮
        HBox actions = new HBox(10);
        Button editBtn   = new Button("编辑");
        editBtn.getStyleClass().add("btn-primary");
        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("btn-danger");

        actions.getChildren().addAll(editBtn, deleteBtn);

        root.getChildren().addAll(searchBar, patientTable, actions);

        // 事件
        searchBtn.setOnAction(e  -> searchPatients(searchField.getText()));
        refreshBtn.setOnAction(e -> refreshPatientTable());
        addBtn.setOnAction(e     -> showPatientDialog(null));
        editBtn.setOnAction(e -> {
            Object[] selected = patientTable.getSelectionModel().getSelectedItem();
            if (selected != null) showPatientDialog(selected);
            else showAlert("提示", "请先选择一条患者记录", Alert.AlertType.WARNING);
        });
        deleteBtn.setOnAction(e -> {
            Object[] selected = patientTable.getSelectionModel().getSelectedItem();
            if (selected != null) deletePatient((int) selected[0], (String) selected[3]);
            else showAlert("提示", "请先选择一条患者记录", Alert.AlertType.WARNING);
        });

        // 回车搜索
        searchField.setOnAction(e -> searchPatients(searchField.getText()));

        return root;
    }

    @SuppressWarnings("unchecked")
    private TableView<Object[]> createPatientTable() {
        TableView<Object[]> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "病历号", "姓名", "性别", "年龄", "电话", "身份证号", "医保类型", "急诊联系人", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            TableColumn<Object[], String> col = new TableColumn<>(headers[i]);
            final int idx = i;
            col.setCellValueFactory(data -> {
                Object val = data.getValue()[idx];
                String str = val != null ? val.toString() : "";
                return new javafx.beans.property.SimpleStringProperty(str);
            });
            if (i == 0) col.setPrefWidth(40);
            if (i == 1) col.setPrefWidth(100);
            if (i == 7) col.setPrefWidth(100);
            table.getColumns().add(col);
        }
        return table;
    }

    private void refreshPatientTable() {
        AsyncUIUtil.executeAsync(this, () -> {
            List<Object[]> data = new ArrayList<>();
            String sql = """
                    SELECT id, patient_no, name, gender, age,
                           phone, id_card, medical_insurance_type,
                           emergency_contact, created_at
                    FROM patients
                    ORDER BY id DESC
                    LIMIT 200""";
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("patient_no"),
                            rs.getString("name"), rs.getString("gender"),
                            rs.getInt("age"), rs.getString("phone"),
                            rs.getString("id_card"), rs.getString("medical_insurance_type"),
                            rs.getString("emergency_contact"),
                            rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    : ""
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            patientData.setAll(data);
        });
    }

    private void searchPatients(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            refreshPatientTable();
            return;
        }
        String kw = "%" + keyword.trim() + "%";
        AsyncUIUtil.executeAsync(this, () -> {
            List<Object[]> data = new ArrayList<>();
            String sql = """
                    SELECT id, patient_no, name, gender, age,
                           phone, id_card, medical_insurance_type,
                           emergency_contact, created_at
                    FROM patients
                    WHERE name LIKE ? OR phone LIKE ? OR id_card LIKE ? OR patient_no LIKE ?
                    ORDER BY id DESC
                    LIMIT 200""";
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, kw);
                ps.setString(2, kw);
                ps.setString(3, kw);
                ps.setString(4, kw);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("patient_no"),
                            rs.getString("name"), rs.getString("gender"),
                            rs.getInt("age"), rs.getString("phone"),
                            rs.getString("id_card"), rs.getString("medical_insurance_type"),
                            rs.getString("emergency_contact"),
                            rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    : ""
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            patientData.setAll(data);
        });
    }

    private void showPatientDialog(Object[] existingData) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(existingData == null ? "新增患者" : "编辑患者");
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField nameField       = new TextField();
        nameField.setPromptText("必填");
        ComboBox<String> genderBox = new ComboBox<>();
        genderBox.getItems().addAll("男", "女");
        genderBox.setValue("男");
        genderBox.setPrefWidth(150);
        DatePicker birthPicker   = new DatePicker();
        birthPicker.setPromptText("出生日期");
        birthPicker.setPrefWidth(150);
        TextField ageField       = new TextField();
        ageField.setPromptText("自动/手动");
        ageField.setPrefWidth(80);
        TextField phoneField     = new TextField();
        phoneField.setPromptText("手机号");
        TextField idCardField    = new TextField();
        idCardField.setPromptText("18位身份证号");
        TextField addressField   = new TextField();
        addressField.setPromptText("家庭住址");
        ComboBox<String> bloodBox = new ComboBox<>();
        bloodBox.getItems().addAll("A", "B", "AB", "O");
        bloodBox.setPrefWidth(150);
        TextField allergyField   = new TextField();
        allergyField.setPromptText("药物/食物过敏史");
        ComboBox<String> insuranceBox = new ComboBox<>();
        insuranceBox.getItems().addAll("自费", "城镇职工医保", "城乡居民医保", "新农合", "商业保险");
        insuranceBox.setValue("自费");
        insuranceBox.setPrefWidth(150);
        TextField insuranceNoField = new TextField();
        insuranceNoField.setPromptText("医保卡号");
        TextField emerContactField  = new TextField();
        emerContactField.setPromptText("紧急联系人");
        TextField emerPhoneField    = new TextField();
        emerPhoneField.setPromptText("紧急联系电话");

        // 出生日期变更时自动计算年龄
        birthPicker.setOnAction(e -> {
            if (birthPicker.getValue() != null) {
                long ageYears = ChronoUnit.YEARS.between(birthPicker.getValue(), LocalDate.now());
                ageField.setText(String.valueOf(ageYears));
            }
        });

        String[] labels = {"姓名:", "性别:", "出生日期:", "年龄:", "电话:", "身份证号:",
                "地址:", "血型:", "过敏史:", "医保类型:", "医保卡号:", "紧急联系人:", "紧急电话:"};
        Control[] fields = {nameField, genderBox, birthPicker, ageField, phoneField, idCardField,
                addressField, bloodBox, allergyField, insuranceBox, insuranceNoField,
                emerContactField, emerPhoneField};

        for (int i = 0; i < labels.length; i++) {
            Label l = new Label(labels[i]);
            l.getStyleClass().add("form-label");
            grid.add(l, 0, i);
            grid.add(fields[i], 1, i);
        }

        dialog.getDialogPane().setContent(grid);
        ButtonType saveBtn = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return false;
            try {
                // 验证必填项
                String name = nameField.getText();
                if (name == null || name.trim().isEmpty())
                    throw new ValidationException("姓名 不能为空");

                // 身份证校验（T-3.1.1）
                String idCard = idCardField.getText();
                if (idCard != null && !idCard.trim().isEmpty()) {
                    String result = validateIdCard(idCard.trim());
                    if (result != null) throw new ValidationException(result);
                }

                try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                    if (existingData == null) {
                        // 新增
                        String patientNo = "P" + System.currentTimeMillis() % 100000000;
                        String sql = """
                                INSERT INTO patients (patient_no, name, gender, birth_date, age,
                                    phone, id_card, address, blood_type, allergy_info,
                                    medical_insurance_type, medical_insurance_no,
                                    emergency_contact, emergency_phone)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
                        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, patientNo);
                        ps.setString(2, name.trim());
                        ps.setString(3, genderBox.getValue());
                        ps.setDate(4, birthPicker.getValue() != null
                                ? Date.valueOf(birthPicker.getValue()) : null);
                        ps.setInt(5, safeParseInt(ageField.getText()));
                        ps.setString(6, phoneField.getText());
                        ps.setString(7, idCard);
                        ps.setString(8, addressField.getText());
                        ps.setString(9, bloodBox.getValue());
                        ps.setString(10, allergyField.getText());
                        ps.setString(11, insuranceBox.getValue());
                        ps.setString(12, insuranceNoField.getText());
                        ps.setString(13, emerContactField.getText());
                        ps.setString(14, emerPhoneField.getText());
                        ps.executeUpdate();

                        // 获取生成ID
                        ResultSet gk = ps.getGeneratedKeys();
                        int newId = gk.next() ? gk.getInt(1) : 0;

                        audit.log(AuditService.ACTION_CREATE, "patients",
                                String.valueOf(newId), "新增患者: " + name.trim() + " (病历号:" + patientNo + ")");

                    } else {
                        // 编辑
                        int patientId = (int) existingData[0];
                        String sql = """
                                UPDATE patients SET name=?, gender=?, birth_date=?, age=?,
                                    phone=?, id_card=?, address=?, blood_type=?, allergy_info=?,
                                    medical_insurance_type=?, medical_insurance_no=?,
                                    emergency_contact=?, emergency_phone=?
                                WHERE id=?""";
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setString(1, name.trim());
                        ps.setString(2, genderBox.getValue());
                        ps.setDate(3, birthPicker.getValue() != null
                                ? Date.valueOf(birthPicker.getValue()) : null);
                        ps.setInt(4, safeParseInt(ageField.getText()));
                        ps.setString(5, phoneField.getText());
                        ps.setString(6, idCard);
                        ps.setString(7, addressField.getText());
                        ps.setString(8, bloodBox.getValue());
                        ps.setString(9, allergyField.getText());
                        ps.setString(10, insuranceBox.getValue());
                        ps.setString(11, insuranceNoField.getText());
                        ps.setString(12, emerContactField.getText());
                        ps.setString(13, emerPhoneField.getText());
                        ps.setInt(14, patientId);
                        ps.executeUpdate();

                        audit.log(AuditService.ACTION_UPDATE, "patients",
                                String.valueOf(patientId), "编辑患者: " + name.trim());
                    }
                }

                refreshPatientTable();
                return true;

            } catch (ValidationException ve) {
                showAlert("验证失败", ve.getMessage(), Alert.AlertType.WARNING);
                return false;
            } catch (Exception ex) {
                log.error("保存患者失败", ex);
                showAlert("错误", "保存失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                return false;
            }
        });

        // 回填编辑数据（异步加载）
        if (existingData != null) {
            final int patientId = (int) existingData[0];
            AsyncUIUtil.executeAsync(() -> {
                Object[] patientDetails = new Object[13];
                try (Connection conn = ConnectionPool.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT * FROM patients WHERE id = ?")) {
                    ps.setInt(1, patientId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        patientDetails[0] = rs.getString("name");
                        patientDetails[1] = rs.getString("gender");
                        patientDetails[2] = rs.getDate("birth_date") != null
                                ? rs.getDate("birth_date").toLocalDate() : null;
                        patientDetails[3] = rs.getInt("age") > 0 ? String.valueOf(rs.getInt("age")) : "";
                        patientDetails[4] = rs.getString("phone");
                        patientDetails[5] = rs.getString("id_card");
                        patientDetails[6] = rs.getString("address");
                        patientDetails[7] = rs.getString("blood_type");
                        patientDetails[8] = rs.getString("allergy_info");
                        patientDetails[9] = rs.getString("medical_insurance_type");
                        patientDetails[10] = rs.getString("medical_insurance_no");
                        patientDetails[11] = rs.getString("emergency_contact");
                        patientDetails[12] = rs.getString("emergency_phone");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return patientDetails;
            }, details -> {
                if (details[0] != null) {
                    nameField.setText((String) details[0]);
                    genderBox.setValue((String) details[1]);
                    if (details[2] != null) birthPicker.setValue((LocalDate) details[2]);
                    ageField.setText((String) details[3]);
                    phoneField.setText((String) details[4]);
                    idCardField.setText((String) details[5]);
                    addressField.setText((String) details[6]);
                    bloodBox.setValue((String) details[7]);
                    allergyField.setText((String) details[8]);
                    insuranceBox.setValue((String) details[9]);
                    insuranceNoField.setText((String) details[10]);
                    emerContactField.setText((String) details[11]);
                    emerPhoneField.setText((String) details[12]);
                }
                dialog.showAndWait();
            });
        } else {
            dialog.showAndWait();
        }
    }

    private void deletePatient(int id, String name) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("确定要删除患者「" + name + "」吗？");
        confirm.setContentText("此操作不可撤销，相关挂号记录也将受到影响！");
        confirm.getDialogPane().getStyleClass().add("alert");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM patients WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
                audit.log(AuditService.ACTION_DELETE, "patients",
                        String.valueOf(id), "删除患者: " + name);
                refreshPatientTable();
                showAlert("成功", "患者已删除", Alert.AlertType.INFORMATION);
            } catch (SQLException e) {
                log.error("删除患者失败", e);
                showAlert("错误", "删除失败: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  挂号管理
    // ═══════════════════════════════════════════════════════════

    private VBox buildRegistrationTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(0));

        // ── 挂号表单 ──
        HBox formRow = new HBox(10);
        formRow.setAlignment(Pos.CENTER_LEFT);

        TextField patientSearchField = new TextField();
        patientSearchField.setPromptText("患者姓名/病历号...");
        patientSearchField.setPrefWidth(180);

        ComboBox<String> deptCombo = new ComboBox<>();
        deptCombo.setPromptText("科室");
        deptCombo.setPrefWidth(140);

        ComboBox<String> doctorCombo = new ComboBox<>();
        doctorCombo.setPromptText("医生");
        doctorCombo.setPrefWidth(120);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(120);

        ComboBox<String> timeSlotCombo = new ComboBox<>();
        timeSlotCombo.getItems().addAll("上午", "下午", "晚上");
        timeSlotCombo.setValue("上午");
        timeSlotCombo.setPrefWidth(80);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("普通", "专家", "急诊");
        typeCombo.setValue("普通");
        typeCombo.setPrefWidth(80);

        CheckBox emergencyCb  = new CheckBox("绿色通道");
        emergencyCb.getStyleClass().add("checkbox");

        Button regBtn = new Button("挂号");
        regBtn.getStyleClass().add("btn-primary");

        formRow.getChildren().addAll(
                new Label("患者:"), patientSearchField,
                new Label("科室:"), deptCombo,
                new Label("医生:"), doctorCombo,
                new Label("日期:"), datePicker,
                new Label("时段:"), timeSlotCombo,
                new Label("类型:"), typeCombo,
                emergencyCb, regBtn
        );

        // ── 查询过滤栏 ──
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        DatePicker filterDateFrom = new DatePicker();
        filterDateFrom.setPromptText("开始日期");
        filterDateFrom.setPrefWidth(120);
        DatePicker filterDateTo   = new DatePicker();
        filterDateTo.setPromptText("结束日期");
        filterDateTo.setPrefWidth(120);

        ComboBox<String> filterStatus = new ComboBox<>();
        filterStatus.getItems().addAll("全部", "待就诊", "已就诊", "已退号");
        filterStatus.setValue("全部");
        filterStatus.setPrefWidth(100);

        TextField filterPatient = new TextField();
        filterPatient.setPromptText("患者名...");
        filterPatient.setPrefWidth(130);

        ComboBox<String> filterType = new ComboBox<>();
        filterType.getItems().addAll("全部", "普通", "专家", "急诊");
        filterType.setValue("全部");
        filterType.setPrefWidth(80);

        Button filterBtn    = new Button("筛选");
        filterBtn.getStyleClass().add("btn-primary");
        Button filterClear  = new Button("清除");
        filterClear.getStyleClass().add("btn-outline");

        filterBar.getChildren().addAll(
                new Label("日期:"), filterDateFrom, filterDateTo,
                new Label("状态:"), filterStatus,
                new Label("患者:"), filterPatient,
                new Label("类型:"), filterType,
                filterBtn, filterClear
        );

        // ── 挂号记录表格 ──
        registrationTable = createRegistrationTable();
        registrationTable.setItems(registrationData);
        VBox.setVgrow(registrationTable, Priority.ALWAYS);

        // ── 操作按钮 ──
        HBox regActions = new HBox(10);
        Button cancelRegBtn  = new Button("退号");
        cancelRegBtn.getStyleClass().add("btn-danger");
        Button refreshRegBtn = new Button("刷新");
        refreshRegBtn.getStyleClass().add("btn-outline");
        Button printBtn      = new Button("打印");
        printBtn.getStyleClass().add("btn-secondary");

        regActions.getChildren().addAll(cancelRegBtn, refreshRegBtn, printBtn);

        root.getChildren().addAll(formRow, filterBar, registrationTable, regActions);

        // ── 加载科室 ──
        loadDepartments(deptCombo);
        deptCombo.setOnAction(e -> loadDoctors(doctorCombo, deptCombo.getValue()));

        // ── 挂号 ──
        regBtn.setOnAction(e -> doRegistration(
                patientSearchField.getText(), deptCombo.getValue(),
                doctorCombo.getValue(), datePicker.getValue(),
                timeSlotCombo.getValue(), typeCombo.getValue(),
                emergencyCb.isSelected()));

        // ── 退号 ──
        cancelRegBtn.setOnAction(e -> {
            Object[] selected = registrationTable.getSelectionModel().getSelectedItem();
            if (selected != null) cancelRegistration((int) selected[0], (String) selected[2]);
            else showAlert("提示", "请先选择一条挂号记录", Alert.AlertType.WARNING);
        });

        // ── 查询筛选 ──
        filterBtn.setOnAction(e -> filterRegistrations(
                filterDateFrom.getValue(), filterDateTo.getValue(),
                filterStatus.getValue(), filterPatient.getText(), filterType.getValue()));
        filterClear.setOnAction(e -> {
            filterDateFrom.setValue(null);
            filterDateTo.setValue(null);
            filterStatus.setValue("全部");
            filterPatient.clear();
            filterType.setValue("全部");
            refreshRegistrationTable();
        });
        refreshRegBtn.setOnAction(e -> refreshRegistrationTable());

        // 回车挂号
        patientSearchField.setOnAction(e -> doRegistration(
                patientSearchField.getText(), deptCombo.getValue(),
                doctorCombo.getValue(), datePicker.getValue(),
                timeSlotCombo.getValue(), typeCombo.getValue(),
                emergencyCb.isSelected()));

        refreshRegistrationTable();

        return root;
    }

    @SuppressWarnings("unchecked")
    private TableView<Object[]> createRegistrationTable() {
        TableView<Object[]> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "挂号号", "患者", "医生", "科室", "日期", "时段",
                "类型", "排队号", "费用", "状态"};
        for (int i = 0; i < headers.length; i++) {
            TableColumn<Object[], String> col = new TableColumn<>(headers[i]);
            final int idx = i;
            col.setCellValueFactory(data -> {
                Object val = data.getValue()[idx];
                String str = val != null ? val.toString() : "";
                return new javafx.beans.property.SimpleStringProperty(str);
            });
            if (i == 0) col.setPrefWidth(40);
            if (i == 1) col.setPrefWidth(120);
            if (i == 8) col.setPrefWidth(60);
            table.getColumns().add(col);
        }

        // 行颜色：急诊标红
        table.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.length > 7 && "急诊".equals(newVal[7])) {
                    row.setStyle("-fx-background-color: #fff3e0;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });

        return table;
    }

    private void refreshRegistrationTable() {
        AsyncUIUtil.executeAsync(this, () -> {
            List<Object[]> data = new ArrayList<>();
            String sql = """
                    SELECT id, registration_no, patient_name, doctor_name,
                           department_name, visit_date, visit_time_slot,
                           visit_type, queue_no, registration_fee, status
                    FROM registrations
                    ORDER BY id DESC
                    LIMIT 200""";
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"),
                            rs.getString("registration_no"),
                            rs.getString("patient_name"),
                            rs.getString("doctor_name"),
                            rs.getString("department_name"),
                            rs.getDate("visit_date").toLocalDate(),
                            rs.getString("visit_time_slot"),
                            rs.getString("visit_type"),
                            rs.getObject("queue_no"),
                            rs.getBigDecimal("registration_fee"),
                            rs.getString("status")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            registrationData.setAll(data);
        });
    }

    private void filterRegistrations(LocalDate dateFrom, LocalDate dateTo,
                                     String status, String patientName, String regType) {
        registrationData.clear();

        StringBuilder sql = new StringBuilder("""
                SELECT id, registration_no, patient_name, doctor_name,
                       department_name, visit_date, visit_time_slot,
                       visit_type, queue_no, registration_fee, status
                FROM registrations WHERE 1=1""");

        if (dateFrom != null) sql.append(" AND visit_date >= ?");
        if (dateTo != null)   sql.append(" AND visit_date <= ?");
        if (status != null && !"全部".equals(status)) sql.append(" AND status = ?");
        if (patientName != null && !patientName.trim().isEmpty()) sql.append(" AND patient_name LIKE ?");
        if (regType != null && !"全部".equals(regType)) sql.append(" AND visit_type = ?");

        sql.append(" ORDER BY id DESC LIMIT 200");

        final String finalSql = sql.toString();

        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(finalSql)) {
                int idx = 1;
                if (dateFrom != null) ps.setDate(idx++, Date.valueOf(dateFrom));
                if (dateTo != null)   ps.setDate(idx++, Date.valueOf(dateTo));
                if (status != null && !"全部".equals(status)) ps.setString(idx++, status);
                if (patientName != null && !patientName.trim().isEmpty())
                    ps.setString(idx++, "%" + patientName.trim() + "%");
                if (regType != null && !"全部".equals(regType)) ps.setString(idx++, regType);

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"),
                            rs.getString("registration_no"),
                            rs.getString("patient_name"),
                            rs.getString("doctor_name"),
                            rs.getString("department_name"),
                            rs.getDate("visit_date").toLocalDate(),
                            rs.getString("visit_time_slot"),
                            rs.getString("visit_type"),
                            rs.getObject("queue_no"),
                            rs.getBigDecimal("registration_fee"),
                            rs.getString("status")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            registrationData.setAll(data);
        });
    }

    private void loadDepartments(ComboBox<String> combo) {
        AsyncUIUtil.executeAsync(() -> {
            List<String> depts = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT name FROM departments WHERE is_active = true ORDER BY id")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    depts.add(rs.getString("name"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return depts;
        }, depts -> {
            combo.getItems().setAll(depts);
        });
    }

    private void loadDoctors(ComboBox<String> combo, String deptName) {
        combo.getItems().clear();
        if (deptName == null) return;
        final String dept = deptName;
        AsyncUIUtil.executeAsync(() -> {
            List<String> doctors = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT d.name FROM doctors d " +
                                 "JOIN departments dept ON d.department_id = dept.id " +
                                 "WHERE dept.name = ? AND d.is_active = true")) {
                ps.setString(1, dept);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    doctors.add(rs.getString("name"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return doctors;
        }, doctors -> {
            combo.getItems().setAll(doctors);
        });
    }

    /**
     * 执行挂号操作
     * 包含：号源检查、排队号生成、急诊绿色通道、审计日志
     */
    private void doRegistration(String patientKeyword, String deptName, String doctorName,
                                LocalDate date, String timeSlot, String regType,
                                boolean isEmergency) {
        try {
            if (patientKeyword == null || patientKeyword.trim().isEmpty())
                throw new BusinessException("请先输入患者信息");
            if (deptName == null)
                throw new BusinessException("请选择科室");
            if (doctorName == null)
                throw new BusinessException("请选择医生");

            // 急诊强制设置类型
            if (isEmergency) regType = "急诊";

            Connection conn = ConnectionPool.getInstance().getConnection();
            conn.setAutoCommit(false);
            try {
                // 1. 查找患者
                PreparedStatement psPatient = conn.prepareStatement(
                        "SELECT id, name FROM patients WHERE name = ? OR patient_no = ? LIMIT 1");
                psPatient.setString(1, patientKeyword.trim());
                psPatient.setString(2, patientKeyword.trim());
                ResultSet rsPatient = psPatient.executeQuery();
                if (!rsPatient.next()) {
                    throw new BusinessException("未找到患者: " + patientKeyword);
                }
                int patientId   = rsPatient.getInt("id");
                String patientName = rsPatient.getString("name");

                // 2. 查找医生
                PreparedStatement psDoctor = conn.prepareStatement(
                        "SELECT d.id, d.department_id FROM doctors d WHERE d.name = ? AND d.is_active = true LIMIT 1");
                psDoctor.setString(1, doctorName);
                ResultSet rsDoctor = psDoctor.executeQuery();
                if (!rsDoctor.next()) {
                    throw new BusinessException("未找到医生: " + doctorName);
                }
                int doctorId = rsDoctor.getInt("id");
                int deptId   = rsDoctor.getInt("department_id");

                // 3. 号源检查（T-3.1.2）
                if (!checkAppointmentSlot(conn, doctorId, date, timeSlot, regType, isEmergency)) {
                    throw new BusinessException("该时段号源已满，无法挂号！");
                }

                // 4. 生成挂号编号
                String regNo = "REG" + System.currentTimeMillis() % 100000000;

                // 5. 生成排队号
                int queueNo = generateQueueNo(conn, doctorId, date, timeSlot);

                // 6. 计算费用
                BigDecimal fee;
                if (isEmergency)      fee = new BigDecimal("20.00");
                else if ("专家".equals(regType)) fee = new BigDecimal("15.00");
                else                  fee = new BigDecimal("10.00");

                // 7. 插入挂号记录
                String sql = """
                        INSERT INTO registrations (registration_no, patient_id, patient_name,
                            department_id, department_name, doctor_id, doctor_name,
                            visit_type, is_emergency, visit_date, visit_time_slot,
                            queue_no, registration_fee, status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '待就诊', ?)""";

                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, regNo);
                ps.setInt(2, patientId);
                ps.setString(3, patientName);
                ps.setInt(4, deptId);
                ps.setString(5, deptName);
                ps.setInt(6, doctorId);
                ps.setString(7, doctorName);
                ps.setString(8, regType);
                ps.setBoolean(9, isEmergency);
                ps.setDate(10, Date.valueOf(date));
                ps.setString(11, timeSlot);
                ps.setInt(12, queueNo);
                ps.setBigDecimal(13, fee);
                ps.setString(14, UserSession.getInstance().getUsername());
                ps.executeUpdate();

                // 8. 扣减号源
                deductSlot(conn, doctorId, date, timeSlot, regType);

                conn.commit();

                // 审计
                String auditDetail = String.format("挂号成功: %s → %s(%s) %s %s %s, ¥%s",
                        patientName, doctorName, deptName, date, timeSlot, regType, fee);
                audit.log(AuditService.ACTION_CREATE, "registrations", regNo, auditDetail);

                String msg = "挂号成功！\n\n" +
                        "挂号号: " + regNo + "\n" +
                        "排队号: " + queueNo + "\n" +
                        "患者: " + patientName + "\n" +
                        "医生: " + doctorName + "\n" +
                        "科室: " + deptName + "\n" +
                        "类型: " + regType + (isEmergency ? " (绿色通道)" : "") + "\n" +
                        "费用: ¥" + fee;

                showAlert("挂号成功", msg, Alert.AlertType.INFORMATION);
                refreshRegistrationTable();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                conn.close();
            }

        } catch (BusinessException be) {
            showAlert("挂号失败", be.getMessage(), Alert.AlertType.WARNING);
        } catch (Exception e) {
            log.error("挂号异常", e);
            showAlert("系统错误", "挂号失败: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * 检查号源是否充足（T-3.1.2）
     */
    private boolean checkAppointmentSlot(Connection conn, int doctorId, LocalDate date,
                                         String timeSlot, String regType, boolean isEmergency) {
        // 急诊不占用号源
        if (isEmergency) return true;

        String sql = """
                SELECT total_quota, used_quota FROM appointment_slots
                WHERE doctor_id = ? AND slot_date = ? AND time_slot = ?
                  AND slot_type = ? AND is_active = true""";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, timeSlot);
            ps.setString(4, regType);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int quota = rs.getInt("total_quota");
                int used  = rs.getInt("used_quota");
                return used < quota;
            }
            // 如果没有号源记录，默认允许挂号（首次）
            return true;
        } catch (SQLException e) {
            log.warn("号源检查失败，默认放行", e);
            return true;  // 容错
        }
    }

    /**
     * 扣减号源
     */
    private void deductSlot(Connection conn, int doctorId, LocalDate date,
                            String timeSlot, String regType) throws SQLException {
        String sql = """
                UPDATE appointment_slots SET used_quota = used_quota + 1
                WHERE doctor_id = ? AND slot_date = ? AND time_slot = ?
                  AND slot_type = ? AND is_active = true""";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, timeSlot);
            ps.setString(4, regType);
            int updated = ps.executeUpdate();

            // 如果没有记录则创建初始号源再扣减
            if (updated == 0) {
                String insertSlot = """
                        INSERT INTO appointment_slots
                        (doctor_id, doctor_name, department_id, slot_date, time_slot, total_quota, used_quota, slot_type)
                        SELECT d.id, d.name, d.department_id, ?, ?, 30, 1, ?
                        FROM doctors d WHERE d.id = ?""";
                try (PreparedStatement ips = conn.prepareStatement(insertSlot)) {
                    ips.setDate(1, Date.valueOf(date));
                    ips.setString(2, timeSlot);
                    ips.setString(3, regType);
                    ips.setInt(4, doctorId);
                    ips.executeUpdate();
                }
            }
        }
    }

    /**
     * 生成排队号：当前时段该医生已挂号数 + 1
     */
    private int generateQueueNo(Connection conn, int doctorId, LocalDate date, String timeSlot)
            throws SQLException {
        String sql = "SELECT COALESCE(MAX(queue_no), 0) FROM registrations " +
                     "WHERE doctor_id = ? AND visit_date = ? AND visit_time_slot = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, timeSlot);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) + 1 : 1;
        }
    }

    /**
     * 退号处理
     */
    private void cancelRegistration(int id, String regNo) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认退号");
        confirm.setHeaderText("确定要退号「" + regNo + "」吗？");
        confirm.setContentText("退号后将释放号源，已产生费用将按规则处理。");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Connection conn = null;
            try {
                conn = ConnectionPool.getInstance().getConnection();
                conn.setAutoCommit(false);

                // 查询当前挂号信息（用于恢复号源）
                PreparedStatement psQuery = conn.prepareStatement(
                        "SELECT doctor_id, visit_date, visit_time_slot, visit_type, is_emergency " +
                                "FROM registrations WHERE id = ? AND status = '待就诊'");
                psQuery.setInt(1, id);
                ResultSet rs = psQuery.executeQuery();
                if (!rs.next()) {
                    showAlert("退号失败", "未找到可退号的记录，可能已就诊或已退号", Alert.AlertType.WARNING);
                    conn.rollback();
                    return;
                }

                int docId    = rs.getInt("doctor_id");
                Date vDate   = rs.getDate("visit_date");
                String slot  = rs.getString("visit_time_slot");
                String vType = rs.getString("visit_type");
                boolean isEm = rs.getBoolean("is_emergency");

                // 更新状态
                PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE registrations SET status = '已退号' WHERE id = ? AND status = '待就诊'");
                psUpdate.setInt(1, id);
                int updated = psUpdate.executeUpdate();

                if (updated > 0) {
                    // 恢复号源（急诊不恢复）
                    if (!isEm) {
                        resetSlot(conn, docId, vDate, slot, vType);
                    }

                    conn.commit();
                    audit.log(AuditService.ACTION_UPDATE, "registrations",
                            String.valueOf(id), "退号: " + regNo);
                    showAlert("退号成功", "挂号号 " + regNo + " 已退号", Alert.AlertType.INFORMATION);
                    refreshRegistrationTable();
                } else {
                    conn.rollback();
                    showAlert("退号失败", "挂号状态已变更，无法退号", Alert.AlertType.WARNING);
                }

            } catch (SQLException e) {
                log.error("退号异常", e);
                try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
                showAlert("错误", "退号失败: " + e.getMessage(), Alert.AlertType.ERROR);
            } finally {
                try {
                    if (conn != null) { conn.setAutoCommit(true); conn.close(); }
                } catch (SQLException e) {}
            }
        }
    }

    private void resetSlot(Connection conn, int doctorId, Date date, String timeSlot, String regType)
            throws SQLException {
        String sql = "UPDATE appointment_slots SET used_quota = GREATEST(used_quota - 1, 0) " +
                     "WHERE doctor_id = ? AND slot_date = ? AND time_slot = ? AND slot_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, date);
            ps.setString(3, timeSlot);
            ps.setString(4, regType);
            ps.executeUpdate();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  号源管理
    // ═══════════════════════════════════════════════════════════

    private VBox buildSlotTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(0));

        // 工具栏
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        DatePicker slotDate = new DatePicker(LocalDate.now());
        slotDate.setPrefWidth(130);
        ComboBox<String> slotDoctorCombo = new ComboBox<>();
        slotDoctorCombo.setPromptText("选择医生");
        slotDoctorCombo.setPrefWidth(150);

        Button queryBtn   = new Button("查询号源");
        queryBtn.getStyleClass().add("btn-primary");
        Button initSlotBtn = new Button("初始化号源");
        initSlotBtn.getStyleClass().add("btn-success");

        toolbar.getChildren().addAll(
                new Label("日期:"), slotDate,
                new Label("医生:"), slotDoctorCombo,
                queryBtn, initSlotBtn
        );

        // 号源表格
        TableView<Object[]> slotTable = new TableView<>();
        slotTable.getStyleClass().add("table-view");
        slotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "医生", "科室", "日期", "时段", "类型", "总号源", "已用", "剩余"};
        for (int i = 0; i < headers.length; i++) {
            TableColumn<Object[], String> col = new TableColumn<>(headers[i]);
            final int idx = i;
            col.setCellValueFactory(data -> {
                Object val = data.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(val != null ? val.toString() : "");
            });
            if (i == 0) col.setPrefWidth(40);
            slotTable.getColumns().add(col);
        }
        slotTable.setItems(slotData);
        VBox.setVgrow(slotTable, Priority.ALWAYS);

        root.getChildren().addAll(toolbar, slotTable);

        // 加载医生列表
        loadAllDoctors(slotDoctorCombo);

        // 查询
        queryBtn.setOnAction(e -> querySlots(slotDate.getValue(), slotDoctorCombo.getValue()));

        // 初始化号源
        initSlotBtn.setOnAction(e -> initSlots(slotDate.getValue(), slotDoctorCombo.getValue()));

        // 初始加载
        querySlots(slotDate.getValue(), slotDoctorCombo.getValue());

        return root;
    }

    private void loadAllDoctors(ComboBox<String> combo) {
        AsyncUIUtil.executeAsync(() -> {
            List<String> doctors = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT name FROM doctors WHERE is_active = true ORDER BY id")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    doctors.add(rs.getString("name"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return doctors;
        }, doctors -> {
            combo.getItems().setAll(doctors);
        });
    }

    private void querySlots(LocalDate date, String doctorName) {
        AsyncUIUtil.executeAsync(this, () -> {
            List<Object[]> data = new ArrayList<>();
            StringBuilder sql = new StringBuilder("""
                    SELECT a.id, a.doctor_name, d.name as dept_name,
                           a.slot_date, a.time_slot, a.slot_type,
                           a.total_quota, a.used_quota, (a.total_quota - a.used_quota) as remaining
                    FROM appointment_slots a
                    LEFT JOIN departments d ON a.department_id = d.id
                    WHERE a.is_active = true""");

            if (date != null) sql.append(" AND a.slot_date = ?");
            if (doctorName != null && !doctorName.isEmpty()) sql.append(" AND a.doctor_name = ?");

            sql.append(" ORDER BY a.slot_date DESC, a.time_slot, a.doctor_name");

            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                if (date != null) ps.setDate(idx++, Date.valueOf(date));
                if (doctorName != null && !doctorName.isEmpty()) ps.setString(idx++, doctorName);

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"),
                            rs.getString("doctor_name"),
                            rs.getString("dept_name"),
                            rs.getDate("slot_date").toLocalDate().toString(),
                            rs.getString("time_slot"),
                            rs.getString("slot_type"),
                            rs.getInt("total_quota"),
                            rs.getInt("used_quota"),
                            rs.getInt("remaining")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            slotData.setAll(data);
        });
    }

    /**
     * 初始化号源：为指定日期+医生创建号源记录
     */
    private void initSlots(LocalDate date, String doctorName) {
        if (date == null) {
            showAlert("提示", "请选择日期", Alert.AlertType.WARNING);
            return;
        }

        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            String findDocSql = doctorName != null && !doctorName.isEmpty()
                    ? "SELECT id, name, department_id FROM doctors WHERE name = ? AND is_active = true"
                    : "SELECT id, name, department_id FROM doctors WHERE is_active = true";

            PreparedStatement psFind = conn.prepareStatement(findDocSql);
            if (doctorName != null && !doctorName.isEmpty()) {
                psFind.setString(1, doctorName);
            }
            ResultSet rs = psFind.executeQuery();

            int created = 0;
            String[] timeSlots = {"上午", "下午", "晚上"};
            String[] types     = {"普通", "专家"};

            String insertSql = """
                    INSERT INTO appointment_slots (doctor_id, doctor_name, department_id,
                        slot_date, time_slot, total_quota, used_quota, slot_type)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                    ON CONFLICT DO NOTHING""";

            while (rs.next()) {
                int docId   = rs.getInt("id");
                String docName = rs.getString("name");
                int deptId  = rs.getInt("department_id");

                for (String slot : timeSlots) {
                    for (String type : types) {
                        int quota = "专家".equals(type) ? 20 : 40;  // 专家号更少
                        try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                            psInsert.setInt(1, docId);
                            psInsert.setString(2, docName);
                            psInsert.setInt(3, deptId);
                            psInsert.setDate(4, Date.valueOf(date));
                            psInsert.setString(5, slot);
                            psInsert.setInt(6, quota);
                            psInsert.setString(7, type);
                            created += psInsert.executeUpdate();
                        }
                    }
                }
            }

            audit.log(AuditService.ACTION_CREATE, "appointment_slots",
                    String.valueOf(date), "初始化号源: " + date + " " +
                            (doctorName != null ? doctorName : "全部医生") + " 创建" + created + "条");
            showAlert("成功", "已为 " + date + " 初始化号源，共创建 " + created + " 条记录",
                    Alert.AlertType.INFORMATION);
            querySlots(date, doctorName);

        } catch (SQLException e) {
            log.error("初始化号源失败", e);
            showAlert("错误", "初始化号源失败: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  身份验证工具
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验中国大陆18位身份证号
     *
     * @return null 表示校验通过，否则返回错误描述
     */
    public static String validateIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) return null;  // 允许为空

        // 长度校验
        if (idCard.length() != 18) {
            return "身份证号必须为18位";
        }

        // 前17位必须为数字
        for (int i = 0; i < 17; i++) {
            if (!Character.isDigit(idCard.charAt(i))) {
                return "身份证号前17位必须为数字";
            }
        }

        // 校验码
        char lastChar = idCard.charAt(17);
        if (!Character.isDigit(lastChar) && lastChar != 'X' && lastChar != 'x') {
            return "身份证号第18位必须为数字或X";
        }

        // 出生日期校验
        try {
            int year  = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day   = Integer.parseInt(idCard.substring(12, 14));
            if (year < 1900 || year > LocalDate.now().getYear()) {
                return "身份证号出生年份不合法";
            }
            if (month < 1 || month > 12) {
                return "身份证号出生月份不合法";
            }
            if (day < 1 || day > 31) {
                return "身份证号出生日期不合法";
            }
            // 严格日期校验
            try {
                IsoChronology.INSTANCE.date(year, month, day);
            } catch (Exception e) {
                return "身份证号出生日期不合法";
            }
        } catch (NumberFormatException e) {
            return "身份证号出生日期解析失败";
        }

        // 校验码计算
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * ID_CARD_WEIGHTS[i];
        }
        char expectedCheck = ID_CARD_CHECK[sum % 11];
        if (Character.toUpperCase(lastChar) != expectedCheck) {
            return "身份证号校验码不正确";
        }

        return null;  // 校验通过
    }

    // ═══════════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════════

    private int safeParseInt(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

}
