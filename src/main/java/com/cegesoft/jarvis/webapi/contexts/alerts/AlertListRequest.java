package com.cegesoft.jarvis.webapi.contexts.alerts;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.alert.Alert;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * GET /alerts/list : {"alerts": [...newest first], "active": n, "unread": n}
 */
public class AlertListRequest extends APIContext {

    public AlertListRequest() {
        super("/alerts/list");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleRequest(HttpExchange exchange) throws IOException {
        JSONArray array = new JSONArray();
        int active = 0;
        int unread = 0;
        for (Alert alert : Jarvis.getAlertManager().getAlerts()) {
            array.add(alert.toJson());
            if (alert.isActive())
                active++;
            if (!alert.read())
                unread++;
        }
        JSONObject answer = new JSONObject();
        answer.put("alerts", array);
        answer.put("active", active);
        answer.put("unread", unread);
        send(exchange, answer, 200);
    }
}
