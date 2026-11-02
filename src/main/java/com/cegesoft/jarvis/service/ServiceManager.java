package com.cegesoft.jarvis.service;

import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.radio.RadioService;
import com.cegesoft.jarvis.webapi.WebAPIService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by HoxiSword on 13/05/2020 for JARVIS
 */
public class ServiceManager {

    // Insertion order is the start order, services are stopped in reverse order.
    private final Map<Class<? extends Service>, Service> services = new LinkedHashMap<>();
    private volatile boolean loaded = false;

    public ServiceManager(JarvisConfig config) {
        services.put(RadioService.class, new RadioService(config));
        services.put(WebAPIService.class, new WebAPIService(config));
    }

    public void loadServices() {
        for (Service service : services.values())
            service.start();
        this.loaded = true;
    }

    public void unloadServices() {
        List<Service> reversed = new ArrayList<>(services.values());
        Collections.reverse(reversed);
        for (Service service : reversed)
            service.stop();
        this.loaded = false;
    }

    public void loadService(Class<? extends Service> service) {
        services.get(service).start();
    }

    public void unloadService(Class<? extends Service> service) {
        services.get(service).stop();
    }

    @SuppressWarnings("unchecked")
    public <T extends Service> T getService(Class<T> service) {
        return (T) services.get(service);
    }

    public boolean isLoaded() {
        return loaded;
    }

}
