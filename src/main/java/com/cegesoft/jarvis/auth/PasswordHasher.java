package com.cegesoft.jarvis.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 (in the JDK, no dependency). The iteration count is stored with each hash, so it can be raised
 * later without invalidating the existing passwords.
 */
public final class PasswordHasher {

    public static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public record Hash(String hash, String salt, int iterations) {
    }

    public static Hash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new Hash(encode(derive(password, salt, ITERATIONS)), encode(salt), ITERATIONS);
    }

    public static boolean verify(char[] password, Hash expected) {
        byte[] salt = Base64.getDecoder().decode(expected.salt());
        byte[] computed = derive(password, salt, expected.iterations());
        return MessageDigest.isEqual(computed, Base64.getDecoder().decode(expected.hash()));
    }

    /**
     * Same work as a real verification : an unknown user name can't be detected through the response time.
     */
    public static void dummyVerify(char[] password) {
        derive(password, new byte[SALT_BYTES], ITERATIONS);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
