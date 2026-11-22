package com.seatsync.security;

import java.util.Objects;
import com.seatsync.common.model.UserRole;

/** Immutable account metadata and password verifier; never holds a plaintext password. */
public final class User {
    private final String id;
    private final String username;
    private final byte[] salt;
    private final byte[] hash;
    private final UserRole role;

    public User(String id, String username, byte[] salt, byte[] hash) {
        this(id, username, salt, hash, UserRole.CUSTOMER);
    }

    public User(String id, String username, byte[] salt, byte[] hash, UserRole role) {
        this.id = Objects.requireNonNull(id);
        this.username = Objects.requireNonNull(username);
        this.salt = Objects.requireNonNull(salt).clone();
        this.hash = Objects.requireNonNull(hash).clone();
        this.role = Objects.requireNonNull(role);
    }

    public String id() { return id; }
    public String username() { return username; }
    public UserRole role() { return role; }
    byte[] salt() { return salt.clone(); }
    byte[] hash() { return hash.clone(); }
}
