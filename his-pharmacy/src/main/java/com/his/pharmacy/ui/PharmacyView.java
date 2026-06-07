package com.his.pharmacy.ui;

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

import com.his.shared.ui.AsyncUIUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 药房管理视图 - 生产级
 * T-3.4: 完善药房管理
 * 功能：药品目录、库存管理、采购管理、发药管理、库存预警、审计日志
 */
public class PharmacyView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(PharmacyView.class);
    private final AuditService audit = AuditService.getInstance();
    private final UserSession session = UserSession.getInstance();

    // ========== Tab1: 药品目录 ==========
    private TableView<Object[]> drugTable;
    private ObservableList<Object[]> drugData = FXCollections.observableArrayList();
    private TextField drugSearchField;
    private TextField drugCodeField;
    private TextField drugGenericField;
    private TextField drugTradeField;
    private ComboBox<String> drugTypeCombo;
    private TextField drugSpecField;
    private TextField drugManufacturerField;
    private CheckBox drugNarcoticCb;
    private CheckBox drugPsychoCb;

    // ========== Tab2: 库存管理 ==========
    private TableView<Object[]> inventoryTable;
    private ObservableList<Object[]> inventoryData = FXCollections.observableArrayList();
    private TextField inventorySearchField;
    private Label lowStockLabel;

    // ========== Tab3: 采购管理 ==========
    private TableView<Object[]> purchaseTable;
    private ObservableList<Object[]> purchaseData = FXCollections.observableArrayList();
    private TextField purchaseSearchField;

    // ========== Tab4: 发药管理 ==========
    private TableView<Object[]> dispenseTable;
    private ObservableList<Object[]> dispenseData = FXCollections.observableArrayList();
    private TextField dispenseSearchField;

    public PharmacyView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        Tab drugTab = new Tab("药品目录");
        drugTab.setContent(buildDrugTab());
        drugTab.setClosable(false);

        Tab invTab = new Tab("库存管理");
        invTab.setContent(buildInventoryTab());
        invTab.setClosable(false);

        Tab purchaseTab = new Tab("采购管理");
        purchaseTab.setContent(buildPurchaseTab());
        purchaseTab.setClosable(false);

        Tab dispenseTab = new Tab("发药管理");
        dispenseTab.setContent(buildDispenseTab());
        dispenseTab.setClosable(false);

        getTabs().addAll(drugTab, invTab, purchaseTab, dispenseTab);
    }

    // ========================================================================
    // Tab1: 药品目录
    // ========================================================================

    private VBox buildDrugTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        drugSearchField = new TextField();
        drugSearchField.setPromptText("搜索药品名称...");
        drugSearchField.setPrefWidth(200);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button newDrugBtn = new Button("新增药品");
        newDrugBtn.getStyleClass().add("btn-success");
        searchRow.getChildren().addAll(new Label("搜索:"), drugSearchField, searchBtn, refreshBtn, newDrugBtn);

        // ---- 药品表格 ----
        drugTable = new TableView<>();
        drugTable.getStyleClass().add("table-view");
        drugTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] drugCols = {"ID", "药品编码", "通用名", "商品名", "类型", "规格", "厂商", "毒麻", "精神", "状态"};
        int[] drugWidths = {40, 100, 150, 150, 80, 100, 150, 50, 50, 60};
        for (int i = 0; i < drugCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(drugCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(drugWidths[i]);
            drugTable.getColumns().add(c);
        }
        drugTable.setItems(drugData);
        drugTable.setPrefHeight(250);
        VBox.setVgrow(drugTable, Priority.ALWAYS);

        // 双击加载详情
        drugTable.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    Object[] selected = row.getItem();
                    if (selected != null) {
                        loadDrugDetail((int) selected[0]);
                    }
                }
            });
            return row;
        });

        // ---- 药品表单 ----
        Label formTitle = new Label("药品详情 / 新增");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(8);

        drugCodeField = new TextField(); drugCodeField.setPromptText("药品编码"); drugCodeField.setPrefWidth(120);
        drugGenericField = new TextField(); drugGenericField.setPromptText("通用名"); drugGenericField.setPrefWidth(150);
        drugTradeField = new TextField(); drugTradeField.setPromptText("商品名"); drugTradeField.setPrefWidth(150);
        drugTypeCombo = new ComboBox<>(); drugTypeCombo.getItems().addAll("西药", "中成药", "中草药", "生物制品", "血液制品"); drugTypeCombo.setValue("西药"); drugTypeCombo.setPrefWidth(100);
        drugSpecField = new TextField(); drugSpecField.setPromptText("规格"); drugSpecField.setPrefWidth(120);
        drugManufacturerField = new TextField(); drugManufacturerField.setPromptText("生产厂家"); drugManufacturerField.setPrefWidth(200);
        drugNarcoticCb = new CheckBox("毒麻药品");
        drugPsychoCb = new CheckBox("精神药品");

        Button saveDrugBtn = new Button("保存");
        saveDrugBtn.getStyleClass().add("btn-primary");
        Button clearDrugBtn = new Button("清空");
        clearDrugBtn.getStyleClass().add("btn-outline");

        form.add(new Label("编码:"), 0, 0); form.add(drugCodeField, 1, 0);
        form.add(new Label("通用名:"), 2, 0); form.add(drugGenericField, 3, 0);
        form.add(new Label("商品名:"), 4, 0); form.add(drugTradeField, 5, 0);
        form.add(new Label("类型:"), 0, 1); form.add(drugTypeCombo, 1, 1);
        form.add(new Label("规格:"), 2, 1); form.add(drugSpecField, 3, 1);
        form.add(new Label("厂商:"), 4, 1); form.add(drugManufacturerField, 5, 1);
        form.add(drugNarcoticCb, 0, 2); form.add(drugPsychoCb, 1, 2);
        form.add(new HBox(10, saveDrugBtn, clearDrugBtn), 0, 3, 6, 1);

        root.getChildren().addAll(searchRow, drugTable, formTitle, form);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadDrugs());
        refreshBtn.setOnAction(e -> loadDrugs());
        newDrugBtn.setOnAction(e -> clearDrugForm());
        saveDrugBtn.setOnAction(e -> saveDrug());
        clearDrugBtn.setOnAction(e -> clearDrugForm());

        // 初始加载
        loadDrugs();

        return root;
    }

    private void loadDrugs() {
        String kw = drugSearchField.getText().trim();
        StringBuilder sql = new StringBuilder(
                "SELECT id, drug_code, generic_name, trade_name, drug_type, spec, manufacturer, is_narcotic, is_psychotropic, is_active " +
                "FROM drugs WHERE 1=1 ");
        if (!kw.isEmpty()) {
            sql.append("AND (generic_name LIKE ? OR trade_name LIKE ? OR drug_code LIKE ?) ");
        }
        sql.append("ORDER BY id DESC LIMIT 200");
        String finalSql = sql.toString();

        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(finalSql)) {
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw); ps.setString(3, likeKw);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("drug_code"), rs.getString("generic_name"),
                            rs.getString("trade_name"), rs.getString("drug_type"), rs.getString("spec"),
                            rs.getString("manufacturer"),
                            rs.getBoolean("is_narcotic") ? "是" : "否",
                            rs.getBoolean("is_psychotropic") ? "是" : "否",
                            rs.getBoolean("is_active") ? "启用" : "停用"
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            drugData.setAll(data);
        });
    }

    private int currentDrugId = -1;

    private void loadDrugDetail(int drugId) {
        AsyncUIUtil.executeAsync(() -> {
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT drug_code, generic_name, trade_name, drug_type, spec, manufacturer, " +
                         "is_narcotic, is_psychotropic FROM drugs WHERE id = ?")) {
                ps.setInt(1, drugId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new Object[]{
                        rs.getString("drug_code"), rs.getString("generic_name"),
                        rs.getString("trade_name"), rs.getString("drug_type"),
                        rs.getString("spec"), rs.getString("manufacturer"),
                        rs.getBoolean("is_narcotic"), rs.getBoolean("is_psychotropic")
                    };
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        }, detail -> {
            if (detail != null) {
                currentDrugId = drugId;
                drugCodeField.setText((String) detail[0]);
                drugGenericField.setText((String) detail[1]);
                drugTradeField.setText((String) detail[2]);
                drugTypeCombo.setValue((String) detail[3]);
                drugSpecField.setText((String) detail[4]);
                drugManufacturerField.setText((String) detail[5]);
                drugNarcoticCb.setSelected((Boolean) detail[6]);
                drugPsychoCb.setSelected((Boolean) detail[7]);
            }
        });
    }

    private void clearDrugForm() {
        currentDrugId = -1;
        drugCodeField.clear();
        drugGenericField.clear();
        drugTradeField.clear();
        drugTypeCombo.setValue("西药");
        drugSpecField.clear();
        drugManufacturerField.clear();
        drugNarcoticCb.setSelected(false);
        drugPsychoCb.setSelected(false);
    }

    private void saveDrug() {
        try {
            String code = ValidationUtil.requireNonBlank(drugCodeField.getText(), "药品编码");
            String generic = ValidationUtil.requireNonBlank(drugGenericField.getText(), "通用名");
            String trade = drugTradeField.getText();
            String type = drugTypeCombo.getValue();
            String spec = drugSpecField.getText();
            String manufacturer = drugManufacturerField.getText();
            boolean narcotic = drugNarcoticCb.isSelected();
            boolean psycho = drugPsychoCb.isSelected();

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                if (currentDrugId <= 0) {
                    // 新增
                    PreparedStatement psCheck = conn.prepareStatement("SELECT id FROM drugs WHERE drug_code = ?");
                    psCheck.setString(1, code);
                    ResultSet rsCheck = psCheck.executeQuery();
                    if (rsCheck.next()) throw new BusinessException("药品编码已存在");

                    PreparedStatement psIns = conn.prepareStatement(
                            "INSERT INTO drugs (drug_code, generic_name, trade_name, drug_type, spec, " +
                            "manufacturer, is_narcotic, is_psychotropic) " +
                            "VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                    psIns.setString(1, code);
                    psIns.setString(2, generic);
                    psIns.setString(3, trade.isEmpty() ? null : trade);
                    psIns.setString(4, type);
                    psIns.setString(5, spec.isEmpty() ? null : spec);
                    psIns.setString(6, manufacturer.isEmpty() ? null : manufacturer);
                    psIns.setBoolean(7, narcotic);
                    psIns.setBoolean(8, psycho);
                    psIns.executeUpdate();

                    ResultSet keys = psIns.getGeneratedKeys();
                    if (keys.next()) currentDrugId = keys.getInt(1);

                    audit.log("CREATE", "drugs", String.valueOf(currentDrugId),
                            "新增药品: " + code + " " + generic);
                } else {
                    // 更新
                    PreparedStatement psUpd = conn.prepareStatement(
                            "UPDATE drugs SET drug_code=?, generic_name=?, trade_name=?, drug_type=?, spec=?, " +
                            "manufacturer=?, is_narcotic=?, is_psychotropic=? WHERE id=?");
                    psUpd.setString(1, code);
                    psUpd.setString(2, generic);
                    psUpd.setString(3, trade.isEmpty() ? null : trade);
                    psUpd.setString(4, type);
                    psUpd.setString(5, spec.isEmpty() ? null : spec);
                    psUpd.setString(6, manufacturer.isEmpty() ? null : manufacturer);
                    psUpd.setBoolean(7, narcotic);
                    psUpd.setBoolean(8, psycho);
                    psUpd.setInt(9, currentDrugId);
                    psUpd.executeUpdate();

                    audit.log("UPDATE", "drugs", String.valueOf(currentDrugId),
                            "更新药品: " + code + " " + generic);
                }
                new Alert(Alert.AlertType.INFORMATION, "保存成功").showAndWait();
                loadDrugs();
            }
        } catch (Exception ex) {
            log.error("保存药品失败", ex);
            new Alert(Alert.AlertType.ERROR, "保存失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // Tab2: 库存管理
    // ========================================================================

    private VBox buildInventoryTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        inventorySearchField = new TextField();
        inventorySearchField.setPromptText("搜索药品名称或编码...");
        inventorySearchField.setPrefWidth(200);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button addStockBtn = new Button("入库");
        addStockBtn.getStyleClass().add("btn-success");
        lowStockLabel = new Label("低库存预警: 0 项");
        lowStockLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #e74c3c;");
        searchRow.getChildren().addAll(new Label("搜索:"), inventorySearchField, searchBtn, refreshBtn, addStockBtn, lowStockLabel);

        // ---- 库存表格 ----
        inventoryTable = new TableView<>();
        inventoryTable.getStyleClass().add("table-view");
        inventoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] invCols = {"ID", "药品", "规格", "批号", "效期", "库存量", "最低库存", "零售价", "位置"};
        int[] invWidths = {40, 150, 100, 80, 90, 70, 70, 70, 80};
        for (int i = 0; i < invCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(invCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(invWidths[i]);
            inventoryTable.getColumns().add(c);
        }
        inventoryTable.setItems(inventoryData);
        inventoryTable.setPrefHeight(300);
        VBox.setVgrow(inventoryTable, Priority.ALWAYS);

        root.getChildren().addAll(searchRow, inventoryTable);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadInventory());
        refreshBtn.setOnAction(e -> loadInventory());
        addStockBtn.setOnAction(e -> showAddStockDialog());

        // 初始加载
        loadInventory();

        return root;
    }

    private void loadInventory() {
        String kw = inventorySearchField.getText().trim();
        StringBuilder sql = new StringBuilder(
                "SELECT di.id, di.drug_name, di.drug_spec, di.batch_no, di.expiry_date, " +
                "di.stock_qty, di.min_stock_qty, di.retail_price, di.storage_loc " +
                "FROM drug_inventory di WHERE 1=1 ");
        if (!kw.isEmpty()) {
            sql.append("AND (di.drug_name LIKE ? OR di.batch_no LIKE ?) ");
        }
        sql.append("ORDER BY di.stock_qty ASC LIMIT 300");
        String finalSql = sql.toString();

        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(finalSql)) {
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("drug_name"), rs.getString("drug_spec"),
                            rs.getString("batch_no"), rs.getDate("expiry_date"),
                            rs.getBigDecimal("stock_qty"), rs.getBigDecimal("min_stock_qty"),
                            rs.getBigDecimal("retail_price"), rs.getString("storage_loc")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            inventoryData.setAll(data);
            int lowStockCount = 0;
            for (Object[] row : data) {
                BigDecimal stock = (BigDecimal) row[5];
                BigDecimal minStock = (BigDecimal) row[6];
                if (stock.compareTo(minStock) <= 0) lowStockCount++;
            }
            lowStockLabel.setText("低库存预警: " + lowStockCount + " 项");
        });
    }

    private void showAddStockDialog() {
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("药品入库");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<String> drugCombo = new ComboBox<>();
        drugCombo.setPrefWidth(200);
        // 异步加载药品
        AsyncUIUtil.executeAsync(() -> {
            List<String> items = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, generic_name, spec FROM drugs WHERE is_active = TRUE ORDER BY generic_name LIMIT 500")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(rs.getInt("id") + "|" + rs.getString("generic_name") + " (" + rs.getString("spec") + ")");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return items;
        }, items -> {
            drugCombo.getItems().setAll(items);
        });

        TextField batchField = new TextField(); batchField.setPromptText("批号");
        TextField expiryField = new TextField(); expiryField.setPromptText("效期 yyyy-mm-dd");
        TextField qtyField = new TextField(); qtyField.setPromptText("入库数量");
        TextField priceField = new TextField(); priceField.setPromptText("零售价");
        TextField locField = new TextField(); locField.setPromptText("存放位置");

        grid.add(new Label("药品:"), 0, 0); grid.add(drugCombo, 1, 0, 2, 1);
        grid.add(new Label("批号:"), 0, 1); grid.add(batchField, 1, 1);
        grid.add(new Label("效期:"), 2, 1); grid.add(expiryField, 3, 1);
        grid.add(new Label("数量:"), 0, 2); grid.add(qtyField, 1, 2);
        grid.add(new Label("零售价:"), 2, 2); grid.add(priceField, 3, 2);
        grid.add(new Label("位置:"), 0, 3); grid.add(locField, 1, 3, 3, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                int drugId = parseIdFromCombo(drugCombo.getValue());
                return drugId > 0 ? new int[]{drugId} : null;
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            int drugId = result.get()[0];
            addStock(drugId, batchField.getText(), expiryField.getText(),
                    qtyField.getText(), priceField.getText(), locField.getText());
        }
    }

    private void addStock(int drugId, String batchNo, String expiryStr, String qtyStr, String priceStr, String loc) {
        try {
            String batch = ValidationUtil.requireNonBlank(batchNo, "批号");
            LocalDate expiry = expiryStr.isEmpty() ? null : LocalDate.parse(expiryStr);
            BigDecimal qty = new BigDecimal(ValidationUtil.requireNonBlank(qtyStr, "数量"));
            BigDecimal price = new BigDecimal(ValidationUtil.requireNonBlank(priceStr, "零售价"));
            String location = loc.isEmpty() ? null : loc;

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                // 获取药品名称
                PreparedStatement psDrug = conn.prepareStatement("SELECT generic_name, spec FROM drugs WHERE id = ?");
                psDrug.setInt(1, drugId);
                ResultSet rsDrug = psDrug.executeQuery();
                if (!rsDrug.next()) throw new BusinessException("药品不存在");
                String drugName = rsDrug.getString(1);
                String drugSpec = rsDrug.getString(2);

                // 检查是否已存在相同批次
                PreparedStatement psCheck = conn.prepareStatement(
                        "SELECT id, stock_qty FROM drug_inventory WHERE drug_id = ? AND batch_no = ?");
                psCheck.setInt(1, drugId);
                psCheck.setString(2, batch);
                ResultSet rsCheck = psCheck.executeQuery();
                if (rsCheck.next()) {
                    // 更新库存
                    BigDecimal newQty = rsCheck.getBigDecimal("stock_qty").add(qty);
                    PreparedStatement psUpd = conn.prepareStatement(
                            "UPDATE drug_inventory SET stock_qty = ?, retail_price = ?, expiry_date = ? WHERE id = ?");
                    psUpd.setBigDecimal(1, newQty);
                    psUpd.setBigDecimal(2, price);
                    psUpd.setDate(3, expiry != null ? Date.valueOf(expiry) : null);
                    psUpd.setInt(4, rsCheck.getInt(1));
                    psUpd.executeUpdate();
                } else {
                    // 新增库存记录
                    PreparedStatement psIns = conn.prepareStatement(
                            "INSERT INTO drug_inventory (drug_id, drug_name, drug_spec, batch_no, expiry_date, " +
                            "stock_qty, retail_price, storage_loc) VALUES (?,?,?,?,?,?,?,?)");
                    psIns.setInt(1, drugId);
                    psIns.setString(2, drugName);
                    psIns.setString(3, drugSpec);
                    psIns.setString(4, batch);
                    psIns.setDate(5, expiry != null ? Date.valueOf(expiry) : null);
                    psIns.setBigDecimal(6, qty);
                    psIns.setBigDecimal(7, price);
                    psIns.setString(8, location);
                    psIns.executeUpdate();
                }

                audit.log("CREATE", "drug_inventory", "?", "药品入库: drugId=" + drugId + ", batch=" + batch + ", qty=" + qty);
                new Alert(Alert.AlertType.INFORMATION, "入库成功").showAndWait();
                loadInventory();
            }
        } catch (Exception ex) {
            log.error("药品入库失败", ex);
            new Alert(Alert.AlertType.ERROR, "入库失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // Tab3: 采购管理
    // ========================================================================

    private VBox buildPurchaseTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        purchaseSearchField = new TextField();
        purchaseSearchField.setPromptText("搜索药品或订单号...");
        purchaseSearchField.setPrefWidth(200);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        Button newPurchaseBtn = new Button("新增采购");
        newPurchaseBtn.getStyleClass().add("btn-success");
        searchRow.getChildren().addAll(new Label("搜索:"), purchaseSearchField, searchBtn, refreshBtn, newPurchaseBtn);

        // ---- 采购表格 ----
        purchaseTable = new TableView<>();
        purchaseTable.getStyleClass().add("table-view");
        purchaseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] purCols = {"ID", "订单号", "药品", "供应商", "批号", "数量", "单价", "金额", "状态", "日期"};
        int[] purWidths = {40, 110, 150, 120, 80, 70, 70, 80, 70, 90};
        for (int i = 0; i < purCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(purCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(purWidths[i]);
            purchaseTable.getColumns().add(c);
        }
        purchaseTable.setItems(purchaseData);
        purchaseTable.setPrefHeight(300);
        VBox.setVgrow(purchaseTable, Priority.ALWAYS);

        root.getChildren().addAll(searchRow, purchaseTable);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadPurchases());
        refreshBtn.setOnAction(e -> loadPurchases());
        newPurchaseBtn.setOnAction(e -> showNewPurchaseDialog());

        // 初始加载
        loadPurchases();

        return root;
    }

    private void loadPurchases() {
        String kw = purchaseSearchField.getText().trim();
        StringBuilder sql = new StringBuilder(
                "SELECT id, order_no, drug_name, supplier, batch_no, purchase_qty, unit_price, total_amount, status, purchase_date " +
                "FROM drug_purchase_orders WHERE 1=1 ");
        if (!kw.isEmpty()) {
            sql.append("AND (order_no LIKE ? OR drug_name LIKE ? OR supplier LIKE ?) ");
        }
        sql.append("ORDER BY id DESC LIMIT 200");
        String finalSql = sql.toString();

        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(finalSql)) {
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw); ps.setString(3, likeKw);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("id"), rs.getString("order_no"), rs.getString("drug_name"),
                            rs.getString("supplier"), rs.getString("batch_no"),
                            rs.getBigDecimal("purchase_qty"), rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("total_amount"), rs.getString("status"),
                            rs.getDate("purchase_date").toLocalDate()
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            purchaseData.setAll(data);
        });
    }

    private void showNewPurchaseDialog() {
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("新增采购订单");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<String> drugCombo = new ComboBox<>();
        drugCombo.setPrefWidth(200);
        // 异步加载药品
        AsyncUIUtil.executeAsync(() -> {
            List<String> items = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, generic_name, spec FROM drugs WHERE is_active = TRUE ORDER BY generic_name")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(rs.getInt("id") + "|" + rs.getString("generic_name") + " (" + rs.getString("spec") + ")");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return items;
        }, items -> {
            drugCombo.getItems().setAll(items);
        });

        TextField supplierField = new TextField(); supplierField.setPromptText("供应商");
        TextField batchField = new TextField(); batchField.setPromptText("批号");
        TextField expiryField = new TextField(); expiryField.setPromptText("效期 yyyy-mm-dd");
        TextField qtyField = new TextField(); qtyField.setPromptText("采购数量");
        TextField priceField = new TextField(); priceField.setPromptText("单价");

        grid.add(new Label("药品:"), 0, 0); grid.add(drugCombo, 1, 0, 2, 1);
        grid.add(new Label("供应商:"), 0, 1); grid.add(supplierField, 1, 1, 2, 1);
        grid.add(new Label("批号:"), 0, 2); grid.add(batchField, 1, 2);
        grid.add(new Label("效期:"), 2, 2); grid.add(expiryField, 3, 2);
        grid.add(new Label("数量:"), 0, 3); grid.add(qtyField, 1, 3);
        grid.add(new Label("单价:"), 2, 3); grid.add(priceField, 3, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                int drugId = parseIdFromCombo(drugCombo.getValue());
                return drugId > 0 ? new int[]{drugId} : null;
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            int drugId = result.get()[0];
            createPurchaseOrder(drugId, supplierField.getText(), batchField.getText(),
                    expiryField.getText(), qtyField.getText(), priceField.getText());
        }
    }

    private void createPurchaseOrder(int drugId, String supplier, String batch, String expiryStr,
                                   String qtyStr, String priceStr) {
        try {
            String supp = ValidationUtil.requireNonBlank(supplier, "供应商");
            String bt = ValidationUtil.requireNonBlank(batch, "批号");
            LocalDate expiry = expiryStr.isEmpty() ? null : LocalDate.parse(expiryStr);
            BigDecimal qty = new BigDecimal(ValidationUtil.requireNonBlank(qtyStr, "采购数量"));
            BigDecimal price = new BigDecimal(ValidationUtil.requireNonBlank(priceStr, "单价"));
            BigDecimal total = qty.multiply(price);

            try (Connection conn = ConnectionPool.getInstance().getConnection()) {
                PreparedStatement psDrug = conn.prepareStatement("SELECT generic_name FROM drugs WHERE id = ?");
                psDrug.setInt(1, drugId);
                ResultSet rsDrug = psDrug.executeQuery();
                if (!rsDrug.next()) throw new BusinessException("药品不存在");
                String drugName = rsDrug.getString(1);

                String orderNo = "PO" + System.currentTimeMillis() % 1000000000;
                PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO drug_purchase_orders (order_no, drug_id, drug_name, supplier, " +
                        "batch_no, expiry_date, purchase_qty, unit_price, total_amount, status, operator_name) " +
                        "VALUES (?,?,?,?,?,?,?,?,'已入库',?)");
                psIns.setString(1, orderNo);
                psIns.setInt(2, drugId);
                psIns.setString(3, drugName);
                psIns.setString(4, supp);
                psIns.setString(5, bt);
                psIns.setDate(6, expiry != null ? Date.valueOf(expiry) : null);
                psIns.setBigDecimal(7, qty);
                psIns.setBigDecimal(8, price);
                psIns.setBigDecimal(9, total);
                psIns.setString(10, session.getUsername());
                psIns.executeUpdate();

                audit.log("CREATE", "drug_purchase_orders", "?", "采购订单: " + orderNo + ", drug=" + drugName);
                new Alert(Alert.AlertType.INFORMATION, "采购订单已创建！订单号: " + orderNo).showAndWait();
                loadPurchases();
            }
        } catch (Exception ex) {
            log.error("创建采购订单失败", ex);
            new Alert(Alert.AlertType.ERROR, "创建失败: " + ex.getMessage()).showAndWait();
        }
    }

    // ========================================================================
    // Tab4: 发药管理
    // ========================================================================

    private VBox buildDispenseTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f5f6fa;");

        // ---- 顶部搜索栏 ----
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        dispenseSearchField = new TextField();
        dispenseSearchField.setPromptText("处方号或患者姓名...");
        dispenseSearchField.setPrefWidth(200);
        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary");
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-outline");
        searchRow.getChildren().addAll(new Label("搜索:"), dispenseSearchField, searchBtn, refreshBtn);

        // ---- 待发药处方表格 ----
        dispenseTable = new TableView<>();
        dispenseTable.getStyleClass().add("table-view");
        dispenseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] dispCols = {"处方ID", "处方号", "患者", "药品数", "总金额", "状态"};
        int[] dispWidths = {70, 120, 100, 70, 80, 70};
        for (int i = 0; i < dispCols.length; i++) {
            TableColumn<Object[], String> c = new TableColumn<>(dispCols[i]);
            final int idx = i;
            c.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            c.setPrefWidth(dispWidths[i]);
            dispenseTable.getColumns().add(c);
        }
        dispenseTable.setItems(dispenseData);
        dispenseTable.setPrefHeight(300);
        VBox.setVgrow(dispenseTable, Priority.ALWAYS);

        // ---- 发药按钮 ----
        HBox btnRow = new HBox(10);
        Button dispenseBtn = new Button("确认发药");
        dispenseBtn.getStyleClass().add("btn-primary");
        btnRow.getChildren().addAll(dispenseBtn);

        root.getChildren().addAll(searchRow, dispenseTable, btnRow);

        // ---- 事件绑定 ----
        searchBtn.setOnAction(e -> loadDispenseList());
        refreshBtn.setOnAction(e -> loadDispenseList());
        dispenseBtn.setOnAction(e -> dispensePrescription());

        // 初始加载
        loadDispenseList();

        return root;
    }

    private void loadDispenseList() {
        String kw = dispenseSearchField.getText().trim();
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.prescription_no, p.patient_name, " +
                "(SELECT COUNT(*) FROM prescription_items pi WHERE pi.prescription_id = p.id) as item_count, " +
                "p.total_amount, p.status " +
                "FROM prescriptions p WHERE p.status = '已缴费' ");
        if (!kw.isEmpty()) {
            sql.append("AND (p.prescription_no LIKE ? OR p.patient_name LIKE ?) ");
        }
        sql.append("ORDER BY p.id ASC LIMIT 100");
        String finalSql = sql.toString();

        AsyncUIUtil.executeAsync(() -> {
            List<Object[]> data = new ArrayList<>();
            try (Connection conn = ConnectionPool.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(finalSql)) {
                if (!kw.isEmpty()) {
                    String likeKw = "%" + kw + "%";
                    ps.setString(1, likeKw); ps.setString(2, likeKw);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                            rs.getInt("p.id"), rs.getString("p.prescription_no"), rs.getString("p.patient_name"),
                            rs.getInt("item_count"), rs.getBigDecimal("p.total_amount"), rs.getString("p.status")
                    });
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return data;
        }, data -> {
            dispenseData.setAll(data);
        });
    }

    private void dispensePrescription() {
        Object[] selected = dispenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "请先选择处方").showAndWait();
            return;
        }
        int prescriptionId = (int) selected[0];
        String prescriptionNo = (String) selected[1];

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "确认发药？\n处方号: " + prescriptionNo, ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 更新处方状态
                PreparedStatement psUpd = conn.prepareStatement(
                        "UPDATE prescriptions SET status = '已发药' WHERE id = ? AND status = '已缴费'");
                psUpd.setInt(1, prescriptionId);
                int updated = psUpd.executeUpdate();
                if (updated == 0) throw new BusinessException("处方状态已变更，请刷新");

                // 扣减库存
                PreparedStatement psItems = conn.prepareStatement(
                        "SELECT pi.drug_id, pi.drug_name, pi.quantity FROM prescription_items pi WHERE pi.prescription_id = ?");
                psItems.setInt(1, prescriptionId);
                ResultSet rsItems = psItems.executeQuery();
                while (rsItems.next()) {
                    int drugId = rsItems.getInt(1);
                    BigDecimal qty = rsItems.getBigDecimal(3);

                    // 扣减库存（按批号先进先出）
                    PreparedStatement psInv = conn.prepareStatement(
                            "SELECT id, stock_qty FROM drug_inventory WHERE drug_id = ? AND stock_qty > 0 " +
                            "ORDER BY expiry_date ASC, id ASC LIMIT 1");
                    psInv.setInt(1, drugId);
                    ResultSet rsInv = psInv.executeQuery();
                    if (rsInv.next()) {
                        BigDecimal newStock = rsInv.getBigDecimal("stock_qty").subtract(qty);
                        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                            throw new BusinessException("药品库存不足: " + rsItems.getString(2));
                        }
                        PreparedStatement psUpdInv = conn.prepareStatement(
                                "UPDATE drug_inventory SET stock_qty = ? WHERE id = ?");
                        psUpdInv.setBigDecimal(1, newStock);
                        psUpdInv.setInt(2, rsInv.getInt(1));
                        psUpdInv.executeUpdate();
                    } else {
                        throw new BusinessException("药品无库存: " + rsItems.getString(2));
                    }
                }

                conn.commit();
                audit.log("UPDATE", "prescriptions", String.valueOf(prescriptionId),
                        "发药: prescriptionNo=" + prescriptionNo);
                new Alert(Alert.AlertType.INFORMATION, "发药成功！").showAndWait();
                loadDispenseList();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            log.error("发药失败", ex);
            new Alert(Alert.AlertType.ERROR, "发药失败: " + ex.getMessage()).showAndWait();
        }
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
