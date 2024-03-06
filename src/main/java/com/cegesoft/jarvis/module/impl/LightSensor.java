package com.cegesoft.jarvis.module.impl;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.structure.sensor.ExternalNumericSensor;
import com.cegesoft.jarvis.properties.IAutoSerializable;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public class LightSensor extends ExternalNumericSensor {
    public LightSensor(String id, ModuleType type) {
        super(id, type);
    }

    public LightSensor() {

    }

    public float getLumens() {
        float Vout = (float)getSensorValue() * (5 / (float)1024);// Conversion analog to voltage
        float RLDR = (10000 * (5 - Vout))/Vout; // Conversion voltage to resistance
        return 500/(RLDR/1000); // Conversion to Lumens
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
