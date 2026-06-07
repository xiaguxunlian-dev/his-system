package com.his.outpatient.ui;

import com.his.auth.AuditService;
import com.his.auth.UserSession;
import com.his.shared.database.ConnectionPool;
import com.his.shared.exception.BusinessException;
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

import com.his.shared.ui.AsyncUIUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * 门诊工作站视图 - 生产级
 * T-3.2: 完善门诊工作站
 * 功能：门诊接诊记录、处方管理、ICD-10诊断编码、药品库存联动、处方审核流程、审计日志
 */
public class OutpatientView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(OutpatientView.class);
    private final AuditService audit = AuditService.getInstance();
    private final UserSession session = UserSession.getInstance();

    // ========== Tab1: 门诊接诊 ==========
    private TableView<Object[]> visitTable;
    private ObservableList<Object[]> visitData = FXCollections.observableArrayList();
    private TextField visitSearchField;
    private ComboBox<String> visitStatusCombo;
    private TextField chiefComplaintField;
    private TextField diagnosisField;
    private TextField diagnosisCodeField;
    private TextArea treatmentPlanArea;
    private int currentVisitId = -1;

    // ========== Tab2: 处方管理 ==========
    private TableView<Object[]> prescriptionTable;
    private ObservableList<Object[]> prescriptionData = FXCollections.observableArrayList();
    private TableView<Object[]> prescriptionItemTable;
    private ObservableList<Object[]> prescriptionItemData = FXCollections.observableArrayList();
    private ComboBox<String> prescriptionTypeCombo;
    private CheckBox narcoticCb;
    private int currentPrescriptionId = -1;
    private int currentVisitIdForRx = -1;

    // ========== ICD-10 选择对话框相关 ==========
    private Dialog<Object[]> icdDialog;
    private TableView<Object[]> icdTable;
    private ObservableList<Object[]> icdData = FXCollections.observableArrayList();

    public OutpatientView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab visitTab = new Tab("门诊接诊");
        visitTab.setContent(buildVisitTab());
        visitTab.setClosable(false);

        Tab rxTab = new Tab("处方管理");
        rxTab.setContent(buildPrescriptionTab());
        rxTab.setClosable(false);

        getTabs().addAll(visitTab, rxTab);
    }

    // ========================================================================
    // Tab1: 门诊接诊
    // ========================================================================

    private VBox buildVisitTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        visitSearchField = new TextField();
        visitSearchField.setPromptText("患者姓名/病历号/就诊号...");
        visitSearchField.setPrefWidth(220);
        visitStatusCombo = new ComboBox<>();
        visitStatusCombo.getItems().addAll("全部", "接诊中", "已完成");
        visitStatusCombo.setValue("全部");
        visitStatusCombo.setPrefWidth(120);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        searchRow.getChildren().addAll(new Label("搜索:"), visitSearchField,
                new Label("状态:"), visitStatusCombo, searchBtn, refreshBtn);

        // ---- 就诊记录表格 ----
        visitTable = new TableView<>();
        visitTable.getStyleClass().add("table-view");
        visitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] visitCols = {"ID", "就诊号", "患者", "医生", "科室", "诊断", "日期", "状态"};
        int[] visitWidths = {40, 110, 100, 80, 100, 200, 90, 70};
        for (int i = 0; i < visitCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(visitCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(visitWidths[i]);
            visitTable.getColumns().add(c);
        }
        visitTable.setItems(visitData);
        visitTable.setPrefHeight(260);
        VBox.setVgrow(visitTable, Priority.ALWAYS);

        // 双击行加载详情
        visitTable.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Object[] selected = row.getItem();
                    if (selected != null) {
                        currentVisitId = (int) selected[0];
                        loadVisitDetail(currentVisitId);
                    }
                }
            });
            return row;
        });

        // ---- 接诊表单 ----
        Label formTitle = new Label("接诊详情 / 新建接诊");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(8);

        chiefComplaintField = new TextField();
        chiefComplaintField.setPromptText("主诉");
        chiefComplaintField.setPrefWidth(300);
        diagnosisField = new TextField();
        diagnosisField.setPromptText("诊断描述");
        diagnosisField.setPrefWidth(300);
        diagnosisCodeField = new TextField();
        diagnosisCodeField.setPromptText("ICD-10编码 (点击选择)");
        diagnosisCodeField.setPrefWidth(200);
        diagnosisCodeField.setEditable(false);
        Button selectIcdBtn = new Button("选择ICD-10");
        selectIcdBtn.getStyleClass().add("btn-outline");
        treatmentPlanArea = new TextArea();
        treatmentPlanArea.setPromptText("治疗方案");
        treatmentPlanArea.setPrefRowCount(3);
        treatmentPlanArea.setPrefWidth(300);

        Button saveVisitBtn = new Button("保存接诊");
        saveVisitBtn.getStyleClass().add("btn-primary");
        saveVisitBtn.setPrefWidth(120);
        Button newVisitBtn = new Button("新建接诊");
        newVisitBtn.getStyleClass().add("btn-success");
        Button completeBtn = new Button("完成接诊");
        completeBtn.getStyleClass().add("btn-warning");

        form.add(new Label("主诉:"), 0, 0);
        form.add(chiefComplaintField, 1, 0);
        form.add(new Label("诊断:"), 0, 1);
        form.add(diagnosisField, 1, 1);
        form.add(new Label("ICD编码:"), 2, 1);
        form.add(diagnosisCodeField, 3, 1);
        form.add(selectIcdBtn, 4, 1);
        form.add(new Label("治疗方案:"), 0, 2);
        form.add(treatmentPlanArea, 1, 2, 4, 1);
        form.add(new HBox(10, saveVisitBtn, newVisitBtn, completeBtn), 0, 3, 5, 1);

        root.getChildren().addAll(searchRow, visitTable, formTitle, form);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadVisits());
        refreshBtn.setOnAction(e -> loadVisits());
        selectIcdBtn.setOnAction(e -> showIcd10Dialog(diagnosisCodeField));
        saveVisitBtn.setOnAction(e -> saveVisit());
        newVisitBtn.setOnAction(e -> createNewVisit());
        completeBtn.setOnAction(e -> completeVisit());

        // 初始加载
        loadVisits();

        return root;
    }

    private void loadVisits() {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT v.id, v.visit_no, p.name, d.name, dept.name, v.diagnosis, v.visit_date, v.status " +
                    "FROM outpatient_visits v " +
                    "JOIN patients p ON v.patient_id = p.id " +
                    "JOIN doctors d ON v.doctor_id = d.id " +
                    "JOIN departments dept ON v.department_id = dept.id " +
                    "WHERE 1=1 ");
            String kw = visitSearchField.getText().trim();
            String status = visitStatusCombo.getValue();
            if (!kw.isEmpty()) {
                sql.append("AND (p.name LIKE ? OR p.patient_no LIKE ? OR v.visit_no LIKE ?) ");
            }
            if (!"全部".equals(status)) {
                sql.append("AND v.status = ? ");
            }
            sql.append("ORDER BY v.id DESC LIMIT 200");

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
                            rs.getInt("id"), rs.getString("visit_no"),
                            rs.getString("name"), rs.getString("d.name"),
                            rs.getString("dept.name"), rs.getString("diagnosis"),
                            rs.getDate("visit_date").toLocalDate(), rs.getString("status")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            visitData.setAll(data);
        });
    }

    private void loadVisitDetail(int visitId) {
        AsyncUIUtil.executeAsync(() -> {
            String[] detail = new String[4];
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT v.chief_complaint, v.diagnosis, v.diagnosis_code, v.treatment_plan, v.status " +
                         "FROM outpatient_visits v WHERE v.id = ?")) {
                ps.setInt(1, visitId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    detail[0] = rs.getString("chief_complaint");
                    detail[1] = rs.getString("diagnosis");
                    detail[2] = rs.getString("diagnosis_code");
                    detail[3] = rs.getString("treatment_plan");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return detail;
        }, detail -> {
            chiefComplaintField.setText(detail[0] != null ? detail[0] : "");
            diagnosisField.setText(detail[1] != null ? detail[1] : "");
            diagnosisCodeField.setText(detail[2] != null ? detail[2] : "");
            treatmentPlanArea.setText(detail[3] != null ? detail[3] : "");
        });
    }

    private void saveVisit() {
        try {
            String chiefComplaint = ValidationUtil.requireNonBlank(chiefComplaintField.getText(), "主诉");
            String diagnosis = ValidationUtil.requireNonBlank(diagnosisField.getText(), "诊断");
            String diagnosisCode = diagnosisCodeField.getText().trim();
            String treatmentPlan = treatmentPlanArea.getText();

            if (currentVisitId <= 0) {
                new Alert(Alert.AlertType.WARNING, "请先选择或新建就诊记录").showAndWait();
                return;
            }

            AsyncUIUtil.executeAsync(() -> {
                try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE outpatient_visits SET chief_complaint=?, diagnosis=?, diagnosis_code=?, treatment_plan=? " +
                            "WHERE id=?");
                    ps.setString(1, chiefComplaint);
                    ps.setString(2, diagnosis);
                    ps.setString(3, diagnosisCode.isEmpty() ? null : diagnosisCode);
                    ps.setString(4, treatmentPlan);
                    ps.setInt(5, currentVisitId);
                    ps.executeUpdate();

                    audit.log("UPDATE", "outpatient_visits", String.valueOf(currentVisitId),
                            "保存接诊: diagnosis=" + diagnosis + ", code=" + diagnosisCode);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }, result -> {
                new Alert(Alert.AlertType.INFORMATION, "接诊记录已保存").showAndWait();
                loadVisits();
            });
        } catch (Exception ex) {
            log.error("保存接诊记录失败", ex);
            new Alert(Alert.AlertType.ERROR, "保存失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void createNewVisit() {
        // 弹出对话框选择患者和挂号记录
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("新建门诊接诊");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField patientField = new TextField();
        patientField.setPromptText("患者姓名或病历号");
        ComboBox<String> regCombo = new ComboBox<>();
        regCombo.setPrefWidth(250);

        // 加载未接诊的挂号记录
        Button loadRegBtn = new Button("加载挂号");
        loadRegBtn.getStyleClass().add("btn-outline");
        loadRegBtn.setOnAction(e -> {
            String kw = patientField.getText().trim();
            if (kw.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "请输入患者信息").showAndWait();
                return;
            }
            AsyncUIUtil.executeAsync(() -> {
                List<String> items = new ArrayList<>();
                try (Connection conn = ConnectionPool.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT r.id, r.registration_no, p.name, d.name, r.visit_date " +
                             "FROM registrations r " +
                             "JOIN patients p ON r.patient_id = p.id " +
                             "JOIN doctors d ON r.doctor_id = d.id " +
                             "LEFT JOIN outpatient_visits v ON v.registration_id = r.id " +
                             "WHERE (p.name LIKE ? OR p.patient_no LIKE ?) AND v.id IS NULL " +
                             "ORDER BY r.id DESC LIMIT 50")) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        items.add(rs.getInt("id") + "|" +
                                rs.getString("registration_no") + " | " +
                                rs.getString("p.name") + " | " +
                                rs.getString("d.name") + " | " +
                                rs.getDate("r.visit_date"));
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return items;
            }, items -> {
                regCombo.getItems().setAll(items);
            });
        });

        grid.add(new Label("患者:"), 0, 0);
        grid.add(patientField, 1, 0);
        grid.add(loadRegBtn, 2, 0);
        grid.add(new Label("挂号记录:"), 0, 1);
        grid.add(regCombo, 1, 1, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String selected = regCombo.getValue();
                if (selected == null || selected.isEmpty()) return null;
                int regId = Integer.parseInt(selected.split("\\|")[0]);
                return new int[]{regId};
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            int regId = result.get()[0];
            createVisitFromRegistration(regId);
        }
    }

    private void createVisitFromRegistration(int regId) {
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                // 获取挂号信息
                PreparedStatement psReg = conn.prepareStatement(
                        "SELECT r.registration_no, r.patient_id, p.name as pname, r.doctor_id, d.name as dname, " +
                        "r.department_id, dept.name as deptname, r.visit_date " +
                        "FROM registrations r " +
                        "JOIN patients p ON r.patient_id = p.id " +
                        "JOIN doctors d ON r.doctor_id = d.id " +
                        "JOIN departments dept ON r.department_id = dept.id " +
                        "WHERE r.id = ?");
                psReg.setInt(1, regId);
                ResultSet rsReg = psReg.executeQuery();
                if (!rsReg.next()) throw new BusinessException("挂号记录不存在");

                String visitNo = "VIS" + System.currentTimeMillis() % 1000000000;
                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO outpatient_visits (visit_no, registration_id, patient_id, patient_name, " +
                        "department_id, department_name, doctor_id, doctor_name, visit_date, status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?, '接诊中')",
                        Statement.RETURN_GENERATED_KEYS);
                psIns.setString(1, visitNo);
                psIns.setInt(2, regId);
                psIns.setInt(3, rsReg.getInt("patient_id"));
                psIns.setString(4, rsReg.getString("pname"));
                psIns.setInt(5, rsReg.getInt("department_id"));
                psIns.setString(6, rsReg.getString("deptname"));
                psIns.setInt(7, rsReg.getInt("doctor_id"));
                psIns.setString(8, rsReg.getString("dname"));
                psIns.setDate(9, Date.valueOf(rsReg.getDate("visit_date").toLocalDate()));
                psIns.executeUpdate();

                ResultSet keys = psIns.getGeneratedKeys();
                int visitId = -1;
                if (keys.next()) {
                    visitId = keys.getInt(1);
                }

                audit.log("CREATE", "outpatient_visits", String.valueOf(visitId),
                        "新建门诊接诊: visitNo=" + visitNo + ", regId=" + regId);

                return visitId;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, visitId -> {
            currentVisitId = visitId;
            new Alert(Alert.AlertType.INFORMATION, "接诊已创建！").showAndWait();
            loadVisits();
            loadVisitDetail(visitId);
        });
    }

    private void completeVisit() {
        if (currentVisitId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择就诊记录").showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认完成接诊？", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE outpatient_visits SET status='已完成' WHERE id=?");
                ps.setInt(1, currentVisitId);
                ps.executeUpdate();

                audit.log("UPDATE", "outpatient_visits", String.valueOf(currentVisitId), "完成接诊");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, result -> {
            new Alert(Alert.AlertType.INFORMATION, "接诊已完成").showAndWait();
            loadVisits();
        });
    }

    // ========================================================================
    // Tab2: 处方管理
    // ========================================================================

    private VBox buildPrescriptionTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 上方：就诊选择 + 处方列表 ----
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        TextField visitSearch = new TextField();
        visitSearch.setPromptText("就诊号或患者姓名...");
        visitSearch.setPrefWidth(200);
        Button loadVisitBtn = new Button("加载就诊处方");
        loadVisitBtn.getStyleClass().add("btn-primary");
        topRow.getChildren().addAll(new Label("就诊:"), visitSearch, loadVisitBtn);

        // 处方列表表格
        prescriptionTable = new TableView<>();
        prescriptionTable.getStyleClass().add("table-view");
        prescriptionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] rxCols = {"ID", "处方号", "类型", "毒麻", "总金额", "状态", "备注"};
        int[] rxWidths = {40, 120, 80, 50, 80, 80, 200};
        for (int i = 0; i < rxCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(rxCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(rxWidths[i]);
            prescriptionTable.getColumns().add(c);
        }
        prescriptionTable.setItems(prescriptionData);
        prescriptionTable.setPrefHeight(200);

        // 双击处方加载明细
        prescriptionTable.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Object[] selected = row.getItem();
                    if (selected != null) {
                        currentPrescriptionId = (int) selected[0];
                        loadPrescriptionItems(currentPrescriptionId);
                    }
                }
            });
            return row;
        });

        // ---- 处方操作按钮 ----
        HBox rxBtnRow = new HBox(10);
        Button newRxBtn = new Button("新建处方");
        newRxBtn.getStyleClass().add("btn-success");
        Button submitRxBtn = new Button("提交缴费");
        submitRxBtn.getStyleClass().add("btn-primary");
        Button cancelRxBtn = new Button("取消处方");
        cancelRxBtn.getStyleClass().add("btn-danger");
        rxBtnRow.getChildren().addAll(newRxBtn, submitRxBtn, cancelRxBtn);

        // ---- 处方明细表格 ----
        Label itemTitle = new Label("处方明细");
        itemTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        prescriptionItemTable = new TableView<>();
        prescriptionItemTable.getStyleClass().add("table-view");
        prescriptionItemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] itemCols = {"ID", "药品", "规格", "用量", "用法", "频次", "天数", "数量", "单价", "总价"};
        int[] itemWidths = {40, 150, 100, 80, 100, 80, 50, 70, 70, 80};
        for (int i = 0; i < itemCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(itemCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(itemWidths[i]);
            prescriptionItemTable.getColumns().add(c);
        }
        prescriptionItemTable.setItems(prescriptionItemData);
        prescriptionItemTable.setPrefHeight(200);
        VBox.setVgrow(prescriptionItemTable, Priority.ALWAYS);

        // ---- 添加药品按钮 ----
        HBox itemBtnRow = new HBox(10);
        Button addItemBtn = new Button("添加药品");
        addItemBtn.getStyleClass().add("btn-primary");
        Button removeItemBtn = new Button("移除药品");
        removeItemBtn.getStyleClass().add("btn-danger");
        itemBtnRow.getChildren().addAll(addItemBtn, removeItemBtn);

        root.getChildren().addAll(topRow, prescriptionTable, rxBtnRow, itemTitle, prescriptionItemTable, itemBtnRow);

        // ---- 事件绑定 ----
        loadVisitBtn.setOnAction(e -> {
            currentVisitIdForRx = -1;
            currentPrescriptionId = -1;
            prescriptionData.clear();
            prescriptionItemData.clear();
            loadVisitAndPrescriptions(visitSearch.getText().trim());
        });
        newRxBtn.setOnAction(e -> createNewPrescription());
        submitRxBtn.setOnAction(e -> submitPrescription());
        cancelRxBtn.setOnAction(e -> cancelPrescription());
        addItemBtn.setOnAction(e -> addPrescriptionItem());
        removeItemBtn.setOnAction(e -> removePrescriptionItem());

        return root;
    }

    private void loadVisitAndPrescriptions(String keyword) {
        if (keyword.isEmpty()) return;
        AsyncUIUtil.executeAsync(() -> {
            int visitId = -1;
            List<Object[]> rxData = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                // 查找就诊记录
                PreparedStatement psV = conn.prepareStatement(
                        "SELECT id FROM outpatient_visits WHERE visit_no LIKE ? OR id IN " +
                        "(SELECT id FROM outpatient_visits WHERE patient_id IN " +
                        "(SELECT id FROM patients WHERE name LIKE ? OR patient_no LIKE ?)) " +
                        "ORDER BY id DESC LIMIT 1");
                String likeKw = "%" + keyword + "%";
                psV.setString(1, likeKw); psV.setString(2, likeKw); psV.setString(3, likeKw);
                ResultSet rsV = psV.executeQuery();
                if (rsV.next()) {
                    visitId = rsV.getInt(1);
                }

                if (visitId > 0) {
                    // 加载处方
                    PreparedStatement psRx = conn.prepareStatement(
                            "SELECT id, prescription_no, prescription_type, is_narcotic, total_amount, status, notes " +
                            "FROM prescriptions WHERE visit_id = ? ORDER BY id DESC");
                    psRx.setInt(1, visitId);
                    ResultSet rsRx = psRx.executeQuery();
                    while (rsRx.next()) {
                        rxData.add(new Object[]{
                                rsRx.getInt("id"), rsRx.getString("prescription_no"),
                                rsRx.getString("prescription_type"), rsRx.getBoolean("is_narcotic") ? "是" : "否",
                                rsRx.getBigDecimal("total_amount"), rsRx.getString("status"), rsRx.getString("notes")
                        });
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return new Object[]{visitId, rxData};
        }, result -> {
            int visitId = (int) result[0];
            if (visitId <= 0) {
                new Alert(Alert.AlertType.WARNING, "未找到就诊记录").showAndWait();
                return;
            }
            currentVisitIdForRx = visitId;
            @SuppressWarnings("unchecked")
            List<Object[]> rxData = (List<Object[]>) result[1];
            prescriptionData.setAll(rxData);
        });
    }

    private void createNewPrescription() {
        if (currentVisitIdForRx <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先加载就诊记录").showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("新建处方");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        prescriptionTypeCombo = new ComboBox<>();
        prescriptionTypeCombo.getItems().addAll("普通", "急诊", "麻醉");
        prescriptionTypeCombo.setValue("普通");
        narcoticCb = new CheckBox("毒麻药品");
        TextField notesField = new TextField();
        notesField.setPromptText("备注");

        grid.add(new Label("处方类型:"), 0, 0);
        grid.add(prescriptionTypeCombo, 1, 0);
        grid.add(narcoticCb, 0, 1);
        grid.add(new Label("备注:"), 0, 2);
        grid.add(notesField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String rxType = prescriptionTypeCombo.getValue();
            boolean isNarcotic = narcoticCb.isSelected();
            String notes = notesField.getText().trim();

            AsyncUIUtil.executeAsync(() -> {
                try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                    // 获取就诊和医生信息
                    PreparedStatement psV = conn.prepareStatement(
                            "SELECT v.patient_id, v.patient_name, v.doctor_id, v.doctor_name " +
                            "FROM outpatient_visits v WHERE v.id=?");
                    psV.setInt(1, currentVisitIdForRx);
                    ResultSet rsV = psV.executeQuery();
                    if (!rsV.next()) throw new BusinessException("就诊记录不存在");

                    String prescriptionNo = "RX" + System.currentTimeMillis() % 1000000000;
                    PreparedStatement psIns = conn.prepareStatement(
                            "INSERT INTO prescriptions (prescription_no, visit_id, patient_id, patient_name, " +
                            "doctor_id, doctor_name, prescription_type, is_narcotic, total_amount, status, notes) " +
                            "VALUES (?,?,?,?,?,?,?,?,0.00,'待缴费',?)",
                            Statement.RETURN_GENERATED_KEYS);
                    psIns.setString(1, prescriptionNo);
                    psIns.setInt(2, currentVisitIdForRx);
                    psIns.setInt(3, rsV.getInt("patient_id"));
                    psIns.setString(4, rsV.getString("patient_name"));
                    psIns.setInt(5, rsV.getInt("doctor_id"));
                    psIns.setString(6, rsV.getString("doctor_name"));
                    psIns.setString(7, rxType);
                    psIns.setBoolean(8, isNarcotic);
                    psIns.setString(9, notes.isEmpty() ? null : notes);
                    psIns.executeUpdate();

                    ResultSet keys = psIns.getGeneratedKeys();
                    int prescriptionId = -1;
                    if (keys.next()) prescriptionId = keys.getInt(1);

                    audit.log("CREATE", "prescriptions", String.valueOf(prescriptionId),
                            "新建处方: " + prescriptionNo + ", type=" + rxType);

                    return new Object[]{prescriptionId, prescriptionNo};
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, res -> {
                currentPrescriptionId = (int) res[0];
                new Alert(Alert.AlertType.INFORMATION, "处方已创建！处方号: " + res[1]).showAndWait();
                loadVisitAndPrescriptions(String.valueOf(currentVisitIdForRx));
            });
        }
    }

    private void submitPrescription() {
        if (currentPrescriptionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择处方").showAndWait();
            return;
        }
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE prescriptions SET status='已缴费' WHERE id=? AND status='待缴费'");
                ps.setInt(1, currentPrescriptionId);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    audit.log("UPDATE", "prescriptions", String.valueOf(currentPrescriptionId), "提交缴费");
                }
                return updated;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, updated -> {
            if (updated == 0) {
                new Alert(Alert.AlertType.WARNING, "处方状态不允许提交").showAndWait();
                return;
            }
            new Alert(Alert.AlertType.INFORMATION, "处方已提交缴费").showAndWait();
            loadVisitAndPrescriptions(String.valueOf(currentVisitIdForRx));
        });
    }

    private void cancelPrescription() {
        if (currentPrescriptionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择处方").showAndWait();
            return;
        }
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE prescriptions SET status='已取消' WHERE id=? AND status IN ('待缴费','已缴费')");
                ps.setInt(1, currentPrescriptionId);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    audit.log("UPDATE", "prescriptions", String.valueOf(currentPrescriptionId), "取消处方");
                }
                return updated;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, updated -> {
            if (updated == 0) {
                new Alert(Alert.AlertType.WARNING, "处方状态不允许取消").showAndWait();
                return;
            }
            new Alert(Alert.AlertType.INFORMATION, "处方已取消").showAndWait();
            loadVisitAndPrescriptions(String.valueOf(currentVisitIdForRx));
        });
    }

    private void loadPrescriptionItems(int prescriptionId) {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, drug_name, drug_spec, dosage, usage_method, frequency, days, quantity, unit_price, total_price " +
                         "FROM prescription_items WHERE prescription_id = ? ORDER BY id")) {
                ps.setInt(1, prescriptionId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("drug_name"), rs.getString("drug_spec"),
                            rs.getString("dosage"), rs.getString("usage_method"), rs.getString("frequency"),
                            rs.getInt("days"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("total_price")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            prescriptionItemData.setAll(data);
        });
    }

    private void addPrescriptionItem() {
        if (currentPrescriptionId <= 0) {
            new Alert(Alert.AlertType.WARNING, "请先选择处方").showAndWait();
            return;
        }

        // 弹出药品选择对话框
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("添加药品");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TextField drugSearch = new TextField();
        drugSearch.setPromptText("搜索药品...");
        TableView<Object[]> drugTable = new TableView<>();
        drugTable.getStyleClass().add("table-view");
        String[] drugCols = {"ID", "药品名称", "规格", "库存", "零售价"};
        for (int i = 0; i < drugCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(drugCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 0 ? 40 : i == 1 ? 150 : i == 2 ? 100 : i == 3 ? 70 : 80);
            drugTable.getColumns().add(c);
        }
        ObservableList<Object[]> drugData = FXCollections.observableArrayList();
        drugTable.setItems(drugData);
        drugTable.setPrefHeight(200);

        // 加载药品
        Button searchDrugBtn = new Button("搜索");
        searchDrugBtn.getStyleClass().add("btn-primary");
        searchDrugBtn.setOnAction(e -> {
            String kw = drugSearch.getText().trim();
            if (kw.isEmpty()) return;
            AsyncUIUtil.executeAsync(() -> {
                List<Object[]> items = new ArrayList<>();
                try (Connection conn = ConnectionPool.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT DISTINCT di.drug_id, di.drug_name, di.drug_spec, " +
                             "SUM(di.stock_qty) as total_stock, MAX(di.retail_price) as price " +
                             "FROM drug_inventory di " +
                             "WHERE di.drug_name LIKE ? OR di.drug_id IN (SELECT id FROM drugs WHERE drug_name LIKE ?) " +
                             "GROUP BY di.drug_id, di.drug_name, di.drug_spec " +
                             "HAVING SUM(di.stock_qty) > 0 " +
                             "LIMIT 50")) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        items.add(new Object[]{
                                rs.getInt("drug_id"), rs.getString("drug_name"),
                                rs.getString("drug_spec"), rs.getBigDecimal("total_stock"), rs.getBigDecimal("price")
                        });
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return items;
            }, items -> {
                drugData.setAll(items);
            });
        });

        // 药品明细表单
        GridPane itemForm = new GridPane();
        itemForm.setHgap(8); itemForm.setVgap(8);
        TextField dosageField = new TextField(); dosageField.setPromptText("用量");
        TextField usageField = new TextField(); usageField.setPromptText("用法");
        TextField freqField = new TextField(); freqField.setPromptText("频次");
        TextField daysField = new TextField(); daysField.setPromptText("天数");
        TextField qtyField = new TextField(); qtyField.setPromptText("数量");
        TextField unitField = new TextField(); unitField.setPromptText("单位");
        TextField priceField = new TextField(); priceField.setPromptText("单价");

        itemForm.add(new Label("用量:"), 0, 0); itemForm.add(dosageField, 1, 0);
        itemForm.add(new Label("用法:"), 2, 0); itemForm.add(usageField, 3, 0);
        itemForm.add(new Label("频次:"), 0, 1); itemForm.add(freqField, 1, 1);
        itemForm.add(new Label("天数:"), 2, 1); itemForm.add(daysField, 3, 1);
        itemForm.add(new Label("数量:"), 0, 2); itemForm.add(qtyField, 1, 2);
        itemForm.add(new Label("单位:"), 2, 2); itemForm.add(unitField, 3, 2);
        itemForm.add(new Label("单价:"), 0, 3); itemForm.add(priceField, 1, 3);

        content.getChildren().addAll(new HBox(10, drugSearch, searchDrugBtn), drugTable, new Label("明细:"), itemForm);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Object[] selected = drugTable.getSelectionModel().getSelectedItem();
                if (selected == null) return null;
                int drugId = (int) selected[0];
                String drugName = (String) selected[1];
                String drugSpec = (String) selected[2];
                BigDecimal retailPrice = (BigDecimal) selected[4];
                // 设置默认值
                if (priceField.getText().isEmpty()) priceField.setText(retailPrice.toString());
                if (unitField.getText().isEmpty()) unitField.setText("盒");
                return new int[]{drugId};
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            int drugId = result.get()[0];
            try {
                String dosage = ValidationUtil.requireNonBlank(dosageField.getText(), "用量");
                String usage = usageField.getText();
                String freq = freqField.getText();
                int days = daysField.getText().isEmpty() ? 1 : Integer.parseInt(daysField.getText());
                BigDecimal qty = new BigDecimal(ValidationUtil.requireNonBlank(qtyField.getText(), "数量"));
                String unit = unitField.getText();
                BigDecimal price = new BigDecimal(ValidationUtil.requireNonBlank(priceField.getText(), "单价"));
                BigDecimal totalPrice = price.multiply(qty);
                Object[] selectedDrug = drugTable.getSelectionModel().getSelectedItem();
                String drugName = (String) selectedDrug[1];
                String drugSpec = (String) selectedDrug[2];

                AsyncUIUtil.executeAsync(() -> {
                    try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                        // 检查库存
                        PreparedStatement psStock = conn.prepareStatement(
                                "SELECT COALESCE(SUM(stock_qty),0) FROM drug_inventory WHERE drug_id=?");
                        psStock.setInt(1, drugId);
                        ResultSet rsStock = psStock.executeQuery();
                        BigDecimal stock = rsStock.next() ? rsStock.getBigDecimal(1) : BigDecimal.ZERO;
                        if (stock.compareTo(qty) < 0) {
                            return new Object[]{false, stock};
                        }

                        PreparedStatement psIns = conn.prepareStatement(
                                "INSERT INTO prescription_items (prescription_id, drug_id, drug_name, drug_spec, " +
                                "dosage, usage_method, frequency, days, quantity, unit, unit_price, total_price) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
                        psIns.setInt(1, currentPrescriptionId);
                        psIns.setInt(2, drugId);
                        psIns.setString(3, drugName);
                        psIns.setString(4, drugSpec);
                        psIns.setString(5, dosage);
                        psIns.setString(6, usage);
                        psIns.setString(7, freq);
                        psIns.setInt(8, days);
                        psIns.setBigDecimal(9, qty);
                        psIns.setString(10, unit);
                        psIns.setBigDecimal(11, price);
                        psIns.setBigDecimal(12, totalPrice);
                        psIns.executeUpdate();

                        // 更新处方总金额
                        PreparedStatement psTotal = conn.prepareStatement(
                                "UPDATE prescriptions SET total_amount = (SELECT COALESCE(SUM(total_price),0) FROM prescription_items WHERE prescription_id=?) " +
                                "WHERE id=?");
                        psTotal.setInt(1, currentPrescriptionId);
                        psTotal.setInt(2, currentPrescriptionId);
                        psTotal.executeUpdate();

                        audit.log("CREATE", "prescription_items", String.valueOf(currentPrescriptionId),
                                "添加药品: drugId=" + drugId + ", qty=" + qty);

                        return new Object[]{true, null};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, res -> {
                    boolean success = (boolean) res[0];
                    if (!success) {
                        BigDecimal stock = (BigDecimal) res[1];
                        new Alert(Alert.AlertType.WARNING, "库存不足！当前库存: " + stock).showAndWait();
                        return;
                    }
                    loadPrescriptionItems(currentPrescriptionId);
                    loadVisitAndPrescriptions(String.valueOf(currentVisitIdForRx));
                });
            } catch (Exception ex) {
                log.error("添加处方药品失败", ex);
                new Alert(Alert.AlertType.ERROR, "添加失败: " + ex.getMessage()).showAndWait();
            }
        }
    }

    private void removePrescriptionItem() {
        Object[] selected = prescriptionItemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择药品").showAndWait();
            return;
        }
        int itemId = (int) selected[0];

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认移除该药品？", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("DELETE FROM prescription_items WHERE id=?");
                ps.setInt(1, itemId);
                ps.executeUpdate();

                // 更新处方总金额
                PreparedStatement psTotal = conn.prepareStatement(
                        "UPDATE prescriptions SET total_amount = (SELECT COALESCE(SUM(total_price),0) FROM prescription_items WHERE prescription_id=?) " +
                        "WHERE id=?");
                psTotal.setInt(1, currentPrescriptionId);
                psTotal.setInt(2, currentPrescriptionId);
                psTotal.executeUpdate();

                audit.log("DELETE", "prescription_items", String.valueOf(itemId), "移除药品");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, result -> {
            loadPrescriptionItems(currentPrescriptionId);
            loadVisitAndPrescriptions(String.valueOf(currentVisitIdForRx));
        });
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
                AsyncUIUtil.executeAsync(() -> {
                    List<Object[]> items = new ArrayList<>();
                    try (Connection conn = ConnectionPool.getInstance().getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "SELECT code, name, category FROM icd10_codes WHERE code LIKE ? OR name LIKE ? LIMIT 100")) {
                        String likeKw = "%" + kw + "%";
                        ps.setString(1, likeKw); ps.setString(2, likeKw);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            items.add(new Object[]{rs.getString("code"), rs.getString("name"), rs.getString("category")});
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return items;
                }, items -> {
                    icdData.setAll(items);
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
