package com.cegesoft.jarvis.radio;

import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.radio.nrf24.Nrf24Driver;
import com.cegesoft.jarvis.radio.protocol.Frame;

import java.io.PrintStream;

/**
 * Command line tool : listen on every channel to choose a free one (the hub must be stopped, it uses the radio).
 * <pre>docker compose run --rm jarvis scan</pre>
 */
public final class RadioScan {

    private static final int PASSES = 200;

    private RadioScan() {
    }

    public static void run(PrintStream out) throws RadioException {
        JarvisConfig config = JarvisConfig.fromEnvironment();
        Nrf24Driver driver = new Nrf24Driver(RadioService.openHardware(config), config.getRadioConfig(), Frame.HUB);
        driver.start();
        try {
            out.println("Scanning channels 0-" + RadioConfig.MAX_CHANNEL + " (" + PASSES + " passes, signals stronger than -64 dBm)...");
            int[] detections = driver.scanChannels(PASSES);
            print(out, detections, config.getRadioConfig().channel());
        } finally {
            driver.stop();
        }
    }

    static void print(PrintStream out, int[] detections, int currentChannel) {
        int best = 0;
        for (int channel = 0; channel < detections.length; channel++) {
            out.printf("%3d  %4d MHz  %-50s %d%s%n", channel, 2400 + channel, "#".repeat(Math.min(50, detections[channel] / 4)),
                    detections[channel], channel == currentChannel ? "   <- current" : "");
            // the quietest channel, with quiet neighbours (a Wi-Fi channel is 22 MHz wide)
            if (neighbourhood(detections, channel) < neighbourhood(detections, best))
                best = channel;
        }
        out.println("Quietest channel : " + best + " (set JARVIS_RADIO_CHANNEL, and the same channel in the firmware)");
    }

    private static int neighbourhood(int[] detections, int channel) {
        int sum = 0;
        for (int i = Math.max(0, channel - 2); i <= Math.min(detections.length - 1, channel + 2); i++)
            sum += detections[i];
        return sum;
    }
}
