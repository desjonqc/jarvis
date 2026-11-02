package com.cegesoft.jarvis.alert;

import org.json.simple.JSONObject;

/**
 * A problem seen by the hub. It is active until its condition disappears (resolvedAt != 0), and stays in the history
 * after that. The web app writes the message from the type.
 *
 * @param moduleId null for the alerts about the hub itself
 */
public record Alert(long id, AlertType type, String moduleId, long createdAt, long resolvedAt, boolean read) {

    public boolean isActive() {
        return resolvedAt == 0;
    }

    public Alert resolve(long now) {
        return new Alert(id, type, moduleId, createdAt, now, read);
    }

    public Alert markRead() {
        return new Alert(id, type, moduleId, createdAt, resolvedAt, true);
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type.name());
        json.put("severity", type.getSeverity().name());
        json.put("moduleId", moduleId);
        json.put("createdAt", createdAt);
        json.put("resolvedAt", resolvedAt == 0 ? null : resolvedAt);
        json.put("read", read);
        return json;
    }

    public static Alert fromJson(JSONObject json) {
        Object resolved = json.get("resolvedAt");
        return new Alert(((Number) json.get("id")).longValue(), AlertType.valueOf((String) json.get("type")),
                (String) json.get("moduleId"), ((Number) json.get("createdAt")).longValue(),
                resolved == null ? 0 : ((Number) resolved).longValue(), Boolean.TRUE.equals(json.get("read")));
    }
}
