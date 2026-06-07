package com.his.shared.ui;

import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * 所有业务 View 的基类
 * 提供 executeAsync() 异步加载能力，避免数据库查询阻塞 UI 线程
 */
public abstract class BaseView extends StackPane {

    private ProgressIndicator loadingIndicator;

    protected BaseView() {
        this.loadingIndicator = new ProgressIndicator();
        this.loadingIndicator.setVisible(false);
        this.loadingIndicator.setMaxSize(50, 50);
        // StackPane 默认居中
    }

    /**
     * 异步执行数据库查询，结果回到 UI 线程处理
     * @param trigger 触发此异步操作的节点（用于决定是否显示 loading）
     * @param bgTask  后台任务（在后台线程执行，禁止操作 UI）
     * @param onSuccess UI 回调（在 JavaFX 线程执行，可安全更新 UI）
     */
    protected <V> void executeAsync(Node trigger, Callable<V> bgTask, Consumer<V> onSuccess) {
        Task<V> task = new Task<>() {
            @Override
            protected V call() throws Exception {
                return bgTask.call();
            }
        };

        task.setOnSucceeded(e -> {
            V result = task.getValue();
            onSuccess.accept(result);
            hideLoading();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("异步任务失败: " + ex.getMessage());
            ex.printStackTrace();
            hideLoading();
        });

        // 显示 loading（如果 trigger 节点可获取）
        showLoading();

        new Thread(task, "Async-DB-" + System.currentTimeMillis()).start();
    }

    /**
     * 兼容旧代码：同步执行（阻塞 UI 线程，不推荐）
     * @deprecated 请使用 executeAsync()
     */
    @Deprecated
    protected void executeWithLoading(Node trigger, Runnable task) {
        showLoading();
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception { task.run(); return null; }
        };
        t.setOnSucceeded(e -> hideLoading());
        t.setOnFailed(e -> hideLoading());
        new Thread(t, "Compat-DB").start();
    }

    // ── Loading 指示器 ──

    private void showLoading() {
        if (loadingIndicator.getParent() == null) {
            this.getChildren().add(loadingIndicator);
        }
        loadingIndicator.setVisible(true);
    }

    private void hideLoading() {
        loadingIndicator.setVisible(false);
    }
}
