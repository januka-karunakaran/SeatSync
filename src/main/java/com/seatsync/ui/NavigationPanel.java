package com.seatsync.ui;

import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Modern side navigation for the SeatSync customer and administration workspaces.
 * Features branded cinema header, distinct section highlights, top-aligned menu layout,
 * and responsive navigation callbacks.
 */
@SuppressWarnings({"serial", "this-escape"})
public final class NavigationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color SELECTED = new Color(21, 101, 192);
    private static final Color NORMAL = new Color(42, 55, 70);
    private static final String[] PAGES = {"Home", "Movies", "Shows", "Seat Map", "My Tickets", "Waitlist", "Admin"};

    private final Map<String, ThemedButton> buttons = new LinkedHashMap<>();
    private final Consumer<String> onNavigate;
    private String selectedPage = "Home";

    public NavigationPanel(Consumer<String> onNavigate) {
        this.onNavigate = Objects.requireNonNull(onNavigate, "onNavigate");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIColors.DEEP_CHARCOAL);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 16, 14));
        setPreferredSize(new Dimension(215, 0));

        // Top gap before brand header
        add(Box.createVerticalStrut(6));

        // Brand Header (Logo + Title)
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandPanel.setOpaque(false);
        brandPanel.setAlignmentX(LEFT_ALIGNMENT);
        brandPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        brandPanel.setPreferredSize(new Dimension(185, 38));

        JLabel logoLabel = new JLabel(new ImageIcon(ImageResourceManager.getSeatSyncLogo(30, 30)));
        JLabel brandName = new JLabel("SeatSync");
        brandName.setFont(UIFonts.TITLE_MEDIUM);
        brandName.setForeground(UIColors.PURE_WHITE);
        brandPanel.add(logoLabel);
        brandPanel.add(brandName);

        add(brandPanel);
        add(Box.createVerticalStrut(22)); // Clean gap between logo and navigation items

        // Subtle section header
        JLabel navHeader = new JLabel("MENU");
        navHeader.setFont(UIFonts.custom(java.awt.Font.BOLD, 10));
        navHeader.setForeground(new Color(0x8A, 0x88, 0x96));
        navHeader.setAlignmentX(LEFT_ALIGNMENT);
        navHeader.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 0));
        add(navHeader);

        for (String page : PAGES) {
            ThemedButton button = new ThemedButton(page, NORMAL, Color.WHITE);
            button.setFont(UIFonts.BODY_BOLD);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            button.setPreferredSize(new Dimension(185, 40));
            button.setHorizontalAlignment(ThemedButton.LEFT);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(NORMAL.darker(), 1),
                BorderFactory.createEmptyBorder(0, 14, 0, 8)
            ));
            button.addActionListener(event -> navigate(page));
            buttons.put(page, button);
            add(button);
            add(Box.createVerticalStrut(6));
        }

        // Vertical glue at the bottom pushes all navigation buttons to the top
        add(Box.createVerticalGlue());
        updateSelection();
    }

    public void navigate(String page) {
        if (!buttons.containsKey(page)) {
            throw new IllegalArgumentException("Unknown navigation page");
        }
        selectedPage = page;
        updateSelection();
        onNavigate.accept(page);
    }

    public String getSelectedPage() {
        return selectedPage;
    }

    public ThemedButton getNavigationButton(String page) {
        return buttons.get(page);
    }

    private void updateSelection() {
        buttons.forEach((page, button) ->
            button.setTheme(page.equals(selectedPage) ? SELECTED : NORMAL, Color.WHITE));
    }
}
