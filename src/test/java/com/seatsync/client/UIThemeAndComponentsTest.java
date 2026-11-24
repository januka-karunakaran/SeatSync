package com.seatsync.client;

import com.seatsync.ui.LoginFrame;
import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.components.EmptyStatePanel;
import com.seatsync.ui.components.ModernButton;
import com.seatsync.ui.components.RoundedPanel;
import com.seatsync.ui.components.SectionHeader;
import com.seatsync.ui.components.StatusBadge;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless-safe test verifying Stage 1 theme tokens, procedural image assets,
 * and reusable presentation components.
 */
class UIThemeAndComponentsTest {

    @Test
    @DisplayName("UIColors design tokens match the cinematic and lavender palette specifications")
    void testDesignTokens() {
        assertEquals(new Color(0x10, 0x10, 0x14), UIColors.CINEMATIC_BLACK);
        assertEquals(new Color(0x1B, 0x1B, 0x23), UIColors.DEEP_CHARCOAL);
        assertEquals(new Color(0xD8, 0xD0, 0xF2), UIColors.LAVENDER_PRIMARY);
        assertEquals(new Color(0x82, 0x74, 0xAB), UIColors.MUTED_PURPLE);
        assertEquals(new Color(0xFA, 0xF9, 0xFC), UIColors.SOFT_WHITE);
        assertEquals(new Color(0x27, 0x83, 0x4B), UIColors.SUCCESS_GREEN);
        assertEquals(new Color(0xE6, 0xA2, 0x3C), UIColors.WARNING_AMBER);
        assertEquals(new Color(0xD8, 0x4C, 0x55), UIColors.ERROR_RED);
    }

    @Test
    @DisplayName("UIFonts exposes consistent typography hierarchy")
    void testFontHierarchy() {
        assertNotNull(UIFonts.HERO_TITLE);
        assertNotNull(UIFonts.TITLE_LARGE);
        assertNotNull(UIFonts.TITLE_MEDIUM);
        assertNotNull(UIFonts.BODY);
        assertNotNull(UIFonts.BUTTON);
        assertTrue(UIFonts.HERO_TITLE.getSize() >= 24);
    }

    @Test
    @DisplayName("ImageResourceManager generates and caches procedural graphics")
    void testImageResourceManager() {
        Image logo1 = ImageResourceManager.getSeatSyncLogo(48, 48);
        Image logo2 = ImageResourceManager.getSeatSyncLogo(48, 48);
        assertNotNull(logo1);
        assertEquals(logo1, logo2, "Subsequent calls with identical dimensions must be cached");

        Image hero = ImageResourceManager.getCinematicHeroBanner(400, 500);
        assertNotNull(hero);

        Image poster = ImageResourceManager.getMoviePoster("Inception", "Sci-Fi", 160, 220);
        assertNotNull(poster);

        assertNotNull(ImageResourceManager.getEyeIcon(true, 16));
        assertNotNull(ImageResourceManager.getEyeIcon(false, 16));
    }

    @Test
    @DisplayName("Reusable Swing components instantiate and configure properly")
    void testReusableComponents() {
        RoundedPanel panel = new RoundedPanel(12, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1);
        assertNotNull(panel);

        ModernButton button = new ModernButton("Click Me", ModernButton.Variant.PRIMARY);
        assertEquals("Click Me", button.getText());
        button.setVariant(ModernButton.Variant.SECONDARY);

        StatusBadge badge = new StatusBadge("Connected", StatusBadge.Type.SUCCESS);
        assertNotNull(badge);
        badge.setStatus("Warning", StatusBadge.Type.WARNING);

        SectionHeader header = new SectionHeader("Now Showing", "Select a movie to view available times");
        assertNotNull(header);

        EmptyStatePanel empty = new EmptyStatePanel("🎬", "No Shows Available", "Please check back later.", "Refresh", () -> {});
        assertNotNull(empty);
    }

    @Test
    @DisplayName("LoginFrame instantiates with default dimensions and layout")
    void testLoginFrameInstantiation() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return; // Skip top-level native JFrame creation on headless CI runners
        }
        LoginFrame login = new LoginFrame("127.0.0.1", 8080);
        assertNotNull(login);
        assertTrue(login.getWidth() >= 800);
        assertTrue(login.getHeight() >= 500);
        login.dispose();
    }
}
