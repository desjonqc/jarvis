package com.cegesoft.jarvis.module.structure.sensor;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.sensor.SensorUpdateValueEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.interfaces.IExternalSensor;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Opcode;

/**
 * Created by HoxiSword on 15/05/2020 for JARVIS
 */
public abstract class ExternalNumericSensor extends ExternalModule implements IExternalSensor<Integer> {

    @SavedItem
    protected volatile int sensorValue = 0;

    public ExternalNumericSensor(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalNumericSensor(){
    }

    @Override
    protected void handleFrame(Frame frame) {
        if (frame.opcode() != Opcode.ANALOG)
            return;
        sensorValue = frame.reader().u16();
        Jarvis.getEventManager().callEvent(new SensorUpdateValueEvent(this));
    }

    @Override
    public Integer getSensorValue() {
        return sensorValue;
    }
}
