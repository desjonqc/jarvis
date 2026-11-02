package com.cegesoft.jarvis.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @TempDir
    Path dataDir;

    @Test
    void sessionsSurviveARestartWithoutStoringTheToken() throws Exception {
        SessionManager sessions = new SessionManager(dataDir.toFile(), 30);
        SessionManager.Created created = sessions.create("clem", true, "Safari");
        assertEquals("clem", sessions.authenticate(created.token()).username());

        String file = Files.readString(dataDir.resolve("Auth/sessions.json"));
        assertFalse(file.contains(created.token()), "only the hash of the token is stored");

        SessionManager restarted = new SessionManager(dataDir.toFile(), 30);
        restarted.load();
        assertEquals(created.session().id(), restarted.authenticate(created.token()).id());
        assertNull(restarted.authenticate("forged"));
    }

    @Test
    void revokedSessionsAreRefused() {
        SessionManager sessions = new SessionManager(dataDir.toFile(), 30);
        SessionManager.Created a = sessions.create("clem", false, null);
        SessionManager.Created b = sessions.create("clem", false, null);
        SessionManager.Created other = sessions.create("alice", false, null);

        assertFalse(sessions.revoke("alice", a.session().id()), "a user can't close another user's session");
        sessions.revokeOthers("clem", a.session().id());
        assertNotNull(sessions.authenticate(a.token()));
        assertNull(sessions.authenticate(b.token()));
        assertNotNull(sessions.authenticate(other.token()));

        sessions.revokeToken(a.token());
        assertNull(sessions.authenticate(a.token()));
        assertNull(sessions.cookieMaxAge(other.session()), "not remembered : cookie deleted with the browser");
    }

    @Test
    void passwordsAreHashedAndVerified() {
        PasswordHasher.Hash hash = PasswordHasher.hash("correct horse".toCharArray());
        assertTrue(PasswordHasher.verify("correct horse".toCharArray(), hash));
        assertFalse(PasswordHasher.verify("correct horsf".toCharArray(), hash));
        assertNotEquals(hash.salt(), PasswordHasher.hash("correct horse".toCharArray()).salt());
    }

    @Test
    void usersAreValidatedAndNamesAreCaseInsensitive() {
        UserStore users = new UserStore(dataDir.toFile());
        assertThrows(IllegalArgumentException.class, () -> users.setPassword("clem", "short".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> users.setPassword("a b", "long enough".toCharArray()));
        users.setPassword("Clem", "long enough".toCharArray());
        assertNotNull(users.find("CLEM"));
        // another process (the "user" command) adds an account : seen without restart
        new UserStore(dataDir.toFile()).setPassword("alice", "long enough".toCharArray());
        assertNotNull(users.find("alice"));
    }
}
