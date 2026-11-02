package com.cegesoft.jarvis.radio.sim;

import com.cegesoft.jarvis.radio.Radio;
import com.cegesoft.jarvis.radio.TxResult;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.FrameCodec;
import com.cegesoft.jarvis.radio.protocol.InvalidFrameException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A radio network without hardware : simulated nodes answer like the firmware does.
 * Used by the tests, and to run the hub on a computer (JARVIS_RADIO_DRIVER=simulated).
 */
public class SimulatedRadio implements Radio {

    private final Map<Integer, SimulatedNode> nodes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<byte[]> received = new ConcurrentLinkedQueue<>();
    private final Random random;
    private volatile double lossRate;
    private volatile Runnable listener = () -> {
    };

    public SimulatedRadio() {
        this(new Random(42));
    }

    public SimulatedRadio(Random random) {
        this.random = random;
    }

    public SimulatedRadio add(SimulatedNode node) {
        nodes.put(node.getId(), node);
        node.attach(this);
        return this;
    }

    public SimulatedNode node(int id) {
        return nodes.get(id);
    }

    /**
     * @param lossRate probability that a packet is lost (in both directions), 0..1
     */
    public void setLossRate(double lossRate) {
        this.lossRate = lossRate;
    }

    private boolean lost() {
        return lossRate > 0 && random.nextDouble() < lossRate;
    }

    /**
     * Make the hub receive a frame, as if a node had sent it (tests).
     */
    public void inject(Frame frame) {
        received.add(FrameCodec.encode(frame));
        listener.run();
    }

    /**
     * A node sends a frame to the hub.
     */
    void emit(Frame frame) {
        if (lost())
            return;
        received.add(FrameCodec.encode(frame));
        listener.run();
    }

    @Override
    public void start() {
        nodes.values().forEach(SimulatedNode::boot);
    }

    @Override
    public TxResult transmit(int node, byte[] packet, boolean noAck) {
        Frame frame;
        try {
            frame = FrameCodec.decode(packet);
        } catch (InvalidFrameException e) {
            throw new IllegalArgumentException(e);
        }
        if (node == Frame.BROADCAST) {
            nodes.values().stream().filter(SimulatedNode::isListening).forEach(n -> n.deliver(frame));
            return new TxResult(true, 0);
        }
        SimulatedNode target = nodes.get(node);
        if (target == null || !target.isListening() || lost())
            return new TxResult(false, 15);
        target.deliver(frame);
        return new TxResult(true, 0);
    }

    @Override
    public List<byte[]> receive() {
        List<byte[]> packets = new ArrayList<>();
        byte[] packet;
        while ((packet = received.poll()) != null)
            packets.add(packet);
        return packets;
    }

    @Override
    public void setReceiveListener(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public boolean signalsReception() {
        return true;
    }

    @Override
    public void stop() {
    }
}
