package com.cegesoft.jarvis.webapi.contexts.auth;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.auth.Session;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * GET /auth/sessions : the devices where the logged in user is connected.
 */
public class SessionListRequest extends APIContext {

    public SessionListRequest() {
        super("/auth/sessions");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleRequest(HttpExchange exchange) throws IOException {
        Session current = getSession(exchange);
        if (current == null) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        JSONArray array = new JSONArray();
        for (Session session : Jarvis.getAuthManager().getSessions().list(current.username()))
            array.add(session.toPublicJson(session.id().equals(current.id())));
        JSONObject answer = new JSONObject();
        answer.put("sessions", array);
        send(exchange, answer, 200);
    }
}
