package com.cegesoft.jarvis;

import com.cegesoft.jarvis.radio.RadioConfig;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runtime configuration, read from environment variables (or the equivalent -D system properties,
 * e.g. JARVIS_API_PORT can also be given as -Djarvis.api.port=8000).
 */
public final class JarvisConfig {

    private final File dataDir;
    private final int apiPort;
    private final List<String> apiKeys;
    private final String corsOrigin;
    private final long commandTimeoutMs;
    private final long autosaveIntervalMs;
    private final String radioDriver;
    private final RadioConfig radioConfig;
    private final int spiController;
    private final int spiChipSelect;
    private final int spiFrequency;
    private final int ceGpio;
    private final int irqGpio;
    private final String simulatedNodes;
    private final String adminUser;
    private final String adminPassword;
    private final int sessionDays;
    private final boolean cookieSecure;

    private JarvisConfig(Function<String, String> source) {
        String defaultDataDir = Jarvis.isWindows() ? "JARVIS" : System.getProperty("user.dir");
        this.dataDir = new File(get(source, "JARVIS_DATA_DIR", defaultDataDir));
        this.apiPort = Integer.parseInt(get(source, "JARVIS_API_PORT", "8000"));
        this.apiKeys = Arrays.stream(get(source, "JARVIS_API_KEYS", "").split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toUnmodifiableList());
        this.corsOrigin = get(source, "JARVIS_CORS_ORIGIN", "*");
        this.commandTimeoutMs = Long.parseLong(get(source, "JARVIS_COMMAND_TIMEOUT_MS", "12000"));
        this.autosaveIntervalMs = Long.parseLong(get(source, "JARVIS_AUTOSAVE_INTERVAL_MS", "300000"));

        this.radioDriver = get(source, "JARVIS_RADIO_DRIVER", "nrf24").toLowerCase(Locale.ROOT);
        this.radioConfig = new RadioConfig(
                Integer.parseInt(get(source, "JARVIS_RADIO_CHANNEL", "76")),
                parseDataRate(get(source, "JARVIS_RADIO_DATA_RATE", "250KBPS")),
                RadioConfig.PaLevel.valueOf(get(source, "JARVIS_RADIO_PA_LEVEL", "MAX").toUpperCase(Locale.ROOT)),
                Integer.parseUnsignedInt(get(source, "JARVIS_RADIO_NETWORK_ID", "4A52564E"), 16),
                15, 1000);
        this.spiController = Integer.parseInt(get(source, "JARVIS_RADIO_SPI_BUS", "0"));
        this.spiChipSelect = Integer.parseInt(get(source, "JARVIS_RADIO_SPI_CS", "0"));
        this.spiFrequency = Integer.parseInt(get(source, "JARVIS_RADIO_SPI_HZ", "4000000"));
        this.ceGpio = Integer.parseInt(get(source, "JARVIS_RADIO_CE_GPIO", "22"));
        this.irqGpio = Integer.parseInt(get(source, "JARVIS_RADIO_IRQ_GPIO", "24"));
        this.simulatedNodes = get(source, "JARVIS_RADIO_SIM_NODES", "2:PULSE_RELAY_ACTIVATOR,3:PULSE_SWITCH_SENSOR");

        this.adminUser = get(source, "JARVIS_ADMIN_USER", "admin");
        this.adminPassword = get(source, "JARVIS_ADMIN_PASSWORD", "");
        this.sessionDays = Integer.parseInt(get(source, "JARVIS_SESSION_DAYS", "30"));
        if (sessionDays < 1)
            throw new IllegalArgumentException("JARVIS_SESSION_DAYS must be at least 1 : " + sessionDays);
        this.cookieSecure = Boolean.parseBoolean(get(source, "JARVIS_COOKIE_SECURE", "false"));
    }

    public static JarvisConfig fromEnvironment() {
        return new JarvisConfig(System::getenv);
    }

    static JarvisConfig from(Function<String, String> source) {
        return new JarvisConfig(source);
    }

    private static String get(Function<String, String> source, String key, String defaultValue) {
        String value = System.getProperty(key.toLowerCase(Locale.ROOT).replace('_', '.'));
        if (value == null || value.isBlank())
            value = source.apply(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static RadioConfig.DataRate parseDataRate(String value) {
        return switch (value.toUpperCase(Locale.ROOT).replace("_", "")) {
            case "250KBPS", "KBPS250" -> RadioConfig.DataRate.KBPS_250;
            case "1MBPS", "MBPS1" -> RadioConfig.DataRate.MBPS_1;
            case "2MBPS", "MBPS2" -> RadioConfig.DataRate.MBPS_2;
            default -> throw new IllegalArgumentException("JARVIS_RADIO_DATA_RATE must be 250KBPS, 1MBPS or 2MBPS : " + value);
        };
    }

    public File getDataDir() {
        return dataDir;
    }

    public int getApiPort() {
        return apiPort;
    }

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public String getCorsOrigin() {
        return corsOrigin;
    }

    public long getCommandTimeoutMs() {
        return commandTimeoutMs;
    }

    public long getAutosaveIntervalMs() {
        return autosaveIntervalMs;
    }

    /**
     * @return "nrf24" (radio wired on the GPIO header) or "simulated" (no hardware, for development)
     */
    public String getRadioDriver() {
        return radioDriver;
    }

    public RadioConfig getRadioConfig() {
        return radioConfig;
    }

    public int getSpiController() {
        return spiController;
    }

    public int getSpiChipSelect() {
        return spiChipSelect;
    }

    public int getSpiFrequency() {
        return spiFrequency;
    }

    public int getCeGpio() {
        return ceGpio;
    }

    public int getIrqGpio() {
        return irqGpio;
    }

    /**
     * @return simulated nodes "id:TYPE,id:TYPE" (simulated driver only)
     */
    public String getSimulatedNodes() {
        return simulatedNodes;
    }

    /**
     * @return the web app account created at the first start (when no account exists yet)
     */
    public String getAdminUser() {
        return adminUser;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    /**
     * @return the lifetime without use of a "stay logged in" session
     */
    public int getSessionDays() {
        return sessionDays;
    }

    /**
     * @return true when the web app is served over HTTPS : the session cookie is then only sent over HTTPS
     */
    public boolean isCookieSecure() {
        return cookieSecure;
    }
}
