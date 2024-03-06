package com.cegesoft.jarvis.module.impl;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.structure.sensor.ExternalBooleanSensor;
import com.cegesoft.jarvis.properties.IAutoSerializable;

public class SwitchSensor extends ExternalBooleanSensor {
    public SwitchSensor(String id, ModuleType type) {
        super(id, type);
    }

    public SwitchSensor() {

    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
