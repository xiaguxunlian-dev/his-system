package com.his.statistics.ui;

import com.his.auth.LoginDialog;
import com.his.auth.UserRole;
import com.his.auth.UserSession;
import com.his.config.AppConfig;
import com.his.shared.database.ConnectionPool;
import com.his.shared.database.MigrationRunner;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统计报表子系统 - 独立启动入口
 * T-10.2.1 连接断开时显示顶部提示条（非阻塞）
 */
public class StatisticsApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(StatisticsApp.class);
    private static final String MODULE_NAME = "统计报表子系统";

    @Override
    public void start(Stage primaryStage) {
        try {
            AppConfig.init();
            ConnectionPool.getInstance().initialize();
            MigrationRunner.run();

            LoginDialog.requireLogin(MODULE_NAME, UserRole.统计员);
            String userInfo = UserSession.getInstance().getDisplayInfo();

            StatisticsView view = new StatisticsView();

            // 连接状态提示条（顶部，默认隐藏）
            Label connLabel = new Label();
            connLabel.setTextFill(Color.WHITE);
            Button retryBtn = new Button("立即重试");
            retryBtn.setStyle("-fx-background-color: white; -fx-text-fill: #c0392b; -fx-font-weight: bold;");
            retryBtn.setOnAction(e -> ConnectionPool.getInstance().reconnect());
            HBox banner = new HBox(10, connLabel, retryBtn);
            banner.setAlignment(Pos.CENTER_LEFT);
            banner.setPadding(new Insets(8, 15, 8, 15));
            banner.setStyle("-fx-background-color: #e74c3c;");
            banner.setVisible(false);

            BorderPane root = new BorderPane(view);
            root.setTop(banner);

            // 注册连接丢失/恢复回调
            ConnectionPool.getInstance().setOnConnectionLost(msg -> {
                connLabel.setText("⚠ 数据库连接已断开：" + msg + "  — 系统将自动重试...");
                banner.setVisible(true);
            });
            ConnectionPool.getInstance().setOnConnectionRestored(msg -> {
                connLabel.setText("✔ 数据库连接已恢复");
                banner.setStyle("-fx-background-color: #27ae60;");
                // 2秒后自动隐藏
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (Exception ignore) {}
                    Platform.runLater(() -> { banner.setVisible(false); banner.setStyle("-fx-background-color: #e74c3c;"); });
                }).start();
            });

            Scene scene = new Scene(root, 1280, 800);
            String cssUrl = getClass().getResource("/css/style.css") != null
                    ? getClass().getResource("/css/style.css").toExternalForm() : null;
            if (cssUrl != null) scene.getStylesheets().add(cssUrl);

            primaryStage.setTitle(MODULE_NAME + " — " + userInfo);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(640);
            primaryStage.setOnCloseRequest(e -> {
                ConnectionPool.getInstance().shutdown();
                Platform.exit();
            });
            primaryStage.show();
            log.info("{}启动成功，当前用户: {}", MODULE_NAME, userInfo);
        } catch (Exception e) {
            log.error("{}启动失败", MODULE_NAME, e);
            new Alert(Alert.AlertType.ERROR, "启动失败: " + e.getMessage()).showAndWait();
            Platform.exit();
        }
    }

    @Override
    public void stop() { ConnectionPool.getInstance().shutdown(); }

    public static void main(String[] args) { launch(args); }
}
