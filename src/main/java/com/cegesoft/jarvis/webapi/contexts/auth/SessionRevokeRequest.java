package com.cegesoft.jarvis.webapi.contexts.auth;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.auth.Session;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /auth/sessions/revoke?id=... : disconnects one of the user's devices.
 */
public class SessionRevokeRequest extends APIContext {

    public SessionRevokeRequest() {
        super("/auth/sessions/revoke");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        Session current = getSession(exchange);
        String id = getParameter(exchange, "id");
        if (current == null || id.isEmpty()) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        if (!Jarvis.getAuthManager().getSessions().revoke(current.username(), id)) {
            send(exchange, NOT_FOUND_ERROR, 404);
            return;
        }
        send(exchange, new JSONObject(), 200);
    }
}
