package com.cegesoft.jarvis.module.structure.sensor;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.sensor.SensorUpdateValueEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.interfaces.IExternalSensor;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import com.cegesoft.jarvis.properties.SavedItem;

/**
 * Created by HoxiSword on 15/05/2020 for JARVIS
 */
public abstract class ExternalNumericSensor extends ExternalModule implements IExternalSensor<Integer> {

    @SavedItem
    protected int sensorValue = 0;

    public ExternalNumericSensor(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalNumericSensor(){
    }

    @Override
    protected void stateChanged(ModuleState state) {}

    @Override
    public void registerCommands() {
        this.registerCommand("value", this::onCommandValue);
    }

    private void onCommandValue(String args) {
        sensorValue = Integer.parseInt(args);
        Jarvis.getEventManager().callEvent(new SensorUpdateValueEvent(this));
    }

    @Override
    public Integer getSensorValue() {
        return sensorValue;
    }
}
