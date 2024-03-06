package com.cegesoft.jarvis.events.type.module;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;

/**
 * Created by HoxiSword on 14/05/2020 for JARVIS
 */
public class ModuleEvent extends Event {

    private final IExternalModule module;

    public ModuleEvent(IExternalModule module) {
        this.module = module;
    }

    public IExternalModule getModule() {
        return module;
    }
}
