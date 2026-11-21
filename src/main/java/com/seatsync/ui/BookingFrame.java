package com.seatsync.ui;

import com.seatsync.client.ClientMessageHandler;
import com.seatsync.client.TcpClient;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LogoutRequest;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main application window for SeatSync desktop client.
 * Features:
 * <ul>
 *   <li>Live interactive 40-seat cinema grid (A1-E8).</li>
 *   <li>Real-time asynchronous seat updates (SEAT_UPDATED) pushed over TCP socket.</li>
 *   <li>Book, Cancel, and Refresh controls with instant visual feedback.</li>
 *   <li>Activity and event audit log with timestamps.</li>
 *   <li>Safe Swing component mutation on the Event Dispatch Thread (EDT).</li>
 * </ul>
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
@SuppressWarnings({"serial", "this-escape"})
public final class BookingFrame extends JFrame implements ClientMessageHandler.Listener {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final transient TcpClient tcpClient;
    private final String username;
    private final String userId;
    private final String showId;

    // Track bookings initiated by this client session: seatId -> bookingId
    private final transient Map<String, String> userBookings = new ConcurrentHashMap<>();

    // UI Components
    private transient SeatGridPanel seatGridPanel;
    private transient JLabel lblSelectedSeat;
    private transient JLabel lblConnectionStatus;
    private transient ThemedButton btnBook;
    private transient ThemedButton btnCancel;
    private transient ThemedButton btnRefresh;
    private transient ThemedButton btnLogout;
    private transient JTextArea logArea;

    /**
     * Constructs the BookingFrame window.
     *
     * @param tcpClient Active connected TCP client
     * @param username  Authenticated client username
     * @param userId    Authenticated client user ID
     */
    public BookingFrame(TcpClient tcpClient, String username, String userId) {
        this(tcpClient, username, userId, ProtocolConstants.DEFAULT_SHOW_ID);
    }

    /**
     * Constructs the BookingFrame window with a specific show ID.
     * Pure constructor that assigns fields without leaking 'this'.
     * Call {@link #initialize()} after construction to build UI and bind listeners.
     *
     * @param tcpClient Active connected TCP client
     * @param username  Authenticated client username
     * @param userId    Authenticated client user ID
     * @param showId    Target cinema show ID
     */
    public BookingFrame(TcpClient tcpClient, String username, String userId, String showId) {
        super("SeatSync - Cinema Seat Reservation (" + username + ")");
        this.tcpClient = tcpClient;
        this.username = username;
        this.userId = userId;
        this.showId = (showId != null && !showId.isBlank()) ? showId : ProtocolConstants.DEFAULT_SHOW_ID;
    }

    /**
     * Initializes UI layout, binds protocol message listeners, and fetches initial seat state.
     * Executed post-construction to eliminate 'this' escape during object initialization.
     */
    public void initialize() {
        initUI();
        if (tcpClient != null) {
            tcpClient.getMessageHandler().addListener(this);
            requestSeatSnapshot();
        }
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 720);
        setMinimumSize(new Dimension(650, 600));
        setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        setContentPane(contentPane);

        // Header
        contentPane.add(createHeaderPanel(), BorderLayout.NORTH);

        // Center: Seat Grid
        seatGridPanel = new SeatGridPanel();
        seatGridPanel.setCurrentUserId(userId);
        seatGridPanel.addSelectionListener(this::onSeatSelectionChanged);
        contentPane.add(seatGridPanel, BorderLayout.CENTER);

        // Bottom: Action Panel + Audit Log
        JPanel bottomContainer = new JPanel();
        bottomContainer.setLayout(new BoxLayout(bottomContainer, BoxLayout.Y_AXIS));

        bottomContainer.add(createActionControlsPanel());
        bottomContainer.add(Box.createVerticalStrut(8));
        bottomContainer.add(createLogPanel());

        contentPane.add(bottomContainer, BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(245, 247, 250));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(207, 216, 220), 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        // Left info
        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        infoPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("🎬 Cinema Reservation: " + showId);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        JLabel userLabel = new JLabel("👤 Authenticated User: " + username + " (" + userId + ")");
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userLabel.setForeground(new Color(55, 71, 79));
        infoPanel.add(titleLabel);
        infoPanel.add(userLabel);
        header.add(infoPanel, BorderLayout.WEST);

