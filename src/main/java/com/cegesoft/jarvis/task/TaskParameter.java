package com.cegesoft.jarvis.task;

import java.util.Date;

/**
 * Created by HoxiSword on 26/05/2020 for JARVIS
 */
public class TaskParameter {

    private long delayed;
    private Date programmedDate;
    private long timer;
    private final Runnable runnable;

    public TaskParameter(Runnable runnable) {
        programmedDate = new Date();
        this.runnable = runnable;
    }

    public TaskParameter setDelayed(long delayed) {
        this.delayed = delayed;
        return this;
    }

    public TaskParameter setProgrammedDate(Date programmedDate) {
        this.programmedDate = programmedDate;
        return this;
    }

    public TaskParameter setTimer(long timer) {
        this.timer = timer;
        return this;
    }

    public Task toTask(TaskId id) {
        if (timer == 0)
            return new Task(id, runnable, delayed);
        return new Task(id, runnable, delayed, timer);
    }

    public Date getDate() {
        return programmedDate;
    }

}
