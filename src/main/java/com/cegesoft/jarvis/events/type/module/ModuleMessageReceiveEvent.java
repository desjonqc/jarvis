package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.HandlerList;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public class ModuleMessageReceiveEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final String moduleId;
    private final String message;

    public ModuleMessageReceiveEvent(String moduleId, String message) {
        this.moduleId = moduleId;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }


    public static HandlerList getHandlerList() {
        return handlers;
    }

    public String getModuleId() {
        return moduleId;
    }
}
