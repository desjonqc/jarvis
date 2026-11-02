package com.cegesoft.jarvis.module.schedule;

import com.cegesoft.jarvis.events.type.TimeTickEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ModuleScheduleEventTest {

    @Test
    void switchSensorComparesNumbersWhateverTheirType() {
        // json-simple gives a Long, the light sensor an Integer
        ModuleScheduleEvent.SwitchScheduleEvent equals = new ModuleScheduleEvent.SwitchScheduleEvent("l1", 0, 500L);
        assertTrue(equals.matches(500));
        assertFalse(equals.matches(501));

        ModuleScheduleEvent.SwitchScheduleEvent below = new ModuleScheduleEvent.SwitchScheduleEvent("l1", -1, 500L);
        assertTrue(below.matches(800));
        assertFalse(below.matches(200));

        ModuleScheduleEvent.SwitchScheduleEvent above = new ModuleScheduleEvent.SwitchScheduleEvent("l1", 1, 500.0);
        assertTrue(above.matches(200));
        assertFalse(above.matches(800));
    }

    @Test
    void switchSensorComparesBooleansWithoutException() {
        ModuleScheduleEvent.SwitchScheduleEvent on = new ModuleScheduleEvent.SwitchScheduleEvent("s1", 0, true);
        assertTrue(on.matches(true));
        assertFalse(on.matches(false));
        assertFalse(on.matches(null));
    }

    @Test
    void hourScheduleTriggersOncePerDay() {
        ModuleScheduleEvent.TimeScheduleEvent event = new ModuleScheduleEvent.TimeScheduleEvent(7);
        LocalDateTime morning = LocalDateTime.of(2026, 11, 2, 7, 0, 10);

        assertFalse(event.passEvent(new TimeTickEvent(morning.minusMinutes(1))));
        assertTrue(event.passEvent(new TimeTickEvent(morning)));
        assertFalse(event.passEvent(new TimeTickEvent(morning.plusMinutes(1))));
        assertFalse(event.passEvent(new TimeTickEvent(morning.plusMinutes(59))));
        assertTrue(event.passEvent(new TimeTickEvent(morning.plusDays(1))));
    }
}
