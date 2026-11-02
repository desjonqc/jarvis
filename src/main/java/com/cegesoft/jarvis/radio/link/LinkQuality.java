package com.cegesoft.jarvis.radio.link;

/**
 * @param score          0 (no link) .. 10 (perfect), computed on the last transmissions
 * @param transmissions  transmissions in the measuring window
 * @param averageRetries average hardware retransmissions of the acknowledged ones
 * @param lastHeard      System.currentTimeMillis() of the last frame received from the node, 0 if never
 */
public record LinkQuality(int score, int transmissions, double averageRetries, long lastHeard) {
}
