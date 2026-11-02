package com.cegesoft.jarvis.radio.sim;

import com.cegesoft.jarvis.radio.link.NackException;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Hello;
import com.cegesoft.jarvis.radio.protocol.Opcode;
import com.cegesoft.jarvis.radio.protocol.Payloads;
import com.cegesoft.jarvis.radio.protocol.Status;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mains powered relay module (pulse or static relay).
 */
public class SimulatedActivator extends SimulatedNode {

    private final AtomicInteger pulses = new AtomicInteger();
    private volatile int state;
    private volatile boolean enabled = true;

    public SimulatedActivator(int id, int type) {
        super(id, type, Hello.CAP_MAINS_POWERED);
    }

    /**
     * @return how many times the relay was pulsed (each pulse must happen exactly once per request)
     */
    public int getPulses() {
        return pulses.get();
    }

    public int getState() {
        return state;
    }

    @Override
    protected Frame handle(Frame request) {
        Opcode opcode = request.opcode();
        if (opcode == null)
            return request.replyWith(getId(), Opcode.NACK, (byte) NackException.UNKNOWN_OPERATION);
        return switch (opcode) {
            case PULSE -> {
                pulses.incrementAndGet();
                state = state == 1 ? 0 : 1;
                yield request.replyWith(getId(), Opcode.STATE, Payloads.i8(state));
            }
            case SET_STATE -> {
                state = request.reader().i8();
                yield request.replyWith(getId(), Opcode.STATE, Payloads.i8(state));
            }
            case SET_ENABLED -> {
                enabled = request.reader().u8() != 0;
                yield request.replyWith(getId(), Opcode.STATUS, new Status(enabled, false, 0, null).encode());
            }
            case STATUS_REQ -> request.replyWith(getId(), Opcode.STATUS, new Status(enabled, false, 0, null).encode());
            case PING -> request.replyWith(getId(), Opcode.PONG, request.payload());
            default -> request.replyWith(getId(), Opcode.NACK, (byte) NackException.UNKNOWN_OPERATION);
        };
    }
}
