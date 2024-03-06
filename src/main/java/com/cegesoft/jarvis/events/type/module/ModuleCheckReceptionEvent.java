package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

/**
 * Created by HoxiSword on 15/05/2020 for JARVIS
 */
public class ModuleCheckReceptionEvent extends ModuleEvent {

    private static final HandlerList handlers = new HandlerList();
    public static HandlerList getHandlerList() {
        return handlers;
    }

    private final int receptionId;

    public ModuleCheckReceptionEvent(IExternalModule module, int receptionId) {
        super(module);
        this.receptionId = receptionId;
    }

    public int getReceptionId() {
        return receptionId;
    }
}
