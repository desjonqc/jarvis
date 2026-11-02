package com.cegesoft.jarvis.radio.link;

import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.Opcode;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Transport used while no radio is available : every command fails cleanly.
 */
public class OfflineTransport implements FrameTransport {

    private static <T> CompletableFuture<T> offline() {
        return CompletableFuture.failedFuture(new UnreachableException("No radio available"));
    }

    @Override
    public void setReceiver(Consumer<Frame> receiver) {
    }

    @Override
    public CompletableFuture<Frame> request(int node, Opcode op, byte[] payload, boolean coalesce) {
        return offline();
    }

    @Override
    public CompletableFuture<Void> send(int node, Opcode op, byte[] payload) {
        return offline();
    }

    @Override
    public void reply(Frame request, Opcode op, byte[] payload) {
    }

    @Override
    public void broadcast(Opcode op, byte[] payload) {
    }

    @Override
    public void configureNode(int node, NodeProfile profile) {
    }

    @Override
    public LinkQuality quality(int node) {
        return null;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
