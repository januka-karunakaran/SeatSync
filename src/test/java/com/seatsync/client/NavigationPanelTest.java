package com.seatsync.client;

import com.seatsync.ui.NavigationPanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationPanelTest {
    @Test
    void exposesAllNavigationDestinationsAndReportsSelection() {
        List<String> selected = new ArrayList<>();
        NavigationPanel panel = new NavigationPanel(selected::add);

        assertEquals("Home", panel.getSelectedPage());
        for (String page : List.of("Home", "Movies", "Shows", "Seat Map", "My Tickets", "Waitlist", "Admin")) {
            assertNotNull(panel.getNavigationButton(page));
        }
        assertEquals(Color.WHITE, panel.getNavigationButton("Home").getForeground());
        assertEquals(new Color(0xE1, 0x1D, 0x48), panel.getNavigationButton("Home").getBackground());

        panel.navigate("Seat Map");
        assertEquals("Seat Map", panel.getSelectedPage());
        assertEquals(List.of("Seat Map"), selected);
        assertEquals(new Color(0xE1, 0x1D, 0x48), panel.getNavigationButton("Seat Map").getBackground());
        assertEquals(new Color(0x2A, 0x22, 0x2C), panel.getNavigationButton("Home").getBackground());
        assertThrows(IllegalArgumentException.class, () -> panel.navigate("Unknown"));
    }
}
