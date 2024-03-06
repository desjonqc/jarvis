package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

public class ModuleBatteryLevelUpdateEvent extends ModuleEvent{

    private static final HandlerList handlers = new HandlerList();
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public ModuleBatteryLevelUpdateEvent(IExternalModule module) {
        super(module);
    }

    public double getNewBatteryLevel() {
        return getModule().getBatteryLevel();
    }
}
