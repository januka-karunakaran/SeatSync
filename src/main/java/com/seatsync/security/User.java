package com.seatsync.security;

import java.util.Objects;

/** Immutable account metadata and password verifier; never holds a plaintext password. */
public final class User {
    private final String id;
    private final String username;
    private final byte[] salt;
    private final byte[] hash;

    public User(String id, String username, byte[] salt, byte[] hash) {
        this.id = Objects.requireNonNull(id);
        this.username = Objects.requireNonNull(username);
        this.salt = Objects.requireNonNull(salt).clone();
        this.hash = Objects.requireNonNull(hash).clone();
    }

    public String id() { return id; }
    public String username() { return username; }
    byte[] salt() { return salt.clone(); }
    byte[] hash() { return hash.clone(); }
}
