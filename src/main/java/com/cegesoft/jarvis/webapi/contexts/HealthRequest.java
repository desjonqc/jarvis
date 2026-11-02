package com.cegesoft.jarvis.webapi.contexts;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.radio.RadioService;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * Liveness probe (Docker healthcheck), callable without API key. It only exposes the radio state.
 */
public class HealthRequest extends APIContext {

    public HealthRequest() {
        super("/health");
    }

    @Override
    protected boolean requiresAuthentication() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleRequest(HttpExchange exchange) throws IOException {
        JSONObject status = new JSONObject();
        status.put("status", "ok");
        status.put("radio", Jarvis.getServiceManager().getService(RadioService.class).isRadioRunning());
        status.put("radioDriver", Jarvis.getConfig().getRadioDriver());
        status.put("radioChannel", Jarvis.getConfig().getRadioConfig().channel());
        status.put("modules", Jarvis.getModuleManager().getModules().size());
        send(exchange, status, 200);
    }
}
