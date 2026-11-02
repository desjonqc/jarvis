package com.cegesoft.jarvis.webapi.contexts.module;

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
        ActivatorState state;
        try {
            state = ActivatorState.valueOf(getParameter(exchange, "stateValue"));
        } catch (IllegalArgumentException e) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        if (!(module instanceof IExternalActivator activator)) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        boolean success = await(activator.write(state));
        send(exchange, success ? new JSONObject() : module.save(), 200);
    }
}
