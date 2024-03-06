package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.EventHandler;
import com.cegesoft.jarvis.events.Listener;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.task.TaskManager;
import lombok.Getter;
import org.json.simple.JSONArray;

import java.util.ArrayList;

public class ModuleScheduleManager implements Listener {

    private final ModuleScheduleFile file;
    @Getter
    private final ArrayList<ModuleSchedule> schedules = new ArrayList<>();

    public ModuleScheduleManager() {
        this.file = new ModuleScheduleFile();
    }

    public void load() {
        this.updateSchedules(this.file.loadSchedules(), false);
        Jarvis.getEventManager().registerListener(this);
    }

    public void unload() {
        Jarvis.getEventManager().unregisterListener(this);
        this.file.saveSchedules();
    }

    public void updateSchedules(JSONArray array,boolean save) {
        this.schedules.clear();
        this.schedules.addAll(ModuleSchedule.load(array));
        if (save)
            this.file.saveSchedules();
    }

    @EventHandler
    public void onEvent(EventCalledEvent event) {
        for (ModuleSchedule schedule : schedules) {
            boolean passed;
            if (schedule.getEvent().getEventClass().equals(EventCalledEvent.class)) {
                passed = schedule.getEvent().passEvent(event);
            } else if (schedule.getEvent().getEventClass().equals(event.getEventCalled().getClass())) {
                passed = schedule.getEvent().passEvent(event.getEventCalled());
            } else {
                passed = false;
            }
            if (passed) {
                schedule.getEvent().resetPassed();
                TaskManager.scheduleTask(() -> schedule.getElements().forEach(ModuleScheduleElement::execute));
            }
        }
    }
}
