package com.cegesoft.jarvis.webapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public abstract class APIContext implements HttpHandler {

    protected static final JSONObject UNAUTHORIZED_ERROR = new JSONObject();
    protected static final JSONObject BAD_REQUEST_ERROR = new JSONObject();
    protected static final JSONObject NOT_FOUND_ERROR = new JSONObject();
    protected static final JSONObject INTERNAL_ERROR = new JSONObject();

    static {
        UNAUTHORIZED_ERROR.put("error", "Error 401 : Unauthorized");
        BAD_REQUEST_ERROR.put("error", "Error 400 : Bad Request");
        NOT_FOUND_ERROR.put("error", "Error 404 : Not Found");
        INTERNAL_ERROR.put("error", "Error 500 : Internal Error");
    }

    protected final String path;

    public APIContext(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        this.configureHeaders(httpExchange);
        if (httpExchange.getRequestMethod().equals("OPTIONS")) {
            httpExchange.sendResponseHeaders(200, -1);
            httpExchange.close();
            return;
        }
        if (!httpExchange.getRequestHeaders().containsKey("apikey") || !WebAPIService.API_KEYS.contains(httpExchange.getRequestHeaders().getFirst("apikey"))) {
            send(httpExchange, UNAUTHORIZED_ERROR, 401);
            httpExchange.close();
            return;
        }
        handleRequest(httpExchange);
        httpExchange.close();
    }

    protected String readBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
        String r = reader.lines().collect(Collectors.joining("\n"));
        reader.close();
        return r;
    }

    protected void send(HttpExchange exchange, Object object, int code) throws IOException {
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    protected String getParameter(HttpExchange exchange, String key) {
        String fullQuery = exchange.getRequestURI().getQuery();
        String[] parameters = fullQuery.split("&");
        for (String parameter : parameters) {
            String paramKey = parameter.split("=")[0];
            String paramValue = parameter.split("=")[1];
            if (paramKey.equals(key)) {
                return paramValue;
            }
        }
        return "";
    }


    protected void configureHeaders(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("Connection", "close");
        httpExchange.getResponseHeaders().set("Content-Type", "application/json");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE, OPTIONS");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Origin, Content-Type, apikey");
    }


    protected abstract void handleRequest(HttpExchange exchange) throws IOException;
}
