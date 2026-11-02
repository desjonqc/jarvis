package com.cegesoft.jarvis.events;

import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.events.type.EventCalledEvent;
import com.cegesoft.jarvis.utils.Tuple;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Created by HoxiSword on 06/04/2020 for BBCollabBot
 */
public class EventManager {

    private final Map<Class<?>, HandlerList> handlerLists = new ConcurrentHashMap<>();

    public void registerListener(Listener listener) {
        forEachHandler(listener, HandlerList::addListener);
    }

    public void unregisterListener(Listener listener) {
        forEachHandler(listener, HandlerList::removeListener);
    }

    @SuppressWarnings("unchecked")
    private void forEachHandler(Listener listener, BiConsumer<HandlerList, Tuple<Listener, Method>> action) {
        for (Method method : listener.getClass().getMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class))
                continue;
            if (method.getParameterCount() != 1)
                continue;
            if (!Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                continue;
            HandlerList list = this.getHandlers((Class<? extends Event>) method.getParameterTypes()[0]);
            if (list != null)
                action.accept(list, new Tuple<>(listener, method));
        }
    }

    public void callEvent(Event event) {
        HandlerList handlerList = this.getHandlers(event.getClass());
        if (handlerList == null)
            return;
        handlerList.callListeners(event);
        if (!(event instanceof EventCalledEvent))
            this.callEvent(new EventCalledEvent(event));
    }

    /**
     * The handler list of an event class is resolved once (reflection on its static getHandlerList method), then cached.
     */
    private HandlerList getHandlers(Class<? extends Event> eventClass) {
        return handlerLists.computeIfAbsent(eventClass, key -> {
            try {
                return (HandlerList) key.getDeclaredMethod("getHandlerList").invoke(null);
            } catch (ReflectiveOperationException | ClassCastException e) {
                Jarvis.getLogger().warn("Event " + key.getName() + " does not implement static method getHandlerList.", e);
                return null;
            }
        });
    }

}
