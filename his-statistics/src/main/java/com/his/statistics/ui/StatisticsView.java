package com.his.statistics.ui;

import com.his.auth.AuditService;
import com.his.auth.UserRole;
import com.his.auth.UserSession;
import com.his.statistics.repository.StatisticsRepository;
import com.his.shared.database.ConnectionPool;
import com.his.shared.exception.DatabaseException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计报表视图 - 生产就绪版
 * <p>
 * 提供挂号统计、科室统计、收入统计、药品消耗统计四大报表模块。
 * 支持日期范围筛选、KPI摘要卡片、柱状图/饼图可视化、Excel导出。
 * </p>
 *
 * <p><b>权限要求:</b> 统计员({@link UserRole#统计员}) 或 管理员</p>
 *
 * @author HIS Team
 * @since 1.0.0
 */
public class StatisticsView extends TabPane {

    private static final Logger log = LoggerFactory.getLogger(StatisticsView.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Color COLOR_BLUE   = Color.valueOf("#3498db");
    private static final Color COLOR_GREEN  = Color.valueOf("#2ecc71");
    private static final Color COLOR_RED    = Color.valueOf("#e74c3c");
    private static final Color COLOR_ORANGE = Color.valueOf("#e67e22");
    private static final Color COLOR_PURPLE = Color.valueOf("#9b59b6");

    private final StatisticsRepository repo = new StatisticsRepository();
    private final AuditService audit = AuditService.getInstance();

    /** KPI 摘要区域 */
    private final Label kpiRegCount    = new Label("--");
    private final Label kpiDeptBusy   = new Label("--");
    private final Label kpiRevenue    = new Label("--");
    private final Label kpiTopDrug    = new Label("--");

    public StatisticsView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        // 全局 KPI 摘要卡片
        VBox kpiBar = buildKpiBar();

        // 四个统计 Tab
        Tab regTab    = createTab("挂号统计", buildRegistrationStats());
        Tab deptTab   = createTab("科室统计", buildDepartmentStats());
        Tab revenueTab = createTab("收入统计", buildRevenueStats());
        Tab drugTab   = createTab("药品统计", buildDrugStats());

        getTabs().addAll(regTab, deptTab, revenueTab, drugTab);

        // Tab 切换时自动刷新 KPI 摘要
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> refreshKpiSummary());
    }

    // ========================================================================
    //  KPI 摘要栏
    // ========================================================================

    private VBox buildKpiBar() {
        VBox container = new VBox(8);
        container.setPadding(new Insets(12, 15, 8, 15));
        container.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        Label title = new Label("统计概览");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        title.setTextFill(Color.valueOf("#2c3e50"));

        HBox cards = new HBox(20);
        cards.setAlignment(Pos.CENTER);

        cards.getChildren().addAll(
                createKpiCard("总挂号量", kpiRegCount,   COLOR_BLUE),
                createKpiCard("最忙科室", kpiDeptBusy,  COLOR_ORANGE),
                createKpiCard("总收入",   kpiRevenue,   COLOR_RED),
                createKpiCard("消耗最多药品", kpiTopDrug, COLOR_GREEN)
        );

        container.getChildren().addAll(title, cards);
        return container;
    }

    private VBox createKpiCard(String label, Label valueLabel, Color accent) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 20, 12, 20));
        card.setPrefWidth(220);
        card.setStyle("-fx-background-color: white; -fx-border-color: " + toHex(accent)
                + "; -fx-border-width: 0 0 3 0; -fx-border-radius: 6; -fx-background-radius: 6;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 1, 2);");

        Text lbl = new Text(label);
        lbl.setFont(Font.font("Microsoft YaHei", 12));
        lbl.setFill(Color.valueOf("#7f8c8d"));

        valueLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        valueLabel.setTextFill(Color.valueOf("#2c3e50"));
        valueLabel.setWrapText(true);
        valueLabel.setMaxWidth(200);

        card.getChildren().addAll(lbl, valueLabel);
        return card;
    }

    /** 刷新全局 KPI 摘要数据（异步 T-10.1.2） */
    private void refreshKpiSummary() {
        Task<Object[]> task = new Task<>() {
            @Override protected Object[] call() throws Exception {
                LocalDate today = LocalDate.now();
                LocalDate thirtyDaysAgo = today.minusDays(30);
                var regData = repo.getRegistrationStats(thirtyDaysAgo, today);
                int totalReg = regData.stream().mapToInt(m -> (int) m.get("count")).sum();
                var deptData = repo.getDepartmentVisitStats(thirtyDaysAgo, today);
                String busiestDept = deptData.isEmpty() ? "--" : String.valueOf(deptData.get(0).get("deptName"));
                var revenueData = repo.getRevenueStats(today.getYear(), today.getMonthValue());
                BigDecimal totalRevenue = revenueData.stream()
                        .map(m -> (BigDecimal) m.get("total"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                var drugData = repo.getDrugConsumptionStats();
                String topDrug = drugData.isEmpty() ? "--" : String.valueOf(drugData.get(0).get("drugName"));
                return new Object[]{totalReg, busiestDept, totalRevenue, topDrug};
            }
        };
        task.setOnSucceeded(e -> {
            Object[] r = task.getValue();
            kpiRegCount.setText(String.valueOf(r[0]));
            kpiDeptBusy.setText((String) r[1]);
            kpiRevenue.setText(formatMoney((BigDecimal) r[2]));
            kpiTopDrug.setText((String) r[3]);
        });
        task.setOnFailed(e -> {
            log.warn("KPI摘要刷新失败: {}", task.getException().getMessage());
        });
        new Thread(task, "KPI-Refresh").start();
    }

    // ========================================================================
    //  挂号统计 Tab
    // ========================================================================

    private VBox buildRegistrationStats() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        // 控制栏
        HBox control = buildDateRangeControl();
        DatePicker startDp = (DatePicker) control.getChildren().get(1);
        DatePicker endDp   = (DatePicker) control.getChildren().get(3);
        startDp.setValue(LocalDate.now().minusDays(30));
        endDp.setValue(LocalDate.now());
        Button queryBtn = (Button) control.lookup(".btn-primary");

        // 摘要信息
        Label summaryLabel = new Label();
        summaryLabel.setFont(Font.font("Microsoft YaHei", 13));
        summaryLabel.setTextFill(Color.valueOf("#7f8c8d"));

        // 柱状图
        BarChart<String, Number> barChart = createBarChart("挂号量趋势", "日期", "挂号数量");
        barChart.setPrefHeight(300);
        barChart.setLegendVisible(false);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        // 表格 + 导出
        TableView<Object[]> table = createTable(new String[]{"日期", "挂号量"});
        table.setPrefHeight(200);
        Button exportBtn = new Button("导出 Excel");
        exportBtn.getStyleClass().add("btn-primary");
        HBox tableHeader = new HBox(10, new Label("明细数据"), exportBtn);
        tableHeader.setAlignment(Pos.CENTER_LEFT);

        // 查询逻辑（异步）
        queryBtn.setOnAction(e -> executeAsync(root,
                // ===== 后台线程：DB 查询 =====
                () -> {
                    LocalDate start = startDp.getValue();
                    LocalDate end   = endDp.getValue();
                    if (start == null || end == null) return null;
                    var data = repo.getRegistrationStats(start, end);
                    int total = data.stream().mapToInt(m -> (int) m.get("count")).sum();
                    return new Object[]{data, total, start, end};
                },
                // ===== JavaFX 线程：更新 UI =====
                result -> {
                    if (result == null) { showWarning("请选择日期范围"); return; }
                    @SuppressWarnings("unchecked")
                    var data  = (List<Map<String, Object>>) ((Object[]) result)[0];
                    int total    = (int) ((Object[]) result)[1];
                    LocalDate start = (LocalDate) ((Object[]) result)[2];
                    LocalDate end   = (LocalDate) ((Object[]) result)[3];
                    populateRegTable(table, data);
                    populateBarChart(barChart, data, "date", "count");
                    summaryLabel.setText(String.format("统计周期: %s ~ %s  |  共 %d 条记录  |  合计: %d 人次",
                            start.format(DATE_FMT), end.format(DATE_FMT), data.size(), total));
                    audit.log(AuditService.ACTION_QUERY, "registrations", "--",
                            String.format("挂号统计: %s~%s, 结果=%d条", start, end, data.size()));
                }
        ));

        exportBtn.setOnAction(e -> {
            if (table.getItems().isEmpty()) { showWarning("无数据可导出"); return; }
            String path = chooseSaveFile("挂号统计_" + LocalDate.now().format(DATE_FMT) + ".xlsx");
            if (path != null) {
                exportToExcel(path, new String[]{"日期", "挂号量"}, table.getItems(),
                        "挂号统计报表");
                audit.log(AuditService.ACTION_EXPORT, "registrations", "--",
                        "导出挂号统计: " + path);
            }
        });

        root.getChildren().addAll(control, summaryLabel, barChart, tableHeader, table);
        queryBtn.fire(); // 默认加载
        return root;
    }

    // ========================================================================
    //  科室统计 Tab
    // ========================================================================

    private VBox buildDepartmentStats() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        HBox control = buildDateRangeControl();
        DatePicker startDp = (DatePicker) control.getChildren().get(1);
        DatePicker endDp   = (DatePicker) control.getChildren().get(3);
        startDp.setValue(LocalDate.now().minusDays(30));
        endDp.setValue(LocalDate.now());
        Button queryBtn = (Button) control.lookup(".btn-primary");

        Label summaryLabel = new Label();
        summaryLabel.setFont(Font.font("Microsoft YaHei", 13));
        summaryLabel.setTextFill(Color.valueOf("#7f8c8d"));

        // 科室分布柱状图
        BarChart<String, Number> barChart = createBarChart("科室就诊量分布", "科室", "就诊量");
        barChart.setPrefHeight(300);
        barChart.setLegendVisible(false);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        TableView<Object[]> table = createTable(new String[]{"科室", "就诊量", "占比"});
        table.setPrefHeight(200);
        Button exportBtn = new Button("导出 Excel");
        exportBtn.getStyleClass().add("btn-primary");
        HBox tableHeader = new HBox(10, new Label("明细数据"), exportBtn);
        tableHeader.setAlignment(Pos.CENTER_LEFT);

        queryBtn.setOnAction(e -> executeAsync(root,
                // 后台：DB 查询
                () -> {
                    LocalDate start = startDp.getValue();
                    LocalDate end   = endDp.getValue();
                    if (start == null || end == null) return null;
                    return repo.getDepartmentVisitStats(start, end);
                },
                // FX 线程：更新 UI
                result -> {
                    if (result == null) { showWarning("请选择日期范围"); return; }
                    @SuppressWarnings("unchecked")
                    var data = (List<Map<String, Object>>) result;
                    long grandTotal = data.stream().mapToLong(m -> ((Number) m.get("count")).longValue()).sum();
                    ObservableList<Object[]> list = FXCollections.observableArrayList();
                    for (var d : data) {
                        long cnt = ((Number) d.get("count")).longValue();
                        double pct = grandTotal > 0 ? (cnt * 100.0 / grandTotal) : 0;
                        list.add(new Object[]{d.get("deptName"), cnt, String.format("%.1f%%", pct)});
                    }
                    table.setItems(list);
                    populateBarChart(barChart, data, "deptName", "count");
                    summaryLabel.setText(String.format("科室统计: %s ~ %s | 共 %d 个科室 | 合计就诊: %d 人次",
                            startDp.getValue().format(DATE_FMT), endDp.getValue().format(DATE_FMT), data.size(), grandTotal));
                    audit.log(AuditService.ACTION_QUERY, "outpatient_visits", "--",
                            String.format("科室统计: %s~%s", startDp.getValue(), endDp.getValue()));
                }
        ));

        exportBtn.setOnAction(e -> {
            if (table.getItems().isEmpty()) { showWarning("无数据可导出"); return; }
            String path = chooseSaveFile("科室统计_" + LocalDate.now().format(DATE_FMT) + ".xlsx");
            if (path != null) {
                exportToExcel(path, new String[]{"科室", "就诊量", "占比"}, table.getItems(),
                        "科室就诊统计");
                audit.log(AuditService.ACTION_EXPORT, "outpatient_visits", "--", "导出科室统计: " + path);
            }
        });

        root.getChildren().addAll(control, summaryLabel, barChart, tableHeader, table);
        queryBtn.fire();
        return root;
    }

    // ========================================================================
    //  收入统计 Tab
    // ========================================================================

    private VBox buildRevenueStats() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        HBox control = new HBox(10);
        control.setAlignment(Pos.CENTER_LEFT);
        ComboBox<Integer> yearCombo = new ComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 5; y <= currentYear; y++) yearCombo.getItems().add(y);
        yearCombo.setValue(currentYear);
        ComboBox<Integer> monthCombo = new ComboBox<>();
        for (int m = 1; m <= 12; m++) monthCombo.getItems().add(m);
        monthCombo.setValue(LocalDate.now().getMonthValue());
        Button queryBtn = new Button("查询");
        queryBtn.getStyleClass().add("btn-primary");
        control.getChildren().addAll(new Label("年份:"), yearCombo, new Label("月份:"), monthCombo, queryBtn);

        // 收入合计卡片
        Label totalLabel = new Label();
        totalLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        totalLabel.setTextFill(COLOR_RED);
        totalLabel.setPadding(new Insets(8, 0, 4, 0));

        // 饼图（收入构成）
        PieChart pieChart = new PieChart();
        pieChart.setPrefHeight(280);
        pieChart.setTitle("收入构成");
        pieChart.setLabelsVisible(true);
        pieChart.setLegendSide(javafx.geometry.Side.RIGHT);

        HBox chartAndTable = new HBox(15);
        chartAndTable.setPadding(new Insets(5, 0, 0, 0));
        VBox.setVgrow(chartAndTable, Priority.ALWAYS);

        TableView<Object[]> table = createTable(new String[]{"类别", "金额", "占比"});
        table.setPrefHeight(280);
        table.setMinWidth(300);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(table, Priority.ALWAYS);

        Button exportBtn = new Button("导出 Excel");
        exportBtn.getStyleClass().add("btn-primary");
        HBox bottomBar = new HBox(10, exportBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        queryBtn.setOnAction(e -> executeAsync(root,
                // 后台：DB 查询
                () -> {
                    int y = yearCombo.getValue();
                    int m = monthCombo.getValue();
                    var data = repo.getRevenueStats(y, m);
                    return new Object[]{data, y, m};
                },
                // FX 线程：更新 UI
                result -> {
                    @SuppressWarnings("unchecked")
                    var data = (List<Map<String, Object>>) ((Object[]) result)[0];
                    int y = (int) ((Object[]) result)[1];
                    int m = (int) ((Object[]) result)[2];
                    BigDecimal totalRevenue = BigDecimal.ZERO;
                    ObservableList<Object[]> list = FXCollections.observableArrayList();
                    ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
                    for (var d : data) {
                        BigDecimal amt = (BigDecimal) d.get("total");
                        if (amt == null) amt = BigDecimal.ZERO;
                        totalRevenue = totalRevenue.add(amt);
                    }
                    for (var d : data) {
                        BigDecimal amt = (BigDecimal) d.get("total");
                        if (amt == null) amt = BigDecimal.ZERO;
                        String cat = String.valueOf(d.get("category"));
                        double pct = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                                ? amt.multiply(new BigDecimal("100")).divide(totalRevenue, 2, RoundingMode.HALF_UP).doubleValue()
                                : 0;
                        list.add(new Object[]{cat, formatMoney(amt), String.format("%.1f%%", pct)});
                        pieData.add(new PieChart.Data(cat + " (" + String.format("%.1f", pct) + "%)", amt.doubleValue()));
                    }
                    table.setItems(list);
                    pieChart.setData(pieData);
                    totalLabel.setText(String.format("%d年%d月 总收入: %s", y, m, formatMoney(totalRevenue)));
                    audit.log(AuditService.ACTION_QUERY, "billing_records", "--",
                            String.format("收入统计: %d-%02d, 总收入=%s", y, m, formatMoney(totalRevenue)));
                }
        ));

        exportBtn.setOnAction(e -> {
            if (table.getItems().isEmpty()) { showWarning("无数据可导出"); return; }
            String path = chooseSaveFile("收入统计_" + yearCombo.getValue() + "-"
                    + String.format("%02d", monthCombo.getValue()) + ".xlsx");
            if (path != null) {
                String sheetName = yearCombo.getValue() + "年" + monthCombo.getValue() + "月 收入统计";
                exportToExcel(path, new String[]{"类别", "金额", "占比"}, table.getItems(), sheetName);
                audit.log(AuditService.ACTION_EXPORT, "billing_records", "--", "导出收入统计: " + path);
            }
        });

        chartAndTable.getChildren().addAll(pieChart, table);
        root.getChildren().addAll(control, totalLabel, chartAndTable, bottomBar);
        queryBtn.fire();
        return root;
    }

    // ========================================================================
    //  药品消耗统计 Tab
    // ========================================================================

    private VBox buildDrugStats() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        HBox control = new HBox(10);
        control.setAlignment(Pos.CENTER_LEFT);
        Button refreshBtn = new Button("刷新数据");
        refreshBtn.getStyleClass().add("btn-primary");
        Label infoLabel = new Label("药品消耗量统计 (Top 20)");
        infoLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        control.getChildren().addAll(infoLabel, refreshBtn);

        // 排名柱状图
        BarChart<String, Number> barChart = createBarChart("药品消耗量排名", "药品", "消耗量");
        barChart.setPrefHeight(350);
        barChart.setLegendVisible(false);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        // 表格
        TableView<Object[]> table = createTable(new String[]{"排名", "药品名称", "消耗总量"});
        table.setPrefHeight(200);

        Button exportBtn = new Button("导出 Excel");
        exportBtn.getStyleClass().add("btn-primary");
        HBox bottomBar = new HBox(10, exportBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        refreshBtn.setOnAction(e -> executeAsync(root,
                // 后台：DB 查询
                () -> repo.getDrugConsumptionStats(),
                // FX 线程：更新 UI
                data -> {
                    @SuppressWarnings("unchecked")
                    var dList = (List<Map<String, Object>>) data;
                    ObservableList<Object[]> list = FXCollections.observableArrayList();
                    int rank = 0;
                    for (var d : dList) {
                        rank++;
                        list.add(new Object[]{rank, d.get("drugName"), d.get("quantity")});
                    }
                    table.setItems(list);
                    populateBarChart(barChart, dList, "drugName", "quantity");
                    audit.log(AuditService.ACTION_QUERY, "prescription_items", "--",
                            String.format("药品消耗统计: Top%d", dList.size()));
                }
        ));

        exportBtn.setOnAction(e -> {
            if (table.getItems().isEmpty()) { showWarning("无数据可导出"); return; }
            String path = chooseSaveFile("药品消耗统计_" + LocalDate.now().format(DATE_FMT) + ".xlsx");
            if (path != null) {
                exportToExcel(path, new String[]{"排名", "药品名称", "消耗总量"}, table.getItems(),
                        "药品消耗统计");
                audit.log(AuditService.ACTION_EXPORT, "prescription_items", "--",
                        "导出药品消耗统计: " + path);
            }
        });

        root.getChildren().addAll(control, barChart, bottomBar, table);
        refreshBtn.fire();
        return root;
    }

    // ========================================================================
    //  通用工具方法
    // ========================================================================

    /** 创建不可关闭的 Tab */
    private Tab createTab(String title, VBox content) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        tab.setClosable(false);
        return tab;
    }

    /** 构建日期范围控制栏 */
    private HBox buildDateRangeControl() {
        HBox control = new HBox(10);
        control.setAlignment(Pos.CENTER_LEFT);
        DatePicker start = new DatePicker(LocalDate.now().minusDays(30));
        DatePicker end   = new DatePicker(LocalDate.now());
        Button queryBtn = new Button("查询");
        queryBtn.getStyleClass().add("btn-primary");
        control.getChildren().addAll(new Label("日期范围:"), start, new Label("至"), end, queryBtn);
        return control;
    }

    /** 创建通用表格 */
    @SafeVarargs
    private final TableView<Object[]> createTable(String... columns) {
        TableView<Object[]> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (int i = 0; i < columns.length; i++) {
            TableColumn<Object[], String> col = new TableColumn<>(columns[i]);
            final int idx = i;
            col.setCellValueFactory(d -> {
                Object v = d.getValue()[idx];
                return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "");
            });
            col.setStyle("-fx-alignment: " + (i == 0 ? "CENTER-LEFT" : "CENTER") + ";");
            table.getColumns().add(col);
        }
        table.setItems(FXCollections.observableArrayList());
        return table;
    }

    /** 创建柱状图 */
    private BarChart<String, Number> createBarChart(String title, String xLabel, String yLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(xLabel);
        xAxis.setTickLabelRotation(45);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setAnimated(true);
        chart.setBarGap(3);
        chart.setCategoryGap(10);
        return chart;
    }

    /** 填充柱状图数据 */
    private void populateBarChart(BarChart<String, Number> chart,
                                   List<Map<String, Object>> data,
                                   String categoryKey, String valueKey) {
        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (var d : data) {
            String cat = String.valueOf(d.get(categoryKey));
            Number val = (Number) d.get(valueKey);
            series.getData().add(new XYChart.Data<>(cat, val));
        }
        chart.getData().add(series);

        // 着色
        Color[] colors = {COLOR_BLUE, COLOR_GREEN, COLOR_RED, COLOR_ORANGE, COLOR_PURPLE};
        int idx = 0;
        for (XYChart.Data<String, Number> point : series.getData()) {
            String colorHex = toHex(colors[idx % colors.length]);
            point.getNode().setStyle("-fx-bar-fill: " + colorHex + ";");
            idx++;
        }
    }

    /** 填充挂号统计表格，返回合计 */
    private int populateRegTable(TableView<Object[]> table, List<Map<String, Object>> data) {
        ObservableList<Object[]> list = FXCollections.observableArrayList();
        int total = 0;
        for (var d : data) {
            int cnt = (int) d.get("count");
            total += cnt;
            list.add(new Object[]{
                    d.get("date") instanceof LocalDate ? ((LocalDate) d.get("date")).format(DATE_FMT) : String.valueOf(d.get("date")),
                    cnt
            });
        }
        table.setItems(list);
        return total;
    }

    // ========================================================================
    //  Excel 导出
    // ========================================================================

    /** 导出 TableView 数据到 Excel */
    private void exportToExcel(String filePath, String[] headers,
                                List<Object[]> data, String sheetName) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);

            // 样式
            org.apache.poi.ss.usermodel.CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // 表头行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            for (int r = 0; r < data.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object[] rowData = data.get(r);
                for (int c = 0; c < rowData.length; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                    Object val = rowData[c];
                    if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                    }
                }
            }

            // 自动列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(currentWidth + 1024, 255 * 256)); // 上限255字符
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                wb.write(fos);
            }
            showInfo("导出成功", "文件已保存至:\n" + filePath);
            log.info("Excel导出成功: {}", filePath);
        } catch (Exception e) {
            log.error("Excel导出失败: {}", filePath, e);
            showError("导出失败", e.getMessage());
        }
    }

    /** 选择保存路径 */
    private String chooseSaveFile(String defaultName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存 Excel 文件");
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel文件 (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        return file != null ? file.getAbsolutePath() : null;
    }

    // ========================================================================
    //  错误处理 & 用户提示
    // ========================================================================

    /** 带 Loading 状态的异步查询包装器 (T-10.1.2 异步加载) */
    private <V> void executeAsync(Node root, java.util.concurrent.Callable<V> bgTask, java.util.function.Consumer<V> onSuccess) {
        Task<V> task = new Task<>() {
            @Override protected V call() throws Exception {
                return bgTask.call();
            }
        };
        task.setOnSucceeded(e -> {
            root.setCursor(Cursor.DEFAULT);
            try {
                onSuccess.accept(task.getValue());
            } catch (Exception ex) {
                log.error("UI更新失败", ex);
                showError("界面更新失败", ex.getMessage());
            }
        });
        task.setOnFailed(e -> {
            root.setCursor(Cursor.DEFAULT);
            Throwable ex = task.getException();
            log.error("后台查询失败", ex);
            String msg = ex instanceof DatabaseException ? ex.getMessage() : "发生未知错误: " + ex.getMessage();
            showError("数据查询失败", msg);
        });
        root.setCursor(Cursor.WAIT);
        new Thread(task, "Stats-Query-" + System.currentTimeMillis()).start();
    }

    /** 兼容旧接口的同步包装（已废弃，请使用 executeAsync） */
    @Deprecated
    private void executeWithLoading(Node root, Runnable task) {
        executeAsync(root, () -> { task.run(); return null; }, v -> {});
    }

    private void showInfo(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    private void showWarning(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("提示");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    private void showError(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    // ========================================================================
    //  工具方法
    // ========================================================================

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "¥0.00";
        return String.format("¥%,.2f", amount);
    }
}
