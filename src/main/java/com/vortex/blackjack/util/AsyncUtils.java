package com.vortex.blackjack.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Utility class for safe async operations and task management
 */
public class AsyncUtils {
    private final Plugin plugin;
    private final ConcurrentMap<String, BukkitTask> runningTasks = new ConcurrentHashMap<>();
    
    public AsyncUtils(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Run a task asynchronously and return a CompletableFuture
     */
    public <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Run a task synchronously on the main thread
     */
    public <T> CompletableFuture<T> runSync(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            // Already on main thread, execute immediately
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Exception e) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Schedule a repeating task with a unique identifier
     */
    public void scheduleRepeating(String taskId, Runnable task, long delayTicks, long periodTicks) {
        cancelTask(taskId); // Cancel existing task with same ID
        
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        runningTasks.put(taskId, bukkitTask);
    }
    
    /**
     * Schedule a delayed task with a unique identifier
     */
    public void scheduleDelayed(String taskId, Runnable task, long delayTicks) {
        cancelTask(taskId); // Cancel existing task with same ID
        
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        runningTasks.put(taskId, bukkitTask);
    }
    
    /**
     * Cancel a task by its identifier
     */
    public void cancelTask(String taskId) {
        BukkitTask task = runningTasks.remove(taskId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
    
    /**
     * Cancel all running tasks
     */
    public void cancelAllTasks() {
        runningTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        runningTasks.clear();
    }
}
