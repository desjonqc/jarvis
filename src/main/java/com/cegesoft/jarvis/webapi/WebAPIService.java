package com.cegesoft.jarvis.webapi;

import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.service.Service;
import com.cegesoft.jarvis.webapi.contexts.HealthRequest;
import com.cegesoft.jarvis.webapi.contexts.SimulationPressRequest;
import com.cegesoft.jarvis.webapi.contexts.alerts.AlertClearRequest;
import com.cegesoft.jarvis.webapi.contexts.alerts.AlertListRequest;
import com.cegesoft.jarvis.webapi.contexts.alerts.AlertReadRequest;
import com.cegesoft.jarvis.webapi.contexts.auth.*;
import com.cegesoft.jarvis.webapi.contexts.module.*;
import com.cegesoft.jarvis.webapi.contexts.schedules.ScheduleEditRequest;
import com.cegesoft.jarvis.webapi.contexts.schedules.ScheduleGetRequest;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.cegesoft.jarvis.Jarvis.getLogger;

public class WebAPIService extends Service {

    private final List<APIContext> contexts = new ArrayList<>();
    private final JarvisConfig config;
    private HttpServer server;
    private ExecutorService executor;

    public WebAPIService(JarvisConfig config) {
        this.config = config;
        this.registerContexts();
    }

    @Override
    protected void startService() throws IOException {
        if (config.getApiKeys().isEmpty())
            getLogger().info("No API key configured (JARVIS_API_KEYS) : only the web app sessions are accepted.");
        server = HttpServer.create(new InetSocketAddress(config.getApiPort()), 0);
        // Requests wait for the modules' answers : each one gets its own (virtual) thread so they never block each other.
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("API-", 0).factory());
        server.setExecutor(executor);
        this.initContexts();
        server.start();
        getLogger().info("Web API listening on port " + config.getApiPort());
    }

    @Override
    protected void stopService() {
        server.stop(1);
        executor.shutdownNow();
    }

    private void registerContexts() {
        contexts.add(new HealthRequest());
        contexts.add(new SimulationPressRequest());

        contexts.add(new ModuleListRequest());
        contexts.add(new ModuleInfoRequest());
        contexts.add(new ModuleEnableRequest());
        contexts.add(new ModuleBatteryUpdateRequest());
        contexts.add(new ModuleActivateRequest());
        contexts.add(new ModuleEditRequest());
        contexts.add(new ModuleDiscoverRequest());

        contexts.add(new LoginRequest());
        contexts.add(new LogoutRequest());
        contexts.add(new MeRequest());
        contexts.add(new PasswordChangeRequest());
        contexts.add(new SessionListRequest());
        contexts.add(new SessionRevokeRequest());

        contexts.add(new AlertListRequest());
        contexts.add(new AlertReadRequest());
        contexts.add(new AlertClearRequest());

        contexts.add(new ScheduleEditRequest());
        contexts.add(new ScheduleGetRequest());
    }

    public APIContext getContextByPath(String path) {
        return contexts.stream().filter(context -> context.path.equals(path)).findFirst().orElse(null);
    }

    private void initContexts() {
        for (APIContext context : contexts) {
            context.configure(config.getApiKeys(), config.getCorsOrigin(), config.isCookieSecure());
            server.createContext(context.getPath(), context);
        }
    }

}
