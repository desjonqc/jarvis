package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;

public class ModuleListRequest extends APIContext {
    public ModuleListRequest() {
        super("/module/list");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        JSONObject returner = new JSONObject();
        JSONArray array = new JSONArray();
        for (IExternalModule module : Jarvis.getModuleManager().getModules()) {
            array.add(describe(module));
        }
        returner.put("modules", array);
        send(exchange, returner, 200);
    }

    /**
     * The saved state of the module, plus what the web app needs to show it.
     */
    @SuppressWarnings("unchecked")
    public static JSONObject describe(IExternalModule module) {
        JSONObject json = module.save();
        json.put("lastSeen", module.getLastSeen());
        json.put("connected", module.isConnected());
        json.put("enabled", module.isEnabled());
        return json;
    }
}
