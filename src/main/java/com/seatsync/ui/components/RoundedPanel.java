package com.seatsync.ui.components;

import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UISpacing;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Modern rounded container panel supporting custom background, borders, and drop shadow.
 */
@SuppressWarnings({"serial", "this-escape"})
public class RoundedPanel extends JPanel {

    private int cornerRadius;
    private Color backgroundColor;
    private Color borderColor;
    private int borderWidth;
    private boolean drawShadow;

    public RoundedPanel() {
        this(UISpacing.RADIUS_MEDIUM, UIColors.PURE_WHITE);
    }

    public RoundedPanel(int cornerRadius, Color backgroundColor) {
        this(cornerRadius, backgroundColor, null, 0, false);
    }

    public RoundedPanel(int cornerRadius, Color backgroundColor, Color borderColor, int borderWidth) {
        this(cornerRadius, backgroundColor, borderColor, borderWidth, false);
    }

    public RoundedPanel(int cornerRadius, Color backgroundColor, Color borderColor, int borderWidth, boolean drawShadow) {
        super();
        this.cornerRadius = cornerRadius;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        this.drawShadow = drawShadow;
        setOpaque(false);
    }

    public RoundedPanel(LayoutManager layout, int cornerRadius, Color backgroundColor) {
        super(layout);
        this.cornerRadius = cornerRadius;
        this.backgroundColor = backgroundColor;
        setOpaque(false);
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    public void setPanelBackground(Color color) {
        this.backgroundColor = color;
        repaint();
    }

    public void setBorderColor(Color color, int width) {
        this.borderColor = color;
        this.borderWidth = width;
        repaint();
    }

    public void setDrawShadow(boolean drawShadow) {
        this.drawShadow = drawShadow;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        SeatSyncTheme.enableAntiAliasing(g2);

        int shadowOffset = drawShadow ? 3 : 0;
        int width = getWidth() - (drawShadow ? 6 : 1);
        int height = getHeight() - (drawShadow ? 6 : 1);
        int x = drawShadow ? 3 : 0;
        int y = drawShadow ? 2 : 0;

        if (drawShadow) {
            g2.setColor(new Color(0, 0, 0, 15));
            g2.fillRoundRect(x - 1, y + 2, width + 2, height + 2, cornerRadius + 2, cornerRadius + 2);
            g2.setColor(new Color(0, 0, 0, 25));
            g2.fillRoundRect(x, y + 1, width, height, cornerRadius, cornerRadius);
        }

        // Fill background
        if (backgroundColor != null) {
            g2.setColor(backgroundColor);
            g2.fillRoundRect(x, y, width, height, cornerRadius, cornerRadius);
        }

        // Stroke border
        if (borderColor != null && borderWidth > 0) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderWidth));
            int inset = borderWidth / 2;
            g2.drawRoundRect(x + inset, y + inset, width - borderWidth, height - borderWidth, cornerRadius, cornerRadius);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
