package com.seatsync.reliability;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.ErrorReply;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Decorating RequestDispatcher that enforces end-to-end request idempotency on mutating commands.
 * Automatically serves cached outcomes upon repeated request transmissions with identical payloads,
 * preventing duplicate reservations or duplicate hold state transitions.
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class IdempotentRequestDispatcher implements RequestDispatcher {

    private static final Logger LOG = Logger.getLogger(IdempotentRequestDispatcher.class.getName());

    private static final Set<String> MUTATING_COMMANDS = Set.of(
        "BOOK_SEAT",
        "HOLD_SEAT",
        "CONFIRM_HOLD",
        "CANCEL_BOOKING",
        "RELEASE_HOLD",
        "JOIN_WAITLIST",
        "LEAVE_WAITLIST"
    );

    private final RequestDispatcher delegate;
    private final IdempotencyCache cache;

    public IdempotentRequestDispatcher(RequestDispatcher delegate, IdempotencyCache cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    public IdempotencyCache getCache() {
        return cache;
    }

    @Override
    public void dispatch(RequestContext context, String jsonLine) {
        if (context == null || jsonLine == null || jsonLine.isBlank()) {
            delegate.dispatch(context, jsonLine);
            return;
        }

        String requestId = null;
        String type = null;
        try {
            JsonNode root = ProtocolJson.parseTree(jsonLine);
            if (root.has("requestId")) {
                requestId = root.path("requestId").asText(null);
            }
            if (root.has("type")) {
                type = root.path("type").asText(null);
            }
        } catch (Exception e) {
            // Let the delegate handle malformed JSON format
            delegate.dispatch(context, jsonLine);
            return;
        }

        if (requestId == null || type == null || !MUTATING_COMMANDS.contains(type)) {
            // Read-only queries or messages without requestId bypass idempotency deduplication
            delegate.dispatch(context, jsonLine);
            return;
        }

        String userId = context.getAuthenticatedUserId().orElse("anon");
        String payloadDigest = IdempotencyCache.computeDigest(jsonLine.trim());

        IdempotencyCache.LookupResult lookup = cache.lookup(userId, requestId, type, payloadDigest);
        if (lookup.status() == IdempotencyCache.LookupStatus.HIT) {
            LOG.info("Idempotent hit for request [" + requestId + "] type [" + type + "] user [" + userId + "]");
            context.send(lookup.cachedReply());
            return;
        } else if (lookup.status() == IdempotencyCache.LookupStatus.CONFLICT) {
            LOG.warning("Idempotent conflict detected for request [" + requestId + "] with modified payload");
            ErrorReply conflictError = new ErrorReply(
                requestId,
                ErrorCode.INVALID_REQUEST_FORMAT,
                "Conflicting payload re-used for existing requestId"
            );
            context.send(ProtocolJson.serialize(conflictError));
            return;
        }

        // Cache miss: delegate and intercept reply
        final String fUserId = userId;
        final String fRequestId = requestId;
        final String fType = type;

        RequestContext interceptingContext = new InterceptingRequestContext(context, replyLine -> {
            cache.record(fUserId, fRequestId, fType, payloadDigest, replyLine);
        });

        delegate.dispatch(interceptingContext, jsonLine);
    }

    /**
     * Context wrapper that intercepts the first reply sent back to the client to record in cache.
     */
    private static class InterceptingRequestContext implements RequestContext {
        private final RequestContext delegate;
        private final java.util.function.Consumer<String> onSend;
        private boolean captured = false;

        InterceptingRequestContext(RequestContext delegate, java.util.function.Consumer<String> onSend) {
            this.delegate = delegate;
            this.onSend = onSend;
        }

        @Override public String getSessionId() { return delegate.getSessionId(); }
        @Override public Optional<String> getAuthenticatedUserId() { return delegate.getAuthenticatedUserId(); }
        @Override public void setAuthenticatedUserId(String userId) { delegate.setAuthenticatedUserId(userId); }
        @Override public void clearAuthentication() { delegate.clearAuthentication(); }
        @Override public boolean isOpen() { return delegate.isOpen(); }
        @Override public void close() { delegate.close(); }

        @Override
        public void send(String jsonLine) {
            if (!captured && jsonLine != null) {
                captured = true;
                try {
                    onSend.accept(jsonLine);
                } catch (Exception ignored) {}
            }
            delegate.send(jsonLine);
        }
    }
}
