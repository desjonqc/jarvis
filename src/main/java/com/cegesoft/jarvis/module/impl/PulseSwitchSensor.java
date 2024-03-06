package com.cegesoft.jarvis.module.impl;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.structure.sensor.ExternalPulseSensor;
import com.cegesoft.jarvis.properties.IAutoSerializable;

public class PulseSwitchSensor extends ExternalPulseSensor {

    public PulseSwitchSensor(String id, ModuleType type) {
        super(id, type);
    }

    public PulseSwitchSensor() {

    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
