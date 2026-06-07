package com.his.admin.ui;

import com.his.auth.LoginDialog;
import com.his.auth.UserRole;
import com.his.auth.UserSession;
import com.his.config.AppConfig;
import com.his.shared.database.ConnectionPool;
import com.his.shared.database.MigrationRunner;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统管理子系统 - 独立启动入口
 * <p>适用岗位: 管理员</p>
 */
public class AdminApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(AdminApp.class);
    private static final String MODULE_NAME = "系统管理子系统";

    @Override
    public void start(Stage primaryStage) {
        try {
            AppConfig.init();
            ConnectionPool.getInstance().initialize();
            MigrationRunner.run();

            LoginDialog.requireLogin(MODULE_NAME, UserRole.管理员);
            String userInfo = UserSession.getInstance().getDisplayInfo();

            AdminView view = new AdminView();
            Scene scene = new Scene(view, 1300, 850);
            String cssUrl = getClass().getResource("/css/style.css") != null
                    ? getClass().getResource("/css/style.css").toExternalForm() : null;
            if (cssUrl != null) scene.getStylesheets().add(cssUrl);

            primaryStage.setTitle(MODULE_NAME + "  —  " + userInfo);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);
            primaryStage.setOnCloseRequest(e -> {
                ConnectionPool.getInstance().shutdown();
                Platform.exit();
            });
            primaryStage.show();
            log.info("{} 启动成功，当前用户: {}", MODULE_NAME, userInfo);
        } catch (Exception e) {
            log.error("{} 启动失败", MODULE_NAME, e);
            new Alert(Alert.AlertType.ERROR, "启动失败: " + e.getMessage()).showAndWait();
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        ConnectionPool.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
