package com.seatsync.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.border.AbstractBorder;

/**
 * Soft rounded outline used by text fields so they match pill buttons.
 */
public class RoundedLineBorder extends AbstractBorder {

    private static final long serialVersionUID = 1L;

    private final Color color;
    private final int radius;
    private final Insets insets;

    public RoundedLineBorder(Color color, int radius, int padding) {
        this.color = color;
        this.radius = radius;
        this.insets = new Insets(padding, padding + 4, padding, padding + 4);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(insets.top, insets.left, insets.bottom, insets.right);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets target) {
        target.top = insets.top;
        target.left = insets.left;
        target.bottom = insets.bottom;
        target.right = insets.right;
        return target;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        g2.dispose();
    }
}
