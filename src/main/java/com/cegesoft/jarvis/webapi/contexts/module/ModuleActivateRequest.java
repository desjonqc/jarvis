package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.ModuleCommandResultReceiveEvent;
import com.cegesoft.jarvis.events.type.module.ModuleLowSignalDetectedEvent;
import com.cegesoft.jarvis.module.activator.ActivatorState;
import com.cegesoft.jarvis.module.interfaces.IExternalActivator;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

public class ModuleActivateRequest extends ModuleTargetRequest {

    public ModuleActivateRequest() {
        super("/module/activate");
    }

    @Override
    public void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException {
        String stateValue = getParameter(exchange, "stateValue");
        ActivatorState state;
        boolean success;
        try {
            if (!(module instanceof IExternalActivator))
                throw new Exception();
            state = ActivatorState.valueOf(stateValue);
            ((IExternalActivator) module).write(state);
            success = Jarvis.getEventManager().waitEventUntil(ModuleCommandResultReceiveEvent.class, ModuleLowSignalDetectedEvent.class, event -> event.getModule().getId().equals(module.getId()) && event.getCommand().equals("state"), event -> event.getModule().getId().equals(module.getId()));
        } catch (Exception ignored) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }

        send(exchange, success ? new JSONObject() : module.save(), 200);
    }
}
