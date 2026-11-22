package com.seatsync.security;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
}
