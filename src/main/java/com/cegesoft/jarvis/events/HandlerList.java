package com.cegesoft.jarvis.events;

import com.cegesoft.jarvis.utils.Tuple;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Created by HoxiSword on 06/04/2020 for BBCollabBot
 */
public class HandlerList {

    private final CopyOnWriteArrayList<Tuple<Listener, Method>> listeners = new CopyOnWriteArrayList<>();

    public HandlerList() {

    }

    void addListener(Tuple<Listener, Method> method) {
        method.getB().trySetAccessible();
        listeners.addIfAbsent(method);
    }

    void removeListener(Tuple<Listener, Method> method) {
        listeners.remove(method);
    }

    void callListeners(Object event) {
        for (Tuple<Listener, Method> method : listeners) {
            try {
                method.getB().invoke(method.getA(), event);
            } catch (InvocationTargetException e) {
                getLogger().error("Listener " + method.getA().getClass().getSimpleName() + "#" + method.getB().getName()
                        + " failed on " + event.getClass().getSimpleName() + " : ", e.getCause());
            } catch (Exception e) {
                getLogger().error("Can't call event : ", e);
            }
        }
    }

}
