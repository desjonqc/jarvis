package com.cegesoft.jarvis.radio.protocol;

/**
 * Remembers the last requests received from one sender, with the reply that was sent, so that a retransmitted
 * request is answered again without being executed twice. Entries expire, so that a sender restarting its seq
 * counter is not mistaken for duplicates. Not thread-safe : owned by the radio thread.
 */
public class DuplicateFilter {

    public static final int WINDOW = 8;
    public static final long EXPIRY_MS = 30_000;

    private final int[] seqs = new int[WINDOW];
    private final long[] times = new long[WINDOW];
    private final Frame[] replies = new Frame[WINDOW];
    private int next;

    public DuplicateFilter() {
        clear();
    }

    /**
     * @return the index of the request in the window, or -1 if it is new
     */
    public int find(int seq, long now) {
        for (int i = 0; i < WINDOW; i++) {
            if (seqs[i] == seq && now - times[i] <= EXPIRY_MS)
                return i;
        }
        return -1;
    }

    public boolean isDuplicate(int seq, long now) {
        return find(seq, now) >= 0;
    }

    /**
     * @return the reply sent to a duplicate request (may be null if none was sent)
     */
    public Frame replyOf(int seq, long now) {
        int index = find(seq, now);
        return index < 0 ? null : replies[index];
    }

    public void record(int seq, long now) {
        seqs[next] = seq;
        times[next] = now;
        replies[next] = null;
        next = (next + 1) % WINDOW;
    }

    public void recordReply(int seq, Frame reply, long now) {
        int index = find(seq, now);
        if (index >= 0)
            replies[index] = reply;
    }

    public void clear() {
        java.util.Arrays.fill(seqs, -1);
        java.util.Arrays.fill(replies, null);
        next = 0;
    }
}
