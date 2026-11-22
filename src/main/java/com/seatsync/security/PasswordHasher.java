package com.seatsync.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 password hashing for the in-memory demo repository. */
public final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    public byte[] newSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    public byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean verify(char[] password, byte[] salt, byte[] expected) {
        byte[] actual = hash(password, salt);
        try {
            return MessageDigest.isEqual(actual, expected);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }
}
