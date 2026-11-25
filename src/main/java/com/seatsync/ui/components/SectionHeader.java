package com.seatsync.ui.components;

import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Consistent section header with title, subtitle, and optional right-aligned action widget.
 */
@SuppressWarnings({"serial", "this-escape"})
public class SectionHeader extends JPanel {

    private final JLabel lblTitle;
    private final JLabel lblSubtitle;
    private final JPanel actionContainer;

    public SectionHeader(String title, String subtitle) {
        this(title, subtitle, null);
    }

    public SectionHeader(String title, String subtitle, JComponent rightAction) {
        super(new BorderLayout(12, 4));
        setOpaque(false);

        JPanel textContainer = new JPanel();
        textContainer.setLayout(new BoxLayout(textContainer, BoxLayout.Y_AXIS));
        textContainer.setOpaque(false);

        lblTitle = new JLabel(title);
        lblTitle.setFont(UIFonts.TITLE_MEDIUM);
        lblTitle.setForeground(UIColors.TEXT_DARK);
        lblTitle.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 12));
        textContainer.add(lblTitle);

        if (subtitle != null && !subtitle.isBlank()) {
            textContainer.add(Box.createVerticalStrut(8));
            lblSubtitle = new JLabel(subtitle);
            lblSubtitle.setFont(UIFonts.TITLE_SMALL);
            lblSubtitle.setForeground(UIColors.ACCENT_DARK);
            JPanel hint = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xFB, 0xE4, 0xEA));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                    g2.setColor(UIColors.ACCENT);
                    g2.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4);
                    g2.dispose();
                    super.paintComponent(g);
                }

                @Override
                public Dimension getMaximumSize() {
                    Dimension preferred = getPreferredSize();
                    return new Dimension(Integer.MAX_VALUE, preferred.height);
                }
            };
            hint.setOpaque(false);
            hint.setAlignmentX(LEFT_ALIGNMENT);
            hint.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 14));
            hint.add(lblSubtitle, BorderLayout.CENTER);
            textContainer.add(hint);
        } else {
            lblSubtitle = null;
        }

        add(textContainer, BorderLayout.CENTER);

        actionContainer = new JPanel(new BorderLayout());
        actionContainer.setOpaque(false);
        if (rightAction != null) {
            actionContainer.add(rightAction, BorderLayout.CENTER);
        }
        add(actionContainer, BorderLayout.EAST);
    }

    public void setRightAction(JComponent component) {
        actionContainer.removeAll();
        if (component != null) {
            actionContainer.add(component, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }
}
