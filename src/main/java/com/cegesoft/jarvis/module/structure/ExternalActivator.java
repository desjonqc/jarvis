package com.cegesoft.jarvis.module.structure;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.activator.ActivatorState;
import com.cegesoft.jarvis.module.interfaces.IExternalActivator;
import com.cegesoft.jarvis.properties.SavedItem;

/**
 * Created by HoxiSword on 26/05/2020 for JARVIS
 */
public abstract class ExternalActivator extends ExternalModule implements IExternalActivator {

    @SavedItem
    private ActivatorState stateValue = ActivatorState.OFF;

    public ExternalActivator(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalActivator() {}

    @Override
    public void write(ActivatorState state) {
        this.executeCommand("state", state.getValue(), true);
    }

    @Override
    protected void stateChanged(ModuleState state) {
        switch (state) {
            case DISABLED:
            case DISCONNECTED:
                this.stateValue = ActivatorState.OFF;
        }
    }

    @Override
    public void registerCommands() {
        this.registerCommand("state", this::onCommandState);
    }

    private void onCommandState(String cmd) {
        ActivatorState newState = ActivatorState.getState(Integer.parseInt(cmd));
        if (newState != null && stateValue != newState) {
            stateValue = newState;
            this.log("Module " + this.getId() + " changed state to : " + stateValue.name());
        }
    }

    public ActivatorState getActivatorState() {
        return stateValue;
    }
}
