package com.cegesoft.jarvis.task;

import java.util.Date;

/**
 * Created by HoxiSword on 25/05/2020 for JARVIS
 */
public class Task extends Thread {

    private final TaskId id;
    private final TaskType type;
    private final Runnable runnable;
    private final long delay;
    private final TaskInfo info;
    private long timer;
    private String name;

    Task(TaskId id, Runnable runnable, long delay) {
        super(id.getThreadName());
        this.id = id;
        this.type = TaskType.RUNNABLE;
        this.info = new TaskInfo();
        this.runnable = () -> {
            try {
                this.info.setState(TaskState.RUNNING);
                Thread.sleep(delay);
                runnable.run();
            } catch (InterruptedException ignored) {
            }
            this.info.setState(TaskState.INTERRUPT);
        };
        this.delay = delay;
    }

    Task(TaskId id, Runnable runnable, long delay, long timer) {
        super(id.getThreadName());
        this.id = id;
        this.type = TaskType.LOOP_INFINITE;
        this.info = new TaskInfo();
        this.runnable = () -> {
            try {
                this.info.setState(TaskState.RUNNING);
                Thread.sleep(delay);
                while (this.info.getState() == TaskState.RUNNING) {
                    runnable.run();
                    Thread.sleep(timer);
                }
            } catch (InterruptedException ignored) {
            }
            this.info.setState(TaskState.INTERRUPT);
        };
        this.delay = delay;
        this.timer = timer;
    }

    public Task setTaskName(String name) {
        if (name == null)
            return this;
        this.setName(this.name = name);
        return this;
    }

    @Override
    public void run() {
        if (this.runnable != null) {
            this.runnable.run();
        }
    }

    @Override
    public synchronized void start() {
        this.info.setStartDate(new Date());
        super.start();
    }

    @Override
    public void interrupt() {
        this.info.setState(TaskState.INTERRUPT);
        this.info.setEndDate(new Date());
        super.interrupt();
    }

    public void waitFor() {
        try {
            this.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public TaskId getTaskId() {
        return id;
    }

    public TaskType getType() {
        return type;
    }

    public long getDelay() {
        return delay;
    }

    public long getTimer() {
        return timer;
    }

    public TaskInfo getInfo() {
        return info;
    }

    public enum TaskType {
        RUNNABLE,
        LOOP_INFINITE,
        LOOP_LIMITED
    }

    public enum TaskState {
        WAIT,
        RUNNING,
        INTERRUPT
    }

}
