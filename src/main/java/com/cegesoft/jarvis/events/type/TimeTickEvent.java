package com.cegesoft.jarvis.events.type;

import com.cegesoft.jarvis.events.Event;
import com.cegesoft.jarvis.events.HandlerList;

import java.time.LocalDateTime;

/**
 * Called periodically, so that time based schedules are checked even when no module talks.
 */
public class TimeTickEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final LocalDateTime time;

    public TimeTickEvent(LocalDateTime time) {
        this.time = time;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public LocalDateTime getTime() {
        return time;
    }
}
