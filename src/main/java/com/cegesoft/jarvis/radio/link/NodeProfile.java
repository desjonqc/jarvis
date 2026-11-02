package com.cegesoft.jarvis.radio.link;

/**
 * @param sleeping the node only listens during a short window after each of its transmissions
 * @param parent   the relay to go through, or 0 for a direct link
 */
public record NodeProfile(boolean sleeping, int parent) {

    public static final NodeProfile DIRECT = new NodeProfile(false, 0);
}
