package com.cineflow.ui.common;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * CineFlow v2 Reusable Background Concurrency Helper.
 * Offloads database queries and long-running computations off the JavaFX Application Thread.
 */
public class AsyncUi {

    private static final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "CineFlow-AsyncWorker");
        thread.setDaemon(true);
        return thread;
    });

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Executes background work returning a value and delivers the result on the FX Application Thread.
     *
     * @param backgroundWork ThrowingSupplier providing the data on a daemon thread
     * @param onSuccess      Consumer processing the result on the JavaFX Thread
     * @param onError        Consumer handling any caught exception on the JavaFX Thread
     * @param <T>            Return type
     */
    public static <T> void run(ThrowingSupplier<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        WORKER_POOL.submit(() -> {
            try {
                T result = backgroundWork.get();
                deliver(() -> {
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                });
            } catch (Throwable t) {
                System.err.println("[AsyncUi] Background task failed: " + t.getMessage());
                deliver(() -> {
                    if (onError != null) {
                        onError.accept(t);
                    }
                });
            }
        });
    }

    private static void deliver(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException e) {
            // JavaFX Platform not started (e.g. running in unit test environment)
            action.run();
        }
    }

    /**
     * Executes void background work and invokes the completion handler on the FX Application Thread.
     */
    public static void run(Runnable backgroundWork, Runnable onSuccess, Consumer<Throwable> onError) {
        run(() -> {
            backgroundWork.run();
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }
}
