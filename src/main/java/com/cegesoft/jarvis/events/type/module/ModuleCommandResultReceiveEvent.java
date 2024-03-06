package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.HandlerList;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

public class ModuleCommandResultReceiveEvent extends ModuleEvent{

    private static final HandlerList handlers = new HandlerList();
    public static HandlerList getHandlerList() {
        return handlers;
    }

    private final String command;

    public ModuleCommandResultReceiveEvent(IExternalModule module, String command) {
        super(module);
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
