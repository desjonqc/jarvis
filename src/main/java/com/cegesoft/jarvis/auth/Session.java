package com.cegesoft.jarvis.auth;

import org.json.simple.JSONObject;

/**
 * A logged in browser. Only the SHA-256 of the token is kept : the sessions file doesn't allow to log in.
 *
 * @param id         public identifier (list and revocation of the sessions)
 * @param remember   "stay logged in" : the cookie survives the browser closing and the session lives longer
 * @param lastUsedAt updated at most once a minute
 */
public record Session(String id, String tokenHash, String username, boolean remember, String userAgent,
                      long createdAt, long lastUsedAt, long expiresAt) {

    public Session touch(long now, long idleTimeoutMs, long absoluteExpiry) {
        return new Session(id, tokenHash, username, remember, userAgent, createdAt, now,
                Math.min(now + idleTimeoutMs, absoluteExpiry));
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("tokenHash", tokenHash);
        json.put("username", username);
        json.put("remember", remember);
        json.put("userAgent", userAgent);
        json.put("createdAt", createdAt);
        json.put("lastUsedAt", lastUsedAt);
        json.put("expiresAt", expiresAt);
        return json;
    }

    public static Session fromJson(JSONObject json) {
        return new Session((String) json.get("id"), (String) json.get("tokenHash"), (String) json.get("username"),
                Boolean.TRUE.equals(json.get("remember")), (String) json.get("userAgent"),
                ((Number) json.get("createdAt")).longValue(), ((Number) json.get("lastUsedAt")).longValue(),
                ((Number) json.get("expiresAt")).longValue());
    }

    /**
     * @return what the web app shows in the list of the logged in devices (no token hash)
     */
    @SuppressWarnings("unchecked")
    public JSONObject toPublicJson(boolean current) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("userAgent", userAgent);
        json.put("remember", remember);
        json.put("createdAt", createdAt);
        json.put("lastUsedAt", lastUsedAt);
        json.put("expiresAt", expiresAt);
        json.put("current", current);
        return json;
    }
}
