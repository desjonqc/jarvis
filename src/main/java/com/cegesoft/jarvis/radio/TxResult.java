package com.cegesoft.jarvis.radio;

/**
 * @param acknowledged the destination acknowledged the packet (always true for a packet sent without ack)
 * @param retries      hardware retransmissions needed (0..15)
 */
public record TxResult(boolean acknowledged, int retries) {
}
