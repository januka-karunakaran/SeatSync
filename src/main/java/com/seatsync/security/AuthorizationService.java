package com.seatsync.security;

import com.seatsync.common.contracts.RequestContext;
import java.util.Objects;
import com.seatsync.common.model.UserRole;
import java.util.Optional;

public final class AuthorizationService {
    private final UserRepository users;

    public AuthorizationService() { this(username -> Optional.empty()); }
    public AuthorizationService(UserRepository users) { this.users = Objects.requireNonNull(users); }

    public Optional<UserRole> roleOf(RequestContext context) {
        return context == null ? Optional.empty() : context.getAuthenticatedUserId()
            .flatMap(users::findById).map(User::role);
    }

    public boolean isAdmin(String userId) {
        return userId != null && users.findById(userId).map(User::role).filter(r -> r == UserRole.ADMIN).isPresent();
    }

    public void requireAdmin(String userId) {
        if (!isAdmin(userId)) { throw new SecurityException("Administrator permission required"); }
    }

    /** Shared policy for M1 ticket/history readers; ownership always comes from server records. */
    public boolean canReadOwnedResource(RequestContext context, String ownerId) {
        return isAuthenticated(context) && ownerId != null && (canCancel(context, ownerId)
            || isAdmin(context.getAuthenticatedUserId().orElse(null)));
    }
    public boolean isAuthenticated(RequestContext context) {
        return context != null && context.getAuthenticatedUserId().filter(id -> !id.isBlank()).isPresent();
    }

    public boolean canCancel(RequestContext context, String ownerId) {
        return isAuthenticated(context) && ownerId != null
            && Objects.equals(context.getAuthenticatedUserId().orElse(null), ownerId);
    }
}
