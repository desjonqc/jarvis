package com.cegesoft.jarvis.webapi.contexts.schedules;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.module.schedule.ModuleSchedule;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONArray;

import java.io.IOException;

public class ScheduleGetRequest extends APIContext {
    public ScheduleGetRequest() {
        super("/schedules/list");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        JSONArray array = new JSONArray();
        for (ModuleSchedule schedule : Jarvis.getModuleManager().getScheduleManager().getSchedules()) {
            array.add(schedule.getJson());
        }
        this.send(exchange, array, 200);
    }
}
