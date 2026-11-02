package com.cegesoft.jarvis.radio.sim;

import com.cegesoft.jarvis.radio.protocol.DuplicateFilter;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;
import com.cegesoft.jarvis.radio.protocol.Opcode;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A node simulated as its firmware behaves : HELLO at boot and on DISCOVER, duplicate requests answered with the
 * cached reply without executing them twice, short listening window after each transmission for sleeping nodes.
 */
public abstract class SimulatedNode {

    static final long LISTENING_WINDOW_MS = 50;

    private final int id;
    private final int type;
    private final int capabilities;
    private final DuplicateFilter seen = new DuplicateFilter();
    private final AtomicInteger acknowledgedRequests = new AtomicInteger();
    private SimulatedRadio radio;
    private int seq;
    private volatile long listeningUntil;
    private volatile byte[] welcome;

    protected SimulatedNode(int id, int type, int capabilities) {
        this.id = id;
        this.type = type;
        this.capabilities = capabilities;
    }

    public int getId() {
        return id;
    }

    void attach(SimulatedRadio radio) {
        this.radio = radio;
    }

    protected boolean sleeps() {
        return (capabilities & Hello.CAP_SLEEPS) != 0;
    }

    boolean isListening() {
        return !sleeps() || System.currentTimeMillis() < listeningUntil;
    }

    /**
     * @return the requests of this node acknowledged by the hub
     */
    public int getAcknowledgedRequests() {
        return acknowledgedRequests.get();
    }

    /**
     * @return the payload of the last WELCOME received (parent, heartbeat, fallback node), or null
     */
    public byte[] getWelcome() {
        return welcome;
    }

    void boot() {
        sendRequest(Opcode.HELLO, new Hello(type, 1, capabilities, 0).encode());
    }

    protected synchronized void sendRequest(Opcode op, byte... payload) {
        seq = (seq + 1) & 0xFF;
        if (sleeps())
            listeningUntil = System.currentTimeMillis() + LISTENING_WINDOW_MS;
        radio.emit(Frame.request(Frame.HUB, id, seq, op, payload));
    }

    synchronized void deliver(Frame frame) {
        if (frame.dst() != id && frame.dst() != Frame.BROADCAST)
            return;
        if (frame.reply()) {
            if (frame.opcode() == Opcode.WELCOME)
                welcome = frame.payload();
            if (frame.opcode() != Opcode.NACK)
                acknowledgedRequests.incrementAndGet();
            return;
        }
        if (frame.opcode() == Opcode.DISCOVER) {
            boot();
            return;
        }
        long now = System.currentTimeMillis();
        if (frame.ackRequested() && seen.isDuplicate(frame.seq(), now)) {
            Frame cached = seen.replyOf(frame.seq(), now);
            if (cached != null)
                radio.emit(cached);
            return;
        }
        if (frame.ackRequested())
            seen.record(frame.seq(), now);
        Frame reply = handle(frame);
        if (frame.ackRequested()) {
            if (reply == null)
                reply = frame.replyWith(id, Opcode.ACK);
            seen.recordReply(frame.seq(), reply, now);
            radio.emit(reply);
        }
    }

    /**
     * @return the reply to a request, or null for a plain ACK
     */
    protected abstract Frame handle(Frame request);
}
