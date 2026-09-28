package com.qrassistant.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Runs code on the JavaFX thread and waits for it: the "thread hop" in one reusable place.
 * Used by FxUiGateway and by services (like the clipboard) that must run on the JavaFX thread.
 */
public final class FxThread {

    private FxThread() {
    }

    /**
     * Runs the task on the JavaFX thread and returns its result.
     * If we're ALREADY on the JavaFX thread, it simply runs the task right away.
     *
     * How the hop works:
     *  1. wrap the task in a FutureTask (a job that can hand back a result later)
     *  2. Platform.runLater(...) puts that job in the JavaFX thread's queue
     *  3. future.get() makes THIS thread wait until the job has run
     */
    public static <T> T call(Callable<T> task) {
        if (Platform.isFxApplicationThread()) {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("UI task failed", e);
            }
        }
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // keep the "please stop" signal for our caller
            throw new IllegalStateException("Interrupted while waiting for the UI", e);
        } catch (ExecutionException e) {
            // The task threw on the FX thread; re-throw it here so the caller's error handling sees it.
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("UI task failed", cause);
        }
    }

    /** Same as call(...), for code that doesn't return anything. */
    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}