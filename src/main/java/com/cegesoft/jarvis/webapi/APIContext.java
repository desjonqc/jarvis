package com.cegesoft.jarvis.webapi;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.auth.Session;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

public abstract class APIContext implements HttpHandler {

    protected static final JSONObject UNAUTHORIZED_ERROR = error("Error 401 : Unauthorized");
    protected static final JSONObject BAD_REQUEST_ERROR = error("Error 400 : Bad Request");
    protected static final JSONObject NOT_FOUND_ERROR = error("Error 404 : Not Found");
    protected static final JSONObject INTERNAL_ERROR = error("Error 500 : Internal Error");
    protected static final JSONObject METHOD_NOT_ALLOWED_ERROR = error("Error 405 : Method Not Allowed");
    public static final String SESSION_COOKIE = "jarvis_session";
    private static final String SESSION_ATTRIBUTE = "jarvis.session";
    private static final int MAX_BODY_LENGTH = 1024 * 1024;

    protected final String path;
    private List<byte[]> apiKeys = List.of();
    private String corsOrigin = "*";
    private boolean cookieSecure;

    public APIContext(String path) {
        this.path = path;
    }

    @SuppressWarnings("unchecked")
    protected static JSONObject error(String message) {
        JSONObject error = new JSONObject();
        error.put("error", message);
        return error;
    }

    void configure(List<String> apiKeys, String corsOrigin, boolean cookieSecure) {
        this.apiKeys = apiKeys.stream().map(key -> key.getBytes(StandardCharsets.UTF_8)).toList();
        this.corsOrigin = corsOrigin;
        this.cookieSecure = cookieSecure;
    }

    public String getPath() {
        return path;
    }

    /**
     * @return false for the contexts which can be called without an API key nor a session
     */
    protected boolean requiresAuthentication() {
        return true;
    }

    @Override
    public void handle(HttpExchange httpExchange) {
        try (httpExchange) {
            this.configureHeaders(httpExchange);
            if (httpExchange.getRequestMethod().equals("OPTIONS")) {
                httpExchange.sendResponseHeaders(204, -1);
                return;
            }
            if (requiresAuthentication() && !authenticate(httpExchange)) {
                send(httpExchange, UNAUTHORIZED_ERROR, 401);
                return;
            }
            handleRequest(httpExchange);
        } catch (Exception e) {
            Jarvis.getLogger().error("Error while handling " + httpExchange.getRequestURI() + " : ", e);
            try {
                send(httpExchange, INTERNAL_ERROR, 500);
            } catch (IOException | RuntimeException ignored) {
                // The response was already (partially) sent : nothing more can be done.
            }
        }
    }

    /**
     * A request is accepted with an API key (scripts, "apikey" header) or a web app session (cookie).
     */
    private boolean authenticate(HttpExchange exchange) {
        if (isAuthorized(exchange.getRequestHeaders().getFirst("apikey")))
            return true;
        Session session = Jarvis.getAuthManager().authenticate(getCookie(exchange, SESSION_COOKIE));
        if (session == null)
            return false;
        exchange.setAttribute(SESSION_ATTRIBUTE, session);
        return true;
    }

    /**
     * @return the web app session of the request, or null (no session, or authenticated with an API key)
     */
    protected Session getSession(HttpExchange exchange) {
        return (Session) exchange.getAttribute(SESSION_ATTRIBUTE);
    }

    protected String getCookie(HttpExchange exchange, String name) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null)
            return null;
        for (String header : headers) {
            for (String cookie : header.split(";")) {
                int separator = cookie.indexOf('=');
                if (separator > 0 && cookie.substring(0, separator).trim().equals(name))
                    return cookie.substring(separator + 1).trim();
            }
        }
        return null;
    }

    /**
     * HttpOnly (unreadable by scripts) and SameSite=Strict (never sent by another site : no CSRF).
     *
     * @param maxAge null = deleted when the browser closes, zero = deleted now
     */
    protected void setSessionCookie(HttpExchange exchange, String token, Duration maxAge) {
        StringBuilder cookie = new StringBuilder(SESSION_COOKIE).append('=').append(token)
                .append("; Path=/; HttpOnly; SameSite=Strict");
        if (maxAge != null)
            cookie.append("; Max-Age=").append(maxAge.toSeconds());
        if (cookieSecure)
            cookie.append("; Secure");
        exchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
    }

    /**
     * @return false (and the 405 answer is sent) if the request doesn't use this method
     */
    protected boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase(method))
            return true;
        exchange.getResponseHeaders().set("Allow", method);
        send(exchange, METHOD_NOT_ALLOWED_ERROR, 405);
        return false;
    }

    /**
     * @return the json object of the body, or null if it is not one
     */
    protected JSONObject readJsonObject(HttpExchange exchange) throws IOException {
        try {
            return new JSONParser().parse(readBody(exchange)) instanceof JSONObject json ? json : null;
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean isAuthorized(String key) {
        if (key == null)
            return false;
        byte[] given = key.getBytes(StandardCharsets.UTF_8);
        boolean authorized = false;
        // Constant time comparison, against every key, so that the response time doesn't leak anything.
        for (byte[] apiKey : apiKeys)
            authorized |= MessageDigest.isEqual(apiKey, given);
        return authorized;
    }

    protected String readBody(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            byte[] bytes = body.readNBytes(MAX_BODY_LENGTH + 1);
            if (bytes.length > MAX_BODY_LENGTH)
                throw new IOException("Request body too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    protected void send(HttpExchange exchange, Object object, int code) throws IOException {
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * @return the decoded value of a query parameter, or an empty string if it is missing
     */
    protected String getParameter(HttpExchange exchange, String key) {
        String fullQuery = exchange.getRequestURI().getRawQuery();
        if (fullQuery == null)
            return "";
        for (String parameter : fullQuery.split("&")) {
            int separator = parameter.indexOf('=');
            String paramKey = URLDecoder.decode(separator < 0 ? parameter : parameter.substring(0, separator), StandardCharsets.UTF_8);
            if (paramKey.equals(key))
                return separator < 0 ? "" : URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8);
        }
        return "";
    }


    protected void configureHeaders(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE, OPTIONS");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Origin, Content-Type, apikey");
        httpExchange.getResponseHeaders().set("Cache-Control", "no-store");
        httpExchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    }


    protected abstract void handleRequest(HttpExchange exchange) throws IOException;
}
