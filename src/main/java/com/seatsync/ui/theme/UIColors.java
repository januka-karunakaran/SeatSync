package com.seatsync.ui.theme;

import java.awt.Color;

/**
 * Design system color tokens for SeatSync.
 * Combines modern minimalist surfaces with cinematic dark & lavender accents.
 */
public final class UIColors {

    private UIColors() {
        // utility class
    }

    // Cinematic Dark Surfaces (Netflix-inspired depth)
    public static final Color CINEMATIC_BLACK  = new Color(0x10, 0x10, 0x14); // #101014
    public static final Color DEEP_CHARCOAL    = new Color(0x1B, 0x1B, 0x23); // #1B1B23
    public static final Color SURFACE_DARK     = new Color(0x22, 0x22, 0x2C); // #22222C
    public static final Color SURFACE_CARD_DARK= new Color(0x29, 0x29, 0x36); // #292936
    public static final Color DARK_BORDER      = new Color(0x36, 0x35, 0x47); // #363547

    // Light Surfaces & Accents (FilmTIX-inspired clean lightness)
    public static final Color SOFT_WHITE       = new Color(0xFA, 0xF9, 0xFC); // #FAF9FC
    public static final Color PURE_WHITE       = new Color(0xFF, 0xFF, 0xFF); // #FFFFFF
    public static final Color SURFACE_LIGHT    = new Color(0xF2, 0xF1, 0xF7); // #F2F1F7
    public static final Color LIGHT_BORDER     = new Color(0xE8, 0xE6, 0xED); // #E8E6ED
    public static final Color BORDER_SUBTLE    = new Color(0xD8, 0xD5, 0xE3); // #D8D5E3

    // Brand & Accent Colors (Lavender & Purple tones)
    public static final Color LAVENDER_PRIMARY = new Color(0xD8, 0xD0, 0xF2); // #D8D0F2
    public static final Color LAVENDER_HOVER   = new Color(0xCE, 0xC4, 0xED); // #CEC4ED
    public static final Color LAVENDER_DARK    = new Color(0xA9, 0x9A, 0xCD); // #A99ACD
    public static final Color MUTED_PURPLE     = new Color(0x82, 0x74, 0xAB); // #8274AB
    public static final Color DEEP_PURPLE      = new Color(0x56, 0x48, 0x82); // #564882
    public static final Color ACCENT_PURPLE    = new Color(0x6C, 0x5C, 0x9E); // #6C5C9E

    // Typography Colors
    public static final Color TEXT_DARK        = new Color(0x25, 0x23, 0x2B); // #25232B
    public static final Color TEXT_MUTED       = new Color(0x52, 0x50, 0x5C); // #52505C
    public static final Color TEXT_SECONDARY   = new Color(0x77, 0x75, 0x80); // #777580
    public static final Color TEXT_LIGHT       = new Color(0xF0, 0xEF, 0xF5); // #F0EFF5
    public static final Color TEXT_WHITE       = new Color(0xFF, 0xFF, 0xFF); // #FFFFFF

    // Functional State Colors
    public static final Color SUCCESS_GREEN    = new Color(0x27, 0x83, 0x4B); // #27834B
    public static final Color SUCCESS_BG       = new Color(0xEB, 0xF6, 0xEE); // #EBF6EE
    public static final Color WARNING_AMBER    = new Color(0xE6, 0xA2, 0x3C); // #E6A23C
    public static final Color WARNING_BG       = new Color(0xFD, 0xF5, 0xE8); // #FDF5E8
    public static final Color ERROR_RED        = new Color(0xD8, 0x4C, 0x55); // #D84C55
    public static final Color ERROR_BG         = new Color(0xFC, 0xEA, 0xEB); // #FCEAEB
    public static final Color INFO_BLUE        = new Color(0x2D, 0x72, 0xD9); // #2D72D9
    public static final Color INFO_BG          = new Color(0xEB, 0xF2, 0xFC); // #EBF2FC

    // Seat Map State Colors
    public static final Color SEAT_AVAILABLE   = new Color(0x2B, 0x2A, 0x36); // Deep Charcoal #2B2A36
    public static final Color SEAT_SELECTED    = new Color(0x82, 0x74, 0xAB); // Muted Purple #8274AB
    public static final Color SEAT_BOOKED      = new Color(0xDD, 0xDB, 0xE5); // Light Gray disabled #DDDBE5
    public static final Color SEAT_MY_BOOKING  = new Color(0x43, 0x79, 0xB8); // Elegant Navy/Blue #4379B8
    public static final Color SEAT_HELD        = new Color(0xE6, 0xA2, 0x3C); // Warning Amber #E6A23C
}