        // Right connection status
        JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        statusBox.setOpaque(false);
        lblConnectionStatus = new JLabel("● Connected: " + tcpClient.getHost() + ":" + tcpClient.getPort());
        lblConnectionStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblConnectionStatus.setForeground(new Color(46, 125, 50));
        statusBox.add(lblConnectionStatus);

        btnLogout = new ThemedButton("Logout", new Color(198, 40, 40), Color.WHITE);
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnLogout.addActionListener(e -> performLogout());
        statusBox.add(btnLogout);

        header.add(statusBox, BorderLayout.EAST);
        return header;
    }

    private JPanel createActionControlsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Seat Operations"),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        // Selected seat display
        lblSelectedSeat = new JLabel("Selected Seat: None");
        lblSelectedSeat.setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(lblSelectedSeat, BorderLayout.WEST);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        btnBook = new ThemedButton("Book Seat", new Color(46, 125, 50), Color.WHITE);
        btnBook.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnBook.setEnabled(false);
        btnBook.addActionListener(e -> performBookSeat());

        btnCancel = new ThemedButton("Cancel Booking", new Color(198, 40, 40), Color.WHITE);
        btnCancel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> performCancelBooking());

        btnRefresh = new ThemedButton("Refresh", new Color(55, 71, 79), Color.WHITE);
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRefresh.addActionListener(e -> requestSeatSnapshot());

        btnPanel.add(btnBook);
        btnPanel.add(btnCancel);
        btnPanel.add(btnRefresh);

        panel.add(btnPanel, BorderLayout.EAST);
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Client Activity & Real-Time Broadcast Log"));

        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setBackground(new Color(250, 250, 250));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);

        appendLog("Session initialized for user " + username + " (" + userId + ")");
        return panel;
    }

    private void onSeatSelectionChanged(String seatId) {
        if (seatId == null) {
            lblSelectedSeat.setText("Selected Seat: None");
            btnBook.setEnabled(false);
            btnCancel.setEnabled(false);
            return;
        }

        SeatView view = seatGridPanel.getSeatView(seatId);
        boolean isBooked = view != null && view.status() == SeatStatus.BOOKED;
        boolean isMine = isBooked && userId != null && userId.equals(view.bookedBy());

        String statusDesc;
        if (isMine) {
            statusDesc = "BOOKED BY YOU";
        } else if (isBooked) {
            statusDesc = "BOOKED (" + (view.bookedBy() != null ? view.bookedBy() : "Other") + ")";
        } else {
            statusDesc = "AVAILABLE";
        }

        lblSelectedSeat.setText("Selected Seat: " + seatId + " [" + statusDesc + "]");
        btnBook.setEnabled(!isBooked);
        btnCancel.setEnabled(isBooked && (isMine || userBookings.containsKey(seatId)));
    }

    // =========================================================================
    // Protocol Operations
    // =========================================================================

    private void requestSeatSnapshot() {
        try {
            GetSeatsRequest request = new GetSeatsRequest(tcpClient.nextRequestId(), showId);
            appendLog("Requesting seat snapshot for " + showId + "...");
            tcpClient.sendRequest(request);
        } catch (IOException e) {
            appendLog("[ERROR] Failed to send GET_SEATS request: " + e.getMessage());
        }
    }

    private void performBookSeat() {
        String seatId = seatGridPanel.getSelectedSeatId();
        if (seatId == null) {
            JOptionPane.showMessageDialog(this, "Please select an available seat first.", "No Seat Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BookSeatRequest request = new BookSeatRequest(tcpClient.nextRequestId(), showId, seatId);
            appendLog("Submitting booking request for seat " + seatId + "...");
            btnBook.setEnabled(false);
            tcpClient.sendRequest(request);
        } catch (IOException e) {
            appendLog("[ERROR] Failed to send BOOK_SEAT request: " + e.getMessage());
            btnBook.setEnabled(true);
        }
    }

    private void performCancelBooking() {
        String seatId = seatGridPanel.getSelectedSeatId();
        String bookingId = (seatId != null) ? userBookings.get(seatId) : null;

        if (bookingId == null) {
            bookingId = JOptionPane.showInputDialog(this, "Enter Booking ID to cancel:", "Cancel Booking", JOptionPane.QUESTION_MESSAGE);
            if (bookingId == null || bookingId.isBlank()) {
                return;
            }
            bookingId = bookingId.trim();
        }

        try {
            CancelBookingRequest request = new CancelBookingRequest(tcpClient.nextRequestId(), bookingId);
            appendLog("Submitting cancellation request for booking " + bookingId + "...");
            btnCancel.setEnabled(false);
            tcpClient.sendRequest(request);
        } catch (IOException e) {
            appendLog("[ERROR] Failed to send CANCEL_BOOKING request: " + e.getMessage());
            btnCancel.setEnabled(true);
        }
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out?", "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            LogoutRequest request = new LogoutRequest(tcpClient.nextRequestId());
            tcpClient.sendRequest(request);
        } catch (Exception ignored) {
        } finally {
            tcpClient.disconnect();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame(tcpClient.getHost(), tcpClient.getPort()).setVisible(true));
        }
    }

    private void appendLog(String message) {
        String timestamp = LocalTime.now().format(TIME_FMT);
        String entry = "[" + timestamp + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(entry);
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    // =========================================================================
    // ClientMessageHandler.Listener Implementation (Runs on EDT safely)
    // =========================================================================

    @Override
    public void onSeatSnapshot(SeatSnapshotReply reply) {
        SwingUtilities.invokeLater(() -> {
            seatGridPanel.updateSeats(reply.seats());
            appendLog("Seat snapshot loaded: " + reply.seats().size() + " seats (Version " + reply.version() + ")");
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onBookSuccess(BookOkReply reply) {
        SwingUtilities.invokeLater(() -> {
            userBookings.put(reply.seatId(), reply.bookingId());
            seatGridPanel.updateSingleSeat(new SeatView(reply.seatId(), SeatStatus.BOOKED, userId));
            appendLog("SUCCESS: Seat " + reply.seatId() + " booked! Booking ID: " + reply.bookingId());
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
            JOptionPane.showMessageDialog(this,
                "Seat " + reply.seatId() + " successfully booked!\nBooking ID: " + reply.bookingId(),
                "Booking Confirmed",
                JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    @Override
    public void onBookRejected(BookRejectedReply reply) {
        SwingUtilities.invokeLater(() -> {
            appendLog("BOOKING REJECTED: " + reply.message() + " (" + reply.errorCode() + ")");
            btnBook.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                reply.message(),
                "Booking Failed (" + reply.errorCode() + ")",
                JOptionPane.ERROR_MESSAGE
            );
        });
    }

    @Override
    public void onCancelSuccess(CancelOkReply reply) {
        SwingUtilities.invokeLater(() -> {
            if (reply.seatId() != null) {
                userBookings.remove(reply.seatId());
                seatGridPanel.updateSingleSeat(SeatView.available(reply.seatId()));
            } else {
                userBookings.values().remove(reply.bookingId());
                requestSeatSnapshot();
            }
            appendLog("SUCCESS: Booking " + reply.bookingId() + " cancelled successfully.");
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
            JOptionPane.showMessageDialog(this,
                "Booking " + reply.bookingId() + " has been cancelled.",
                "Cancellation Confirmed",
                JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    @Override
    public void onCancelRejected(CancelRejectedReply reply) {
        SwingUtilities.invokeLater(() -> {
            appendLog("CANCEL REJECTED: " + reply.message() + " (" + reply.errorCode() + ")");
            btnCancel.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                reply.message(),
                "Cancellation Failed (" + reply.errorCode() + ")",
                JOptionPane.ERROR_MESSAGE
            );
        });
    }

    @Override
    public void onSeatUpdated(SeatUpdatedEvent event) {
        SwingUtilities.invokeLater(() -> {
            seatGridPanel.updateSeats(event.seats());
            appendLog("BROADCAST: Live seat update received (" + event.seats().size() + " seats updated, Version " + event.version() + ")");
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onError(ErrorReply error) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[SERVER ERROR] " + error.message() + " (" + error.errorCode() + ")");
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblConnectionStatus.setText("● Disconnected: " + reason);
            lblConnectionStatus.setForeground(Color.RED);
            btnBook.setEnabled(false);
            btnCancel.setEnabled(false);
            btnRefresh.setEnabled(false);
            appendLog("[DISCONNECT] " + reason);
        });
    }

    @Override
    public void onConnectionError(Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[CONNECTION ERROR] " + cause.getMessage());
        });
    }
}
