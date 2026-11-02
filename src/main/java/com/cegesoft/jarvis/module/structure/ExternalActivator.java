package com.cegesoft.jarvis.module.structure;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.ModuleCommandResultReceiveEvent;
import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.activator.ActivatorState;
import com.cegesoft.jarvis.module.interfaces.IExternalActivator;
import com.cegesoft.jarvis.properties.SavedItem;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.protocol.Payloads;

import java.util.concurrent.CompletableFuture;

/**
 * Created by HoxiSword on 26/05/2020 for JARVIS
 */
public abstract class ExternalActivator extends ExternalModule implements IExternalActivator {

    @SavedItem
    private volatile ActivatorState stateValue = ActivatorState.OFF;

    public ExternalActivator(String id, ModuleType type) {
        super(id, type);
    }

    public ExternalActivator() {}

    /**
     * Only the latest requested state matters : a pending older one is replaced.
     */
    @Override
    public CompletableFuture<?> write(ActivatorState state) {
        return this.request(Opcode.SET_STATE, true, Payloads.i8(state.getValue()));
    }

    @Override
    protected void handleFrame(Frame frame) {
        if (frame.opcode() != Opcode.STATE)
            return;
        ActivatorState newState = ActivatorState.getState(frame.reader().i8());
        if (stateValue != newState) {
            stateValue = newState;
            this.log("Module " + this.getId() + " changed state to : " + stateValue.name());
        }
        Jarvis.getEventManager().callEvent(new ModuleCommandResultReceiveEvent(this, "state"));
    }

    @Override
    public ActivatorState getActivatorState() {
        return stateValue;
    }
}
