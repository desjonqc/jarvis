package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public class ModuleLowPowerDetectedEvent extends ModuleEvent {
    private static final HandlerList handlers = new HandlerList();
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public ModuleLowPowerDetectedEvent(IExternalModule module) {
        super(module);
    }

}
