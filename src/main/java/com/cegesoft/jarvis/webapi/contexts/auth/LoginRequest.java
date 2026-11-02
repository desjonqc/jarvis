package com.cegesoft.jarvis.webapi.contexts.auth;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.auth.AuthManager;
import com.cegesoft.jarvis.auth.SessionManager;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * POST /auth/login {"username", "password", "remember"} : opens a session (cookie).
 */
public class LoginRequest extends APIContext {

    public LoginRequest() {
        super("/auth/login");
    }

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST"))
            return;
        JSONObject body = readJsonObject(exchange);
        if (body == null || !(body.get("username") instanceof String username) || !(body.get("password") instanceof String password)) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        boolean remember = Boolean.TRUE.equals(body.get("remember"));
        AuthManager auth = Jarvis.getAuthManager();
        switch (auth.login(username, password, remember, exchange.getRequestHeaders().getFirst("User-Agent"))) {
            case AuthManager.LoginResult.Success(SessionManager.Created created) -> {
                setSessionCookie(exchange, created.token(), auth.getSessions().cookieMaxAge(created.session()));
                JSONObject answer = new JSONObject();
                answer.put("username", created.session().username());
                send(exchange, answer, 200);
            }
            case AuthManager.LoginResult.Invalid() -> send(exchange, error("Identifiant ou mot de passe incorrect"), 401);
            case AuthManager.LoginResult.Throttled(long retryAfterMs) -> {
                long seconds = Math.max(1, (retryAfterMs + 999) / 1000);
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(seconds));
                JSONObject answer = error("Trop de tentatives, réessaie dans " + seconds + " s");
                answer.put("retryAfter", seconds);
                send(exchange, answer, 429);
            }
        }
    }
}
