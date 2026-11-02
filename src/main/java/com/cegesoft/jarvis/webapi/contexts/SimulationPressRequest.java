package com.cegesoft.jarvis.webapi.contexts;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.radio.RadioService;
import com.cegesoft.jarvis.radio.sim.SimulatedNode;
import com.cegesoft.jarvis.radio.sim.SimulatedRadio;
import com.cegesoft.jarvis.radio.sim.SimulatedSwitch;
import com.cegesoft.jarvis.webapi.APIContext;
import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * Development only (JARVIS_RADIO_DRIVER=simulated) : press a simulated button, /sim/press?id=3
 */
public class SimulationPressRequest extends APIContext {

    public SimulationPressRequest() {
        super("/sim/press");
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        SimulatedRadio radio = Jarvis.getServiceManager().getService(RadioService.class).getSimulatedRadio();
        if (radio == null) {
            send(exchange, NOT_FOUND_ERROR, 404);
            return;
        }
        SimulatedNode node;
        try {
            node = radio.node(Integer.parseInt(getParameter(exchange, "id")));
        } catch (NumberFormatException e) {
            node = null;
        }
        if (!(node instanceof SimulatedSwitch button)) {
            send(exchange, NOT_FOUND_ERROR, 404);
            return;
        }
        button.press();
        send(exchange, new JSONObject(), 200);
    }
}
