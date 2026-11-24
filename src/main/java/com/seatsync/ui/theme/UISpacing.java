package com.seatsync.ui.theme;

import java.awt.Insets;

/**
 * Standard spacing, corner radii, and padding constants for the SeatSync UI.
 */
public final class UISpacing {

    private UISpacing() {
        // utility class
    }

    // Corner Radii
    public static final int RADIUS_TINY   = 4;
    public static final int RADIUS_SMALL  = 8;
    public static final int RADIUS_MEDIUM = 12;
    public static final int RADIUS_LARGE  = 16;
    public static final int RADIUS_XLARGE = 22;
    public static final int RADIUS_PILL   = 999;

    // Standard Insets
    public static final Insets PADDING_CARD       = new Insets(18, 20, 18, 20);
    public static final Insets PADDING_CARD_COMPACT= new Insets(12, 14, 12, 14);
    public static final Insets PADDING_BUTTON     = new Insets(10, 20, 10, 20);
    public static final Insets PADDING_BUTTON_SM  = new Insets(6, 12, 6, 12);
    public static final Insets PADDING_INPUT      = new Insets(8, 12, 8, 12);
}
