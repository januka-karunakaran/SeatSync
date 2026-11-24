package com.seatsync.ui.components;

import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import com.seatsync.ui.theme.UISpacing;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Modern pill badge indicating functional or operational status with a colored dot.
 */
@SuppressWarnings({"serial", "this-escape"})
public class StatusBadge extends JPanel {

    public enum Type {
        SUCCESS(UIColors.SUCCESS_GREEN, UIColors.SUCCESS_BG),
        WARNING(UIColors.WARNING_AMBER, UIColors.WARNING_BG),
        ERROR(UIColors.ERROR_RED, UIColors.ERROR_BG),
        INFO(UIColors.INFO_BLUE, UIColors.INFO_BG),
        NEUTRAL(UIColors.TEXT_MUTED, UIColors.SURFACE_LIGHT);

        final Color primary;
        final Color background;

        Type(Color primary, Color background) {
            this.primary = primary;
            this.background = background;
        }
    }

    private Type type;
    private final JLabel label;

    public StatusBadge(String text, Type type) {
        super(new FlowLayout(FlowLayout.CENTER, 6, 4));
        this.type = type;
        setOpaque(false);

        label = new JLabel(text);
        label.setFont(UIFonts.BADGE);
        label.setForeground(type.primary);
        add(label);
    }

    public void setStatus(String text, Type type) {
        this.type = type;
        this.label.setText(text);
        this.label.setForeground(type.primary);
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        SeatSyncTheme.enableAntiAliasing(g2);

        int width = getWidth();
        int height = getHeight();

        // Draw pill background
        g2.setColor(type.background);
        g2.fillRoundRect(0, 0, width, height, height, height);

        // Draw subtle border
        g2.setColor(new Color(type.primary.getRed(), type.primary.getGreen(), type.primary.getBlue(), 60));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, width - 1, height - 1, height, height);

        // Draw glowing status indicator dot
        int dotSize = 6;
        int dotX = 8;
        int dotY = (height - dotSize) / 2;
        g2.setColor(type.primary);
        g2.fillOval(dotX, dotY, dotSize, dotSize);

        g2.dispose();
        super.paintComponent(g);
    }
}
