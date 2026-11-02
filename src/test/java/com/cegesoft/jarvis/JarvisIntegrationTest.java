package com.cegesoft.jarvis;

import com.cegesoft.jarvis.radio.RadioService;
import com.cegesoft.jarvis.radio.sim.SimulatedActivator;
import com.cegesoft.jarvis.radio.sim.SimulatedNode;
import com.cegesoft.jarvis.radio.sim.SimulatedRadio;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole hub with a simulated radio network : node 2 = pulse relay (cellar), node 3 = battery push button.
 */
class JarvisIntegrationTest {

    private static final int PORT = 18_090;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    static Path dataDir;

    @BeforeAll
    static void start() {
        System.setProperty("jarvis.data.dir", dataDir.toString());
        System.setProperty("jarvis.api.port", String.valueOf(PORT));
        System.setProperty("jarvis.api.keys", "test-key");
        System.setProperty("jarvis.radio.driver", "simulated");
        System.setProperty("jarvis.radio.sim.nodes", "2:PULSE_RELAY_ACTIVATOR,3:PULSE_SWITCH_SENSOR");
        System.setProperty("jarvis.command.timeout.ms", "2000");
        System.setProperty("jarvis.admin.user", "Clem");
        System.setProperty("jarvis.admin.password", "correct horse");
        Jarvis.startJarvis();
    }

