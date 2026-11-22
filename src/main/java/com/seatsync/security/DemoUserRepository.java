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
            users = Map.copyOf(accounts);
        } finally {
            Arrays.fill(demoPassword, '\0');
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
}
