package com.cegesoft.jarvis.radio.link;

import com.cegesoft.jarvis.radio.Radio;
import com.cegesoft.jarvis.radio.RadioException;
import com.cegesoft.jarvis.radio.TxResult;
import com.cegesoft.jarvis.radio.protocol.DuplicateFilter;
import com.cegesoft.jarvis.radio.protocol.Frame;
import com.cegesoft.jarvis.radio.protocol.FrameCodec;
import com.cegesoft.jarvis.radio.protocol.InvalidFrameException;
import com.cegesoft.jarvis.radio.protocol.Opcode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Owner of the radio : a single "RADIO" thread does every radio operation and owns every structure below
 * (queues, messages in flight, duplicate filters, statistics), so none of them needs a lock.
 * <p>
 * The other threads only touch two thread-safe objects : the command queue ({@link #commands}, closures run by the
 * radio thread) and the {@link CompletableFuture}s returned to them. Received frames are handed to a single
 * dispatch thread, which keeps their order.
 */
public class RadioLink implements FrameTransport {

    static final long[] REPLY_TIMEOUTS_MS = {200, 500, 1000, 1000};
    static final int MAX_ATTEMPTS = REPLY_TIMEOUTS_MS.length;
    static final int QUEUE_CAPACITY = 16;
    /**
     * A sleeping node listens 50 ms after each transmission : keep a margin.
     */
    static final long AWAKE_WINDOW_MS = 40;
    static final int STATS_WINDOW = 20;
    private static final long HOP_RETRY_DELAY_MS = 50;
    private static final long IRQ_SAFETY_POLL_MS = 100;
    private static final long NO_IRQ_POLL_MS = 5;
    private static final int MAX_TRANSMISSIONS_PER_ROUND = 32;
    /**
     * Requests answered by the upper layer ; every other request is acknowledged by the link itself.
     */
    private static final Set<Opcode> REPLIED_BY_RECEIVER = EnumSet.of(Opcode.HELLO);

    private final Radio radio;
    private final long defaultTimeoutMs;
    private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final Map<Integer, LinkQuality> qualities = new ConcurrentHashMap<>();
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RADIO-DISPATCH");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Consumer<Frame> receiver = frame -> {
    };
    private volatile boolean running;
    private Thread thread;

    // ---- radio thread only
    private final Map<Integer, NodeLink> nodes = new HashMap<>();
    private final List<Integer> roundRobin = new ArrayList<>();
    private int roundRobinStart;

    public RadioLink(Radio radio, long defaultTimeoutMs) {
        this.radio = radio;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    // ================================================================== public API (any thread)

    @Override
    public void setReceiver(Consumer<Frame> receiver) {
        this.receiver = receiver;
    }

    @Override
    public CompletableFuture<Frame> request(int node, Opcode op, byte[] payload, boolean coalesce) {
        return submit(Outbound.message(node, op, payload, true, coalesce));
    }

    @Override
    public CompletableFuture<Void> send(int node, Opcode op, byte[] payload) {
        return submit(Outbound.message(node, op, payload, false, false)).thenApply(frame -> null);
    }

    @Override
    public void reply(Frame request, Opcode op, byte[] payload) {
        submit(Outbound.reply(request.replyWith(Frame.HUB, op, payload)));
    }

    @Override
    public void broadcast(Opcode op, byte[] payload) {
        submit(Outbound.message(Frame.BROADCAST, op, payload, false, false));
    }

    @Override
    public void configureNode(int node, NodeProfile profile) {
        commands.add(() -> node(node).profile = profile);
    }

    @Override
    public LinkQuality quality(int node) {
        return qualities.get(node);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private CompletableFuture<Frame> submit(Outbound message) {
        if (!running) {
            message.future.completeExceptionally(new UnreachableException("Radio not running"));
            return message.future;
        }
        message.deadline = now() + defaultTimeoutMs;
        commands.add(() -> enqueue(message));
        return message.future;
    }

    @Override
    public synchronized void start() throws RadioException {
        if (running)
            return;
        radio.setReceiveListener(() -> commands.offer(() -> {
        }));
        radio.start();
        running = true;
        thread = Thread.ofPlatform().name("RADIO").daemon().start(this::run);
    }

    @Override
    public synchronized void stop() {
        if (!running)
            return;
        running = false;
        commands.offer(() -> {
        });
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (NodeLink node : nodes.values())
            node.failAll(new UnreachableException("Radio stopped"));
        radio.stop();
        dispatcher.shutdown();
    }

    // ================================================================== radio thread

    private static long now() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void run() {
        long pollInterval = radio.signalsReception() ? IRQ_SAFETY_POLL_MS : NO_IRQ_POLL_MS;
        while (running) {
            try {
                long now = now();
                long wait = Math.max(0, Math.min(nextDeadline(now) - now, pollInterval));
                Runnable command = commands.poll(wait, TimeUnit.MILLISECONDS);
                while (command != null) {
                    command.run();
                    command = commands.poll();
                }
                if (!running)
                    break;
                receiveAll();
                handleTimeouts(now());
                transmitReady();
            } catch (InterruptedException e) {
                break;
            } catch (RadioException | RuntimeException e) {
                getLogger().error("Radio error : ", e);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    break;
                }
            }
        }
    }

    private NodeLink node(int id) {
        return nodes.computeIfAbsent(id, key -> {
            roundRobin.add(key);
            return new NodeLink(key);
        });
    }

    private void enqueue(Outbound message) {
        NodeLink node = node(message.node);
        if (message.isReply()) {
            node.addReply(message, now());
            return;
        }
        if (message.coalesce) {
            for (Iterator<Outbound> it = node.queue.iterator(); it.hasNext(); ) {
                Outbound queued = it.next();
                if (queued.op == message.op && queued.seq < 0) {
                    it.remove();
                    message.future.whenComplete((frame, error) -> {
                        if (error != null) queued.future.completeExceptionally(error);
                        else queued.future.complete(frame);
                    });
                }
            }
        }
        if (node.queue.size() >= QUEUE_CAPACITY) {
            message.future.completeExceptionally(new UnreachableException("Queue of node " + message.node + " is full"));
            return;
        }
        node.queue.add(message);
    }

    private long nextDeadline(long now) {
        long next = now + 1000;
        for (NodeLink node : nodes.values()) {
            if (node.inFlight != null)
                next = Math.min(next, node.inFlight.replyDeadline);
            Outbound head = node.queue.peek();
            if (head != null) {
                next = Math.min(next, head.deadline);
                if (node.canTransmit(now))
                    next = Math.min(next, Math.max(now, head.nextAttemptAt));
            }
            if (!node.replies.isEmpty()) {
                next = Math.min(next, node.replies.peek().deadline);
                if (node.canTransmit(now))
                    next = now;
            }
        }
        return next;
    }

    // ---------------------------------------------------------------- reception

    private void receiveAll() throws RadioException {
        for (byte[] packet : radio.receive()) {
            try {
                handleIncoming(FrameCodec.decode(packet));
            } catch (InvalidFrameException e) {
                getLogger().debug("Invalid radio frame ignored : " + e.getMessage());
            }
        }
    }

    private void handleIncoming(Frame frame) {
        if (frame.dst() != Frame.HUB)
            return;
        long now = now();
        NodeLink node = node(frame.src());
        node.lastHeard = System.currentTimeMillis();
        if (node.profile.sleeping())
            node.awakeUntil = now + AWAKE_WINDOW_MS;
        publishQuality(node);

        if (frame.reply()) {
            Outbound request = node.takeRequest(frame.seq());
            if (request != null) {
                if (frame.opcode() == Opcode.NACK)
                    request.future.completeExceptionally(new NackException(frame.payloadLength() > 0 ? frame.reader().u8() : 0));
                else
                    request.future.complete(frame);
            }
            dispatch(frame);
            return;
        }

        if (frame.opcode() == Opcode.HELLO)
            node.incoming.clear(); // the node (re)started : its seq counter restarted too
        if (frame.ackRequested()) {
            if (node.incoming.isDuplicate(frame.seq(), now)) {
                // Our reply was lost : send it again, without handing the request over a second time
                Frame reply = node.incoming.replyOf(frame.seq(), now);
                if (reply != null)
                    node.addReply(Outbound.reply(reply), now);
                return;
            }
            node.incoming.record(frame.seq(), now);
            Opcode opcode = frame.opcode();
            if (opcode == null) {
                node.addReply(Outbound.reply(frame.replyWith(Frame.HUB, Opcode.NACK, (byte) NackException.UNKNOWN_OPERATION)), now);
                return;
            }
            if (!REPLIED_BY_RECEIVER.contains(opcode))
                node.addReply(Outbound.reply(frame.replyWith(Frame.HUB, Opcode.ACK)), now);
        }
        dispatch(frame);
    }

    private void dispatch(Frame frame) {
        dispatcher.execute(() -> {
            try {
                receiver.accept(frame);
            } catch (RuntimeException e) {
                getLogger().error("Can't handle " + frame + " : ", e);
            }
        });
    }

    // ---------------------------------------------------------------- timeouts

    private void handleTimeouts(long now) {
        for (NodeLink node : nodes.values()) {
            Outbound inFlight = node.inFlight;
            if (inFlight != null && now >= inFlight.replyDeadline) {
                node.inFlight = null;
                if (inFlight.attempts >= MAX_ATTEMPTS || now >= inFlight.deadline) {
                    inFlight.future.completeExceptionally(new TimeoutException("No reply from node " + node.id + " to " + inFlight.op));
                } else {
                    inFlight.nextAttemptAt = now; // retransmitted with the same seq : the node answers without executing twice
                    node.queue.addFirst(inFlight);
                }
            }
            expire(node.queue, node, now);
            expire(node.replies, node, now);
        }
    }

    private static void expire(Deque<Outbound> messages, NodeLink node, long now) {
        for (Iterator<Outbound> it = messages.iterator(); it.hasNext(); ) {
            Outbound message = it.next();
            if (now >= message.deadline) {
                it.remove();
                message.future.completeExceptionally(new TimeoutException("Message " + message.op + " to node " + node.id + " expired"));
            }
        }
    }

    // ---------------------------------------------------------------- transmission

    private void transmitReady() throws RadioException {
        int transmissions = 0;
        boolean progress = true;
        while (progress && transmissions < MAX_TRANSMISSIONS_PER_ROUND) {
            progress = false;
            int count = roundRobin.size();
            for (int i = 0; i < count && transmissions < MAX_TRANSMISSIONS_PER_ROUND; i++) {
                NodeLink node = nodes.get(roundRobin.get((roundRobinStart + i) % count));
                long now = now();
                if (!node.canTransmit(now))
                    continue;
                Outbound message = node.next(now);
                if (message == null)
                    continue;
                transmit(node, message, now);
                transmissions++;
                progress = true;
                receiveAll(); // a reply may already be there
            }
            roundRobinStart = count == 0 ? 0 : (roundRobinStart + 1) % count;
        }
    }

    private void transmit(NodeLink node, Outbound message, long now) throws RadioException {
        if (message.seq < 0)
            message.seq = node.nextSeq();
        Frame frame = message.toFrame();
        boolean broadcast = node.id == Frame.BROADCAST;
        int nextHop = broadcast || node.profile.parent() == 0 ? node.id : node.profile.parent();
        TxResult result = radio.transmit(nextHop, FrameCodec.encode(frame), broadcast);
        message.attempts++;
        if (!broadcast)
            node.record(result);
        publishQuality(node);

        if (!result.acknowledged()) {
            // A lost reply is not retried here : the node retransmits its request, and gets the cached reply
            if (message.isReply() || message.attempts >= MAX_ATTEMPTS) {
                node.remove(message);
                message.future.completeExceptionally(new UnreachableException("Node " + node.id + " doesn't acknowledge " + message.op));
            } else {
                message.nextAttemptAt = now + HOP_RETRY_DELAY_MS * message.attempts;
            }
            return;
        }
        node.remove(message);
        if (message.expectsReply) {
            message.replyDeadline = now() + REPLY_TIMEOUTS_MS[Math.min(message.attempts, MAX_ATTEMPTS) - 1];
            node.inFlight = message;
        } else {
            message.future.complete(null);
        }
    }

    private void publishQuality(NodeLink node) {
        if (node.id != Frame.BROADCAST)
            qualities.put(node.id, node.quality());
    }

    // ================================================================== radio thread structures

    private static final class Outbound {
        final int node;
        final Opcode op;
        final byte[] payload;
        final boolean expectsReply;
        final boolean coalesce;
        /**
         * The reply frame, built as soon as the request is received (its seq is the request's one).
         */
        final Frame reply;
        final CompletableFuture<Frame> future = new CompletableFuture<>();
        long deadline = Long.MAX_VALUE;
        long nextAttemptAt;
        long replyDeadline;
        int attempts;
        int seq = -1;

        private Outbound(int node, Opcode op, byte[] payload, boolean expectsReply, boolean coalesce, Frame reply) {
            this.node = node;
            this.op = op;
            this.payload = payload == null ? new byte[0] : payload;
            this.expectsReply = expectsReply;
            this.coalesce = coalesce;
            this.reply = reply;
            if (reply != null)
                this.seq = reply.seq();
        }

        static Outbound message(int node, Opcode op, byte[] payload, boolean expectsReply, boolean coalesce) {
            return new Outbound(node, op, payload, expectsReply, coalesce, null);
        }

        static Outbound reply(Frame reply) {
            return new Outbound(reply.dst(), reply.opcode(), null, false, false, reply);
        }

        boolean isReply() {
            return reply != null;
        }

        Frame toFrame() {
            if (reply != null)
                return reply;
            return new Frame(node, Frame.HUB, expectsReply, false, Frame.DEFAULT_TTL, seq, op.code(), payload);
        }
    }

    private static final class NodeLink {
        final int id;
        final Deque<Outbound> queue = new ArrayDeque<>();
        final Deque<Outbound> replies = new ArrayDeque<>();
        final DuplicateFilter incoming = new DuplicateFilter();
        final boolean[] acknowledged = new boolean[STATS_WINDOW];
        final int[] retries = new int[STATS_WINDOW];
        NodeProfile profile = NodeProfile.DIRECT;
        Outbound inFlight;
        long awakeUntil;
        long lastHeard;
        int statsCount;
        int statsIndex;
        int seq;

        NodeLink(int id) {
            this.id = id;
            // Random start : after a hub restart, the node must not take the first requests for duplicates
            this.seq = ThreadLocalRandom.current().nextInt(256);
        }

        int nextSeq() {
            seq = (seq + 1) & 0xFF;
            return seq;
        }

        boolean canTransmit(long now) {
            return !profile.sleeping() || now < awakeUntil;
        }

        /**
         * Replies first (the node is waiting for them), then one request at a time, in order.
         */
        Outbound next(long now) {
            if (!replies.isEmpty())
                return replies.peek();
            Outbound head = queue.peek();
            if (head == null || head.nextAttemptAt > now)
                return null;
            if (head.expectsReply && inFlight != null)
                return null;
            return head;
        }

        void remove(Outbound message) {
            if (!replies.remove(message))
                queue.remove(message);
        }

        /**
         * The reply is remembered right away : if it gets lost, the retransmitted request will get it again.
         */
        void addReply(Outbound reply, long now) {
            if (reply.deadline == Long.MAX_VALUE)
                reply.deadline = now + REPLY_TIMEOUTS_MS[MAX_ATTEMPTS - 1];
            incoming.recordReply(reply.seq, reply.reply, now);
            replies.add(reply);
        }

        /**
         * @return the request answered by a reply with this seq (in flight, or waiting for a retransmission)
         */
        Outbound takeRequest(int replySeq) {
            if (inFlight != null && inFlight.seq == replySeq) {
                Outbound request = inFlight;
                inFlight = null;
                return request;
            }
            Outbound head = queue.peek();
            if (head != null && head.expectsReply && head.seq == replySeq) {
                queue.poll();
                return head;
            }
            return null;
        }

        void record(TxResult result) {
            acknowledged[statsIndex] = result.acknowledged();
            retries[statsIndex] = result.retries();
            statsIndex = (statsIndex + 1) % STATS_WINDOW;
            statsCount = Math.min(statsCount + 1, STATS_WINDOW);
        }

        LinkQuality quality() {
            if (statsCount == 0)
                return new LinkQuality(10, 0, 0, lastHeard);
            int success = 0;
            int totalRetries = 0;
            for (int i = 0; i < statsCount; i++) {
                if (acknowledged[i]) {
                    success++;
                    totalRetries += retries[i];
                }
            }
            double averageRetries = success == 0 ? 15 : totalRetries / (double) success;
            // 10 * success ratio, minus up to 3 points when the packets need many retransmissions
            double score = 10.0 * success / statsCount - 3.0 * averageRetries / 15.0;
            return new LinkQuality((int) Math.round(Math.max(0, Math.min(10, score))), statsCount, averageRetries, lastHeard);
        }

        void failAll(Throwable error) {
            if (inFlight != null)
                inFlight.future.completeExceptionally(error);
            queue.forEach(message -> message.future.completeExceptionally(error));
            replies.forEach(message -> message.future.completeExceptionally(error));
        }
    }
}
