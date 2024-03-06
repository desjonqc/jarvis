package com.cegesoft.jarvis.events.type.module.sensor;

import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.interfaces.IExternalSensor;

/**
 * Created by HoxiSword on 15/05/2020 for JARVIS
 */
public class SensorUpdateValueEvent extends SensorEvent {
    private static final HandlerList handlers = new HandlerList();

    public SensorUpdateValueEvent(IExternalSensor<?> sensor) {
        super(sensor);
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
