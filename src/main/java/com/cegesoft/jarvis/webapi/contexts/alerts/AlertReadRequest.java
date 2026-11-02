package com.cegesoft.jarvis.webapi.contexts.alerts;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /alerts/read?id=... : marks an alert as read (every alert without id).
 */
public class AlertReadRequest extends APIContext {

    public AlertReadRequest() {
        super("/alerts/read");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        String id = getParameter(exchange, "id");
        Long alertId;
        try {
            alertId = id.isEmpty() ? null : Long.parseLong(id);
        } catch (NumberFormatException e) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        if (!Jarvis.getAlertManager().markRead(alertId)) {
            send(exchange, NOT_FOUND_ERROR, 404);
            return;
        }
        send(exchange, new JSONObject(), 200);
    }
}
