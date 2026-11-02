package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.events.type.TimeTickEvent;
import com.cegesoft.jarvis.events.type.module.ModuleEnableChangeEvent;
import com.cegesoft.jarvis.events.type.module.sensor.SensorPulseEvent;
import com.cegesoft.jarvis.events.type.module.sensor.SensorUpdateValueEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public abstract class ModuleScheduleEvent {

    private volatile boolean hasPassed = false;
    private volatile TaskId resetTask;

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
            Event called = ((EventCalledEvent) event).getEventCalled();
            boolean and = combination.equals("and");
            boolean passed = and;
            // Every sub event sees the event (no short-circuit) : each one has to remember that it passed.
            for (ModuleScheduleEvent e : events) {
                boolean subPassed = e.passEvent(called);
                passed = and ? passed && subPassed : passed || subPassed;
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

        public String getModuleId() {
            return moduleId;
        }

        /**
         * @return the type of the module, or null if it is unknown (not connected yet)
         */
        private ModuleType getType() {
            IExternalModule module = Jarvis.getModuleManager().getModule(moduleId);
            return module == null ? null : module.getType();
        }

        @Override
        public Class<? extends Event> getEventClass() {
            ModuleType type = this.getType();
            if (type == null)
                return null;
            switch (type) {
                case PULSE_SWITCH_SENSOR:
                    return SensorPulseEvent.class;
                case SWITCH_SENSOR:
                case LIGHT_SENSOR:
                    return SensorUpdateValueEvent.class;
            }
            return null;
        }

        @Override
        protected boolean _passEvent(Event event) {
            if (event instanceof SensorPulseEvent)
                return ((SensorPulseEvent) event).getSensor().getId().equals(moduleId);
            if (event instanceof SensorUpdateValueEvent) {
                SensorUpdateValueEvent sEvent = (SensorUpdateValueEvent) event;
                return sEvent.getSensor().getId().equals(moduleId) && matches(sEvent.getSensor().getSensorValue());
            }
            return false;
        }

        /**
         * comparator 0 : the sensor value equals the expected value.
         * comparator -1 / 1 : the expected value is lower / greater than the sensor value (numeric sensors).
         */
        boolean matches(Object sensorValue) {
            if (value instanceof Number && sensorValue instanceof Number) {
                int comparison = Integer.signum(Double.compare(((Number) value).doubleValue(), ((Number) sensorValue).doubleValue()));
                return comparison == comparator;
            }
            return comparator == 0 && sensorValue != null && String.valueOf(value).equals(String.valueOf(sensorValue));
        }
    }

    /**
     * Alone, triggers once a day when the given hour starts (local time, see the TZ variable).
     * Inside a combination, it is a condition : "the current hour is the given hour".
     */
    public static class TimeScheduleEvent extends ModuleScheduleEvent {
        private final int hour;
        private volatile LocalDate lastTrigger;

        public TimeScheduleEvent(int hour) {
            this.hour = hour;
        }

        @Override
        public Class<? extends Event> getEventClass() {
            return TimeTickEvent.class;
        }

        @Override
        public boolean passEvent(Event event) {
            if (!(event instanceof TimeTickEvent))
                return LocalTime.now().getHour() == hour;
            return _passEvent(event);
        }

        @Override
        protected boolean _passEvent(Event event) {
            TimeTickEvent tick = (TimeTickEvent) event;
            LocalDate today = tick.getTime().toLocalDate();
            if (tick.getTime().getHour() != hour || today.equals(lastTrigger))
                return false;
            lastTrigger = today;
            return true;
        }
    }

}
