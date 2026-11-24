package com.seatsync.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Side navigation for the customer and administration workspaces. */
@SuppressWarnings("serial")
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
        setBackground(new Color(30, 41, 59));
        setBorder(BorderFactory.createEmptyBorder(16, 10, 16, 10));
        setPreferredSize(new Dimension(174, 0));

        for (String page : PAGES) {
            ThemedButton button = new ThemedButton(page, NORMAL, Color.WHITE);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            button.setHorizontalAlignment(ThemedButton.LEFT);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(NORMAL.darker(), 1),
                BorderFactory.createEmptyBorder(0, 14, 0, 8)
            ));
            button.addActionListener(event -> navigate(page));
            buttons.put(page, button);
            add(button);
            add(Box.createVerticalStrut(5));
        }
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
