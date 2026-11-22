package com.seatsync.security;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    /** Server-only identity lookup. Legacy repositories grant no elevated privileges. */
    default Optional<User> findById(String userId) { return Optional.empty(); }
}
