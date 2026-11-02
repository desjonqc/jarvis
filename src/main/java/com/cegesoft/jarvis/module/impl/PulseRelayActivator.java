package com.cegesoft.jarvis.module.impl;

import com.cegesoft.jarvis.module.ModuleType;
import com.cegesoft.jarvis.module.activator.ActivatorState;
import com.cegesoft.jarvis.module.structure.ExternalActivator;
import com.cegesoft.jarvis.properties.IAutoSerializable;
import com.cegesoft.jarvis.radio.protocol.Opcode;

import java.util.concurrent.CompletableFuture;

/**
 * A relay pulsed to toggle a remote switch (télérupteur) : its real state is unknown, every request is a toggle.
 */
public class PulseRelayActivator extends ExternalActivator {
    public PulseRelayActivator(String id, ModuleType type) {
        super(id, type);
    }

    public PulseRelayActivator() {}

    /**
     * Every pulse counts (two quick presses = two toggles) : never coalesced. The radio seq guarantees that a
     * retransmitted pulse is not executed twice.
     */
    public CompletableFuture<?> pulse() {
        return this.request(Opcode.PULSE, false);
    }

    @Override
    public CompletableFuture<?> write(ActivatorState state) {
        return this.pulse();
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
