package com.seatsync.ui;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Interactive Swing presentation component displaying the 40-seat cinema grid (A1-E8).
 * Provides visual cues for available, booked, and user-owned seats, and handles seat selection.
 * <p>
 * Thread-safe: UI state mutations are guaranteed to execute on the Swing Event Dispatch Thread (EDT).
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
public class SeatGridPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // Visual Palette
    private static final Color COLOR_AVAILABLE = new Color(46, 125, 50);     // Forest Green
    private static final Color COLOR_BOOKED = new Color(198, 40, 40);        // Deep Red
    private static final Color COLOR_MY_BOOKING = new Color(25, 118, 210);    // Royal Blue
    private static final Color COLOR_SELECTED = new Color(255, 179, 0);      // Amber Gold
    private static final Color COLOR_SCREEN_BG = new Color(55, 71, 79);      // Slate Charcoal
    private static final Font FONT_SEAT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_SCREEN = new Font("Segoe UI", Font.BOLD, 14);

    private final Map<String, JButton> seatButtons = new LinkedHashMap<>(40);
    private final Map<String, SeatView> seatViews = new LinkedHashMap<>(40);
    private final List<Consumer<String>> selectionListeners = new CopyOnWriteArrayList<>();

    private String currentUserId;
    private String selectedSeatId;

    public SeatGridPanel() {
        setLayout(new BorderLayout(10, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        initComponentLayout();
    }

    private void initComponentLayout() {
        // 1. Stage / Screen Indicator at the top
        JPanel screenPanel = new JPanel(new BorderLayout());
        screenPanel.setBackground(COLOR_SCREEN_BG);
        screenPanel.setPreferredSize(new Dimension(500, 32));
        screenPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));

        JLabel screenLabel = new JLabel("--- CINEMA SCREEN / STAGE ---", SwingConstants.CENTER);
        screenLabel.setForeground(Color.WHITE);
        screenLabel.setFont(FONT_SCREEN);
        screenPanel.add(screenLabel, BorderLayout.CENTER);
        add(screenPanel, BorderLayout.NORTH);

        // 2. 5x8 Grid of Seats (A1 to E8)
        JPanel grid = new JPanel(new GridLayout(5, 8, 8, 8));
        grid.setOpaque(false);

        for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
            JButton btn = createSeatButton(seatId);
            seatButtons.put(seatId, btn);
            seatViews.put(seatId, SeatView.available(seatId));
            grid.add(btn);
        }
        add(grid, BorderLayout.CENTER);

        // 3. Legend Panel at bottom
        JPanel legendPanel = createLegendPanel();
        add(legendPanel, BorderLayout.SOUTH);
    }

    private JButton createSeatButton(String seatId) {
        JButton btn = new JButton(seatId);
        btn.setFont(FONT_SEAT);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(6, 6, 6, 6));
        btn.setPreferredSize(new Dimension(56, 44));
        btn.setBackground(COLOR_AVAILABLE);
        btn.setForeground(Color.WHITE);
        btn.setToolTipText("Seat " + seatId + " - Available");

        btn.addActionListener(e -> selectSeat(seatId));
        return btn;
    }

    private JPanel createLegendPanel() {
        JPanel legend = new JPanel(new GridLayout(1, 4, 10, 0));
        legend.setOpaque(false);
        legend.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        legend.add(createLegendBadge("Available", COLOR_AVAILABLE, Color.WHITE));
        legend.add(createLegendBadge("My Booking", COLOR_MY_BOOKING, Color.WHITE));
        legend.add(createLegendBadge("Booked", COLOR_BOOKED, Color.WHITE));
        legend.add(createLegendBadge("Selected", COLOR_SELECTED, Color.BLACK));
        return legend;
    }

    private JPanel createLegendBadge(String text, Color bg, Color fg) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel badge = new JLabel(text, SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        panel.add(badge, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Sets the authenticated user ID so personal bookings can be highlighted.
     *
     * @param userId Authenticated user identifier (e.g. "USR-101")
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        refreshAllSeatStyles();
    }

    /**
     * Selects a seat and alerts registered selection listeners.
     *
     * @param seatId Identifier of the chosen seat
     */
    public void selectSeat(String seatId) {
        this.selectedSeatId = seatId;
        refreshAllSeatStyles();
        for (Consumer<String> listener : selectionListeners) {
            listener.accept(seatId);
        }
    }

    /**
     * Clears any active seat selection.
     */
    public void clearSelection() {
        this.selectedSeatId = null;
        refreshAllSeatStyles();
        for (Consumer<String> listener : selectionListeners) {
            listener.accept(null);
        }
    }

    /**
     * Updates all seat states from a server snapshot. Safe to call from any thread.
     *
     * @param seats List of updated seat views
     */
    public void updateSeats(List<SeatView> seats) {
        if (seats == null) {
            return;
        }

        for (SeatView seat : seats) {
            seatViews.put(seat.seatId(), seat);
        }

        if (SwingUtilities.isEventDispatchThread()) {
            refreshAllSeatStyles();
        } else {
            SwingUtilities.invokeLater(this::refreshAllSeatStyles);
        }
    }

    /**
     * Updates an individual seat's state. Safe to call from any thread.
     *
     * @param seat Updated seat view
     */
    public void updateSingleSeat(SeatView seat) {
        if (seat == null) {
            return;
        }
        updateSeats(Collections.singletonList(seat));
    }

    private void refreshAllSeatStyles() {
        for (Map.Entry<String, JButton> entry : seatButtons.entrySet()) {
            String seatId = entry.getKey();
            JButton btn = entry.getValue();
            SeatView view = seatViews.get(seatId);

            boolean isSelected = seatId.equals(selectedSeatId);
            boolean isBooked = view != null && view.status() == SeatStatus.BOOKED;
            boolean isMine = isBooked && currentUserId != null && currentUserId.equals(view.bookedBy());

            if (isSelected) {
                btn.setBackground(COLOR_SELECTED);
                btn.setForeground(Color.BLACK);
                btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            } else if (isMine) {
                btn.setBackground(COLOR_MY_BOOKING);
                btn.setForeground(Color.WHITE);
                btn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
            } else if (isBooked) {
                btn.setBackground(COLOR_BOOKED);
                btn.setForeground(Color.WHITE);
                btn.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
            } else {
                btn.setBackground(COLOR_AVAILABLE);
                btn.setForeground(Color.WHITE);
                btn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
            }

            // Tooltip and accessibility text
            String ownerText = isMine ? " (You)" : (isBooked && view.bookedBy() != null ? " (" + view.bookedBy() + ")" : "");
            String statusText = isBooked ? "Booked" + ownerText : "Available";
            btn.setToolTipText("Seat " + seatId + " - " + statusText);
        }
    }

    public String getSelectedSeatId() {
        return selectedSeatId;
    }

    public SeatView getSelectedSeatView() {
        return selectedSeatId != null ? seatViews.get(selectedSeatId) : null;
    }

    public SeatView getSeatView(String seatId) {
        return seatViews.get(seatId);
    }

    public void addSelectionListener(Consumer<String> listener) {
        if (listener != null && !selectionListeners.contains(listener)) {
            selectionListeners.add(listener);
        }
    }

    public void removeSelectionListener(Consumer<String> listener) {
        selectionListeners.remove(listener);
    }
}
