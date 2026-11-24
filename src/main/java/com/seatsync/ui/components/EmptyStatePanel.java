package com.seatsync.ui.components;

import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import com.seatsync.ui.theme.UISpacing;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;

/**
 * Informative empty state card with icon, title, message, and optional call to action.
 */
@SuppressWarnings({"serial", "this-escape"})
public class EmptyStatePanel extends RoundedPanel {

    public EmptyStatePanel(String iconText, String title, String description) {
        this(iconText, title, description, null, null);
    }

    public EmptyStatePanel(String title, String description) {
        this("🔒", title, description, null, null);
    }

    public EmptyStatePanel(String iconText, String title, String description, String actionText, Runnable onAction) {
        super(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(30, 20, 30, 20));

        JLabel iconLabel = new JLabel(iconText, SwingConstants.CENTER);
        iconLabel.setFont(UIFonts.custom(java.awt.Font.BOLD, 36));
        iconLabel.setForeground(UIColors.MUTED_PURPLE);
        iconLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UIFonts.TITLE_MEDIUM);
        titleLabel.setForeground(UIColors.TEXT_DARK);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel descLabel = new JLabel("<html><center>" + description + "</center></html>", SwingConstants.CENTER);
        descLabel.setFont(UIFonts.BODY);
        descLabel.setForeground(UIColors.TEXT_SECONDARY);
        descLabel.setAlignmentX(CENTER_ALIGNMENT);
        descLabel.setMaximumSize(new Dimension(380, 60));

        add(iconLabel);
        add(Box.createVerticalStrut(12));
        add(titleLabel);
        add(Box.createVerticalStrut(6));
        add(descLabel);

        if (actionText != null && onAction != null) {
            add(Box.createVerticalStrut(16));
            ModernButton btnAction = new ModernButton(actionText, ModernButton.Variant.PRIMARY);
            btnAction.setAlignmentX(CENTER_ALIGNMENT);
            btnAction.addActionListener(e -> onAction.run());
            add(btnAction);
        }
    }
}
