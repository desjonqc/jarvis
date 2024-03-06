package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public class ModuleEnableChangeEvent extends ModuleEvent {

    private final boolean newState;

    private static final HandlerList handlers = new HandlerList();
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public ModuleEnableChangeEvent(IExternalModule module, boolean newState) {
        super(module);
        this.newState = newState;
    }

    public boolean isNewState() {
        return newState;
    }
}
