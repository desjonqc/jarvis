package com.cegesoft.jarvis.auth;

import com.cegesoft.jarvis.utils.JsonFile;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Web app sessions, kept in Auth/sessions.json so that a hub restart doesn't log everybody out.
 * <ul>
 *     <li>"stay logged in" : expires after {@code rememberDays} without use, and in any case 6 times later ;</li>
 *     <li>otherwise : expires after 12 hours without use, and in any case after 7 days.</li>
 * </ul>
 */
public class SessionManager {

    static final long TOUCH_INTERVAL_MS = 60_000;
    private static final Duration SHORT_IDLE = Duration.ofHours(12);
    private static final Duration SHORT_MAX = Duration.ofDays(7);
    private static final int MAX_SESSIONS_PER_USER = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Created(String token, Session session) {
    }

    private final JsonFile file;
    private final Duration rememberIdle;
    private final Duration rememberMax;
    // token hash -> session
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public SessionManager(File dataDir, int rememberDays) {
        this.file = new JsonFile(new File(dataDir, "Auth/sessions.json").toPath());
        this.rememberIdle = Duration.ofDays(rememberDays);
        this.rememberMax = rememberIdle.multipliedBy(6);
    }

    public void load() {
        long now = System.currentTimeMillis();
        for (Object obj : file.readArray()) {
            try {
                Session session = Session.fromJson((JSONObject) obj);
                if (session.expiresAt() > now)
                    sessions.put(session.tokenHash(), session);
            } catch (RuntimeException e) {
                getLogger().warn("Session ignored : " + e);
            }
        }
    }

    /**
     * @return the session's lifetime, for the cookie Max-Age (null = cookie deleted when the browser closes)
     */
    public Duration cookieMaxAge(Session session) {
        return session.remember() ? rememberIdle : null;
    }

    public Created create(String username, boolean remember, String userAgent) {
        long now = System.currentTimeMillis();
        String token = randomToken(32);
        Session session = new Session(randomToken(9), sha256(token), username, remember, truncate(userAgent),
                now, now, now + idle(remember).toMillis());
        sessions.put(session.tokenHash(), session);
        trimSessionsOf(username);
        persist();
        return new Created(token, session);
    }

    /**
     * @return the valid session of this cookie token, or null
     */
    public Session authenticate(String token) {
        if (token == null || token.isEmpty())
            return null;
        String hash = sha256(token);
        Session session = sessions.get(hash);
        if (session == null)
            return null;
        long now = System.currentTimeMillis();
        if (session.expiresAt() <= now) {
            sessions.remove(hash, session);
            dirty = true;
            return null;
        }
        if (now - session.lastUsedAt() >= TOUCH_INTERVAL_MS) {
            Session touched = session.touch(now, idle(session.remember()).toMillis(), session.createdAt() + max(session.remember()).toMillis());
            sessions.replace(hash, session, touched);
            dirty = true; // saved by the periodic save : no SD card write for each request
            return touched;
        }
        return session;
    }

    public void revokeToken(String token) {
        if (token != null && sessions.remove(sha256(token)) != null)
            persist();
    }

    /**
     * @return false if the user has no session with this id
     */
    public boolean revoke(String username, String id) {
        boolean removed = sessions.values().removeIf(session -> session.username().equals(username) && session.id().equals(id));
        if (removed)
            persist();
        return removed;
    }

    public void revokeOthers(String username, String keepId) {
        if (sessions.values().removeIf(session -> session.username().equals(username) && !session.id().equals(keepId)))
            persist();
    }

    public List<Session> list(String username) {
        long now = System.currentTimeMillis();
        return sessions.values().stream()
                .filter(session -> session.username().equals(username) && session.expiresAt() > now)
                .sorted(Comparator.comparingLong(Session::lastUsedAt).reversed())
                .toList();
    }

    /**
     * Remove the expired sessions and write the last use dates (periodic task and shutdown).
     */
    public void save() {
        long now = System.currentTimeMillis();
        if (sessions.values().removeIf(session -> session.expiresAt() <= now))
            dirty = true;
        if (dirty)
            persist();
    }

    private void trimSessionsOf(String username) {
        List<Session> sessionsOfUser = list(username);
        for (int i = MAX_SESSIONS_PER_USER; i < sessionsOfUser.size(); i++)
            sessions.remove(sessionsOfUser.get(i).tokenHash());
    }

    @SuppressWarnings("unchecked")
    private synchronized void persist() {
        dirty = false;
        JSONArray array = new JSONArray();
        for (Session session : sessions.values())
            array.add(session.toJson());
        try {
            file.write(array);
        } catch (IOException e) {
            dirty = true;
            getLogger().error("Can't save sessions in " + file.getPath() + " : ", e);
        }
    }

    private Duration idle(boolean remember) {
        return remember ? rememberIdle : SHORT_IDLE;
    }

    private Duration max(boolean remember) {
        return remember ? rememberMax : SHORT_MAX;
    }

    private static String truncate(String userAgent) {
        if (userAgent == null)
            return "";
        return userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
    }

    static String randomToken(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
