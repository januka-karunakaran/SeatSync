package com.seatsync.security;

import com.seatsync.common.model.AuthResult;
import com.seatsync.common.model.ErrorCode;
import java.util.Arrays;
import java.util.Objects;

/** Implements the shared authentication contract. */
public final class AuthService implements com.seatsync.common.contracts.AuthService {
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final byte[] dummySalt;
    private final byte[] dummyHash;

    public AuthService(UserRepository users, PasswordHasher hasher) {
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
        dummySalt = hasher.newSalt();
        char[] dummy = new char[] {'x'};
        try {
            dummyHash = hasher.hash(dummy, dummySalt);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }

    @Override
    public AuthResult login(String username, char[] password) {
        try {
            if (!InputValidator.username(username) || password == null || password.length == 0 || password.length > 1024) {
                return failed();
            }
            User user = users.findByUsername(username).orElse(null);
            boolean matches = hasher.verify(password, user == null ? dummySalt : user.salt(),
                user == null ? dummyHash : user.hash());
            return user != null && matches
                ? AuthResult.ok(user.id(), user.username(), "Login successful")
                : failed();
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static AuthResult failed() {
        return AuthResult.failed(ErrorCode.INVALID_CREDENTIALS, "Username or password incorrect");
    }
}
