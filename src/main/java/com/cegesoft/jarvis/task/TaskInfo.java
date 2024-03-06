package com.cegesoft.jarvis.task;

import java.util.Date;

/**
 * Created by HoxiSword on 26/05/2020 for JARVIS
 */
public class TaskInfo {

    private Task.TaskState state;
    private Date start;
    private Date end;

    TaskInfo() {}

    public Task.TaskState getState() {
        return state;
    }

    public void setState(Task.TaskState state) {
        this.state = state;
    }

    public Date getStartDate() {
        return start;
    }

    public void setStartDate(Date start) {
        this.start = start;
    }

    public Date getEndDate() {
        return end;
    }

    public void setEndDate(Date end) {
        this.end = end;
    }
}
