package com.seatsync.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Local test accounts, recreated with fresh salts whenever the server starts. */
public final class DemoUserRepository implements UserRepository {
    private final Map<String, User> users;

    public DemoUserRepository(char[] demoPassword, PasswordHasher hasher) {
        this(demoPassword, null, hasher);
    }

    /** Admin access is opt-in and uses a separately supplied password. */
    public DemoUserRepository(char[] demoPassword, char[] adminPassword, PasswordHasher hasher) {
        Objects.requireNonNull(demoPassword, "demoPassword");
        try {
            Objects.requireNonNull(hasher, "hasher");
            if (demoPassword.length == 0 || demoPassword.length > 1024) {
                throw new IllegalArgumentException("Demo password must contain 1 to 1024 characters");
            }
            Map<String, User> accounts = new HashMap<>();
            for (String name : new String[] {"alice", "bob", "carol", "dave", "erin"}) {
                byte[] salt = hasher.newSalt();
                accounts.put(name, new User("demo-" + name, name, salt, hasher.hash(demoPassword, salt)));
            }
            if (adminPassword != null) {
                if (adminPassword.length < 12 || adminPassword.length > 1024
                        || Arrays.equals(demoPassword, adminPassword)) {
                    throw new IllegalArgumentException("Admin password must be distinct and contain 12 to 1024 characters");
                }
                byte[] salt = hasher.newSalt();
                accounts.put("admin", new User("demo-admin", "admin", salt, hasher.hash(adminPassword, salt),
                    com.seatsync.common.model.UserRole.ADMIN));
            }
            users = Map.copyOf(accounts);
        } finally {
            Arrays.fill(demoPassword, '\0');
            if (adminPassword != null) { Arrays.fill(adminPassword, '\0'); }
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public Optional<User> findById(String userId) {
        return users.values().stream().filter(user -> user.id().equals(userId)).findFirst();
    }
}
