package com.cegesoft.jarvis.radio.link;

import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.sim.SimulatedActivator;
import com.cegesoft.jarvis.radio.sim.SimulatedRadio;
import com.cegesoft.jarvis.radio.sim.SimulatedSwitch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class RadioLinkTest {

    private static final int RELAY = 2;
    private static final int SWITCH = 3;
    private static final int PULSE_RELAY_TYPE = 2;
    private static final int PULSE_SWITCH_TYPE = 4;

    private SimulatedRadio radio;
    private SimulatedActivator relay;
    private SimulatedSwitch button;
    private RadioLink link;
    private final List<Frame> received = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        radio = new SimulatedRadio(new Random(1));
        relay = new SimulatedActivator(RELAY, PULSE_RELAY_TYPE);
        button = new SimulatedSwitch(SWITCH, PULSE_SWITCH_TYPE);
        radio.add(relay).add(button);
        link = new RadioLink(radio, 5000);
        link.setReceiver(received::add);
        link.start();
        link.configureNode(SWITCH, new NodeProfile(true, 0));
    }

    @AfterEach
    void tearDown() {
        link.stop();
    }

    private long count(Opcode op, int src) {
        synchronized (received) {
            return received.stream().filter(f -> f.opcode() == op && f.src() == src && !f.reply()).count();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end)
            Thread.sleep(5);
    }

    @Test
    void requestGetsTheReply() throws Exception {
        Frame reply = link.request(RELAY, Opcode.PULSE, new byte[0], false).get(2, TimeUnit.SECONDS);
        assertEquals(Opcode.STATE, reply.opcode());
        assertEquals(1, relay.getPulses());
        assertEquals(10, link.quality(RELAY).score());
    }

    @Test
    void lossyLinkStillPulsesExactlyOncePerRequest() throws Exception {
        radio.setLossRate(0.3);
        int ok = 0;
        for (int i = 0; i < 30; i++) {
            try {
                link.request(RELAY, Opcode.PULSE, new byte[0], false).get(10, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException e) {
                // may fail after every retry : then the relay may or may not have pulsed, but never twice
            }
        }
        assertTrue(ok >= 25, "retries hide most losses : " + ok + "/30");
        assertTrue(relay.getPulses() >= ok && relay.getPulses() <= 30, "never executed twice : " + relay.getPulses());
        assertTrue(link.quality(RELAY).score() < 10);
    }

    @Test
    void unknownNodeFailsWithoutBlockingOthers() throws Exception {
        CompletableFuture<Frame> lost = link.request(42, Opcode.PULSE, new byte[0], false);
        Frame reply = link.request(RELAY, Opcode.PULSE, new byte[0], false).get(1, TimeUnit.SECONDS);
        assertNotNull(reply);
        ExecutionException error = assertThrows(ExecutionException.class, () -> lost.get(5, TimeUnit.SECONDS));
        assertInstanceOf(UnreachableException.class, error.getCause());
    }

    @Test
    void newerStateCommandReplacesTheQueuedOne() throws Exception {
        Thread.sleep(100); // the listening window after the boot HELLO is over
        // the switch sleeps : commands wait in its queue
        CompletableFuture<Frame> first = link.request(SWITCH, Opcode.SET_STATE, new byte[]{1}, true);
        CompletableFuture<Frame> second = link.request(SWITCH, Opcode.SET_STATE, new byte[]{0}, true);
        Thread.sleep(50);
        assertFalse(first.isDone());
        button.press(); // wakes up : the queue is sent during its listening window
        Frame reply = second.get(2, TimeUnit.SECONDS);
        assertSame(reply, first.get(1, TimeUnit.SECONDS), "the replaced command gets the result of the newer one");
        waitUntil(() -> button.getAcknowledgedRequests() == 1);
        assertEquals(1, button.getAcknowledgedRequests(), "the PULSE_EVT was acknowledged too");
    }

    @Test
    void messagesToASleepingNodeExpire() throws Exception {
        SimulatedRadio otherRadio = new SimulatedRadio();
        otherRadio.add(new SimulatedSwitch(SWITCH, PULSE_SWITCH_TYPE));
        RadioLink shortLink = new RadioLink(otherRadio, 300);
        try {
            shortLink.start();
            shortLink.configureNode(SWITCH, new NodeProfile(true, 0));
            Thread.sleep(100); // the listening window after the boot HELLO is over
            CompletableFuture<Frame> request = shortLink.request(SWITCH, Opcode.STATUS_REQ, new byte[0], false);
            ExecutionException error = assertThrows(ExecutionException.class, () -> request.get(3, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, error.getCause());
        } finally {
            shortLink.stop();
        }
    }

    @Test
    void nodeRequestsAreAcknowledgedAndDispatchedOnce() throws Exception {
        waitUntil(() -> count(Opcode.HELLO, SWITCH) == 1);
        button.press();
        waitUntil(() -> count(Opcode.PULSE_EVT, SWITCH) == 1);
        waitUntil(() -> button.getAcknowledgedRequests() >= 1);
        assertEquals(1, count(Opcode.PULSE_EVT, SWITCH));
        assertTrue(button.getAcknowledgedRequests() >= 1);
    }

    @Test
    void retransmittedNodeRequestIsAnsweredAgainButDispatchedOnce() throws Exception {
        Frame pulse = Frame.request(Frame.HUB, RELAY, 77, Opcode.PULSE_EVT, (byte) 1, (byte) 0);
        radio.inject(pulse);
        radio.inject(pulse);
        Thread.sleep(200);
        assertEquals(1, count(Opcode.PULSE_EVT, RELAY));
    }

    @Test
    void helloIsLeftToTheReceiverAndStartsTheNodeFresh() throws Exception {
        waitUntil(() -> count(Opcode.HELLO, RELAY) == 1);
        Frame hello = received.stream().filter(f -> f.opcode() == Opcode.HELLO && f.src() == RELAY).findFirst().orElseThrow();
        link.reply(hello, Opcode.WELCOME, new byte[]{0, 60, 0});
        waitUntil(() -> relay.getAcknowledgedRequests() == 1);
        assertEquals(1, relay.getAcknowledgedRequests(), "WELCOME received");
    }

    @Test
    void broadcastReachesListeningNodes() throws Exception {
        Thread.sleep(100); // the listening window after the boot HELLO is over
        received.clear();
        link.broadcast(Opcode.DISCOVER, new byte[0]);
        waitUntil(() -> count(Opcode.HELLO, RELAY) == 1);
        assertEquals(1, count(Opcode.HELLO, RELAY));
        assertEquals(0, count(Opcode.HELLO, SWITCH), "a sleeping node doesn't hear it");
    }
}
