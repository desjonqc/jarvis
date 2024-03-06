package com.cegesoft.jarvis.webapi.contexts.schedules;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;

public class ScheduleEditRequest extends APIContext {
    public ScheduleEditRequest() {
        super("/schedules/edit");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        try {
            JSONArray object = (JSONArray) new JSONParser().parse(this.readBody(exchange));
            Jarvis.getModuleManager().getScheduleManager().updateSchedules(object, true);
            this.send(exchange, new JSONObject(), 200);
            return;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        this.send(exchange, BAD_REQUEST_ERROR, 400);
    }
}
