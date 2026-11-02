package com.cegesoft.jarvis.task;

import com.cegesoft.jarvis.Jarvis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by HoxiSword on 25/05/2020 for JARVIS
 * <p>
 * A single scheduler thread keeps the timing, and every execution runs on its own virtual thread :
 * tasks may block (serial I/O, waits in schedules) without delaying the other ones, and no thread sits idle.
 */
public class TaskManager {

    private final Map<TaskId, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    // Makes "schedule then register" atomic : a one-shot task firing immediately must not unregister itself before it is registered.
    private final Object registrationLock = new Object();
    private ScheduledThreadPoolExecutor scheduler;
    private ExecutorService workers;

    public TaskManager() {

    }

    public synchronized void start() {
        if (scheduler != null)
            return;
        this.scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "TASK-SCHEDULER");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.setRemoveOnCancelPolicy(true);
        this.workers = Executors.newVirtualThreadPerTaskExecutor();
    }

    public synchronized void stop() {
        if (scheduler == null)
            return;
        this.scheduler.shutdownNow();
        this.workers.shutdownNow();
        this.tasks.clear();
    }

    /**
     * Run a task on a virtual thread right now, without scheduling.
     */
    public void execute(Runnable runnable, String name) {
        workers.execute(wrap(runnable, name));
    }

    private Runnable wrap(Runnable runnable, String name) {
        return () -> {
            Thread.currentThread().setName(name);
            try {
                runnable.run();
            } catch (Throwable t) {
                Jarvis.getLogger().error("Task " + name + " failed : ", t);
            }
        };
    }

    private TaskId schedule(Runnable runnable, String name, long delay, long timer) {
        TaskId id = TaskId.generate();
        Runnable task = wrap(runnable, name == null ? id.getThreadName() : name);
        synchronized (registrationLock) {
            ScheduledFuture<?> future;
            if (timer > 0) {
                future = scheduler.scheduleWithFixedDelay(() -> workers.execute(task), delay, timer, TimeUnit.MILLISECONDS);
            } else {
                future = scheduler.schedule(() -> {
                    synchronized (registrationLock) {
                        tasks.remove(id);
                    }
                    workers.execute(task);
                }, delay, TimeUnit.MILLISECONDS);
            }
            tasks.put(id, future);
        }
        return id;
    }

    public static boolean isScheduled(TaskId id) {
        return id != null && Jarvis.getTaskManager().tasks.containsKey(id);
    }

    public static TaskId scheduleTask(Runnable runnable) {
        return scheduleTask(runnable, null);
    }

    public static TaskId scheduleTaskLater(Runnable runnable, long delay) {
        return scheduleTaskLater(runnable, null, delay);
    }

    public static TaskId scheduleTaskTimer(Runnable runnable, long delay, long timer) {
        return scheduleTaskTimer(runnable, null, delay, timer);
    }

    public static TaskId scheduleTask(Runnable runnable, String name) {
        return scheduleTaskLater(runnable, name, 0);
    }

    public static TaskId scheduleTaskLater(Runnable runnable, String name, long delay) {
        return Jarvis.getTaskManager().schedule(runnable, name, delay, 0);
    }

    public static TaskId scheduleTaskTimer(Runnable runnable, String name, long delay, long timer) {
        if (timer <= 0)
            throw new IllegalArgumentException("timer must be positive");
        return Jarvis.getTaskManager().schedule(runnable, name, delay, timer);
    }

    /**
     * Cancel a task which has not started yet, or stop a repeating task. A task already running finishes normally.
     */
    public static void cancelTask(TaskId id) {
        if (id == null)
            return;
        ScheduledFuture<?> future = Jarvis.getTaskManager().tasks.remove(id);
        if (future != null)
            future.cancel(false);
    }
}
