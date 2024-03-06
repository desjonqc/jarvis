package com.cegesoft.jarvis.service.serial;

import com.cegesoft.jarvis.events.type.SerialReceiveEvent;
import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.service.Service;
import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Clément on 13/05/2020 for JARVIS
 */
public class SerialService extends Service {

    private static final String[] PORTS = new String[] {(Jarvis.isWindows() ? "COM5" : "/dev/ttyS81")};

    private final HashMap<String, SerialConnection> connections = new HashMap<>();
    private final HashMap<String, Byte[]> writeQueue = new HashMap<>();

    private Timer listenThread;
    private boolean isListening;

    private void registerPorts() {
        for (String port : PORTS) {
            if (!connections.containsKey(port))
                connections.put(port, new SerialConnection(port));
        }
    }

    public void writeSerial(String port, Byte[] bytes) {
        if (!this.getState().equals(State.RUNNING)) {
            writeQueue.put(port, bytes);
        } else {
            SerialConnection connection = connections.get(port);
            connection.write(ArrayUtils.toPrimitive(bytes));
        }
    }


    @Override
    protected void startService() {
        registerPorts();
        for (Map.Entry<String, Byte[]> write : writeQueue.entrySet()) {
            SerialConnection connection = connections.get(write.getKey());
            connection.write(ArrayUtils.toPrimitive(write.getValue()));
        }
        writeQueue.clear();

        startListening();
    }

    @Override
    protected void stopService() {
        stopListening();

        for (SerialConnection connection : connections.values()) {
            connection.disconnect();
        }
    }

    void startListening() {
        if (isListening)
            return;
        this.isListening = true;
        this.listenThread = new Timer();
        this.listenThread.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!getState().equals(State.RUNNING)) {
                    return;
                }
                for (SerialConnection connection : connections.values()) {
                    if (!connection.isConnected())
                        return;
                    byte[] input = connection.read();
                    Jarvis.getEventManager().callEvent(new SerialReceiveEvent(input, connection.getPortName()));
                }
            }
        }, 0, 100);
    }

    void stopListening() {
        if (!isListening)
            return;
        this.isListening = false;
        this.listenThread.cancel();
    }
}
