package com.cegesoft.jarvis.radio.link;

import com.cegesoft.jarvis.radio.RadioException;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Opcode;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Exchanges JARVIS-RF frames with the nodes. Every method is thread-safe.
 */
public interface FrameTransport {

    /**
     * @param receiver called, in order, on a single thread, for every frame received from a node
     */
    void setReceiver(Consumer<Frame> receiver);

    /**
     * Send a request and wait for the node's reply (retransmitted if needed).
     *
     * @param coalesce replace a same request still waiting in the queue of this node (the latest value wins)
     * @return the reply frame ; completed exceptionally with {@link NackException}, {@link java.util.concurrent.TimeoutException}
     * or {@link UnreachableException}
     */
    CompletableFuture<Frame> request(int node, Opcode op, byte[] payload, boolean coalesce);

    /**
     * Send a message without waiting for a reply (only the hardware acknowledgement).
     */
    CompletableFuture<Void> send(int node, Opcode op, byte[] payload);

    /**
     * Answer a request received from a node.
     */
    void reply(Frame request, Opcode op, byte[] payload);

    void broadcast(Opcode op, byte[] payload);

    void configureNode(int node, NodeProfile profile);

    /**
     * @return the link quality with this node, or null if nothing was exchanged yet
     */
    LinkQuality quality(int node);

    boolean isRunning();

    void start() throws RadioException;

    void stop();
}
