package com.cegesoft.jarvis.webapi.contexts.module;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.interfaces.IExternalModule;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public abstract class ModuleTargetRequest extends APIContext {

    public ModuleTargetRequest(String path) {
        super(path);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String moduleId = getParameter(exchange, "id");
        if (moduleId == null || moduleId.equals("")) {
            send(exchange, BAD_REQUEST_ERROR, 400);
            return;
        }
        IExternalModule module = Jarvis.getModuleManager().getModule(moduleId);
        if (module == null) {
            this.send(exchange, NOT_FOUND_ERROR, 404);
            return;
        }
        this.handleRequest(exchange, module);
    }

    public abstract void handleRequest(HttpExchange exchange, IExternalModule module) throws IOException;

}
