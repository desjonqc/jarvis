package com.cegesoft.jarvis.webapi.contexts.auth;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.auth.Session;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /auth/password {"current", "next"} : changes the password of the logged in user, and closes its other sessions.
 */
public class PasswordChangeRequest extends APIContext {

    public PasswordChangeRequest() {
        super("/auth/password");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        Session session = getSession(exchange);
        JSONObject body = readJsonObject(exchange);
        if (session == null || body == null || !(body.get("current") instanceof String current) || !(body.get("next") instanceof String next)) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        try {
            if (!Jarvis.getAuthManager().changePassword(session, current, next)) {
                send(exchange, error("Mot de passe actuel incorrect"), 403);
                return;
            }
        } catch (IllegalArgumentException e) {
            send(exchange, error(e.getMessage()), 400);
            return;
        }
        send(exchange, new JSONObject(), 200);
    }
}
