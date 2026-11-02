package com.cegesoft.jarvis.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventManagerTest {

    public static class AnswerEvent extends Event {
        private static final HandlerList handlers = new HandlerList();
        final String id;

        AnswerEvent(String id) {
            this.id = id;
        }

        public static HandlerList getHandlerList() {
            return handlers;
        }
    }

    public static class Recorder implements Listener {
        final List<String> received = new ArrayList<>();

        @EventHandler
        public void onAnswer(AnswerEvent event) {
            received.add(event.id);
        }
    }

    private final EventManager manager = new EventManager();

    @Test
    void listenersAreRegisteredOnceAndCanBeRemoved() {
        Recorder recorder = new Recorder();
        manager.registerListener(recorder);
        manager.registerListener(recorder);
        manager.callEvent(new AnswerEvent("x"));
        manager.unregisterListener(recorder);
        manager.callEvent(new AnswerEvent("y"));
        assertEquals(List.of("x"), recorder.received);
    }
}
