package com.cegesoft.jarvis.webapi.contexts.auth;

import com.cegesoft.jarvis.auth.Session;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * GET /auth/me : who is logged in (401 when the session is over).
 */
public class MeRequest extends APIContext {

    public MeRequest() {
        super("/auth/me");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleRequest(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        JSONObject answer = new JSONObject();
        answer.put("username", session == null ? null : session.username());
        answer.put("method", session == null ? "apikey" : "session");
        if (session != null) {
            answer.put("sessionId", session.id());
            answer.put("expiresAt", session.expiresAt());
        }
        send(exchange, answer, 200);
    }
}
