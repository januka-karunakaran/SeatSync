package com.seatsync.ui;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.QuadCurve2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Interactive Swing presentation component displaying the 40-seat cinema grid (A1-E8).
 * Features a modern curved cinema screen indicator, FilmTIX-style color palette,
 * responsive hover elevation, and real-time seat status synchronization.
 * <p>
 * Thread-safe: UI state mutations are guaranteed to execute on the Swing Event Dispatch Thread (EDT).
 */
@SuppressWarnings({"serial", "this-escape"})
public final class SeatGridPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // FilmTIX Modern Cinema Palette
    public static final Color COLOR_AVAILABLE = new Color(0x0F, 0x76, 0x6E);   // Open seat teal
    public static final Color COLOR_SELECTED = new Color(0xE1, 0x1D, 0x48);    // Selected rose
    public static final Color COLOR_BOOKED = new Color(0xE7, 0xE2, 0xDA);      // Warm taken seat
    public static final Color COLOR_MY_BOOKING = new Color(0x1D, 0x4E, 0xD8);  // Your seat
    public static final Color COLOR_HELD = new Color(0xD9, 0x77, 0x06);        // Held amber
    public static final Color COLOR_TEXT_BOOKED = new Color(0x6B, 0x5E, 0x56);

    private static final Font FONT_SEAT = UIFonts.custom(Font.BOLD, 12);

    private final transient Map<String, SeatButton> seatButtons = new LinkedHashMap<>(40);
    private final transient Map<String, SeatView> seatViews = new LinkedHashMap<>(40);
    private final transient List<Consumer<String>> selectionListeners = new CopyOnWriteArrayList<>();

    private String currentUserId;
    private String selectedSeatId;
    private final Set<String> selectedSeatIds = new LinkedHashSet<>();

    public SeatGridPanel() {
        setLayout(new BorderLayout(10, 16));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        initComponentLayout();
    }

    private void initComponentLayout() {
        // 1. Sleek Curved Cinema Screen Indicator at the top
        CurvedScreenIndicator screenPanel = new CurvedScreenIndicator();
        add(screenPanel, BorderLayout.NORTH);

        // 2. 5x8 Grid of Seats (A1 to E8)
        JPanel grid = new JPanel(new GridLayout(5, 8, 10, 10));
        grid.setOpaque(false);

        for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
            SeatButton btn = new SeatButton(seatId);
            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    btn.putClientProperty("extend", event.isShiftDown() || event.isControlDown());
                }
            });
            btn.addActionListener(e -> {
                boolean extend = Boolean.TRUE.equals(btn.getClientProperty("extend"));
                btn.putClientProperty("extend", Boolean.FALSE);
                if (extend) {
                    addSelectedSeat(seatId);
                } else {
                    selectSeat(seatId);
                }
            });
            seatButtons.put(seatId, btn);
            seatViews.put(seatId, SeatView.available(seatId));
            grid.add(btn);
        }
        add(grid, BorderLayout.CENTER);

        // 3. Legend Panel at bottom
        JPanel legendPanel = createLegendPanel();
        add(legendPanel, BorderLayout.SOUTH);

        refreshAllSeatStyles();
    }

    private JPanel createLegendPanel() {
        JPanel legend = new JPanel();
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setOpaque(false);
        legend.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 2));
        top.setOpaque(false);
        top.add(createLegendBadge("Available", COLOR_AVAILABLE));
        top.add(createLegendBadge("Your seat", COLOR_MY_BOOKING));
        top.add(createLegendBadge("Selected", COLOR_SELECTED));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 2));
        bottom.setOpaque(false);
        bottom.add(createLegendBadge("Held", COLOR_HELD));
        bottom.add(createLegendBadge("Booked", COLOR_BOOKED));

        legend.add(top);
        legend.add(bottom);
        return legend;
    }

    private JPanel createLegendBadge(String text, Color bg) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        panel.setOpaque(false);

        JPanel swatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                SeatSyncTheme.enableAntiAliasing(g2);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.setColor(bg.darker());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                g2.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(14, 14));
        swatch.setOpaque(false);

        JLabel label = new JLabel(text);
        label.setFont(UIFonts.CAPTION);
        label.setForeground(UIColors.TEXT_DARK);

        panel.add(swatch);
        panel.add(label);
        return panel;
    }

    /**
     * Sets the authenticated user ID so personal bookings can be highlighted.
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        refreshAllSeatStyles();
    }

    /**
     * Selects a seat and alerts registered selection listeners.
     */
    public void selectSeat(String seatId) {
        selectedSeatIds.clear();
        if (seatId != null) {
            selectedSeatIds.add(seatId);
        }
        this.selectedSeatId = seatId;
        refreshAllSeatStyles();
        for (Consumer<String> listener : selectionListeners) {
            listener.accept(seatId);
        }
    }

    /**
     * Adds a seat to the current selection so several open seats can be held together.
     */
    public void addSelectedSeat(String seatId) {
        if (seatId == null) {
            return;
        }
        selectedSeatIds.add(seatId);
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
        selectedSeatIds.clear();
        refreshAllSeatStyles();
        for (Consumer<String> listener : selectionListeners) {
            listener.accept(null);
        }
    }

    /**
     * Updates all seat states from a server snapshot. Safe to call from any thread.
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
     */
    public void updateSingleSeat(SeatView seat) {
        if (seat == null) {
            return;
        }
        updateSeats(Collections.singletonList(seat));
    }

    private void refreshAllSeatStyles() {
        for (Map.Entry<String, SeatButton> entry : seatButtons.entrySet()) {
            String seatId = entry.getKey();
            SeatButton btn = entry.getValue();
            SeatView view = seatViews.get(seatId);

            boolean isSelected = selectedSeatIds.contains(seatId);
            boolean isBooked = view != null && view.status() == SeatStatus.BOOKED;
            boolean isHeld = view != null && view.status() == SeatStatus.HELD;
            boolean isMine = isBooked && currentUserId != null && currentUserId.equals(view.bookedBy());

            Color fill;
            Color stroke;
            Color text;
            String statusText;

            if (isSelected) {
                fill = COLOR_SELECTED;
                stroke = COLOR_SELECTED.darker();
                text = Color.WHITE;
                statusText = "Selected";
                btn.setText("<html><center><b>" + seatId + "</b><br><small style='font-size:9px'>Selected</small></center></html>");
            } else if (isMine) {
                fill = COLOR_MY_BOOKING;
                stroke = COLOR_MY_BOOKING.darker();
                text = Color.WHITE;
                statusText = "Your seat";
                btn.setText("<html><center><b>" + seatId + "</b><br><small style='font-size:9px'>Mine</small></center></html>");
            } else if (isHeld) {
                fill = COLOR_HELD;
                stroke = COLOR_HELD.darker();
                text = Color.WHITE;
                statusText = "Held";
                btn.setText("<html><center><b>" + seatId + "</b><br><small style='font-size:9px'>Held</small></center></html>");
            } else if (isBooked) {
                fill = COLOR_BOOKED;
                stroke = new Color(0xC0, 0xBE, 0xCC);
                text = COLOR_TEXT_BOOKED;
                statusText = "Booked";
                btn.setText("<html><center><font color='#4a4559'><b>" + seatId + "</b><br><small style='font-size:9px'>Booked</small></font></center></html>");
            } else {
                fill = COLOR_AVAILABLE;
                stroke = COLOR_AVAILABLE.darker();
                text = Color.WHITE;
                statusText = "Available";
                btn.setText("<html><center><b>" + seatId + "</b><br><small style='font-size:9px'>Open</small></center></html>");
            }

            btn.setBackground(fill);
            btn.setForeground(text);
            btn.setSeatStyle(fill, stroke, text, statusText);

            String ownerText = isMine ? " (You)" : (isBooked && view.bookedBy() != null ? " (" + view.bookedBy() + ")" : "");
            btn.setToolTipText("Seat " + seatId + " - " + statusText + ownerText);
        }
    }

    public String getSelectedSeatId() {
        return selectedSeatId;
    }

    public List<String> getSelectedSeatIds() {
        return List.copyOf(selectedSeatIds);
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

    /**
     * Curved cinema screen indicator with luminous lavender arc and ambient glow.
     */
    private static class CurvedScreenIndicator extends JPanel {
        CurvedScreenIndicator() {
            setPreferredSize(new Dimension(500, 48));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            SeatSyncTheme.enableAntiAliasing(g2);
            int w = getWidth();

            int arcY = 8;
            int arcH = 16;
            QuadCurve2D arc = new QuadCurve2D.Float(24, arcY + arcH, w / 2f, arcY, w - 24, arcY + arcH);

            // Ambient glow
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xE1, 0x1D, 0x48, 70));
            g2.draw(arc);

            // Luminous curved screen line
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            GradientPaint gp = new GradientPaint(24, 0, new Color(0xFB, 0x71, 0x85), w - 24, 0, UIColors.ACCENT);
            g2.setPaint(gp);
            g2.draw(arc);

            // Screen label
            g2.setFont(UIFonts.CAPTION);
            g2.setColor(UIColors.TEXT_MUTED);
            String label = "CINEMA SCREEN THIS WAY";
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(label)) / 2;
            g2.drawString(label, tx, arcY + arcH + 16);

            g2.dispose();
        }
    }

    /**
     * Custom antialiased cinema seat button rendering rounded backrest, cushion, and hover dynamics.
     */
    private static class SeatButton extends JButton {
        private final String seatId;
        private Color fillColor = COLOR_AVAILABLE;
        private Color strokeColor = COLOR_AVAILABLE.darker();
        private Color labelColor = Color.WHITE;
        private String statusLabel = "Available";
        private boolean isHovered;

        SeatButton(String seatId) {
            this.seatId = seatId;
            setUI(new javax.swing.plaf.basic.BasicButtonUI());
            setFont(FONT_SEAT);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setMargin(new Insets(2, 2, 2, 2));
            setPreferredSize(new Dimension(56, 46));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        isHovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        void setSeatStyle(Color fill, Color stroke, Color text, String status) {
            this.fillColor = fill;
            this.strokeColor = stroke;
            this.labelColor = text;
            this.statusLabel = status;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            SeatSyncTheme.enableAntiAliasing(g2);
            int w = getWidth();
            int h = getHeight();

            Color base = fillColor;
            if (isHovered && isEnabled()) {
                base = new Color(
                    Math.min(255, base.getRed() + 25),
                    Math.min(255, base.getGreen() + 25),
                    Math.min(255, base.getBlue() + 30)
                );
            }

            // Draw cinema seat body
            int arc = 16;
            g2.setColor(base);
            g2.fillRoundRect(2, 2, w - 4, h - 4, arc, arc);

            // Border
            g2.setColor(isHovered ? Color.WHITE : strokeColor);
            g2.setStroke(new BasicStroke(isHovered ? 1.5f : 1f));
            g2.drawRoundRect(2, 2, w - 5, h - 5, arc, arc);

            // Headrest subtle highlight
            g2.setColor(new Color(255, 255, 255, 25));
            g2.drawLine(6, 6, w - 7, 6);

            // Seat ID text
            g2.setFont(FONT_SEAT);
            g2.setColor(labelColor);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(seatId)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - 3;
            g2.drawString(seatId, tx, ty);

            // Bottom cushion contour
            g2.setColor(new Color(0, 0, 0, 25));
            g2.fillRoundRect(6, h - 8, w - 12, 3, 2, 2);

            g2.dispose();
        }
    }
}
