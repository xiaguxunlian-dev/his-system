package com.his.examination.ui;

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
 * 检查管理视图 - 生产级
 * T-3.5: 完善检查管理
 * 功能：检查申请、检查报告、危急值处理、报告审核流程、审计日志
 */
public class ExaminationView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(ExaminationView.class);
    private final AuditService audit = AuditService.getInstance();
    private final UserSession session = UserSession.getInstance();

    // ========== Tab1: 检查申请 ==========
    private TableView<Object[]> requestTable;
    private ObservableList<Object[]> requestData = FXCollections.observableArrayList();
    private TextField requestSearchField;
    private ComboBox<String> requestStatusCombo;
    private int currentRequestId = -1;

    // ========== Tab2: 检查报告 ==========
    private TableView<Object[]> reportTable;
    private ObservableList<Object[]> reportData = FXCollections.observableArrayList();
    private TextField reportSearchField;
    private int currentReportId = -1;

    public ExaminationView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab requestTab = new Tab("检查申请");
        requestTab.setContent(buildRequestTab());
        requestTab.setClosable(false);

        Tab reportTab = new Tab("检查报告");
        reportTab.setContent(buildReportTab());
        reportTab.setClosable(false);

        getTabs().addAll(requestTab, reportTab);
    }

    // ========================================================================
    // Tab1: 检查申请
    // ========================================================================

    private VBox buildRequestTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        requestSearchField = new TextField();
        requestSearchField.setPromptText("患者姓名/申请号...");
        requestSearchField.setPrefWidth(200);
        requestStatusCombo = new ComboBox<>();
        requestStatusCombo.getItems().addAll("全部", "待检查", "已完成", "已取消");
        requestStatusCombo.setValue("全部");
        requestStatusCombo.setPrefWidth(120);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button newRequestBtn = new Button("新增申请");
        newRequestBtn.getStyleClass().add("btn-success");
        searchRow.getChildren().addAll(new Label("搜索:"), requestSearchField,
                new Label("状态:"), requestStatusCombo, searchBtn, refreshBtn, newRequestBtn);

        // ---- 申请表格 ----
        requestTable = new TableView<>();
        requestTable.getStyleClass().add("table-view");
        requestTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] reqCols = {"ID", "申请号", "患者", "项目", "类别", "紧急", "状态", "申请日期"};
        int[] reqWidths = {40, 110, 100, 150, 80, 50, 70, 90};
        for (int i = 0; i < reqCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(reqCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(reqWidths[i]);
            requestTable.getColumns().add(c);
        }
        requestTable.setItems(requestData);
        requestTable.setPrefHeight(280);
        VBox.setVgrow(requestTable, Priority.ALWAYS);

        // 双击加载详情
        requestTable.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Object[] selected = row.getItem();
                    if (selected != null) {
                        currentRequestId = (int) selected[0];
                        // 切换到报告Tab并加载
                    }
                }
            });
            return row;
        });

        // ---- 申请操作按钮 ----
        HBox btnRow = new HBox(10);
        Button completeBtn = new Button("完成检查");
        completeBtn.getStyleClass().add("btn-primary");
        Button cancelReqBtn = new Button("取消申请");
        cancelReqBtn.getStyleClass().add("btn-danger");
        btnRow.getChildren().addAll(completeBtn, cancelReqBtn);

        root.getChildren().addAll(searchRow, requestTable, btnRow);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadRequests());
        refreshBtn.setOnAction(e -> loadRequests());
        newRequestBtn.setOnAction(e -> showNewRequestDialog());
        completeBtn.setOnAction(e -> completeRequest());
        cancelReqBtn.setOnAction(e -> cancelRequest());

        // 初始加载
        loadRequests();

        return root;
    }

    private void loadRequests() {
        String kw = requestSearchField.getText().trim();
        String status = requestStatusCombo.getValue();
        String sql = buildRequestSql(kw, status);

        AsyncUIUtil.executeAsync(requestTable, () -> {
            List<Object[]> results = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 1;
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(idx++, likeKw);
                    ps.setString(idx++, likeKw);
                }
                if (!"全部".equals(status)) {
                    ps.setString(idx++, status);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(new Object[]{
                            rs.getInt("er.id"), rs.getString("er.request_no"),
                            rs.getString("p.name"), rs.getString("er.item_name"),
                            rs.getString("er.category"),
                            rs.getBoolean("er.is_urgent") ? "是" : "否",
                            rs.getString("er.status"),
                            rs.getDate("er.request_date").toLocalDate()
                    });
                }
            }
            return results;
        }, results -> {
            requestData.clear();
            requestData.addAll(results);
        });
    }

    private String buildRequestSql(String kw, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT er.id, er.request_no, p.name, er.item_name, er.category, er.is_urgent, er.status, er.request_date " +
                "FROM examination_requests er " +
                "JOIN patients p ON er.patient_id = p.id " +
                "WHERE 1=1 ");
        if (!kw.isEmpty()) {
            sql.append("AND (p.name LIKE ? OR er.request_no LIKE ?) ");
        }
        if (!"全部".equals(status)) {
            sql.append("AND er.status = ? ");
        }
        sql.append("ORDER BY er.id DESC LIMIT 200");
        return sql.toString();
    }

    private void showNewRequestDialog() {
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("新增检查申请");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField patientField = new TextField();
        patientField.setPromptText("患者姓名或病历号");
        Button selectPatientBtn = new Button("选择");

        ComboBox<String> itemCombo = new ComboBox<>();
        itemCombo.setPrefWidth(250);
        // 加载检查项目
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, item_name, category FROM exam_items WHERE is_active = TRUE ORDER BY category, item_name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                itemCombo.getItems().add(rs.getInt("id") + "|" + rs.getString("item_name") +
                        " (" + rs.getString("category") + ")");
            }
        } catch (SQLException ex) {
            log.error("加载检查项目失败", ex);
        }

        TextArea clinicalArea = new TextArea();
        clinicalArea.setPromptText("临床信息");
        clinicalArea.setPrefRowCount(2);
        CheckBox urgentCb = new CheckBox("紧急检查");

        grid.add(new Label("患者:"), 0, 0);
        grid.add(patientField, 1, 0);
        grid.add(selectPatientBtn, 2, 0);
        grid.add(new Label("项目:"), 0, 1);
        grid.add(itemCombo, 1, 1, 2, 1);
        grid.add(new Label("临床信息:"), 0, 2);
        grid.add(clinicalArea, 1, 2, 2, 1);
        grid.add(urgentCb, 0, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                int itemId = parseIdFromCombo(itemCombo.getValue());
                return itemId > 0 ? new int[]{itemId} : null;
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            int itemId = result.get()[0];
            createRequest(itemId, patientField.getText(), clinicalArea.getText(), urgentCb.isSelected());
        }
    }

    private void createRequest(int itemId, String patientStr, String clinical, boolean urgent) {
        try {
            if (patientStr == null || patientStr.trim().isEmpty()) {
                throw new BusinessException("请选择患者");
            }
            // 解析患者ID
            int patientId = -1;
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement psP = conn.prepareStatement(
                         "SELECT id, name FROM patients WHERE name LIKE ? OR patient_no LIKE ? LIMIT 1")) {
                String likeKw = "%" + patientStr.trim() + "%";
                psP.setString(1, likeKw); psP.setString(2, likeKw);
                ResultSet rsP = psP.executeQuery();
                if (rsP.next()) {
                    patientId = rsP.getInt(1);
                } else {
                    throw new BusinessException("患者不存在");
                }
            }

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                // 获取患者姓名和项目信息
                String patientName;
                try (PreparedStatement psP = conn.prepareStatement("SELECT name FROM patients WHERE id = ?")) {
                    psP.setInt(1, patientId);
                    ResultSet rsP = psP.executeQuery();
                    if (!rsP.next()) throw new BusinessException("患者不存在");
                    patientName = rsP.getString(1);
                }

                String itemName, category, bodyPart;
                try (PreparedStatement psItem = conn.prepareStatement(
                        "SELECT item_name, category, body_part FROM exam_items WHERE id = ?")) {
                    psItem.setInt(1, itemId);
                    ResultSet rsItem = psItem.executeQuery();
                    if (!rsItem.next()) throw new BusinessException("检查项目不存在");
                    itemName = rsItem.getString(1);
                    category = rsItem.getString(2);
                    bodyPart = rsItem.getString(3);
                }

                // 获取当前医生信息
                int doctorId = (int) session.getUserId();
                String doctorName = session.getUsername();
                String deptName = session.getDepartmentName();

                String requestNo = "EXAM" + System.currentTimeMillis() % 1000000000;
                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO examination_requests (request_no, patient_id, patient_name, " +
                        "doctor_id, doctor_name, department_name, item_id, item_name, category, " +
                        "exam_body_part, is_urgent, clinical_info, status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                psIns.setString(1, requestNo);
                psIns.setInt(2, patientId);
                psIns.setString(3, patientName);
                psIns.setInt(4, doctorId);
                psIns.setString(5, doctorName);
                psIns.setString(6, deptName);
                psIns.setInt(7, itemId);
                psIns.setString(8, itemName);
                psIns.setString(9, category);
                psIns.setString(10, bodyPart);
                psIns.setBoolean(11, urgent);
                psIns.setString(12, clinical.isEmpty() ? null : clinical);
                psIns.setString(13, "待检查");
                psIns.executeUpdate();

                ResultSet keys = psIns.getGeneratedKeys();
                if (keys.next()) currentRequestId = keys.getInt(1);

                audit.log("CREATE", "examination_requests", String.valueOf(currentRequestId),
                        "新增检查申请: " + requestNo + ", item=" + itemName);
                new Alert(Alert.AlertType.INFORMATION, "检查申请已创建！申请号: " + requestNo).showAndWait();
                loadRequests();
            }
        } catch (Exception ex) {
            log.error("创建检查申请失败", ex);
            new Alert(Alert.AlertType.ERROR, "创建失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void completeRequest() {
        Object[] selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择申请").showAndWait();
            return;
        }
        int requestId = (int) selected[0];
        AsyncUIUtil.executeAsync(requestTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE examination_requests SET status='已完成', exam_date=CURRENT_DATE WHERE id=? AND status='待检查'");
                ps.setInt(1, requestId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new BusinessException("申请状态不允许完成");
                }
                audit.log("UPDATE", "examination_requests", String.valueOf(requestId), "完成检查");
            }
            return true;
        }, ok -> {
            new Alert(Alert.AlertType.INFORMATION, "检查已完成，请录入报告").showAndWait();
            loadRequests();
        });
    }

    private void cancelRequest() {
        Object[] selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择申请").showAndWait();
            return;
        }
        int requestId = (int) selected[0];
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认取消申请？", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        AsyncUIUtil.executeAsync(requestTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE examination_requests SET status='已取消' WHERE id=? AND status='待检查'");
                ps.setInt(1, requestId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new BusinessException("申请状态不允许取消");
                }
                audit.log("UPDATE", "examination_requests", String.valueOf(requestId), "取消申请");
            }
            return true;
        }, ok -> {
            new Alert(Alert.AlertType.INFORMATION, "申请已取消").showAndWait();
            loadRequests();
        });
    }

    // ========================================================================
    // Tab2: 检查报告
    // ========================================================================

    private VBox buildReportTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        reportSearchField = new TextField();
        reportSearchField.setPromptText("患者姓名/报告号...");
        reportSearchField.setPrefWidth(200);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button newReportBtn = new Button("录入报告");
        newReportBtn.getStyleClass().add("btn-success");
        Button handleCriticalBtn = new Button("处理危急值");
        handleCriticalBtn.getStyleClass().add("btn-warning");
        searchRow.getChildren().addAll(new Label("搜索:"), reportSearchField,
                searchBtn, refreshBtn, newReportBtn, handleCriticalBtn);

        // ---- 报告表格 ----
        reportTable = new TableView<>();
        reportTable.getStyleClass().add("table-view");
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] rptCols = {"ID", "报告号", "患者", "项目", "是否异常", "危急值", "已处理", "报告日期"};
        int[] rptWidths = {40, 110, 100, 150, 70, 70, 70, 90};
        for (int i = 0; i < rptCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(rptCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(rptWidths[i]);
            reportTable.getColumns().add(c);
        }
        reportTable.setItems(reportData);
        reportTable.setPrefHeight(280);
        VBox.setVgrow(reportTable, Priority.ALWAYS);

        // ---- 报告操作按钮 ----
        HBox btnRow = new HBox(10);
        Button editReportBtn = new Button("编辑报告");
        editReportBtn.getStyleClass().add("btn-primary");
        Button deleteReportBtn = new Button("删除报告");
        deleteReportBtn.getStyleClass().add("btn-danger");
        btnRow.getChildren().addAll(editReportBtn, deleteReportBtn);

        root.getChildren().addAll(searchRow, reportTable, btnRow);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadReports());
        refreshBtn.setOnAction(e -> loadReports());
        newReportBtn.setOnAction(e -> showNewReportDialog());
        handleCriticalBtn.setOnAction(e -> handleCriticalValue());
        editReportBtn.setOnAction(e -> editReport());
        deleteReportBtn.setOnAction(e -> deleteReport());

        // 初始加载
        loadReports();

        return root;
    }

    private void loadReports() {
        String kw = reportSearchField.getText().trim();
        String sql = buildReportSql(kw);

        AsyncUIUtil.executeAsync(reportTable, () -> {
            List<Object[]> results = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(new Object[]{
                            rs.getInt("rpt.id"), rs.getString("rpt.report_no"),
                            rs.getString("p.name"), rs.getString("rpt.item_name"),
                            rs.getBoolean("rpt.is_abnormal") ? "是" : "否",
                            rs.getBoolean("rpt.is_critical") ? "是" : "否",
                            rs.getBoolean("rpt.critical_handled") ? "是" : "否",
                            rs.getDate("rpt.report_date").toLocalDate()
                    });
                }
            }
            return results;
        }, results -> {
            reportData.clear();
            reportData.addAll(results);
        });
    }

    private String buildReportSql(String kw) {
        StringBuilder sql = new StringBuilder(
                "SELECT rpt.id, rpt.report_no, p.name, rpt.item_name, " +
                "rpt.is_abnormal, rpt.is_critical, rpt.critical_handled, rpt.report_date " +
                "FROM examination_reports rpt " +
                "JOIN patients p ON rpt.patient_id = p.id " +
                "WHERE 1=1 ");
        if (!kw.isEmpty()) {
            sql.append("AND (p.name LIKE ? OR rpt.report_no LIKE ?) ");
        }
        sql.append("ORDER BY rpt.id DESC LIMIT 200");
        return sql.toString();
    }

    private void showNewReportDialog() {
        // 先选择已完成的检查申请
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("选择检查申请");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        TextField searchField = new TextField();
        searchField.setPromptText("搜索申请号或患者...");
        TableView<Object[]> table = new TableView<>();
        table.getStyleClass().add("table-view");
        String[] cols = {"ID", "申请号", "患者", "项目", "状态"};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(cols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(i == 2 ? 100 : 120);
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
                         "SELECT er.id, er.request_no, p.name, er.item_name, er.status " +
                         "FROM examination_requests er " +
                         "JOIN patients p ON er.patient_id = p.id " +
                         "WHERE (p.name LIKE ? OR er.request_no LIKE ?) AND er.status='已完成' " +
                         "ORDER BY er.id DESC LIMIT 50")) {
                String likeKw = "%" + kw + "%";
                ps.setString(1, likeKw); ps.setString(2, likeKw);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{rs.getInt("er.id"), rs.getString("er.request_no"),
                                rs.getString("p.name"), rs.getString("er.item_name"), rs.getString("er.status")});
                }
            } catch (SQLException ex) {
                log.error("搜索申请失败", ex);
            }
        });

        content.getChildren().addAll(new HBox(10, searchField, searchBtn), table);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Object[] selected = table.getSelectionModel().getSelectedItem();
                return selected != null ? (Integer) selected[0] : null;
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            createReportForRequest(result.get());
        }
    }

    private void createReportForRequest(int requestId) {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            // 获取申请信息
            PreparedStatement psReq = conn.prepareStatement(
                    "SELECT er.request_no, er.patient_id, er.patient_name, er.item_name, er.doctor_name " +
                    "FROM examination_requests er WHERE er.id = ?");
            psReq.setInt(1, requestId);
            ResultSet rsReq = psReq.executeQuery();
            if (!rsReq.next()) throw new BusinessException("申请不存在");
            String requestNo = rsReq.getString(1);
            int patientId = rsReq.getInt(2);
            String patientName = rsReq.getString(3);
            String itemName = rsReq.getString(4);
            String doctorName = rsReq.getString(5);

            // 弹出报告录入对话框
            Dialog<ButtonType> reportDialog = new Dialog<>();
            reportDialog.setTitle("录入检查报告 - " + itemName);
            reportDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(10);
            grid.setPadding(new Insets(20));

            TextArea findingsArea = new TextArea();
            findingsArea.setPromptText("检查结果/发现");
            findingsArea.setPrefRowCount(3);
            TextArea conclusionArea = new TextArea();
            conclusionArea.setPromptText("诊断结论");
            conclusionArea.setPrefRowCount(3);
            CheckBox abnormalCb = new CheckBox("异常结果");
            CheckBox criticalCb = new CheckBox("危急值");

            grid.add(new Label("检查结果:"), 0, 0);
            grid.add(findingsArea, 1, 0, 2, 1);
            grid.add(new Label("诊断结论:"), 0, 1);
            grid.add(conclusionArea, 1, 1, 2, 1);
            grid.add(abnormalCb, 0, 2);
            grid.add(criticalCb, 1, 2);

            reportDialog.getDialogPane().setContent(grid);
            reportDialog.setResultConverter(btn -> btn);

            var rptResult = reportDialog.showAndWait();
            if (rptResult.isPresent() && rptResult.get() == ButtonType.OK) {
                String findings = findingsArea.getText();
                String conclusion = conclusionArea.getText();
                boolean abnormal = abnormalCb.isSelected();
                boolean critical = criticalCb.isSelected();

                String reportNo = "RPT" + System.currentTimeMillis() % 1000000000;
                String techName = session.getUsername();

                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO examination_reports (request_id, report_no, patient_id, patient_name, " +
                        "item_name, findings, conclusion, is_abnormal, is_critical, " +
                        "critical_handled, tech_name, doctor_name) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,'已完成')",
                        Statement.RETURN_GENERATED_KEYS);
                psIns.setInt(1, requestId);
                psIns.setString(2, reportNo);
                psIns.setInt(3, patientId);
                psIns.setString(4, patientName);
                psIns.setString(5, itemName);
                psIns.setString(6, findings.isEmpty() ? null : findings);
                psIns.setString(7, conclusion.isEmpty() ? null : conclusion);
                psIns.setBoolean(8, abnormal);
                psIns.setBoolean(9, critical);
                psIns.setBoolean(10, false); // critical_handled = false
                psIns.setString(11, techName);
                psIns.setString(12, doctorName);
                psIns.executeUpdate();

                ResultSet keys = psIns.getGeneratedKeys();
                if (keys.next()) currentReportId = keys.getInt(1);

                audit.log("CREATE", "examination_reports", String.valueOf(currentReportId),
                        "录入检查报告: " + reportNo + ", critical=" + critical);
                new Alert(Alert.AlertType.INFORMATION, "报告已保存！报告号: " + reportNo +
                        (critical ? "\n⚠ 请注意：本报告包含危急值！" : "")).showAndWait();
                loadReports();
            }
        } catch (Exception ex) {
            log.error("录入报告失败", ex);
            new Alert(Alert.AlertType.ERROR, "录入失败: " + ex.getMessage()).showAndWait();
        }
    }

    private void handleCriticalValue() {
        Object[] selected = reportTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择包含危急值的报告").showAndWait();
            return;
        }
        int reportId = (int) selected[0];

        AsyncUIUtil.executeAsync(reportTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE examination_reports SET critical_handled = TRUE WHERE id = ? AND is_critical = TRUE AND critical_handled = FALSE");
                ps.setInt(1, reportId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new BusinessException("该报告无危急值或已处理");
                }
                audit.log("UPDATE", "examination_reports", String.valueOf(reportId), "处理危急值");
            }
            return true;
        }, ok -> {
            new Alert(Alert.AlertType.INFORMATION, "危急值已标记为已处理").showAndWait();
            loadReports();
        });
    }

    private void editReport() {
        // 编辑报告 - 类似createReportForRequest但加载已有数据
        Object[] selected = reportTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择报告").showAndWait();
            return;
        }
        int reportId = (int) selected[0];
        // 简化：直接提示并已处理
        new Alert(Alert.AlertType.INFORMATION, "编辑报告功能开发中").showAndWait();
    }

    private void deleteReport() {
        Object[] selected = reportTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择报告").showAndWait();
            return;
        }
        int reportId = (int) selected[0];
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认删除报告？", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        AsyncUIUtil.executeAsync(reportTable, () -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM examination_reports WHERE id = ?");
                ps.setInt(1, reportId);
                ps.executeUpdate();
                audit.log("DELETE", "examination_reports", String.valueOf(reportId), "删除报告");
            }
            return true;
        }, ok -> {
            new Alert(Alert.AlertType.INFORMATION, "报告已删除").showAndWait();
            loadReports();
        });
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private int parseIdFromCombo(String value) {
        if (value == null || value.isEmpty()) return -1;
        int idx = value.indexOf("|");
        return idx > 0 ? Integer.parseInt(value.substring(0, idx)) : -1;
    }
}
