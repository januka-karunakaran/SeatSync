package com.seatsync.ui.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Modern typography hierarchy for SeatSync desktop application.
 * Selects Segoe UI, Inter, or San Francisco with safe system fallbacks.
 */
public final class UIFonts {

    private static final String FONT_FAMILY;

    static {
        Set<String> available = new HashSet<>(
            Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
        );
        if (available.contains("Segoe UI")) {
            FONT_FAMILY = "Segoe UI";
        } else if (available.contains("Inter")) {
            FONT_FAMILY = "Inter";
        } else if (available.contains("Helvetica Neue")) {
            FONT_FAMILY = "Helvetica Neue";
        } else if (available.contains("Arial")) {
            FONT_FAMILY = "Arial";
        } else {
            FONT_FAMILY = Font.SANS_SERIF;
        }
    }

    private UIFonts() {
        // utility class
    }

    public static final Font HERO_TITLE   = new Font(FONT_FAMILY, Font.BOLD, 28);
    public static final Font TITLE_LARGE  = new Font(FONT_FAMILY, Font.BOLD, 22);
    public static final Font TITLE_MEDIUM = new Font(FONT_FAMILY, Font.BOLD, 18);
    public static final Font TITLE_SMALL  = new Font(FONT_FAMILY, Font.BOLD, 15);

    public static final Font SUBHEADER    = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font BODY_BOLD    = new Font(FONT_FAMILY, Font.BOLD, 13);
    public static final Font BODY         = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font BODY_SMALL   = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font CAPTION      = new Font(FONT_FAMILY, Font.PLAIN, 11);
    public static final Font BADGE        = new Font(FONT_FAMILY, Font.BOLD, 10);
    public static final Font BUTTON       = new Font(FONT_FAMILY, Font.BOLD, 13);
    public static final Font SEAT_LABEL   = new Font(FONT_FAMILY, Font.BOLD, 11);

    public static Font custom(int style, float size) {
        return new Font(FONT_FAMILY, style, (int) size);
    }
}
