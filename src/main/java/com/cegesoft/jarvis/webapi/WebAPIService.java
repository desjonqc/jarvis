package com.cegesoft.jarvis.webapi;

import com.cegesoft.jarvis.service.Service;
import com.cegesoft.jarvis.webapi.contexts.module.*;
import com.cegesoft.jarvis.webapi.contexts.schedules.ScheduleEditRequest;
import com.cegesoft.jarvis.webapi.contexts.schedules.ScheduleGetRequest;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class WebAPIService extends Service {

    public static final List<String> API_KEYS = new ArrayList<>();

    static {
        API_KEYS.add("yBo$6G6jj7pGdojti6y@PAKJJf@GkzgQeC56fXgt");
    }

    private final List<APIContext> contexts = new ArrayList<>();
    private HttpServer server;

    public WebAPIService() {
        this.registerContexts();
    }

    @Override
    protected void startService() {
        try {
            server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.setExecutor(null); // creates a default executor
            this.initContexts();
            server.start();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void stopService() {
        server.stop(0);
    }

    private void registerContexts() {
        contexts.add(new ModuleListRequest());
        contexts.add(new ModuleInfoRequest());
        contexts.add(new ModuleEnableRequest());
        contexts.add(new ModuleBatteryUpdateRequest());
        contexts.add(new ModuleActivateRequest());

        contexts.add(new ScheduleEditRequest());
        contexts.add(new ScheduleGetRequest());
    }

    public APIContext getContextByPath(String path) {
        return contexts.stream().filter(context -> context.path.equals(path)).findFirst().orElse(null);
    }

    private void initContexts() {
        for (APIContext context : contexts) {
            server.createContext(context.getPath(), context);
        }
    }

}
