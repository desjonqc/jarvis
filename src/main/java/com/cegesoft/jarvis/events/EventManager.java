package com.cegesoft.jarvis.events;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.utils.Tuple;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Created by HoxiSword on 06/04/2020 for BBCollabBot
 */
public class EventManager {



    public void registerListener(Listener listener) {
        Class<? extends Listener> listenerClass = listener.getClass();
        for (Method method : listenerClass.getMethods()) {
            EventHandler[] annotations = method.getAnnotationsByType(EventHandler.class);
            if (annotations.length == 0)
                continue;
            if (method.getParameterCount() != 1)
                continue;
            if (!Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                continue;
            Class<? extends Event> eventClass = (Class<? extends Event>)method.getParameterTypes()[0];
            try {
                Method handlerListGetter = eventClass.getMethod("getHandlerList");
                HandlerList list = (HandlerList)handlerListGetter.invoke(null);
                list.addListener(new Tuple<>(listener, method));
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }
    }

    public void unregisterListener(Listener listener) {
        Class<? extends Listener> listenerClass = listener.getClass();
        for (Method method : listenerClass.getMethods()) {
            EventHandler[] annotations = method.getAnnotationsByType(EventHandler.class);
            if (annotations.length == 0)
                continue;
            if (method.getParameterCount() != 1)
                continue;
            if (!Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                continue;
            Class<? extends Event> eventClass = (Class<? extends Event>)method.getParameterTypes()[0];
            try {
                Method handlerListGetter = eventClass.getMethod("getHandlerList");
                HandlerList list = (HandlerList)handlerListGetter.invoke(null);
                list.removeListener(new Tuple<>(listener, method));
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }
    }

    public void callEvent(Event event) {
        try {
            HandlerList handlerList = this.getHandlers(event.getClass());
            handlerList.callListeners(event);
            if (!(event instanceof EventCalledEvent))
                this.callEvent(new EventCalledEvent(event));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Jarvis.getLogger().warn("Event " + event.getClass().getName() + " does not implement static method getHandlerList.", e);
        }
    }

    private HandlerList getHandlers(Class<? extends Event> eventClass) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (HandlerList)eventClass.getDeclaredMethod("getHandlerList").invoke(null);
    }

    public <T extends Event> void waitEventUntil(Class<T> eventClass, Predicate<T> predicate, int timeout) {
        try {
            HandlerList handlerList = getHandlers(eventClass);
            Object synchronizer = new Object();
            handlerList.synchronizers.put(synchronizer, obj -> predicate.test((T) obj));
            synchronized (synchronizer) {
                synchronizer.wait(timeout);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InterruptedException ignored) {}
    }

    public <T extends Event, R extends Event> boolean waitEventUntil(Class<T> eventClass, Class<R> errorEvent, Predicate<T> predicate, Predicate<R> errorPredicate) {
        try {
            HandlerList handlerList = getHandlers(eventClass);
            HandlerList errorHandlerList = getHandlers(errorEvent);
            Object synchronizer = new Object();
            handlerList.synchronizers.put(synchronizer, obj -> predicate.test((T) obj));
            errorHandlerList.synchronizers.put(synchronizer, obj -> errorPredicate.test((R) obj));
            synchronized (synchronizer) {
                synchronizer.wait();
                if (handlerList.synchronizers.containsKey(synchronizer)) {
                    handlerList.synchronizers.remove(synchronizer);
                    return false;
                }
                errorHandlerList.synchronizers.remove(synchronizer);
                return true;
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InterruptedException ignored) {}
        return false;
    }

}
