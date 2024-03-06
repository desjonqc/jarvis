package com.cegesoft.jarvis.module.structure.sensor;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.sensor.SensorUpdateValueEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.interfaces.IExternalSensor;
import com.cegesoft.jarvis.module.structure.ExternalModule;
import com.cegesoft.jarvis.properties.SavedItem;

public abstract class ExternalBooleanSensor extends ExternalModule implements IExternalSensor<Boolean> {

    @SavedItem
    protected boolean sensorValue = false;

    public ExternalBooleanSensor(String id, ModuleType type) {
        super(id, type);

    }

    public ExternalBooleanSensor() {
    }

    @Override
    protected void stateChanged(ModuleState state) {}

    @Override
    public void postLoad() {
        super.postLoad();
        this.registerCommand("value", args -> {
            sensorValue = Boolean.parseBoolean(args);
            Jarvis.getEventManager().callEvent(new SensorUpdateValueEvent(this));
        });
    }

    @Override
    public void registerCommands() {
        this.registerCommand("value", this::onCommandValue);
    }

    private void onCommandValue(String args) {
        sensorValue = Boolean.parseBoolean(args);
        Jarvis.getEventManager().callEvent(new SensorUpdateValueEvent(this));
    }

    @Override
    public Boolean getSensorValue() {
        return sensorValue;
    }
}