    @AfterAll
    static void stop() {
        Jarvis.stopJarvis();
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith("jarvis."))
                .forEach(System::clearProperty);
    }

    private static SimulatedRadio network() {
        return Jarvis.getServiceManager().getService(RadioService.class).getSimulatedRadio();
    }

    private static Object get(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
                .header("apikey", "test-key").build(), HttpResponse.BodyHandlers.ofString());
        return new JSONParser().parse(response.body());
    }

    private static int post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
                .header("apikey", "test-key").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null)
            request.header("Cookie", cookie);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * @return "jarvis_session=<token>" from the Set-Cookie of the response
     */
    private static String sessionCookie(HttpResponse<?> response) {
        String header = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(header.contains("HttpOnly") && header.contains("SameSite=Strict"), header);
        return header.substring(0, header.indexOf(';'));
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end)
            Thread.sleep(20);
    }

    @Test
    void buttonPressPulsesTheRelayExactlyOnceThroughASchedule() throws Exception {
        // the relay answers DISCOVER at startup ; the sleeping button introduced itself when it booted
        waitUntil(() -> Jarvis.getModuleManager().getModule("2") != null && Jarvis.getModuleManager().getModule("3") != null);
        JSONArray modules = (JSONArray) ((JSONObject) get("/module/list")).get("modules");
        assertEquals(2, modules.size());

        assertEquals(200, post("/schedules/edit", """
                [{"event":{"type":"SENSOR","args":{"id":"3"}},
                  "elements":[{"type":"ACTIVATOR","args":{"id":"2","value":"ON"}}]}]"""));

        SimulatedActivator relay = (SimulatedActivator) network().node(2);
        int before = relay.getPulses();
        get("/sim/press?id=3");
        waitUntil(() -> relay.getPulses() > before);
        Thread.sleep(300);
        assertEquals(before + 1, relay.getPulses(), "one press = one pulse");

        // The schedule makes the relay the button's fallback : the hub asked the button to introduce itself
        // again, and told it in the new WELCOME (parent u8, heartbeat u16, fallback u8)
        SimulatedNode button = network().node(3);
        waitUntil(() -> button.getWelcome() != null && button.getWelcome()[3] == 2);
        assertEquals(2, button.getWelcome()[3]);
    }

    @Test
    void activateAnswersAsSoonAsTheRelayConfirms() throws Exception {
        waitUntil(() -> Jarvis.getModuleManager().getModule("2") != null);
        long start = System.currentTimeMillis();
        assertEquals(new JSONObject(), get("/module/activate?id=2&stateValue=ON"));
        assertTrue(System.currentTimeMillis() - start < 1000);
    }

    @Test
    void sleepingButtonReportsItsBatteryWithoutBeingWaitedFor() throws Exception {
        waitUntil(() -> Jarvis.getModuleManager().getModule("3") != null);
        get("/sim/press?id=3");
        waitUntil(() -> {
            try {
                return ((JSONObject) get("/module/info?id=3")).containsKey("batteryState");
            } catch (Exception e) {
                return false;
            }
        });
        JSONObject info = (JSONObject) get("/module/info?id=3");
        assertEquals("OK", info.get("batteryState"));
        assertEquals(Boolean.TRUE, info.get("sleeper"));

        long start = System.currentTimeMillis();
        JSONObject battery = (JSONObject) get("/module/update/battery?id=3");
        assertTrue(System.currentTimeMillis() - start < 500, "a sleeping module is not waited for");
        assertNotNull(battery.get("batteryLevel"));
    }

    @Test
    void healthReportsTheRadio() throws Exception {
        JSONObject health = (JSONObject) get("/health");
        assertEquals(Boolean.TRUE, health.get("radio"));
        assertEquals("simulated", health.get("radioDriver"));
    }

    @Test
    void modulesAreSavedWithTheirNodeIds() throws Exception {
        waitUntil(() -> Jarvis.getModuleManager().getModule("2") != null);
        Jarvis.getModuleManager().save();
        String saved = Files.readString(dataDir.resolve("Module/modules.json"));
        assertTrue(saved.contains("\"id\":\"2\""), saved);
        assertTrue(saved.contains("PulseRelayActivator"), saved);
    }

    @Test
    void webAppSessionLifecycle() throws Exception {
        // no API key and no session : refused
        assertEquals(401, send("GET", "/module/list", null, null).statusCode());

        HttpResponse<String> login = send("POST", "/auth/login",
                "{\"username\":\"clem\",\"password\":\"correct horse\",\"remember\":true}", null);
        assertEquals(200, login.statusCode(), login.body());
        assertEquals("clem", ((JSONObject) new JSONParser().parse(login.body())).get("username"));
        assertTrue(login.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age="));
        String cookie = sessionCookie(login);

        assertEquals(200, send("GET", "/module/list", null, cookie).statusCode());
        JSONObject me = (JSONObject) new JSONParser().parse(send("GET", "/auth/me", null, cookie).body());
        assertEquals("session", me.get("method"));

        // a second device, then the first one disconnects it
        String other = sessionCookie(send("POST", "/auth/login", "{\"username\":\"clem\",\"password\":\"correct horse\"}", null));
        JSONArray sessions = (JSONArray) ((JSONObject) new JSONParser().parse(send("GET", "/auth/sessions", null, cookie).body())).get("sessions");
        assertTrue(sessions.size() >= 2);
        String otherId = ((java.util.List<?>) sessions).stream().map(JSONObject.class::cast)
                .filter(session -> !Boolean.TRUE.equals(session.get("current")))
                .map(session -> (String) session.get("id")).findFirst().orElseThrow();
        assertEquals(200, send("POST", "/auth/sessions/revoke?id=" + otherId, null, cookie).statusCode());
        assertEquals(401, send("GET", "/auth/me", null, other).statusCode());

        // a state changing request needs POST
        assertEquals(405, send("GET", "/auth/logout", null, cookie).statusCode());
        assertEquals(200, send("POST", "/auth/logout", null, cookie).statusCode());
        assertEquals(401, send("GET", "/module/list", null, cookie).statusCode());
    }

    @Test
    void wrongPasswordsAreThrottled() throws Exception {
        String body = "{\"username\":\"nobody\",\"password\":\"guess\"}";
        for (int i = 0; i < 5; i++)
            assertEquals(401, send("POST", "/auth/login", body, null).statusCode());
        HttpResponse<String> throttled = send("POST", "/auth/login", body, null);
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void passwordChangeKeepsOnlyTheCurrentSession() throws Exception {
        Jarvis.getAuthManager().getUsers().setPassword("alice", "first password".toCharArray());
        String login = "{\"username\":\"alice\",\"password\":\"%s\"}";
        String first = sessionCookie(send("POST", "/auth/login", login.formatted("first password"), null));
        String second = sessionCookie(send("POST", "/auth/login", login.formatted("first password"), null));

        assertEquals(403, send("POST", "/auth/password", "{\"current\":\"wrong\",\"next\":\"second password\"}", first).statusCode());
        assertEquals(400, send("POST", "/auth/password", "{\"current\":\"first password\",\"next\":\"short\"}", first).statusCode());
        assertEquals(200, send("POST", "/auth/password", "{\"current\":\"first password\",\"next\":\"second password\"}", first).statusCode());

        assertEquals(200, send("GET", "/auth/me", null, first).statusCode());
        assertEquals(401, send("GET", "/auth/me", null, second).statusCode());
        assertEquals(401, send("POST", "/auth/login", login.formatted("first password"), null).statusCode());
        assertEquals(200, send("POST", "/auth/login", login.formatted("second password"), null).statusCode());
    }

    @Test
    void modulesCanBeNamedAndPausedSchedulesDontRun() throws Exception {
        waitUntil(() -> Jarvis.getModuleManager().getModule("2") != null && Jarvis.getModuleManager().getModule("3") != null);
        assertEquals(200, post("/module/edit?id=2", "{\"name\":\"  Plafonnier \",\"room\":\"Cave\"}"));
        JSONObject relay = (JSONObject) get("/module/info?id=2");
        assertEquals("Plafonnier", relay.get("name"));
        assertEquals("Cave", relay.get("room"));
        assertEquals(Boolean.TRUE, relay.get("connected"));
        assertEquals(400, post("/module/edit?id=2", "{\"name\":\"" + "x".repeat(41) + "\"}"));

        assertEquals(200, post("/schedules/edit", """
                [{"id":"a","name":"Lumière cave","enabled":false,"event":{"type":"SENSOR","args":{"id":"3"}},
                  "elements":[{"type":"ACTIVATOR","args":{"id":"2","value":"ON"}}]}]"""));
        assertEquals("Lumière cave", ((JSONObject) ((JSONArray) get("/schedules/list")).getFirst()).get("name"));
        SimulatedActivator activator = (SimulatedActivator) network().node(2);
        int before = activator.getPulses();
        get("/sim/press?id=3");
        Thread.sleep(500);
        assertEquals(before, activator.getPulses(), "a paused schedule doesn't run");
    }

    @Test
    void alertsCanBeListedAndRead() throws Exception {
        waitUntil(() -> Jarvis.getModuleManager().getModule("2") != null);
        Jarvis.getModuleManager().getModule("2").markDisconnected();
        JSONObject list = (JSONObject) get("/alerts/list");
        JSONObject alert = ((java.util.List<?>) list.get("alerts")).stream().map(JSONObject.class::cast)
                .filter(a -> "MODULE_DISCONNECTED".equals(a.get("type")) && "2".equals(a.get("moduleId")))
                .findFirst().orElseThrow();
        assertEquals("CRITICAL", alert.get("severity"));
        assertNull(alert.get("resolvedAt"));

        assertEquals(200, post("/alerts/read?id=" + alert.get("id"), ""));
        assertEquals(404, post("/alerts/read?id=999999", ""));

        // the relay answers again : connected, and the alert is resolved
        get("/module/activate?id=2&stateValue=ON");
        waitUntil(() -> Jarvis.getModuleManager().getModule("2").isConnected());
        assertTrue(Jarvis.getAlertManager().getAlerts().stream()
                .filter(a -> a.id() == (Long) alert.get("id")).noneMatch(com.cegesoft.jarvis.alert.Alert::isActive));
    }
}
