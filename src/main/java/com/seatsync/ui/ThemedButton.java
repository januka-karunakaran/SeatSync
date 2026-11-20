package com.seatsync.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;

/**
 * Custom high-contrast Swing button that overrides default Look & Feel limitations
 * (such as Windows native button white-on-white rendering bug).
 * Guarantees explicit background and foreground styling across all platforms and states.
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
public class ThemedButton extends JButton {

    private static final long serialVersionUID = 1L;

    private static final Color DISABLED_BG = new Color(224, 224, 224);
    private static final Color DISABLED_FG = new Color(97, 97, 97);
    private static final Color DISABLED_BORDER = new Color(189, 189, 189);

    private Color normalBg;
    private Color normalFg;

    public ThemedButton(String text, Color normalBg, Color normalFg) {
        super(text);
        this.normalBg = normalBg;
        this.normalFg = normalFg;
        setUI(new BasicButtonUI());
        setOpaque(true);
        setContentAreaFilled(true);
        setFocusPainted(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(6, 14, 6, 14));
        applyStyle();
    }

    public ThemedButton(String text) {
        this(text, new Color(21, 101, 192), Color.WHITE);
    }

    public void setTheme(Color bg, Color fg) {
        this.normalBg = bg;
        this.normalFg = fg;
        applyStyle();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        applyStyle();
    }

    private void applyStyle() {
        setUI(new BasicButtonUI());
        setOpaque(true);
        setContentAreaFilled(true);
        if (isEnabled()) {
            setBackground(normalBg != null ? normalBg : new Color(21, 101, 192));
            setForeground(normalFg != null ? normalFg : Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(normalBg != null ? normalBg.darker() : Color.GRAY, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
            ));
        } else {
            setBackground(DISABLED_BG);
            setForeground(DISABLED_FG);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DISABLED_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
            ));
        }
        repaint();
    }
}
