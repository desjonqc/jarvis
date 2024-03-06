package com.cegesoft.jarvis.events.type.module.sensor;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.structure.sensor.ExternalPulseSensor;

public class SensorPulseEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final ExternalPulseSensor sensor;

    public SensorPulseEvent(ExternalPulseSensor sensor) {
        this.sensor = sensor;
    }

    public ExternalPulseSensor getSensor() {
        return sensor;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
