package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

public class ModuleBatteryUpdateRequest extends ModuleTargetRequest {

    public ModuleBatteryUpdateRequest() {
        super("/module/update/battery");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException {
        boolean success = await(module.updateBatteryLevel());
        JSONObject returner = new JSONObject();
        returner.put("batteryLevel", module.getBatteryLevel());
        send(exchange, success ? returner : module.save(), 200);
    }
}
