package com.cegesoft.jarvis.webapi.contexts.alerts;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /alerts/clear : deletes the history (resolved alerts). The active ones stay.
 */
public class AlertClearRequest extends APIContext {

    public AlertClearRequest() {
        super("/alerts/clear");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        Jarvis.getAlertManager().clearResolved();
        send(exchange, new JSONObject(), 200);
    }
}
