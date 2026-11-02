package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /module/discover : asks every listening module to introduce itself (a new module appears in the list when
 * it answers). Sleeping modules introduce themselves when they wake up.
 */
public class ModuleDiscoverRequest extends APIContext {

    public ModuleDiscoverRequest() {
        super("/module/discover");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        Jarvis.getModuleManager().discover();
        send(exchange, new JSONObject(), 200);
    }
}
