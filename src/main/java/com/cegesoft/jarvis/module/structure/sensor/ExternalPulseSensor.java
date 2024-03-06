package com.cegesoft.jarvis.module.structure.sensor;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.sensor.SensorPulseEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.structure.ExternalModule;

public abstract class ExternalPulseSensor extends ExternalModule {

    private int sensorPacketId = 0;

    public ExternalPulseSensor(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalPulseSensor() {
    }

    @Override
    protected void stateChanged(ModuleState state) {
    }

    @Override
    public void registerCommands() {
        this.registerCommand("value", this::onCommandValue);
    }

    private void onCommandValue(String args) {
        int sensorPacketId = Integer.parseInt(args);
        if (sensorPacketId == this.sensorPacketId || sensorPacketId == 0) {
            return;
        }
        this.sensorPacketId = sensorPacketId;
        Jarvis.getEventManager().callEvent(new SensorPulseEvent(this));
    }
}
