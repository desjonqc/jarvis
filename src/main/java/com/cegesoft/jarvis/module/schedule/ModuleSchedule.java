package com.cegesoft.jarvis.module.schedule;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ModuleSchedule {

    private final JSONObject json;
    private final ModuleScheduleEvent event;
    private final List<ModuleScheduleElement> elements;

    public ModuleSchedule(JSONObject json, ModuleScheduleEvent event, List<ModuleScheduleElement> elements) {
        this.json = json;
        this.event = event;
        this.elements = elements;
    }

    /**
     * @throws IllegalArgumentException if the json doesn't describe a valid schedule
     */
    public static ModuleSchedule load(Object obj) {
        try {
            JSONObject object = (JSONObject) obj;
            JSONObject event = (JSONObject) object.get("event");
            JSONArray elem = (JSONArray) object.get("elements");
            List<ModuleScheduleElement> elements = new ArrayList<>();
            for (Object elemObj : elem) {
                elements.add(ModuleScheduleElement.load((JSONObject) elemObj));
            }
            return new ModuleSchedule(object, ModuleScheduleEvent.load(event), elements);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid schedule " + obj + " : " + e, e);
        }
    }

    /**
     * @return false if the user paused this schedule in the web app ("enabled": false)
     */
    public boolean isEnabled() {
        return !Boolean.FALSE.equals(json.get("enabled"));
    }

    public JSONObject getJson() {
        return json;
    }

    public ModuleScheduleEvent getEvent() {
        return event;
    }

    public List<ModuleScheduleElement> getElements() {
        return elements;
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
