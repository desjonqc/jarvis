package com.cegesoft.jarvis.radio.nrf24;

/**
 * nRF24L01+ registers, bits and SPI commands (Nordic product specification v1.0, chapter 9).
 */
final class Nrf24Registers {

    private Nrf24Registers() {
    }

    // Registers
    static final int CONFIG = 0x00;
    static final int EN_AA = 0x01;
    static final int EN_RXADDR = 0x02;
    static final int SETUP_AW = 0x03;
    static final int SETUP_RETR = 0x04;
    static final int RF_CH = 0x05;
    static final int RF_SETUP = 0x06;
    static final int STATUS = 0x07;
    static final int OBSERVE_TX = 0x08;
    static final int RPD = 0x09;
    static final int RX_ADDR_P0 = 0x0A;
    static final int RX_ADDR_P1 = 0x0B;
    static final int TX_ADDR = 0x10;
    static final int FIFO_STATUS = 0x17;
    static final int DYNPD = 0x1C;
    static final int FEATURE = 0x1D;

    // CONFIG
    static final int EN_CRC = 1 << 3;
    static final int CRCO = 1 << 2;
    static final int PWR_UP = 1 << 1;
    static final int PRIM_RX = 1;

    // STATUS
    static final int RX_DR = 1 << 6;
    static final int TX_DS = 1 << 5;
    static final int MAX_RT = 1 << 4;

    // FIFO_STATUS
    static final int RX_EMPTY = 1;

    // RF_SETUP
    static final int RF_DR_LOW = 1 << 5;
    static final int RF_DR_HIGH = 1 << 3;

    // FEATURE
    static final int EN_DPL = 1 << 2;
    static final int EN_DYN_ACK = 1;

    // EN_RXADDR / EN_AA
    static final int PIPE_0 = 1;
    static final int PIPE_1 = 1 << 1;
    static final int ALL_PIPES = 0x3F;

    // Commands
    static final int R_REGISTER = 0x00;
    static final int W_REGISTER = 0x20;
    static final int R_RX_PL_WID = 0x60;
    static final int R_RX_PAYLOAD = 0x61;
    static final int W_TX_PAYLOAD = 0xA0;
    static final int W_TX_PAYLOAD_NOACK = 0xB0;
    static final int FLUSH_TX = 0xE1;
    static final int FLUSH_RX = 0xE2;
    static final int ACTIVATE = 0x50;
    static final int ACTIVATE_KEY = 0x73;
    static final int NOP = 0xFF;

    static final int ADDRESS_WIDTH = 5;
    static final int MAX_PAYLOAD = 32;
}
