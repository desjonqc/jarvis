package com.cegesoft.jarvis.auth;

import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.task.TaskId;
import com.cegesoft.jarvis.task.TaskManager;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Login of the web app users and their sessions (cookie). The API keys stay accepted for scripts.
 */
public class AuthManager {

    private static final long SESSION_SAVE_INTERVAL_MS = 10 * 60_000;

    public sealed interface LoginResult {
        record Success(SessionManager.Created created) implements LoginResult {
        }

        record Invalid() implements LoginResult {
        }

        record Throttled(long retryAfterMs) implements LoginResult {
        }
    }

    private final JarvisConfig config;
    private final UserStore users;
    private final SessionManager sessions;
    private final LoginThrottle throttle = new LoginThrottle();
    private TaskId saveTask;

    public AuthManager(JarvisConfig config) {
        this.config = config;
        this.users = new UserStore(config.getDataDir());
        this.sessions = new SessionManager(config.getDataDir(), config.getSessionDays());
    }

    public void load() {
        sessions.load();
        bootstrapAdmin();
        saveTask = TaskManager.scheduleTaskTimer(sessions::save, "SESSIONS-SAVE", SESSION_SAVE_INTERVAL_MS, SESSION_SAVE_INTERVAL_MS);
    }

    public void unload() {
        TaskManager.cancelTask(saveTask);
        sessions.save();
    }

    /**
     * First start : the account given by JARVIS_ADMIN_USER / JARVIS_ADMIN_PASSWORD is created.
     */
    private void bootstrapAdmin() {
        if (!users.isEmpty())
            return;
        String password = config.getAdminPassword();
        if (password.isEmpty()) {
            getLogger().warn("No web app account : set JARVIS_ADMIN_PASSWORD, or create one with \"docker compose run --rm jarvis user <name>\".");
            return;
        }
        try {
            users.setPassword(config.getAdminUser(), password.toCharArray());
            getLogger().info("Web app account \"" + config.getAdminUser() + "\" created (JARVIS_ADMIN_PASSWORD can now be removed from .env).");
        } catch (IllegalArgumentException e) {
            getLogger().error("Can't create the web app account : " + e.getMessage());
        }
    }

    public LoginResult login(String username, String password, boolean remember, String userAgent) {
        String key = username == null ? "" : UserStore.normalize(username);
        long wait = throttle.retryAfterMs(key);
        if (wait > 0)
            return new LoginResult.Throttled(wait);
        UserStore.User user = users.find(key);
        char[] given = password == null ? new char[0] : password.toCharArray();
        boolean valid;
        if (user == null) {
            PasswordHasher.dummyVerify(given);
            valid = false;
        } else {
            valid = PasswordHasher.verify(given, user.password());
        }
        if (!valid) {
            throttle.onFailure(key);
            getLogger().warn("Failed web app login for \"" + key + "\"");
            return new LoginResult.Invalid();
        }
        throttle.onSuccess(key);
        getLogger().info("Web app login : " + user.username());
        return new LoginResult.Success(sessions.create(user.username(), remember, userAgent));
    }

    /**
     * @return false if the current password is wrong
     * @throws IllegalArgumentException if the new password is not valid
     */
    public boolean changePassword(Session session, String current, String next) {
        UserStore.User user = users.find(session.username());
        if (user == null || current == null || !PasswordHasher.verify(current.toCharArray(), user.password()))
            return false;
        users.setPassword(user.username(), next.toCharArray());
        // A stolen session must not survive a password change
        sessions.revokeOthers(user.username(), session.id());
        getLogger().info("Password changed for " + user.username());
        return true;
    }

    /**
     * @return the session if it is still valid and its user still exists
     */
    public Session authenticate(String token) {
        Session session = sessions.authenticate(token);
        if (session == null || users.find(session.username()) == null)
            return null;
        return session;
    }

    public SessionManager getSessions() {
        return sessions;
    }

    public UserStore getUsers() {
        return users;
    }
}
