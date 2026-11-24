package com.seatsync.ui.components;

import com.seatsync.ui.ThemedButton;
import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import com.seatsync.ui.theme.UISpacing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Modern custom-painted Swing button supporting multiple visual variants,
 * smooth hover states, rounded corners, and consistent cinematic styling.
 */
@SuppressWarnings({"serial", "this-escape"})
public class ModernButton extends ThemedButton {

    public enum Variant {
        PRIMARY,   // Lavender accent button with dark purple text
        SECONDARY, // Dark charcoal button with light text
        ACCENT,    // Deep purple button with white text
        OUTLINE,   // Transparent button with light border
        DANGER     // Red button with white text
    }

    private Variant variant;
    private int cornerRadius;
    private boolean isHovered;
    private boolean isPressed;
    private Color customBg;
    private Color customFg;

    public ModernButton(String text) {
        this(text, Variant.PRIMARY);
    }

    public ModernButton(String text, Variant variant) {
        super(text);
        this.variant = variant;
        this.cornerRadius = UISpacing.RADIUS_MEDIUM;
        setFont(UIFonts.BUTTON);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(8, 18, 8, 18));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    public ModernButton(String text, Color bg, Color fg) {
        this(text, Variant.PRIMARY);
        this.customBg = bg;
        this.customFg = fg;
    }

    public void setVariant(Variant variant) {
        this.variant = variant;
        repaint();
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        // Ensure comfortable modern touch target height
        return new Dimension(Math.max(size.width, 100), Math.max(size.height, 38));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        SeatSyncTheme.enableAntiAliasing(g2);

        int width = getWidth();
        int height = getHeight();

        Color bg;
        Color fg;
        Color border = null;

        if (!isEnabled()) {
            bg = new Color(0xEE, 0xEE, 0xF2);
            fg = new Color(0x9E, 0x9D, 0xA8);
            border = new Color(0xDC, 0xDA, 0xE4);
        } else if (customBg != null) {
            bg = isPressed ? customBg.darker() : (isHovered ? customBg.brighter() : customBg);
            fg = customFg != null ? customFg : UIColors.TEXT_WHITE;
        } else {
            switch (variant) {
                case PRIMARY -> {
                    bg = isPressed ? UIColors.LAVENDER_DARK : (isHovered ? UIColors.LAVENDER_HOVER : UIColors.LAVENDER_PRIMARY);
                    fg = UIColors.DEEP_PURPLE;
                    border = UIColors.LAVENDER_DARK;
                }
                case SECONDARY -> {
                    bg = isPressed ? UIColors.CINEMATIC_BLACK : (isHovered ? UIColors.SURFACE_DARK : UIColors.DEEP_CHARCOAL);
                    fg = UIColors.TEXT_LIGHT;
                    border = UIColors.DARK_BORDER;
                }
                case ACCENT -> {
                    bg = isPressed ? UIColors.DEEP_PURPLE.darker() : (isHovered ? UIColors.ACCENT_PURPLE : UIColors.DEEP_PURPLE);
                    fg = UIColors.PURE_WHITE;
                }
                case OUTLINE -> {
                    bg = isPressed ? new Color(0, 0, 0, 15) : (isHovered ? new Color(0, 0, 0, 8) : new Color(0, 0, 0, 0));
                    fg = UIColors.TEXT_DARK;
                    border = UIColors.LIGHT_BORDER;
                }
                case DANGER -> {
                    bg = isPressed ? UIColors.ERROR_RED.darker() : (isHovered ? new Color(0xE8, 0x5D, 0x66) : UIColors.ERROR_RED);
                    fg = UIColors.PURE_WHITE;
                }
                default -> {
                    bg = UIColors.LAVENDER_PRIMARY;
                    fg = UIColors.DEEP_PURPLE;
                }
            }
        }

        // Fill background
        if (bg.getAlpha() > 0) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius);
        }

        // Draw border if defined
        if (border != null) {
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius);
        }

        // Draw text
        g2.setFont(getFont());
        g2.setColor(fg);
        String text = getText();
        if (text != null && !text.isEmpty()) {
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int textX = (width - fm.stringWidth(text)) / 2;
            int textY = (height + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, textX, textY);
        }

        g2.dispose();
    }
}
