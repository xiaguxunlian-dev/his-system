package com.his.admin.ui;

import com.his.admin.repository.AdminRepository;
import com.his.auth.AuditService;
import com.his.auth.UserRole;
import com.his.auth.UserSession;
import com.his.shared.ui.AsyncUIUtil;
import java.util.concurrent.Callable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 系统管理视图 - 生产就绪版
 * <p>
 * 提供系统管理功能，包括：
 * 1. 用户管理（增删改查用户、重置密码、启用/停用、解锁）
 * 2. 审计日志（查询、筛选、分页查看操作日志）
 * 3. 系统配置（管理系统配置项）
 * 4. 系统监控（连接池状态、系统信息）
 * </p>
 *
 * <p><b>权限要求:</b> 管理员 ({@link UserRole#管理员})</p>
 *
 * @author HIS Team
 * @since 1.0.0
 */
public class AdminView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(AdminView.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AdminRepository repo = new AdminRepository();
    private final AuditService audit = AuditService.getInstance();

    private static final Color COLOR_BLUE   = Color.valueOf("#3498db");
    private static final Color COLOR_GREEN  = Color.valueOf("#2ecc71");
    private static final Color COLOR_RED    = Color.valueOf("#e74c3c");
    private static final Color COLOR_ORANGE = Color.valueOf("#e67e22");
    private static final Color COLOR_PURPLE = Color.valueOf("#9b59b6");

    // ========================================================================
    //  用户管理 Tab (Tab 1) - 成员变量
    // ========================================================================

    private TableView<Object[]> userTable;
    private ComboBox<String> roleCombo;
    private ComboBox<Map<String, Object>> deptCombo;
    private TextField userUsernameField;
    private TextField userDisplayNameField;
    private PasswordField userPasswordField;
    private CheckBox userActiveCheck;
    private Button userSaveBtn;
    private int editingUserId = -1; // -1 表示新增模式

    // ========================================================================
    //  审计日志 Tab (Tab 2) - 成员变量
    // ========================================================================

    private TableView<Object[]> auditTable;
    private TextField auditUsernameField;
    private ComboBox<String> auditActionCombo;
    private ComboBox<String> auditTableCombo;
    private DatePicker auditStartPicker;
    private DatePicker auditEndPicker;
    private TextField auditDetailField; // 详情筛选
    private Pagination auditPagination;
    private static final int AUDIT_PAGE_SIZE = 50;
    private int auditTotalPages = 1;

    // ========================================================================
    //  系统配置 Tab (Tab 3) - 成员变量
    // ========================================================================

    private TableView<Object[]> configTable;
    private TextField configKeyField;
    private TextField configValueField;
    private TextField configDescField;
    private ComboBox<String> configModuleCombo;
    private int editingConfigId = -1;

    // ========================================================================
    //  构造函数
    // ========================================================================

    public AdminView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab userTab  = createTab("用户管理",  buildUserManagementTab());
        Tab auditTab = createTab("审计日志", buildAuditLogTab());
        Tab configTab = createTab("系统配置", buildSystemConfigTab());
        Tab monitorTab = createTab("系统监控", buildSystemMonitorTab());

        getTabs().addAll(userTab, auditTab, configTab, monitorTab);

        // 初始加载数据
        refreshUserTable();
        refreshConfigTable();
    }

    // ========================================================================
    //  Tab 工厂方法
    // ========================================================================

    private Tab createTab(String title, Node content) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        tab.setClosable(false);
        return tab;
    }

    // ########################################################################
    //  Tab 1: 用户管理
    // ########################################################################

    private VBox buildUserManagementTab() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // ---- 用户列表区域 ----
        Label listLabel = new Label("用户列表");
        listLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        userTable = createUserTable();
        userTable.setPrefHeight(350);
        VBox.setVgrow(userTable, Priority.ALWAYS);

        // ---- 编辑区域 ----
        Label editLabel = new Label("用户信息");
        editLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        GridPane editForm = buildUserEditForm();
        editForm.setHgap(10);
        editForm.setVgap(8);
        editForm.setPadding(new Insets(5, 0, 5, 0));

        // ---- 操作按钮 ----
        HBox btnBar = new HBox(10);
        btnBar.setAlignment(Pos.CENTER_LEFT);
        Button addBtn = new Button("新增用户");
        addBtn.getStyleClass().add("btn-primary");
        Button resetPwdBtn = new Button("重置密码");
        resetPwdBtn.getStyleClass().add("btn-warning");
        Button toggleBtn = new Button("启用/停用");
        toggleBtn.getStyleClass().add("btn-warning");
        Button unlockBtn = new Button("解锁账号");
        unlockBtn.getStyleClass().add("btn-success");
        Button deleteBtn = new Button("删除用户");
        deleteBtn.getStyleClass().add("btn-danger");
        Button refreshBtn = new Button("刷新列表");
        btnBar.getChildren().addAll(addBtn, resetPwdBtn, toggleBtn, unlockBtn, deleteBtn, refreshBtn);

        // ---- 事件绑定 ----
        addBtn.setOnAction(e -> startAddUser());
        resetPwdBtn.setOnAction(e -> handleResetPassword());
        toggleBtn.setOnAction(e -> handleToggleUserActive());
        unlockBtn.setOnAction(e -> handleUnlockUser());
        deleteBtn.setOnAction(e -> handleDeleteUser());
        refreshBtn.setOnAction(e -> refreshUserTable());
        userTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) loadUserToForm(sel); });

        root.getChildren().addAll(listLabel, userTable, editLabel, editForm, btnBar);
        startAddUser(); // 默认进入新增模式
        return root;
    }

    private TableView<Object[]> createUserTable() {
        TableView<Object[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] cols = {"ID", "用户名", "姓名", "角色", "科室", "状态", "失败次数", "锁定至", "最后登录", "创建时间"};
        for (int i = 0; i < cols.length; i++) {
            final int idx = i;
            TableColumn<Object[], String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                if (v instanceof Timestamp) return new javafx.beans.property.SimpleStringProperty(
                        ((Timestamp) v).toLocalDateTime().format(DATE_TIME_FMT));
                if (v instanceof Boolean) return new javafx.beans.property.SimpleStringProperty(
                        (Boolean) v ? "✓ 启用" : "✗ 停用");
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            col.setStyle("-fx-alignment: " + (idx == 0 ? "CENTER" : "CENTER-LEFT") + ";");
            if (idx == 0) col.setPrefWidth(50);
            table.getColumns().add(col);
        }
        return table;
    }

    private GridPane buildUserEditForm() {
        GridPane grid = new GridPane();
        userUsernameField = new TextField();
        userDisplayNameField = new TextField();
        userPasswordField = new PasswordField();
        userPasswordField.setPromptText("新增时必填，修改时留空表示不修改密码");
        roleCombo = new ComboBox<>();
        for (UserRole r : UserRole.values()) {
            roleCombo.getItems().add(r.name());
        }
        roleCombo.setValue(UserRole.挂号员.name());
        deptCombo = new ComboBox<>();
        loadDepartments();
        userActiveCheck = new CheckBox("启用");
        userActiveCheck.setSelected(true);
        userSaveBtn = new Button("保存");
        userSaveBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-secondary");

        grid.add(new Label("用户名:"), 0, 0);
        grid.add(userUsernameField, 1, 0);
        grid.add(new Label("姓名:"), 2, 0);
        grid.add(userDisplayNameField, 3, 0);
        grid.add(new Label("密码:"), 0, 1);
        grid.add(userPasswordField, 1, 1);
        grid.add(new Label("角色:"), 2, 1);
        grid.add(roleCombo, 3, 1);
        grid.add(new Label("科室:"), 0, 2);
        grid.add(deptCombo, 1, 2);
        grid.add(userActiveCheck, 2, 2);
        HBox btnBox = new HBox(10, userSaveBtn, cancelBtn);
        grid.add(btnBox, 3, 2);

        userSaveBtn.setOnAction(e -> handleSaveUser());
        cancelBtn.setOnAction(e -> startAddUser());

        return grid;
    }

    private void loadDepartments() {
        AsyncUIUtil.executeAsync(() -> repo.getAllDepartments(), depts -> {
            ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
            items.add(null); // 空选项
            items.addAll(depts);
            deptCombo.setItems(items);
            deptCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Map<String, Object> item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "(无科室)" :
                            item.get("name") + " (" + item.get("type") + ")");
                }
            });
            deptCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Map<String, Object> obj) {
                    return obj == null ? "(无科室)" :
                            obj.get("name") + " (" + obj.get("type") + ")";
                }
                @Override public Map<String, Object> fromString(String str) { return null; }
            });
        });
    }

    private void startAddUser() {
        editingUserId = -1;
        userUsernameField.clear();
        userDisplayNameField.clear();
        userPasswordField.clear();
        roleCombo.setValue(UserRole.挂号员.name());
        deptCombo.setValue(null);
        userActiveCheck.setSelected(true);
        userSaveBtn.setText("新增用户");
        userTable.getSelectionModel().clearSelection();
    }

    private void loadUserToForm(Object[] row) {
        if (row == null) return;
        editingUserId = (int) row[0];
        userUsernameField.setText(String.valueOf(row[1]));
        userDisplayNameField.setText(String.valueOf(row[2]));
        String roleName = String.valueOf(row[3]);
        roleCombo.setValue(roleName);
        // 科室
        Integer deptId = row[4] instanceof Integer ? (Integer) row[4] : null;
        if (deptId != null) {
            for (Map<String, Object> d : deptCombo.getItems()) {
                if (d != null && d.get("id").equals(deptId)) {
                    deptCombo.setValue(d);
                    break;
                }
            }
        } else {
            deptCombo.setValue(null);
        }
        userActiveCheck.setSelected(row[5] instanceof Boolean && (Boolean) row[5]);
        userPasswordField.clear();
        userSaveBtn.setText("保存修改");
    }

    private void handleSaveUser() {
        String username = userUsernameField.getText();
        String displayName = userDisplayNameField.getText();
        String password = userPasswordField.getText();
        String role = roleCombo.getValue();
        Map<String, Object> dept = deptCombo.getValue();
        Integer deptId = dept != null ? (Integer) dept.get("id") : null;
        boolean isActive = userActiveCheck.isSelected();

        // 校验
        if (username == null || username.trim().isEmpty()) {
            showWarning("请输入用户名");
            return;
        }
        if (editingUserId == -1 && (password == null || password.isEmpty())) {
            showWarning("请为新增用户设置密码");
            return;
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            showWarning("请输入姓名");
            return;
        }

        try {
            if (editingUserId == -1) {
                // 新增
                boolean exists = repo.usernameExists(username, null);
                if (exists) {
                    showWarning("用户名已存在: " + username);
                    return;
                }
                String hash = AdminRepository.hashPassword(password);
                int newId = repo.createUser(username, hash, displayName, role, deptId);
                audit.log(AuditService.ACTION_CREATE, "system_users",
                        String.valueOf(newId), "新增用户: " + username + "/" + displayName);
                showInfo("新增成功", "用户 [" + username + "] 创建成功");
            } else {
                // 修改（不修改密码）
                repo.updateUser(editingUserId, displayName, role, deptId, isActive);
                if (!password.isEmpty()) {
                    String hash = AdminRepository.hashPassword(password);
                    repo.resetPassword(editingUserId, hash);
                    audit.log(AuditService.ACTION_UPDATE, "system_users",
                            String.valueOf(editingUserId), "重置用户密码: " + username);
                }
                audit.log(AuditService.ACTION_UPDATE, "system_users",
                        String.valueOf(editingUserId), "修改用户信息: " + username);
                showInfo("修改成功", "用户 [" + username + "] 信息已更新");
            }
            refreshUserTable();
            startAddUser();
        } catch (Exception e) {
            log.error("保存用户失败", e);
            showError("保存失败", e.getMessage());
        }
    }

    private void handleResetPassword() {
        Object[] sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showWarning("请先选择用户"); return; }
        int userId = (int) sel[0];
        String username = String.valueOf(sel[1]);

        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("重置密码");
        dlg.setHeaderText("为用户 [" + username + "] 重置密码");
        PasswordField pwdField = new PasswordField();
        pwdField.setPromptText("输入新密码");
        VBox content = new VBox(10, new Label("新密码:"), pwdField);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(btn -> btn == ButtonType.OK ? pwdField.getText() : null);
        dlg.showAndWait().ifPresent(newPwd -> {
            if (newPwd == null || newPwd.isEmpty()) { showWarning("密码不能为空"); return; }
            try {
                String hash = AdminRepository.hashPassword(newPwd);
                repo.resetPassword(userId, hash);
                audit.log(AuditService.ACTION_UPDATE, "system_users",
                        String.valueOf(userId), "管理员重置密码: " + username);
                showInfo("成功", "用户 [" + username + "] 密码已重置");
            } catch (Exception e) {
                log.error("重置密码失败", e);
                showError("失败", e.getMessage());
            }
        });
    }

    private void handleToggleUserActive() {
        Object[] sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showWarning("请先选择用户"); return; }
        int userId = (int) sel[0];
        String username = String.valueOf(sel[1]);
        boolean currentActive = sel[5] instanceof Boolean && (Boolean) sel[5];
        boolean newActive = !currentActive;
        String actStr = newActive ? "启用" : "停用";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认要" + actStr + "用户 [" + username + "] 吗？", ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    repo.setUserActive(userId, newActive);
                    audit.log(AuditService.ACTION_UPDATE, "system_users",
                            String.valueOf(userId), actStr + "用户: " + username);
                    refreshUserTable();
                    showInfo(actStr + "成功", "用户 [" + username + "] 已" + actStr);
                } catch (Exception e) {
                    log.error("设置用户状态失败", e);
                    showError("失败", e.getMessage());
                }
            }
        });
    }

    private void handleUnlockUser() {
        Object[] sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showWarning("请先选择用户"); return; }
        int userId = (int) sel[0];
        String username = String.valueOf(sel[1]);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认要解锁用户 [" + username + "] 吗？", ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    repo.unlockUser(userId);
                    audit.log(AuditService.ACTION_UPDATE, "system_users",
                            String.valueOf(userId), "解锁用户: " + username);
                    refreshUserTable();
                    showInfo("解锁成功", "用户 [" + username + "] 已解锁");
                } catch (Exception e) {
                    log.error("解锁用户失败", e);
                    showError("失败", e.getMessage());
                }
            }
        });
    }

    private void handleDeleteUser() {
        Object[] sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showWarning("请先选择用户"); return; }
        int userId = (int) sel[0];
        String username = String.valueOf(sel[1]);
        String currentUser = UserSession.getInstance().getUsername();
        if (username.equals(currentUser)) {
            showWarning("不能删除自己的账号！");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "确认要删除用户 [" + username + "] 吗？\n此操作不可撤销！",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    repo.deleteUser(userId);
                    audit.log(AuditService.ACTION_DELETE, "system_users",
                            String.valueOf(userId), "删除用户: " + username);
                    refreshUserTable();
                    startAddUser();
                    showInfo("删除成功", "用户 [" + username + "] 已删除");
                } catch (Exception e) {
                    log.error("删除用户失败", e);
                    showError("删除失败", e.getMessage());
                }
            }
        });
    }

    private void refreshUserTable() {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            List<Map<String, Object>> result = repo.getAllUsers();
            for (var d : result) {
                data.add(new Object[]{
                        d.get("id"), d.get("username"), d.get("displayName"),
                        d.get("role"), d.get("deptName"),
                        d.get("isActive"), d.get("failedAttempts"),
                        d.get("lockedUntil"), d.get("lastLogin"), d.get("createdAt")
                });
            }
            return data;
        }, data -> {
            userTable.setItems(FXCollections.observableArrayList(data));
        });
    }

    // ########################################################################
    //  Tab 2: 审计日志
    // ########################################################################

    private VBox buildAuditLogTab() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // ---- 筛选控制栏 ----
        HBox filterBar = buildAuditFilterBar();
        auditStartPicker = (DatePicker) filterBar.getChildren().get(3);
        auditEndPicker = (DatePicker) filterBar.getChildren().get(5);
        auditUsernameField = (TextField) filterBar.getChildren().get(9);
        auditActionCombo = (ComboBox<String>) filterBar.getChildren().get(11);
        auditTableCombo = (ComboBox<String>) filterBar.getChildren().get(13);
        Button queryBtn = (Button) filterBar.getChildren().get(15);

        // ---- 审计日志表格 ----
        auditTable = createAuditTable();
        auditTable.setPrefHeight(450);
        VBox.setVgrow(auditTable, Priority.ALWAYS);

        // ---- 分页控件 ----
        auditPagination = new Pagination(auditTotalPages, 0);
        auditPagination.setPageCount(auditTotalPages);
        auditPagination.setCurrentPageIndex(0);
        auditPagination.setMaxPageIndicatorCount(10);

        queryBtn.setOnAction(e -> {
            auditPagination.setCurrentPageIndex(0);
            loadAuditPage(0);
        });
        auditPagination.currentPageIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            loadAuditPage(newIdx.intValue());
        });

        root.getChildren().addAll(filterBar, auditTable, auditPagination);
        queryBtn.fire(); // 默认加载
        return root;
    }

    private HBox buildAuditFilterBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 5, 0));

        TextField usernameField = new TextField();
        usernameField.setPromptText("用户名 (模糊)");
        usernameField.setPrefWidth(120);

        DatePicker startDp = new DatePicker(LocalDate.now().minusDays(7));
        DatePicker endDp = new DatePicker(LocalDate.now());

        ComboBox<String> actionCb = new ComboBox<>();
        actionCb.getItems().add(""); // 空选项 = 全部
        AsyncUIUtil.executeAsync(() -> repo.getDistinctActions(), actions -> {
            actionCb.getItems().addAll(actions);
        });
        actionCb.setPrefWidth(120);

        ComboBox<String> tableCb = new ComboBox<>();
        tableCb.getItems().add(""); // 空选项 = 全部
        AsyncUIUtil.executeAsync(() -> repo.getDistinctTables(), tables -> {
            tableCb.getItems().addAll(tables);
        });
        tableCb.setPrefWidth(140);

        Button queryBtn = new Button("查询");
        queryBtn.getStyleClass().add("btn-primary");
        Button exportBtn = new Button("导出当前页");
        exportBtn.getStyleClass().add("btn-secondary");

        bar.getChildren().addAll(
                new Label("用户名:"), usernameField,
                new Label("起始:"), startDp, new Label("结束:"), endDp,
                new Label("操作:"), actionCb,
                new Label("表:"), tableCb,
                queryBtn, exportBtn
        );
        return bar;
    }

    private TableView<Object[]> createAuditTable() {
        TableView<Object[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] cols = {"ID", "用户", "操作", "目标表", "目标ID", "详情", "IP", "时间"};
        int[] widths = {50, 100, 80, 120, 100, 200, 120, 150};
        for (int i = 0; i < cols.length; i++) {
            final int idx = i;
            TableColumn<Object[], String> col = new TableColumn<>(cols[i]);
            col.setPrefWidth(widths[i]);
            col.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                if (v instanceof Timestamp) return new javafx.beans.property.SimpleStringProperty(
                        ((Timestamp) v).toLocalDateTime().format(DATE_TIME_FMT));
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            col.setStyle("-fx-alignment: CENTER-LEFT;");
            table.getColumns().add(col);
        }
        return table;
    }

    private void loadAuditPage(int pageIndex) {
        String username = auditUsernameField.getText();
        String action = auditActionCombo.getValue();
        String table = auditTableCombo.getValue();
        LocalDate start = auditStartPicker.getValue();
        LocalDate end = auditEndPicker.getValue();

        AsyncUIUtil.executeAsync(() -> {
            int total = repo.getAuditLogCount(
                    username, action, table, start, end);
            List<Map<String, Object>> data = repo.getAuditLogs(
                    username, action, table, start, end, AUDIT_PAGE_SIZE, pageIndex * AUDIT_PAGE_SIZE);

            List<Object[]> list = new ArrayList<>();
            for (var d : data) {
                list.add(new Object[]{
                        d.get("id"), d.get("username"), d.get("action"),
                        d.get("targetTable"), d.get("targetId"), d.get("detail"),
                        d.get("ipAddress"), d.get("createdAt")
                });
            }
            return new Object[]{total, list};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<Object[]> list = (List<Object[]>) ((Object[]) result)[1];
            int total = (int) ((Object[]) result)[0];
            auditTotalPages = Math.max(1, (total + AUDIT_PAGE_SIZE - 1) / AUDIT_PAGE_SIZE);
            auditPagination.setPageCount(auditTotalPages);
            auditTable.setItems(FXCollections.observableArrayList(list));
        });
    }

    // ########################################################################
    //  Tab 3: 系统配置
    // ########################################################################

    private VBox buildSystemConfigTab() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        Label listLabel = new Label("系统配置列表");
        listLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        configTable = createConfigTable();
        configTable.setPrefHeight(350);
        VBox.setVgrow(configTable, Priority.ALWAYS);

        // 编辑表单
        Label editLabel = new Label("配置信息");
        editLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        GridPane editForm = buildConfigEditForm();
        editForm.setHgap(10);
        editForm.setVgap(8);

        // 按钮栏
        HBox btnBar = new HBox(10);
        btnBar.setAlignment(Pos.CENTER_LEFT);
        Button addBtn = new Button("新增配置");
        addBtn.getStyleClass().add("btn-primary");
        Button saveBtn = new Button("保存修改");
        saveBtn.getStyleClass().add("btn-primary");
        Button deleteBtn = new Button("删除配置");
        deleteBtn.getStyleClass().add("btn-danger");
        Button refreshBtn = new Button("刷新列表");
        btnBar.getChildren().addAll(addBtn, saveBtn, deleteBtn, refreshBtn);

        addBtn.setOnAction(e -> startAddConfig());
        saveBtn.setOnAction(e -> handleSaveConfig());
        deleteBtn.setOnAction(e -> handleDeleteConfig());
        refreshBtn.setOnAction(e -> refreshConfigTable());
        configTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) loadConfigToForm(sel); });

        root.getChildren().addAll(listLabel, configTable, editLabel, editForm, btnBar);
        startAddConfig();
        return root;
    }

    private TableView<Object[]> createConfigTable() {
        TableView<Object[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] cols = {"ID", "配置键", "配置值", "描述", "模块", "更新时间"};
        for (int i = 0; i < cols.length; i++) {
            final int idx = i;
            TableColumn<Object[], String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                if (v instanceof Timestamp) return new javafx.beans.property.SimpleStringProperty(
                        ((Timestamp) v).toLocalDateTime().format(DATE_TIME_FMT));
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            col.setStyle("-fx-alignment: CENTER-LEFT;");
            table.getColumns().add(col);
        }
        return table;
    }

    private GridPane buildConfigEditForm() {
        GridPane grid = new GridPane();
        configKeyField = new TextField();
        configValueField = new TextField();
        configDescField = new TextField();
        configModuleCombo = new ComboBox<>();
        configModuleCombo.getItems().addAll("", "system", "registration", "outpatient", "inpatient",
                "pharmacy", "examination", "emr", "billing", "statistics", "admin");
        configModuleCombo.setValue("system");

        Button saveBtn = new Button("保存");
        saveBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-secondary");

        grid.add(new Label("配置键:"), 0, 0);
        grid.add(configKeyField, 1, 0);
        grid.add(new Label("配置值:"), 2, 0);
        grid.add(configValueField, 3, 0);
        grid.add(new Label("描述:"), 0, 1);
        grid.add(configDescField, 1, 1);
        grid.add(new Label("模块:"), 2, 1);
        grid.add(configModuleCombo, 3, 1);
        HBox btnBox = new HBox(10, saveBtn, cancelBtn);
        grid.add(btnBox, 4, 1);

        saveBtn.setOnAction(e -> handleSaveConfig());
        cancelBtn.setOnAction(e -> startAddConfig());

        return grid;
    }

    private void startAddConfig() {
        editingConfigId = -1;
        configKeyField.clear();
        configValueField.clear();
        configDescField.clear();
        configModuleCombo.setValue("system");
        configTable.getSelectionModel().clearSelection();
    }

    private void loadConfigToForm(Object[] row) {
        if (row == null) return;
        editingConfigId = (int) row[0];
        configKeyField.setText(String.valueOf(row[1]));
        configValueField.setText(String.valueOf(row[2]));
        configDescField.setText(row[3] != null ? row[3].toString() : "");
        configModuleCombo.setValue(row[4] != null ? row[4].toString() : "");
    }

    private void handleSaveConfig() {
        String key = configKeyField.getText();
        String value = configValueField.getText();
        String desc = configDescField.getText();
        String module = configModuleCombo.getValue();

        if (key == null || key.trim().isEmpty()) { showWarning("请输入配置键"); return; }
        if (value == null || value.isEmpty()) { showWarning("请输入配置值"); return; }

        try {
            repo.upsertConfig(key.trim(), value, desc, module);
            audit.log(AuditService.ACTION_UPDATE, "system_configs", key,
                    "保存系统配置: " + key + "=" + value);
            showInfo("保存成功", "配置 [" + key + "] 已保存");
            refreshConfigTable();
            startAddConfig();
        } catch (Exception e) {
            log.error("保存配置失败", e);
            showError("保存失败", e.getMessage());
        }
    }

    private void handleDeleteConfig() {
        Object[] sel = configTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showWarning("请先选择配置项"); return; }
        String key = String.valueOf(sel[1]);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "确认要删除配置 [" + key + "] 吗？", ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    repo.deleteConfig((int) sel[0]);
                    audit.log(AuditService.ACTION_DELETE, "system_configs", key,
                            "删除系统配置: " + key);
                    refreshConfigTable();
                    startAddConfig();
                    showInfo("删除成功", "配置 [" + key + "] 已删除");
                } catch (Exception e) {
                    log.error("删除配置失败", e);
                    showError("删除失败", e.getMessage());
                }
            }
        });
    }

    private void refreshConfigTable() {
        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            List<Map<String, Object>> result = repo.getAllConfigs();
            for (var d : result) {
                data.add(new Object[]{
                        d.get("id"), d.get("configKey"), d.get("configValue"),
                        d.get("description"), d.get("module"), d.get("updatedAt")
                });
            }
            return data;
        }, data -> {
            configTable.setItems(FXCollections.observableArrayList(data));
        });
    }

    // ########################################################################
    //  Tab 4: 系统监控
    // ########################################################################

    private VBox buildSystemMonitorTab() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("系统运行状态");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setTextFill(Color.valueOf("#2c3e50"));

        // 系统信息卡片区域
        HBox infoCards = new HBox(20);
        infoCards.setAlignment(Pos.CENTER);

        // 数据库连接状态
        VBox dbCard = createMonitorCard("数据库连接", "检测中...", COLOR_BLUE);
        // 当前在线用户数（简单估算：audut_logs 当天有登录记录）
        VBox onlineCard = createMonitorCard("今日活跃用户", "加载中...", COLOR_GREEN);
        // 系统启动时间
        VBox uptimeCard = createMonitorCard("系统运行时间", "未知", COLOR_ORANGE);

        infoCards.getChildren().addAll(dbCard, onlineCard, uptimeCard);

        // 刷新按钮
        Button refreshBtn = new Button("刷新状态");
        refreshBtn.getStyleClass().add("btn-primary");
        refreshBtn.setOnAction(e -> {
            checkDbStatus(dbCard);
            loadOnlineUsers(onlineCard);
            loadUptime(uptimeCard);
        });

        root.getChildren().addAll(title, infoCards, refreshBtn);

        // 自动加载
        refreshBtn.fire();
        return root;
    }

    private VBox createMonitorCard(String title, String value, Color accentColor) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15, 20, 15, 20));
        card.setPrefWidth(250);
        card.setStyle("-fx-background-color: white; -fx-border-color: " + toHex(accentColor) +
                "; -fx-border-width: 0 0 3 0; -fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 1, 2);");

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Microsoft YaHei", 12));
        titleLbl.setTextFill(Color.valueOf("#7f8c8d"));

        Label valueLbl = new Label(value);
        valueLbl.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        valueLbl.setTextFill(Color.valueOf("#2c3e50"));
        valueLbl.setUserData(value); // 暂存引用

        card.getChildren().addAll(titleLbl, valueLbl);
        card.setUserData(valueLbl); // 用 setUserData 存储 value Label 引用
        return card;
    }

    @SuppressWarnings("unchecked")
    private void checkDbStatus(VBox card) {
        Label valueLbl = (Label) card.getUserData();
        AsyncUIUtil.executeAsync(() -> repo.checkDbConnection(), ok -> {
            if (ok) {
                valueLbl.setText("\u2713 正常");
                valueLbl.setTextFill(COLOR_GREEN);
            } else {
                valueLbl.setText("\u2717 异常");
                valueLbl.setTextFill(COLOR_RED);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void loadOnlineUsers(VBox card) {
        Label valueLbl = (Label) card.getUserData();
        AsyncUIUtil.executeAsync(() -> repo.getTodayActiveUserCount(), cnt -> {
            valueLbl.setText(String.valueOf(cnt));
        });
    }

    @SuppressWarnings("unchecked")
    private void loadUptime(VBox card) {
        Label valueLbl = (Label) card.getUserData();
        AsyncUIUtil.executeAsync(() -> repo.getFirstLogTime(), firstLog -> {
            if (firstLog != null) {
                long hours = java.time.Duration.between(
                        firstLog.toLocalDateTime(), LocalDateTime.now()).toHours();
                valueLbl.setText(hours + " 小时");
            } else {
                valueLbl.setText("无记录");
            }
        });
    }

    // ========================================================================
    //  工具方法
    // ========================================================================

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255));
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showWarning(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("提示");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
