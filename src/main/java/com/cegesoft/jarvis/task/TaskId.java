package com.cegesoft.jarvis.task;

import java.util.Random;

/**
 * Created by HoxiSword on 25/05/2020 for JARVIS
 */
public class TaskId {

    private final int id;

    TaskId(int id) {
        this.id = id;
    }

    TaskId() {
        this.id = new Random().nextInt(50000);
    }

    static TaskId generate() {
        return new TaskId();
    }

    public String getThreadName() {
        return "TASK-" + id;
    }

}
