package com.cegesoft.jarvis.auth;

import com.cegesoft.jarvis.utils.JsonFile;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The accounts allowed to log in to the web app, in Auth/users.json. The file is read again when it changes, so
 * that a user created with the command line ("user" argument) can log in without restarting the hub.
 */
public class UserStore {

    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9._-]{2,32}");

    public record User(String username, PasswordHasher.Hash password, long createdAt) {
    }

    private final JsonFile file;
    private Map<String, User> users = new LinkedHashMap<>();
    private FileTime loadedVersion;

    public UserStore(File dataDir) {
        this.file = new JsonFile(new File(dataDir, "Auth/users.json").toPath());
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized User find(String username) {
        reloadIfChanged();
        return username == null ? null : users.get(normalize(username));
    }

    public synchronized boolean isEmpty() {
        reloadIfChanged();
        return users.isEmpty();
    }

    public synchronized List<String> usernames() {
        reloadIfChanged();
        return List.copyOf(users.keySet());
    }

    /**
     * Create the user, or replace its password.
     *
     * @throws IllegalArgumentException if the name or the password is not valid
     */
    public synchronized void setPassword(String username, char[] password) {
        if (!isValidUsername(username))
            throw new IllegalArgumentException("Nom d'utilisateur invalide (2 à 32 caractères : lettres, chiffres, . _ -)");
        if (password.length < MIN_PASSWORD_LENGTH)
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères");
        reloadIfChanged();
        String key = normalize(username);
        User existing = users.get(key);
        users.put(key, new User(key, PasswordHasher.hash(password), existing == null ? System.currentTimeMillis() : existing.createdAt()));
        save();
    }

    private void reloadIfChanged() {
        FileTime version = version();
        if (version != null && version.equals(loadedVersion))
            return;
        Map<String, User> loaded = new LinkedHashMap<>();
        for (Object obj : file.readArray()) {
            JSONObject json = (JSONObject) obj;
            String username = (String) json.get("username");
            loaded.put(username, new User(username,
                    new PasswordHasher.Hash((String) json.get("hash"), (String) json.get("salt"), ((Number) json.get("iterations")).intValue()),
                    ((Number) json.getOrDefault("createdAt", 0L)).longValue()));
        }
        this.users = loaded;
        this.loadedVersion = version;
    }

    @SuppressWarnings("unchecked")
    private void save() {
        JSONArray array = new JSONArray();
        for (User user : users.values()) {
            JSONObject json = new JSONObject();
            json.put("username", user.username());
            json.put("hash", user.password().hash());
            json.put("salt", user.password().salt());
            json.put("iterations", user.password().iterations());
            json.put("createdAt", user.createdAt());
            array.add(json);
        }
        try {
            file.write(array);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't save " + file.getPath(), e);
        }
        this.loadedVersion = version();
    }

    private FileTime version() {
        try {
            return Files.exists(file.getPath()) ? Files.getLastModifiedTime(file.getPath()) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
