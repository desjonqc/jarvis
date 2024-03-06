package com.cegesoft.jarvis.module.schedule;

import lombok.Getter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ModuleSchedule {

    @Getter
    private final JSONObject json;
    @Getter
    private final ModuleScheduleEvent event;
    @Getter
    private final List<ModuleScheduleElement> elements;

    public ModuleSchedule(JSONObject json, ModuleScheduleEvent event, List<ModuleScheduleElement> elements) {
        this.json = json;
        this.event = event;
        this.elements = elements;
    }

    public static List<ModuleSchedule> load(JSONArray json) {
        List<ModuleSchedule> schedules = new ArrayList<>();
        for (Object obj : json) {
            try {
                JSONObject object = (JSONObject) obj;
                JSONObject event = (JSONObject) object.get("event");
                JSONArray elem = (JSONArray) object.get("elements");
                List<ModuleScheduleElement> elements = new ArrayList<>();
                for (Object elemObj : elem) {
                    elements.add(ModuleScheduleElement.load((JSONObject) elemObj));
                }
                schedules.add(new ModuleSchedule(object, ModuleScheduleEvent.load(event), elements));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return schedules;
    }

    public enum ScheduleElementType {
        WAIT,
        ENABLE,
        ACTIVATOR;

    }

    public enum ScheduleEventType {
        DATE,
        HOUR,
        SENSOR,
        ENABLE,
        COMBINATION
    }

}
