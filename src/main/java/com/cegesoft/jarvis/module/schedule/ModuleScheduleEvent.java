package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.events.type.module.ModuleEnableChangeEvent;
import com.cegesoft.jarvis.events.type.module.sensor.SensorPulseEvent;
import com.cegesoft.jarvis.events.type.module.sensor.SensorUpdateValueEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public abstract class ModuleScheduleEvent {

    private boolean hasPassed = false;
    private TaskId resetTask;

    public static ModuleScheduleEvent load(JSONObject event) throws Exception {
        ModuleSchedule.ScheduleEventType type = ModuleSchedule.ScheduleEventType.valueOf((String) event.get("type"));
        JSONObject args = (JSONObject) event.get("args");
        switch (type) {
            case COMBINATION:
                List<ModuleScheduleEvent> events = new ArrayList<>();
                for (Object obj : (JSONArray) args.get("events")) {
                    events.add(load((JSONObject) obj));
                }
                return new CombinationScheduleEvent((String) args.get("combination"), events);
            case ENABLE:
                return new EnableScheduleEvent((String) args.get("id"), (Boolean) args.get("value"));
            case HOUR:
                return new TimeScheduleEvent(((Long) args.get("value")).intValue());
            case SENSOR:
                return new SwitchScheduleEvent((String) args.get("id"), !args.containsKey("comparator") ? 0 : ((Long) args.get("comparator")).intValue(), args.get("value"));
        }
        throw new Exception("Invalid Event Type");
    }

    public abstract Class<? extends Event> getEventClass();

    protected abstract boolean _passEvent(Event event);

    public boolean passEvent(Event event) {
        if (!this.getEventClass().equals(event.getClass())) {
            return hasPassed;
        }
        this.hasPassed = this._passEvent(event);
        TaskManager.cancelTask(resetTask);
        if (this.hasPassed)
            this.resetTask = TaskManager.scheduleTaskLater(() -> this.hasPassed = false, 20000);
        return this.hasPassed;
    }

    public void resetPassed() {
        TaskManager.cancelTask(resetTask);
        this.hasPassed = false;
    }


    public static class CombinationScheduleEvent extends ModuleScheduleEvent {

        private final String combination;
        private final List<ModuleScheduleEvent> events;

        public CombinationScheduleEvent(String combination, List<ModuleScheduleEvent> events) {
            this.combination = combination;
            this.events = events;
        }


        @Override
        public Class<? extends Event> getEventClass() {
            return EventCalledEvent.class;
        }

        @Override
        public boolean _passEvent(Event event) {
            boolean passed = combination.equals("and");
            for (ModuleScheduleEvent e : events) {
                if (combination.equals("and")) {
                    passed = passed && e.passEvent(((EventCalledEvent) event).getEventCalled());
                } else {
                    passed = e.passEvent(((EventCalledEvent) event).getEventCalled());
                    if (passed)
                        break;
                }
            }
            return passed;
        }

        @Override
        public void resetPassed() {
            super.resetPassed();
            this.events.forEach(ModuleScheduleEvent::resetPassed);
        }
    }

    public static class EnableScheduleEvent extends ModuleScheduleEvent {

        private final String moduleId;
        private final boolean enable;

        public EnableScheduleEvent(String moduleId, boolean enable) {
            this.moduleId = moduleId;
            this.enable = enable;
        }

        @Override
        public Class<? extends Event> getEventClass() {
            return ModuleEnableChangeEvent.class;
        }

        @Override
        protected boolean _passEvent(Event event) {
            ModuleEnableChangeEvent mEvent = (ModuleEnableChangeEvent) event;
            return mEvent.getModule().getId().equals(moduleId) && mEvent.isNewState() == enable;
        }
    }

    public static class SwitchScheduleEvent extends ModuleScheduleEvent {
        private final String moduleId;
        private final int comparator;
        private final Object value;

        public SwitchScheduleEvent(String moduleId, int comparator, Object value) {
            this.moduleId = moduleId;
            this.comparator = comparator;
            this.value = value;
        }

        private ModuleType getType() {
            return Jarvis.getModuleManager().getModule(moduleId).getType();
        }

        @Override
        public Class<? extends Event> getEventClass() {
            try {
                switch (this.getType()) {
                    case PULSE_SWITCH_SENSOR:
                        return SensorPulseEvent.class;
                    case SWITCH_SENSOR:
                    case LIGHT_SENSOR:
                        return SensorUpdateValueEvent.class;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected boolean _passEvent(Event event) {
            switch (this.getType()) {
                case PULSE_SWITCH_SENSOR:
                    return ((SensorPulseEvent) event).getSensor().getId().equals(moduleId);
                case LIGHT_SENSOR:
                case SWITCH_SENSOR:
                    SensorUpdateValueEvent sEvent = (SensorUpdateValueEvent) event;
                    if (!sEvent.getSensor().getId().equals(moduleId))
                        return false;
                    return (comparator == 0 && value.equals(sEvent.getSensor().getSensorValue())) || (comparator == Float.compare((float) value, (Float) sEvent.getSensor().getSensorValue()));
            }
            return false;
        }
    }

    public static class TimeScheduleEvent extends ModuleScheduleEvent {
        private final int hour;

        public TimeScheduleEvent(int hour) {
            this.hour = hour;
        }

        @Override
        public Class<? extends Event> getEventClass() {
            return EventCalledEvent.class;
        }

        @Override
        protected boolean _passEvent(Event event) {
            return (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1) % 24 == hour;
        }
    }

}
