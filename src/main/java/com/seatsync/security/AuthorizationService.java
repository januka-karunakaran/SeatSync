package com.seatsync.security;

import com.seatsync.common.contracts.RequestContext;
import java.util.Objects;

public final class AuthorizationService {
    public boolean isAuthenticated(RequestContext context) {
        return context != null && context.getAuthenticatedUserId().filter(id -> !id.isBlank()).isPresent();
    }

    public boolean canCancel(RequestContext context, String ownerId) {
        return isAuthenticated(context) && ownerId != null
            && Objects.equals(context.getAuthenticatedUserId().orElse(null), ownerId);
    }
}
