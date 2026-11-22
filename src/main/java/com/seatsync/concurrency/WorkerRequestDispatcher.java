package com.seatsync.concurrency;

import com.seatsync.common.contracts.*;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.ErrorReply;

public final class WorkerRequestDispatcher implements RequestDispatcher, AutoCloseable {
    private final WorkerPoolManager workers = new WorkerPoolManager();
    private final RequestDispatcher processor;

    public WorkerRequestDispatcher(AuthService auth, ReservationService reservations) {
        var bookingProcessor = new BookingRequestProcessor(auth, reservations);
        processor = bookingProcessor::process;
    }

    public WorkerRequestDispatcher(RequestDispatcher dispatcher) {
        processor = java.util.Objects.requireNonNull(dispatcher);
    }

    @Override
    public void dispatch(RequestContext context, String jsonLine) {
        if (!workers.submit(context, () -> processor.dispatch(context, jsonLine))) {
            String id = "N/A";
            try {
                var field = ProtocolJson.parseTree(jsonLine).path("requestId");
                if (field.isTextual() && !field.textValue().isBlank()) {
                    id = field.textValue();
                }
            } catch (IllegalArgumentException ignored) {
                // No usable correlation ID on a malformed frame.
            }
            context.send(ProtocolJson.serialize(new ErrorReply(id, ErrorCode.RATE_LIMITED, "Server busy")));
        }
    }

    @Override
    public void close() {
        workers.close();
    }
}
