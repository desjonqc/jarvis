package com.cegesoft.jarvis.module.structure.sensor;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.sensor.SensorUpdateValueEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.interfaces.IExternalSensor;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Opcode;

public abstract class ExternalBooleanSensor extends ExternalModule implements IExternalSensor<Boolean> {

    @SavedItem
    protected volatile boolean sensorValue = false;

    public ExternalBooleanSensor(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalBooleanSensor() {
    }

    @Override
    protected void handleFrame(Frame frame) {
        if (frame.opcode() != Opcode.SWITCH)
            return;
        sensorValue = frame.reader().u8() != 0;
        Jarvis.getEventManager().callEvent(new SensorUpdateValueEvent(this));
    }

    @Override
    public Boolean getSensorValue() {
        return sensorValue;
    }
}
