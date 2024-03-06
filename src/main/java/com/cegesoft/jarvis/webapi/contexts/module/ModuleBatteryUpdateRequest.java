package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.module.ModuleBatteryLevelUpdateEvent;
import com.cegesoft.jarvis.events.type.module.ModuleLowSignalDetectedEvent;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

public class ModuleBatteryUpdateRequest extends ModuleTargetRequest {

    public ModuleBatteryUpdateRequest() {
        super("/module/update/battery");
    }

    @Override
    public void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException {
        module.updateBatteryLevel();
        boolean success = Jarvis.getEventManager().waitEventUntil(ModuleBatteryLevelUpdateEvent.class, ModuleLowSignalDetectedEvent.class, event -> event.getModule().getId().equals(module.getId()), event -> event.getModule().getId().equals(module.getId()));
        JSONObject returner = new JSONObject();
        returner.put("batteryLevel", module.getBatteryLevel());
        send(exchange, success ? returner : module.save(), 200);
    }
}
