package dev.mc2p.plugin.thread;

import dev.mc2p.plugin.facade.FacadeException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Runs Bukkit work on the server main thread and blocks the caller (an
 * off-main-thread
 * tool executor) until it completes or times out.
 */
public final class MainThread {

    private final Plugin plugin;
    private final long timeoutMillis;

    public MainThread(final Plugin plugin, final long timeoutMillis) {
        this.plugin = plugin;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Executes on the main thread, returning the result. The calling thread blocks
     * up to
     * the configured timeout.
     */
    public <T> T call(final Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.call();
            } catch (final Exception e) {
                throw new FacadeException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
            }
        }
        final FutureTask<T> future = new FutureTask<>(() -> {
            try {
                return task.call();
            } catch (final Exception e) {
                throw new FacadeException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
            }
        });
        Bukkit.getScheduler().runTask(plugin, future);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            future.cancel(true);
            throw new FacadeException("server did not respond within " + timeoutMillis + " ms");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FacadeException("interrupted while waiting for the server main thread");
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof final FacadeException fe) {
                throw fe;
            }
            throw new FacadeException(
                    cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(), cause);
        }
    }

    /**
     * Runs a fire-and-forget task on the main thread (notifications, announce
     * broadcasts).
     */
    public void run(final Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
