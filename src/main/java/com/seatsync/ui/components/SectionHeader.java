package com.seatsync.ui.components;

import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

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
            textContainer.add(Box.createVerticalStrut(2));
            lblSubtitle = new JLabel(subtitle);
            lblSubtitle.setFont(UIFonts.BODY_SMALL);
            lblSubtitle.setForeground(UIColors.TEXT_SECONDARY);
            lblSubtitle.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 12));
            textContainer.add(lblSubtitle);
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
