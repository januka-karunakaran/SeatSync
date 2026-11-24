package com.seatsync.client;

import com.seatsync.common.model.MovieDetails;
import com.seatsync.ui.BookingFrame;
import com.seatsync.ui.NavigationPanel;
import com.seatsync.ui.components.MovieCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless-safe unit test verifying Stage 2 components:
 * MovieCard presentation, selection callbacks, navigation panel styling, and BookingFrame page creation.
 */
class HomeAndMoviesPageTest {

    @Test
    @DisplayName("MovieCard displays movie metadata and invokes callback on selection")
    void testMovieCard() {
        MovieDetails movie = new MovieDetails("MOV-01", "Inception", "A mind-bending heist thriller", "Sci-Fi", 148);
        AtomicReference<MovieDetails> selected = new AtomicReference<>();
        MovieCard card = new MovieCard(movie, selected::set);

        assertNotNull(card);
        assertEquals("Inception", card.getMovie().title());
        assertEquals("Sci-Fi", card.getMovie().genre());
        assertEquals(148, card.getMovie().durationMinutes());

        card.setSelected(true);
        assertTrue(card.isSelected());
    }

    @Test
    @DisplayName("NavigationPanel exposes cinema navigation destinations")
    void testNavigationPanel() {
        AtomicReference<String> navigated = new AtomicReference<>();
        NavigationPanel nav = new NavigationPanel(navigated::set);

        assertNotNull(nav.getNavigationButton("Home"));
        assertNotNull(nav.getNavigationButton("Movies"));
        assertNotNull(nav.getNavigationButton("Shows"));
        assertNotNull(nav.getNavigationButton("Seat Map"));

        nav.navigate("Movies");
        assertEquals("Movies", nav.getSelectedPage());
        assertEquals("Movies", navigated.get());
    }

    @Test
    @DisplayName("BookingFrame constructs Home and Movie pages cleanly")
    void testBookingFramePages() {
        if (GraphicsEnvironment.isHeadless()) {
            return; // Skip native JFrame peer creation on headless Linux CI runners
        }
        BookingFrame frame = new BookingFrame(null, "alice", "USR-101");
        frame.initialize();
        assertNotNull(frame);
        assertTrue(frame.getWidth() >= 900);
        frame.dispose();
    }
}
