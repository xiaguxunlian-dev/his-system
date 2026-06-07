package com.his.ui;

import com.his.auth.UserSession;
import com.his.config.AppConfig;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * HIS 统一主窗口基类
 * 所有子系统的主界面都应基于此构建
 * 提供：顶部导航栏（系统名+用户名+时钟+退出）、底部状态栏
 */
public abstract class HisMainPane extends BorderPane {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    protected final String moduleName;
    protected final Stage  primaryStage;

    private Label  timeLabel;
    private Label  statusLabel;
    private Label  dbStatusLabel;
    private Timeline clock;

    public HisMainPane(Stage primaryStage, String moduleName) {
        this.primaryStage = primaryStage;
        this.moduleName   = moduleName;

        setTop(buildHeader());
        setBottom(buildStatusBar());
        // 子类在构造中通过 setCenter() / buildCenter() 填充主内容区

        // 加载CSS
        try {
            String cssUrl = getClass().getResource("/css/style.css") != null
                    ? getClass().getResource("/css/style.css").toExternalForm() : null;
            if (cssUrl != null) {
                this.getStylesheets().add(cssUrl);
            }
        } catch (Exception ignore) {}

        startClock();
    }

    /** 构建顶部导航栏 */
    private HBox buildHeader() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 16, 0, 16));
        header.setSpacing(10);
        header.getStyleClass().add("header-bar");
        header.setStyle("-fx-background-color: #1565C0; -fx-min-height: 52px; -fx-pref-height: 52px;");

        // 医院名 + 系统名
        VBox titleBox = new VBox(2);
        Label hospitalLabel = new Label(AppConfig.getInstance().getHospitalName());
        hospitalLabel.setFont(Font.font("微软雅黑", 11));
        hospitalLabel.setTextFill(Color.web("#BBDEFB"));

        Label titleLabel = new Label(moduleName);
        titleLabel.setFont(Font.font("微软雅黑", FontWeight.BOLD, 17));
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.getStyleClass().add("title-label");

        titleBox.getChildren().addAll(hospitalLabel, titleLabel);

        // 弹簧
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 用户信息
        UserSession session = UserSession.getInstance();
        Label userLabel = new Label("👤  " + session.getDisplayInfo());
        userLabel.setFont(Font.font("微软雅黑", 12));
        userLabel.setTextFill(Color.web("#E3F2FD"));

        // 时间
        timeLabel = new Label("");
        timeLabel.setFont(Font.font("微软雅黑", 12));
        timeLabel.setTextFill(Color.web("#90CAF9"));
        timeLabel.getStyleClass().add("time-label");

        // 退出按钮
        Button logoutBtn = new Button("退出");
        logoutBtn.setFont(Font.font("微软雅黑", 12));
        logoutBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; " +
            "-fx-background-radius: 3; -fx-cursor: hand; -fx-padding: 4 12 4 12;"
        );
        logoutBtn.setOnMouseEntered(e -> logoutBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.28); -fx-text-fill: white; " +
            "-fx-background-radius: 3; -fx-cursor: hand; -fx-padding: 4 12 4 12;"
        ));
        logoutBtn.setOnMouseExited(e -> logoutBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; " +
            "-fx-background-radius: 3; -fx-cursor: hand; -fx-padding: 4 12 4 12;"
        ));
        logoutBtn.setOnAction(e -> onLogout());

        header.getChildren().addAll(titleBox, spacer, userLabel, timeLabel, logoutBtn);
        return header;
    }

    /** 构建底部状态栏 */
    private HBox buildStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(3, 12, 3, 12));
        statusBar.setStyle("-fx-background-color: #EEEEEE; -fx-border-color: #BDBDBD transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("就绪");
        statusLabel.setFont(Font.font("微软雅黑", 12));
        statusLabel.setTextFill(Color.web("#616161"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        dbStatusLabel = new Label("● 数据库已连接");
        dbStatusLabel.setFont(Font.font("微软雅黑", 12));
        dbStatusLabel.setTextFill(Color.web("#2E7D32"));

        Label versionLabel = new Label("HIS v" + AppConfig.getInstance().getAppVersion());
        versionLabel.setFont(Font.font("微软雅黑", 12));
        versionLabel.setTextFill(Color.web("#9E9E9E"));

        statusBar.getChildren().addAll(statusLabel, spacer, dbStatusLabel, versionLabel);
        return statusBar;
    }

    /** 启动时钟 */
    private void startClock() {
        clock = new Timeline(
            new KeyFrame(Duration.seconds(1), e ->
                timeLabel.setText(LocalDateTime.now().format(TIME_FMT))
            )
        );
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    /** 退出登录 */
    private void onLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("退出确认");
        alert.setHeaderText("确认退出 " + moduleName + "？");
        alert.setContentText("退出后需重新登录。");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (clock != null) clock.stop();
            UserSession.getInstance().logout();
            primaryStage.close();
            Platform.exit();
        }
    }

    // ===== 提供给子类的方法 =====

    /** 更新状态栏文本 */
    protected void setStatus(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setTextFill(Color.web("#616161"));
        });
    }

    /** 更新状态栏（带颜色） */
    protected void setStatusOk(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText("✓ " + msg);
            statusLabel.setTextFill(Color.web("#2E7D32"));
        });
    }

    protected void setStatusError(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText("✕ " + msg);
            statusLabel.setTextFill(Color.web("#C62828"));
        });
    }

    protected void setStatusWarning(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText("⚠ " + msg);
            statusLabel.setTextFill(Color.web("#E65100"));
        });
    }

    /** 更新数据库状态 */
    protected void setDbStatus(boolean connected) {
        Platform.runLater(() -> {
            if (connected) {
                dbStatusLabel.setText("● 数据库已连接");
                dbStatusLabel.setTextFill(Color.web("#2E7D32"));
            } else {
                dbStatusLabel.setText("● 数据库已断开");
                dbStatusLabel.setTextFill(Color.web("#C62828"));
            }
        });
    }

    /** 显示信息提示框 */
    protected void showInfo(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /** 显示错误提示框 */
    protected void showError(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /** 显示确认对话框，返回true=确认 */
    protected boolean showConfirm(String title, String content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        a.setTitle(title);
        a.setHeaderText(null);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    /** 停止时钟（窗口关闭时调用） */
    public void dispose() {
        if (clock != null) clock.stop();
    }
}
