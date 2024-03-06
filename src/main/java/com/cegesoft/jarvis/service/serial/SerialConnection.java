package com.cegesoft.jarvis.service.serial;

import gnu.io.*;
import org.apache.commons.lang3.ArrayUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Created by HoxiSword on 03/05/2020 for JARVIS
 */
public class SerialConnection {

    private final String portName;
    private SerialPort port;
    private int currentBaud;
    private boolean connected;

    public SerialConnection(String portName) {
        this.portName = portName;
        this.connected = this.connect();
    }

    private CommPortIdentifier getPortIdentifier() {
        try {
            return CommPortIdentifier.getPortIdentifier(portName);
        } catch (NoSuchPortException ignored) {
        }
        return null;
    }

    public boolean connect() {
        if (isConnected())
            return true;
        CommPortIdentifier identifier = null;
        try {
            int counter = 0;
            while (identifier == null) {
                identifier = this.getPortIdentifier();
                Thread.sleep(500);
                counter++;
                if (counter > 3)
                    return false;
            }
            CommPort comm = identifier.open("JARVIS:" + portName, 5000);
            ((SerialPort) comm).setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            this.port = (SerialPort)comm;
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public void write(byte[] bytes) {
        if (!this.isConnected()) {
            getLogger().info("Port isn't connected...");
            return;
        }
        try {
            this.port.getOutputStream().write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public byte[] read() {
        if (!this.isConnected()) {
            // getLogger().info("Port isn't connected...");
            return new byte[0];
        }
        //this.configureSpeedRate(baud);
        byte[] buffer = new byte[0];
        try {
            DataInputStream in = new DataInputStream(this.port.getInputStream());
            List<Byte> list = new ArrayList<>();
            int b;
            while ((b = in.read()) != '#') {
                if (b > 0)
                    list.add((byte)b);
            }
            list.add((byte) '#');
            return ArrayUtils.toPrimitive(list.toArray(new Byte[0]));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    private void configureSpeedRate(int baud) {
        if (currentBaud == baud)
            return;
        try {
            this.port.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            this.currentBaud = baud;
        } catch (UnsupportedCommOperationException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (!connected)
            return;
        this.connected = false;
        port.close();
    }

    public String getPortName() {
        return portName;
    }
}
