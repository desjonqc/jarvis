package com.cegesoft.jarvis.events.type;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.HandlerList;

/**
 * Created by HoxiSword on 26/05/2020 for JARVIS
 */
public class EventCalledEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Event event;

    public EventCalledEvent(Event event) {
        this.event = event;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Event getEventCalled() {
        return event;
    }
}
