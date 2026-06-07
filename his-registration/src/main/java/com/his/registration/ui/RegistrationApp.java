package com.his.registration.ui;

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
 * 挂号管理子系统 - 独立启动入口
 * 适用岗位: 挂号员、管理员
 */
public class RegistrationApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(RegistrationApp.class);
    private static final String MODULE_NAME = "挂号管理子系统";

    @Override
    public void start(Stage primaryStage) {
        try {
            // 初始化配置和数据库
            AppConfig.init();
            ConnectionPool.getInstance().initialize();
            MigrationRunner.run();

            // 登录验证（仅允许挂号员和管理员）
            LoginDialog.requireLogin(MODULE_NAME, UserRole.挂号员);

            // 更新窗口标题显示登录用户
            String userInfo = UserSession.getInstance().getDisplayInfo();

            // 创建主界面
            RegistrationView view = new RegistrationView();
            Scene scene = new Scene(view, 1280, 800);
            String cssUrl = getClass().getResource("/css/style.css") != null
                    ? getClass().getResource("/css/style.css").toExternalForm() : null;
            if (cssUrl != null) scene.getStylesheets().add(cssUrl);

            primaryStage.setTitle(MODULE_NAME + "  —  " + userInfo);
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
    public void stop() {
        ConnectionPool.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
