package com.his.shared.ui;

import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * 异步UI工具类 - 提供通用的后台数据库查询+UI更新方法
 * T-10 性能优化：避免JavaFX UI线程阻塞，支持光标反馈
 */
public class AsyncUIUtil {

    private AsyncUIUtil() {}

    /**
     * 在后台线程执行数据库查询，查询完成后在JavaFX线程更新UI
     * @param trigger 触发节点（用于关联loading状态和光标）
     * @param bgTask 后台任务（数据库查询）
     * @param onSuccess UI更新回调（在JavaFX线程执行）
     * @param <V> 返回类型
     */
    public static <V> void executeAsync(Node trigger, Callable<V> bgTask, Consumer<V> onSuccess) {
        Task<V> task = new Task<>() {
            @Override
            protected V call() throws Exception {
                return bgTask.call();
            }
        };
        // 运行时设置等待光标
        task.setOnRunning(e -> setCursor(trigger, Cursor.WAIT));
        task.setOnSucceeded(e -> {
            restoreCursor(trigger);
            V result = task.getValue();
            onSuccess.accept(result);
        });
        task.setOnFailed(e -> {
            restoreCursor(trigger);
            Throwable ex = task.getException();
            System.err.println("异步任务失败: " + ex.getMessage());
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "操作失败: " + ex.getMessage()).showAndWait();
        });
        task.setOnCancelled(e -> restoreCursor(trigger));
        new Thread(task, "Async-DB").start();
    }

    /**
     * 简化版：不需要trigger节点
     */
    public static <V> void executeAsync(Callable<V> bgTask, Consumer<V> onSuccess) {
        executeAsync(null, bgTask, onSuccess);
    }

    /**
     * 带 loading 消息的异步执行
     */
    public static <V> void executeAsync(Node trigger, Callable<V> bgTask, Consumer<V> onSuccess, String loadingMsg) {
        executeAsync(trigger, bgTask, onSuccess);
    }

    private static void setCursor(Node trigger, Cursor cursor) {
        if (trigger != null && trigger.getScene() != null) {
            trigger.getScene().setCursor(cursor);
        }
    }

    private static void restoreCursor(Node trigger) {
        if (trigger != null && trigger.getScene() != null) {
            trigger.getScene().setCursor(Cursor.DEFAULT);
        }
    }
}
