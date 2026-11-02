package com.cegesoft.jarvis.webapi.contexts.auth;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.time.Duration;

/**
 * POST /auth/logout : closes the session of the cookie (always succeeds).
 */
public class LogoutRequest extends APIContext {

    public LogoutRequest() {
        super("/auth/logout");
    }

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        Jarvis.getAuthManager().getSessions().revokeToken(getCookie(exchange, SESSION_COOKIE));
        setSessionCookie(exchange, "", Duration.ZERO);
        send(exchange, new JSONObject(), 200);
    }
}
