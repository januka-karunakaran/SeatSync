package com.seatsync.ui;

import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.theme.SeatSyncTheme;
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
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Side navigation grouped as a booking path, then the visitor's own tickets.
 * Page keys stay stable for the card layout; button text is the visitor-facing label.
 */
@SuppressWarnings({"serial", "this-escape"})
public final class NavigationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color SELECTED = UIColors.ACCENT;
    private static final Color NORMAL = new Color(0x2A, 0x22, 0x2C);
    private static final Color SIDEBAR = new Color(0x1A, 0x12, 0x18);

    private static final String[][] SECTIONS = {
        {"BOOK A TICKET", "Home", "Movies", "Shows", "Seat Map"},
        {"YOUR VISIT", "My Tickets", "Waitlist"},
        {"STAFF", "Admin"}
    };

    private static final Map<String, String> LABELS = Map.of(
        "Home", "Home",
        "Movies", "1   Movies",
        "Shows", "2   Showtimes",
        "Seat Map", "3   Choose seats",
        "My Tickets", "My tickets",
        "Waitlist", "Waitlist",
        "Admin", "Staff tools"
    );

    private final Map<String, ThemedButton> buttons = new LinkedHashMap<>();
    private final Map<String, String> pageSection = new LinkedHashMap<>();
    private final Map<String, JLabel> sectionHeaders = new LinkedHashMap<>();
    private final Consumer<String> onNavigate;
    private String selectedPage = "Home";

    public NavigationPanel(Consumer<String> onNavigate) {
        this.onNavigate = Objects.requireNonNull(onNavigate, "onNavigate");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(SIDEBAR);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 16, 14));
        setPreferredSize(new Dimension(230, 0));

        add(Box.createVerticalStrut(6));

        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandPanel.setOpaque(false);
        brandPanel.setAlignmentX(LEFT_ALIGNMENT);
        brandPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        brandPanel.setPreferredSize(new Dimension(200, 38));

        JLabel logoLabel = new JLabel(new ImageIcon(ImageResourceManager.getSeatSyncLogo(30, 30)));
        JLabel brandName = new JLabel("SeatSync");
        brandName.setFont(UIFonts.TITLE_MEDIUM);
        brandName.setForeground(UIColors.PURE_WHITE);
        brandPanel.add(logoLabel);
        brandPanel.add(brandName);
        add(brandPanel);
        add(Box.createVerticalStrut(18));

        for (String[] section : SECTIONS) {
            JLabel header = sectionLabel(section[0]);
            sectionHeaders.put(section[0], header);
            add(header);
            for (int index = 1; index < section.length; index++) {
                String page = section[index];
                ThemedButton button = new NavButton(LABELS.get(page), NORMAL, new Color(0xF8, 0xEC, 0xF0));
                button.setFont(UIFonts.BODY_BOLD);
                button.setAlignmentX(LEFT_ALIGNMENT);
                button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
                button.setPreferredSize(new Dimension(200, 42));
                button.setHorizontalAlignment(ThemedButton.LEFT);
                button.addActionListener(event -> navigate(page));
                buttons.put(page, button);
                pageSection.put(page, section[0]);
                add(button);
                add(Box.createVerticalStrut(6));
            }
            add(Box.createVerticalStrut(8));
        }

        add(Box.createVerticalGlue());
        updateSelection();
    }

    public void select(String page) {
        if (!buttons.containsKey(page)) {
            return;
        }
        selectedPage = page;
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

    public void setPageEnabled(String page, boolean enabled) {
        ThemedButton button = buttons.get(page);
        if (button != null) {
            button.setEnabled(enabled);
            button.setToolTipText(enabled ? null : "Choose a film, then a showtime, before choosing seats");
        }
    }

    public void setPageVisible(String page, boolean visible) {
        ThemedButton button = buttons.get(page);
        if (button != null) {
            button.setVisible(visible);
        }
        sectionHeaders.forEach((section, header) -> {
            boolean anyVisible = buttons.entrySet().stream()
                .anyMatch(entry -> section.equals(pageSection.get(entry.getKey())) && entry.getValue().isVisible());
            header.setVisible(anyVisible);
        });
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIFonts.custom(java.awt.Font.BOLD, 10));
        label.setForeground(new Color(0x8A, 0x88, 0x96));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 0));
        return label;
    }

    private void updateSelection() {
        buttons.forEach((page, button) ->
            button.setTheme(page.equals(selectedPage) ? SELECTED : NORMAL,
                page.equals(selectedPage) ? Color.WHITE : new Color(0xF8, 0xEC, 0xF0)));
    }

    /**
     * Borderless rounded navigation item. Background color stays readable by tests.
     */
    private static final class NavButton extends ThemedButton {
        private NavButton(String text, Color background, Color foreground) {
            super(text, background, foreground);
            soften();
        }

        @Override
        public void setTheme(Color background, Color foreground) {
            super.setTheme(background, foreground);
            soften();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            soften();
        }

        private void soften() {
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            SeatSyncTheme.enableAntiAliasing(g2);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 3, getWidth(), getHeight() - 6, 14, 14);
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            int textY = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
            g2.drawString(getText() == null ? "" : getText(), 16, textY);
            g2.dispose();
        }
    }
}
