package com.cegesoft.jarvis.service;

import com.cegesoft.jarvis.service.serial.SerialService;
import com.cegesoft.jarvis.webapi.WebAPIService;

import java.util.HashMap;

/**
 * Created by HoxiSword on 13/05/2020 for JARVIS
 */
public class ServiceManager {

    private final HashMap<Class<? extends Service>, Service> services = new HashMap<>();
    private boolean loaded = false;
    private final Object lock = new Object();

    public ServiceManager() {
        registerServices();
        new Thread(this::loadServices, "SERVICE-LOADER").start();
    }

    private void registerServices() {
        services.put(SerialService.class, new SerialService());
        services.put(WebAPIService.class, new WebAPIService());
    }

    public void loadServices() {
        synchronized (lock) {
            for (Class<? extends Service> key : services.keySet())
                loadService(key);
            this.loaded = true;
            lock.notifyAll();
        }
    }

    public void unloadServices() {
        for (Class<? extends Service> key : services.keySet())
            unloadService(key);
    }

    public void waitForServiceLoadFinish() {
        synchronized (lock) {
            try {
                while (!loaded) {
                    lock.wait(100);
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void loadService(Class<? extends Service> service) {
        Service s = services.get(service);
        s.start();
    }

    public void unloadService(Class<? extends Service> service) {
        services.get(service).stop();
    }

    public <T extends Service> T getService(Class<T> service) {
        return (T)services.get(service);
    }

    public boolean isLoaded() {
        return loaded;
    }

}
