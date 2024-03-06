package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.ModuleEnableChangeEvent;
import com.cegesoft.jarvis.events.type.module.ModuleLowSignalDetectedEvent;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

public class ModuleEnableRequest extends ModuleTargetRequest {

    public ModuleEnableRequest() {
        super("/module/enable");
    }

    @Override
    public void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException {
        String enable = getParameter(exchange, "enable");
        if (enable == null || enable.equals("")) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        boolean enabled = enable.equals("true");
        module.setEnabled(enabled);
        boolean success = Jarvis.getEventManager().waitEventUntil(ModuleEnableChangeEvent.class, ModuleLowSignalDetectedEvent.class,
                event -> event.getModule().getId().equals(module.getId()),
                event -> event.getModule().getId().equals(module.getId()));
        if (!success || module.isEnabled() == enabled) {
            send(exchange, success ? new JSONObject() : module.save(), 200);
            return;
        }
        send(exchange, new JSONObject(), 500);
    }
}
