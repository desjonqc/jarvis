package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.EventHandler;
import com.cegesoft.jarvis.events.Listener;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.events.type.TimeTickEvent;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;
import org.json.simple.JSONArray;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.cegesoft.jarvis.Jarvis.getLogger;

public class ModuleScheduleManager implements Listener {

    private static final long TIME_TICK_INTERVAL_MS = 30000;

    private final ModuleScheduleFile file;
    // Replaced as a whole (never modified) : the event thread can iterate while the web API updates the schedules.
    private volatile List<ModuleSchedule> schedules = List.of();
    // Entries of the file which couldn't be loaded, written back as they were until the schedules are edited.
    private volatile List<Object> unloadable = List.of();
    private TaskId timeTickTask;

    public ModuleScheduleManager(File dataDir) {
        this.file = new ModuleScheduleFile(dataDir);
    }

    public void load() {
        List<ModuleSchedule> loaded = new ArrayList<>();
        List<Object> invalid = new ArrayList<>();
        for (Object obj : this.file.loadSchedules()) {
            try {
                loaded.add(ModuleSchedule.load(obj));
            } catch (IllegalArgumentException e) {
                getLogger().error("Schedule ignored : ", e);
                invalid.add(obj);
            }
        }
        this.schedules = List.copyOf(loaded);
        this.unloadable = List.copyOf(invalid);
        getLogger().info(loaded.size() + " schedule(s) loaded.");
        Jarvis.getEventManager().registerListener(this);
        this.timeTickTask = TaskManager.scheduleTaskTimer(
                () -> Jarvis.getEventManager().callEvent(new TimeTickEvent(LocalDateTime.now())),
                "TIME-TICK", TIME_TICK_INTERVAL_MS, TIME_TICK_INTERVAL_MS);
    }

    public void unload() {
        TaskManager.cancelTask(this.timeTickTask);
        Jarvis.getEventManager().unregisterListener(this);
        this.save();
    }

    private void save() {
        List<Object> json = new ArrayList<>();
        this.schedules.forEach(schedule -> json.add(schedule.getJson()));
        json.addAll(this.unloadable);
        this.file.saveSchedules(json);
    }

    /**
     * Replace all the schedules. Nothing is changed if one of them is invalid.
     *
     * @throws IllegalArgumentException if a schedule is invalid
     */
    public void updateSchedules(JSONArray array, boolean save) {
        List<ModuleSchedule> loaded = new ArrayList<>();
        for (Object obj : array)
            loaded.add(ModuleSchedule.load(obj));
        this.schedules = List.copyOf(loaded);
        this.unloadable = List.of();
        if (save)
            this.save();
    }

    public List<ModuleSchedule> getSchedules() {
        return schedules;
    }

    @EventHandler
    public void onEvent(EventCalledEvent event) {
        for (ModuleSchedule schedule : schedules) {
            try {
                this.checkSchedule(schedule, event);
            } catch (RuntimeException e) {
                getLogger().error("Can't check schedule " + schedule.getJson() + " : ", e);
            }
        }
    }

    private void checkSchedule(ModuleSchedule schedule, EventCalledEvent event) {
        if (!schedule.isEnabled())
            return;
        Class<? extends Event> eventClass = schedule.getEvent().getEventClass();
        boolean passed;
        if (eventClass == null) {
            passed = false;
        } else if (eventClass.equals(EventCalledEvent.class)) {
            passed = schedule.getEvent().passEvent(event);
        } else if (eventClass.equals(event.getEventCalled().getClass())) {
            passed = schedule.getEvent().passEvent(event.getEventCalled());
        } else {
            passed = false;
        }
        if (passed) {
            schedule.getEvent().resetPassed();
            TaskManager.scheduleTask(() -> schedule.getElements().forEach(ModuleScheduleElement::execute), "SCHEDULE");
        }
    }
}
