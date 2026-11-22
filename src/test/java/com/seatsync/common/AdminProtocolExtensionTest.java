package com.seatsync.common;

import com.seatsync.common.model.*;
import com.seatsync.common.protocol.*;
import com.seatsync.common.protocol.request.*;
import com.seatsync.common.protocol.reply.*;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminProtocolExtensionTest {
    @Test void newCommandsAndRepliesRoundTripWithOneDiscriminator() throws Exception {
        var movie = new MovieDetails("M", "Title", "Description", "Drama", 90);
        var show = new ShowDetails("S", "Title", "Desc", "Drama", "2030-01-01T18:00:00Z", "2030-01-01T20:00:00Z",
            "Room", new AuditoriumLayout("L", "Room", 5, 8, 40, List.of()));
        List<Message> messages = List.of(new AdminCreateMovieRequest("m", movie), new AdminCreateShowRequest("s", "M", show),
            new AdminDisableShowRequest("d", "S"), new AdminListBookingsRequest("b", "S", 0, 10), new GetMoviesRequest("g", 0, 5),
            new AdminMovieCreatedReply("m", "M", "Created"), new AdminShowDisabledReply("d", "S", "Disabled"),
            new AdminBookingsReply("b", new PagedResult<>(List.of(new AdminBookingView("B", "S", "A1", "owner")), 0, 10, 1, false)),
            new MovieListReply("g", new PagedResult<>(List.of(movie), 0, 5, 1, false)));
        var strict = ProtocolJson.mapper().readerFor(com.fasterxml.jackson.databind.JsonNode.class)
            .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        for (Message message : messages) {
            String json = ProtocolJson.serialize(message);
            assertNotNull(strict.readValue(json));
            assertEquals(message, ProtocolJson.deserializeMessage(json));
        }
    }

    @Test void additiveRoleAndShowLifecycleFieldsPreserveLegacyMessages() {
        var login = (LoginOkReply) ProtocolJson.deserializeMessage(
            "{\"type\":\"LOGIN_OK\",\"requestId\":\"r\",\"userId\":\"id\",\"username\":\"alice\"}");
        assertEquals(UserRole.CUSTOMER, login.role());
        var summary = new ShowSummary("S", "Title", "2030-01-01T18:00:00Z", "Room", 40, 40);
        var disabled = new ShowUpdatedEvent("S", summary, 1, false);
        assertEquals(disabled, ProtocolJson.deserializeMessage(ProtocolJson.serialize(disabled)));
        assertTrue(new ShowUpdatedEvent("S", summary, 0).enabled());
        assertEquals(UserRole.ADMIN, ((LoginOkReply) ProtocolJson.deserializeMessage(
            ProtocolJson.serialize(new LoginOkReply("r", "id", "admin", UserRole.ADMIN)))).role());
    }
}
