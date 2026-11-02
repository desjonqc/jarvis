package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /module/edit?id=... {"name", "room"} : the labels shown in the web app.
 */
public class ModuleEditRequest extends ModuleTargetRequest {

    static final int MAX_LABEL_LENGTH = 40;

    public ModuleEditRequest() {
        super("/module/edit");
    }

    @Override
    public void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        JSONObject body = readJsonObject(exchange);
        if (body == null || !isLabel(body.get("name")) || !isLabel(body.get("room"))) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        module.setLabels((String) body.get("name"), (String) body.get("room"));
        Jarvis.getModuleManager().save();
        send(exchange, ModuleListRequest.describe(module), 200);
    }

    private static boolean isLabel(Object value) {
        return value == null || (value instanceof String label && label.strip().length() <= MAX_LABEL_LENGTH);
    }
}
