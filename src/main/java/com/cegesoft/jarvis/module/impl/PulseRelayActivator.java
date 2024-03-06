package com.cegesoft.jarvis.module.impl;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.activator.ActivatorState;
import com.cegesoft.jarvis.module.structure.ExternalActivator;
import com.cegesoft.jarvis.properties.IAutoSerializable;

import java.util.Random;

public class PulseRelayActivator extends ExternalActivator {
    public PulseRelayActivator(String id, ModuleType type) {
        super(id, type);
    }

    public PulseRelayActivator() {}


    @Override
    public void postLoad() {
        super.postLoad();
    }

    public void pulse() {
        this.executeCommand("state", new Random().nextInt(100), true);
    }

    @Override
    public void write(ActivatorState state) {
        this.pulse();
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
