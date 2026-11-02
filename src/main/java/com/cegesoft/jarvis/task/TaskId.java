package com.cegesoft.jarvis.task;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by HoxiSword on 25/05/2020 for JARVIS
 */
public class TaskId {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int id;

    private TaskId(int id) {
        this.id = id;
    }

    static TaskId generate() {
        return new TaskId(COUNTER.incrementAndGet());
    }

    public String getThreadName() {
        return "TASK-" + id;
    }

    @Override
    public String toString() {
        return getThreadName();
    }
}
