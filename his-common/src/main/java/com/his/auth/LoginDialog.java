package com.his.auth;

import com.his.config.AppConfig;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录对话框（JavaFX）
 * 全屏或居中弹出，用户输入用户名+密码后验证
 * 失败3次后锁定账号5分钟
 */
public class LoginDialog {

    private static final Logger log = LoggerFactory.getLogger(LoginDialog.class);

    private final Stage stage;
    private final String moduleName;
    private final UserRole[] allowedRoles;
    private boolean loginSuccess = false;

    private TextField    usernameField;
    private PasswordField passwordField;
    private Label        messageLabel;
    private Button       loginButton;

    /**
     * @param owner       父窗口（可为null）
     * @param moduleName  当前子系统名称（如"挂号管理"）
     * @param allowedRoles 允许登录的角色，管理员始终可以登录
     */
    public LoginDialog(Stage owner, String moduleName, UserRole... allowedRoles) {
        this.moduleName   = moduleName;
        this.allowedRoles = allowedRoles;
        this.stage        = new Stage();

        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);
        stage.setTitle("登录 — " + moduleName);

        buildUI();
    }

    private void buildUI() {
        // ===== 顶部蓝色标题栏 =====
        VBox header = new VBox(6);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(30, 30, 20, 30));
        header.setStyle("-fx-background-color: #1565C0;");

        Label contactLabel = new Label("有问题联系1432758432@qq.com");
        contactLabel.setFont(Font.font("微软雅黑", FontWeight.NORMAL, 11));
        contactLabel.setTextFill(Color.web("#BBDEFB"));

        Label titleLabel = new Label(moduleName);
        titleLabel.setFont(Font.font("微软雅黑", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.WHITE);

        Label subtitleLabel = new Label("医院信息管理系统");
        subtitleLabel.setFont(Font.font("微软雅黑", FontWeight.NORMAL, 13));
        subtitleLabel.setTextFill(Color.web("#90CAF9"));

        header.getChildren().addAll(contactLabel, titleLabel, subtitleLabel);

        // ===== 中部表单区 =====
        VBox formBox = new VBox(14);
        formBox.setPadding(new Insets(28, 35, 20, 35));
        formBox.setStyle("-fx-background-color: #FFFFFF;");

        // 用户名
        Label userLabel = new Label("用户名");
        userLabel.setFont(Font.font("微软雅黑", 13));
        userLabel.setTextFill(Color.web("#555555"));
        usernameField = new TextField();
        usernameField.setPromptText("请输入用户名");
        usernameField.setPrefHeight(38);
        usernameField.setFont(Font.font("微软雅黑", 13));
        usernameField.setStyle(fieldStyle());

        // 密码
        Label passLabel = new Label("密  码");
        passLabel.setFont(Font.font("微软雅黑", 13));
        passLabel.setTextFill(Color.web("#555555"));
        passwordField = new PasswordField();
        passwordField.setPromptText("请输入密码");
        passwordField.setPrefHeight(38);
        passwordField.setFont(Font.font("微软雅黑", 13));
        passwordField.setStyle(fieldStyle());

        // 提示信息
        messageLabel = new Label("");
        messageLabel.setFont(Font.font("微软雅黑", 12));
        messageLabel.setTextFill(Color.web("#C62828"));
        messageLabel.setWrapText(true);
        messageLabel.setMinHeight(20);

        // 登录按钮
        loginButton = new Button("登  录");
        loginButton.setPrefWidth(Double.MAX_VALUE);
        loginButton.setPrefHeight(42);
        loginButton.setFont(Font.font("微软雅黑", FontWeight.BOLD, 15));
        loginButton.setStyle(
            "-fx-background-color: #1565C0; -fx-text-fill: white; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
        );
        loginButton.setOnMouseEntered(e -> loginButton.setStyle(
            "-fx-background-color: #0D47A1; -fx-text-fill: white; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
        ));
        loginButton.setOnMouseExited(e -> loginButton.setStyle(
            "-fx-background-color: #1565C0; -fx-text-fill: white; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
        ));
        loginButton.setOnAction(e -> doLogin());

        formBox.getChildren().addAll(
            userLabel, usernameField,
            passLabel, passwordField,
            messageLabel, loginButton
        );

        // ===== 底部提示 =====
        Label hintLabel = new Label("默认用户: admin  |  密码: admin123");
        hintLabel.setFont(Font.font("微软雅黑", 11));
        hintLabel.setTextFill(Color.web("#9E9E9E"));
        HBox footer = new HBox(hintLabel);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8, 10, 14, 10));
        footer.setStyle("-fx-background-color: #F5F5F5;");

        VBox root = new VBox(0, header, formBox, footer);
        root.setStyle("-fx-border-color: #BDBDBD; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 3);");

        Scene scene = new Scene(root, 380, 440);

        // 回车键登录
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER: doLogin(); break;
                case ESCAPE: Platform.exit(); break;
                default: break;
            }
        });

        stage.setScene(scene);
        stage.centerOnScreen();
    }

    private String fieldStyle() {
        return "-fx-border-color: #BDBDBD; -fx-border-radius: 3; -fx-background-radius: 3; " +
               "-fx-padding: 4 8 4 8;";
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("请输入用户名和密码", false);
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("登录中...");
        messageLabel.setText("");

        // 在后台线程执行（避免UI卡顿）
        new Thread(() -> {
            AuthService authService = new AuthService();
            AuthService.LoginResult result = authService.login(username, password);

            Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("登  录");

                if (result.success) {
                    // 检查角色权限
                    boolean roleOk = result.role == UserRole.管理员;
                    if (!roleOk && allowedRoles != null && allowedRoles.length > 0) {
                        for (UserRole r : allowedRoles) {
                            if (r == result.role) { roleOk = true; break; }
                        }
                    }

                    if (!roleOk) {
                        showMessage("您的角色（" + result.role + "）无权访问此模块", false);
                        return;
                    }

                    // 更新会话
                    UserSession.getInstance().login(
                        result.userId, username, result.displayName,
                        result.role, result.departmentId,
                        result.departmentId != null ? "部门" + result.departmentId : ""
                    );

                    loginSuccess = true;
                    stage.close();
                } else {
                    showMessage(result.message, true);
                    passwordField.clear();
                    passwordField.requestFocus();
                }
            });
        }, "login-thread").start();
    }

    private void showMessage(String msg, boolean isError) {
        messageLabel.setText(msg);
        messageLabel.setTextFill(isError ? Color.web("#C62828") : Color.web("#2E7D32"));
    }

    /**
     * 显示登录对话框，阻塞直到用户登录成功或关闭
     * @return true=登录成功，false=取消/关闭
     */
    public boolean showAndWait() {
        usernameField.requestFocus();
        stage.showAndWait();
        return loginSuccess;
    }

    /**
     * 静态工厂方法：显示登录对话框，登录成功才能继续使用
     * 登录失败（用户关闭窗口）则退出应用
     *
     * @param moduleName  模块名称
     * @param allowedRoles 允许的角色
     */
    public static void requireLogin(String moduleName, UserRole... allowedRoles) {
        LoginDialog dialog = new LoginDialog(null, moduleName, allowedRoles);
        boolean ok = dialog.showAndWait();
        if (!ok) {
            log.info("用户取消登录，退出应用");
            Platform.exit();
            System.exit(0);
        }
    }
}
