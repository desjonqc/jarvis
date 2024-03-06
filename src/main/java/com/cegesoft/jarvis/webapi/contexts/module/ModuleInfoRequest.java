package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class ModuleInfoRequest extends ModuleTargetRequest {

    public ModuleInfoRequest() {
        super("/module/info");
    }


    @Override
    public void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException {
        send(exchange, module.save(), 200);
    }
}
