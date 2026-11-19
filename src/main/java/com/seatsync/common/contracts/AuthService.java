package com.seatsync.common.contracts;

import com.seatsync.common.model.AuthResult;

/**
 * Authentication service contract for validating user credentials.
 */
public interface AuthService {

    /**
     * Validates user credentials and establishes a user identity.
     *
     * @param username Client username
     * @param password Password character array (wiped after use for security hygiene)
     * @return Result of the authentication attempt
     */
    AuthResult login(String username, char[] password);
}
