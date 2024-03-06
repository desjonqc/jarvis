package com.cegesoft.jarvis.events;

import com.cegesoft.jarvis.utils.Tuple;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Created by HoxiSword on 06/04/2020 for BBCollabBot
 */
public class HandlerList {

    private final List<Tuple<Listener, Method>> listeners = new ArrayList<>();
    public final ConcurrentHashMap<Object, Predicate<Object>> synchronizers = new ConcurrentHashMap<>();

    public HandlerList() {

    }

    void addListener(Tuple<Listener, Method> method) {
        listeners.add(method);
    }

    void removeListener(Tuple<Listener, Method> method) {
        listeners.remove(method);
    }

    void callListeners(Object event) {
        for (Tuple<Listener, Method> method : new ArrayList<>(listeners)) {
            try {
                method.getB().invoke(method.getA(), event);
            } catch (Exception e) {
                getLogger().error("Can't call event : ", e);
            }
        }
        for (Map.Entry<Object, Predicate<Object>> entry : synchronizers.entrySet()) {
            if (entry.getValue().test(event)) {
                synchronizers.remove(entry.getKey());
                synchronized (entry.getKey()) {
                    entry.getKey().notifyAll();
                }
            }
        }
    }

}
