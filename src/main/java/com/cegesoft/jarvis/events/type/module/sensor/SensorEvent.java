package com.cegesoft.jarvis.events.type.module.sensor;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.module.interfaces.IExternalSensor;

/**
 * Created by HoxiSword on 15/05/2020 for JARVIS
 */
public class SensorEvent extends Event {

    private final IExternalSensor<?> sensor;

    public SensorEvent(IExternalSensor<?> sensor) {
        this.sensor = sensor;
    }

    public IExternalSensor<?> getSensor() {
        return sensor;
    }
}
