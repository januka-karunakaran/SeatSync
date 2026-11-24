package com.seatsync.ui;

import com.seatsync.client.ClientMessageHandler;
import com.seatsync.client.TcpClient;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.AuditoriumLayout;
import com.seatsync.common.model.BookingHistoryItem;
import com.seatsync.common.model.MovieDetails;
import com.seatsync.common.model.SeatTier;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.model.TierType;
import com.seatsync.common.model.UserRole;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.RequestMessage;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;
import com.seatsync.common.protocol.reply.AdminAuditLogsReply;
import com.seatsync.common.protocol.reply.AdminBookingsReply;
import com.seatsync.common.protocol.reply.AdminMovieCreatedReply;
import com.seatsync.common.protocol.reply.AdminShowAddedReply;
import com.seatsync.common.protocol.reply.BookingHistoryReply;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ConfirmOkReply;
import com.seatsync.common.protocol.reply.ConfirmRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.HoldOkReply;
import com.seatsync.common.protocol.reply.HoldRejectedReply;
import com.seatsync.common.protocol.reply.HoldReleasedOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.MovieListReply;
import com.seatsync.common.protocol.reply.NetworkMetricsReply;
import com.seatsync.common.protocol.reply.ServerMetricsReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.reply.ShowListReply;
import com.seatsync.common.protocol.reply.TicketReply;
import com.seatsync.common.protocol.reply.WaitlistJoinedOkReply;
import com.seatsync.common.protocol.reply.WaitlistStatusReply;
import com.seatsync.common.protocol.request.AdminCreateMovieRequest;
import com.seatsync.common.protocol.request.AdminCreateShowRequest;
import com.seatsync.common.protocol.request.AdminDisableShowRequest;
import com.seatsync.common.protocol.request.AdminGetAuditLogsRequest;
import com.seatsync.common.protocol.request.AdminListBookingsRequest;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.ConfirmHoldRequest;
import com.seatsync.common.protocol.request.GetBookingHistoryRequest;
import com.seatsync.common.protocol.request.GetMoviesRequest;
import com.seatsync.common.protocol.request.GetNetworkMetricsRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.GetServerMetricsRequest;
import com.seatsync.common.protocol.request.GetShowsRequest;
import com.seatsync.common.protocol.request.GetTicketRequest;
import com.seatsync.common.protocol.request.GetWaitlistStatusRequest;
import com.seatsync.common.protocol.request.HoldSeatRequest;
import com.seatsync.common.protocol.request.JoinWaitlistRequest;
import com.seatsync.common.protocol.request.LeaveWaitlistRequest;
import com.seatsync.common.protocol.request.LogoutRequest;
import com.seatsync.common.protocol.request.ReleaseHoldRequest;
import com.seatsync.common.protocol.request.SubscribeEventsRequest;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

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
 * Owned by Member 05 (Desktop UI & Presentation).
 */
@SuppressWarnings({"serial", "this-escape"})
public final class BookingFrame extends JFrame implements ClientMessageHandler.Listener {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final transient TcpClient tcpClient;
    private final String username;
    private final String userId;
    private volatile String showId;
    private final UserRole role;
    private final transient ExecutorService networkExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "seatsync-ui-network");
        thread.setDaemon(true);
        return thread;
    });

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
    private transient JLabel lblSeatState;
    private transient JLabel lblHomeAvailable;
    private transient JLabel lblHomeBooked;
    private transient JLabel lblHomeHeld;
    private transient JLabel lblHomeStatus;
    private transient JLabel lblShowTitle;
    private transient CardLayout pageLayout;
    private transient JPanel pageCards;
    private transient DefaultTableModel movieRows;
    private transient DefaultTableModel showRows;
    private transient DefaultTableModel ticketRows;
    private transient JTable showsTable;
    private transient JTable ticketsTable;
    private transient JLabel lblWaitlistStatus;
    private transient JLabel lblMovieState;
    private transient JLabel lblShowState;
    private transient JLabel lblTicketState;
    private transient ThemedButton btnPreviousMovies;
    private transient ThemedButton btnNextMovies;
    private int moviePage;
    private transient JTextArea adminDashboard;
    private transient ThemedButton btnHold;
    private transient ThemedButton btnConfirmHold;
    private transient ThemedButton btnReleaseHold;
    private transient JLabel lblHoldCountdown;
    private transient Timer holdTimer;
    private volatile String activeHoldId;
    private volatile String activeHoldSeatId;
    private volatile long activeHoldExpiresAt;
    private final transient Map<String, Long> seatHoldExpiries = new ConcurrentHashMap<>();

    /**
     * Constructs the BookingFrame window.
     *
     * @param tcpClient Active connected TCP client
     * @param username  Authenticated client username
     * @param userId    Authenticated client user ID
     */
    public BookingFrame(TcpClient tcpClient, String username, String userId) {
        this(tcpClient, username, userId, ProtocolConstants.DEFAULT_SHOW_ID, UserRole.CUSTOMER);
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
        this(tcpClient, username, userId, showId, UserRole.CUSTOMER);
    }

    public BookingFrame(TcpClient tcpClient, String username, String userId, String showId, UserRole role) {
        super("SeatSync - Cinema Seat Reservation (" + username + ")");
        this.tcpClient = tcpClient;
        this.username = username;
        this.userId = userId;
        this.showId = (showId != null && !showId.isBlank()) ? showId : ProtocolConstants.DEFAULT_SHOW_ID;
        this.role = role == null ? UserRole.CUSTOMER : role;
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
            requestMovies();
            requestShows();
            subscribeToSelectedShow();
        }
        holdTimer = new Timer(1000, event -> updateHoldCountdown());
        holdTimer.start();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);
        contentPane.add(createHeaderPanel(), BorderLayout.NORTH);

        NavigationPanel navigation = new NavigationPanel(this::showPage);
        if (role != UserRole.ADMIN) {
            navigation.getNavigationButton("Admin").setVisible(false);
        }
        contentPane.add(navigation, BorderLayout.WEST);
        pageLayout = new CardLayout();
        pageCards = new JPanel(pageLayout);
        pageCards.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        pageCards.add(createHomePage(), "Home");
        pageCards.add(createMoviesPage(), "Movies");
        pageCards.add(createShowsPage(), "Shows");
        pageCards.add(createSeatMapPage(), "Seat Map");
        pageCards.add(createTicketsPage(), "My Tickets");
        pageCards.add(createWaitlistPage(), "Waitlist");
        pageCards.add(createAdminPage(), "Admin");
        contentPane.add(pageCards, BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                if (tcpClient != null) {
                    tcpClient.getMessageHandler().removeListener(BookingFrame.this);
                }
                if (holdTimer != null) {
                    holdTimer.stop();
                }
                networkExecutor.shutdownNow();
            }
        });
    }

    private JPanel createSeatMapPage() {
        JPanel seatMap = new JPanel(new BorderLayout(10, 10));
        seatGridPanel = new SeatGridPanel();
        seatGridPanel.setCurrentUserId(userId);
        seatGridPanel.addSelectionListener(this::onSeatSelectionChanged);
        lblSeatState = new JLabel("Loading seats...", SwingConstants.CENTER);
        lblSeatState.setForeground(new Color(21, 101, 192));
        seatMap.add(lblSeatState, BorderLayout.NORTH);
        seatMap.add(seatGridPanel, BorderLayout.CENTER);

        JPanel bottomContainer = new JPanel();
        bottomContainer.setLayout(new BoxLayout(bottomContainer, BoxLayout.Y_AXIS));
        lblHoldCountdown = new JLabel("No active seat hold", SwingConstants.CENTER);
        bottomContainer.add(lblHoldCountdown);
        bottomContainer.add(createActionControlsPanel());
        bottomContainer.add(Box.createVerticalStrut(8));
        bottomContainer.add(createLogPanel());
        seatMap.add(bottomContainer, BorderLayout.SOUTH);
        return seatMap;
    }

    private JPanel createMoviesPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        movieRows = tableModel("Movie ID", "Title", "Genre", "Duration (min)");
        JTable table = new JTable(movieRows);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(actionButton("Refresh movies", this::requestMovies));
        btnPreviousMovies = actionButton("Previous", () -> {
            if (moviePage > 0) {
                moviePage--;
                requestMovies();
            }
        });
        btnNextMovies = actionButton("Next", () -> {
            moviePage++;
            requestMovies();
        });
        btnPreviousMovies.setEnabled(false);
        btnNextMovies.setEnabled(false);
        actions.add(btnPreviousMovies);
        actions.add(btnNextMovies);
        if (role == UserRole.ADMIN) {
            actions.add(actionButton("Create movie", this::showCreateMovieDialog));
        }
        page.add(actions, BorderLayout.NORTH);
        page.add(new JScrollPane(table), BorderLayout.CENTER);
        lblMovieState = new JLabel("Loading movies...");
        page.add(lblMovieState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createShowsPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        showRows = tableModel("Show ID", "Title", "Start time", "Venue", "Available", "Capacity");
        showsTable = new JTable(showRows);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(actionButton("Refresh shows", this::requestShows));
        actions.add(actionButton("Open selected show", this::openSelectedShow));
        if (role == UserRole.ADMIN) {
            actions.add(actionButton("Create show", this::showCreateShowDialog));
            actions.add(actionButton("Disable show", this::disableSelectedShow));
        }
        page.add(actions, BorderLayout.NORTH);
        page.add(new JScrollPane(showsTable), BorderLayout.CENTER);
        lblShowState = new JLabel("Loading shows...");
        page.add(lblShowState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createTicketsPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        ticketRows = tableModel("Booking ID", "Show", "Seat", "Show time", "Status", "Ticket ID");
        ticketsTable = new JTable(ticketRows);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(actionButton("Refresh history", this::requestBookingHistory));
        actions.add(actionButton("View selected ticket", this::viewSelectedTicket));
        page.add(actions, BorderLayout.NORTH);
        page.add(new JScrollPane(ticketsTable), BorderLayout.CENTER);
        lblTicketState = new JLabel("No booking history loaded.");
        page.add(lblTicketState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createWaitlistPage() {
        JPanel page = new JPanel(new BorderLayout(10, 10));
        lblWaitlistStatus = new JLabel("Select a show to view or join its waitlist.", SwingConstants.CENTER);
        lblWaitlistStatus.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JPanel actions = new JPanel(new FlowLayout());
        actions.add(actionButton("Check status", this::requestWaitlistStatus));
        actions.add(actionButton("Join waitlist", this::joinWaitlist));
        actions.add(actionButton("Leave waitlist", this::leaveWaitlist));
        page.add(lblWaitlistStatus, BorderLayout.CENTER);
        page.add(actions, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createAdminPage() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        adminDashboard = new JTextArea();
        adminDashboard.setEditable(false);
        adminDashboard.setLineWrap(true);
        adminDashboard.setWrapStyleWord(true);
        adminDashboard.setText(role != UserRole.ADMIN
            ? "Administrator role required."
            : "Admin network dashboard. Select Refresh to load live metrics and audit records.");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(actionButton("Refresh metrics", this::requestAdminMetrics));
        actions.add(actionButton("Audit logs", this::requestAuditLogs));
        actions.add(actionButton("List bookings", this::requestAdminBookings));
        page.add(actions, BorderLayout.NORTH);
        page.add(new JScrollPane(adminDashboard), BorderLayout.CENTER);
        return page;
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private ThemedButton actionButton(String title, Runnable action) {
        ThemedButton button = new ThemedButton(title, new Color(55, 71, 79), Color.WHITE);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel createHomePage() {
        JPanel home = new JPanel(new BorderLayout(16, 16));
        home.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 6));
        heading.setOpaque(false);
        JLabel title = new JLabel("Welcome, " + username);
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        lblShowTitle = new JLabel("Your SeatSync booking overview for " + showId);
        JLabel subtitle = lblShowTitle;
        subtitle.setForeground(new Color(100, 116, 139));
        heading.add(title);
        heading.add(subtitle);
        home.add(heading, BorderLayout.NORTH);

        JPanel summary = new JPanel(new GridLayout(1, 3, 14, 0));
        summary.add(createSummaryCard("Available seats", value -> lblHomeAvailable = value));
        summary.add(createSummaryCard("Booked seats", value -> lblHomeBooked = value));
        summary.add(createSummaryCard("Held seats", value -> lblHomeHeld = value));
        home.add(summary, BorderLayout.CENTER);
        lblHomeStatus = new JLabel("Loading current seat availability...", SwingConstants.CENTER);
        lblHomeStatus.setForeground(new Color(71, 85, 105));
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.add(lblHomeStatus, BorderLayout.CENTER);
        ThemedButton openSeatMap = new ThemedButton("Open Seat Map", new Color(21, 101, 192), Color.WHITE);
        openSeatMap.addActionListener(event -> showPage("Seat Map"));
        footer.add(openSeatMap, BorderLayout.SOUTH);
        home.add(footer, BorderLayout.SOUTH);
        return home;
    }

    private JPanel createSummaryCard(String title, java.util.function.Consumer<JLabel> valueConsumer) {
        JPanel card = new JPanel(new GridLayout(2, 1, 4, 8));
        card.setBackground(new Color(248, 250, 252));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240)),
            BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(new Color(71, 85, 105));
        JLabel valueLabel = new JLabel("Loading...");
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        card.add(titleLabel);
        card.add(valueLabel);
        valueConsumer.accept(valueLabel);
        return card;
    }

    private void showPage(String page) {
        pageLayout.show(pageCards, page);
        switch (page) {
            case "Movies" -> requestMovies();
            case "Shows" -> requestShows();
            case "My Tickets" -> requestBookingHistory();
            case "Waitlist" -> requestWaitlistStatus();
            case "Admin" -> {
                if (role == UserRole.ADMIN) {
                    requestAdminMetrics();
                }
            }
            default -> {
            }
        }
    }

    private void requestMovies() {
        lblMovieState.setText("Loading movies...");
        sendRequestAsync(new GetMoviesRequest(tcpClient.nextRequestId(), moviePage, 5), "GET_MOVIES",
            () -> lblMovieState.setText("Movie request failed. Check the connection and retry."),
            reply -> {
                if (reply instanceof MovieListReply result) {
                    movieRows.setRowCount(0);
                    result.movies().items().forEach(movie -> movieRows.addRow(new Object[] {
                        movie.movieId(), movie.title(), movie.genre(), movie.durationMinutes()
                    }));
                    lblMovieState.setText(result.movies().items().isEmpty()
                        ? "No movies are currently available."
                        : "Page " + (result.movies().pageNumber() + 1) + ": "
                            + result.movies().items().size() + " movie(s) of " + result.movies().totalItems() + ".");
                    btnPreviousMovies.setEnabled(result.movies().pageNumber() > 0);
                    btnNextMovies.setEnabled(result.movies().hasMore());
                } else {
                    lblMovieState.setText("Unable to load movies: " + reply.getType());
                    appendLog("[ERROR] Movie catalog request returned " + reply.getType());
                }
            });
    }

    private void requestShows() {
        lblShowState.setText("Loading shows...");
        sendRequestAsync(new GetShowsRequest(tcpClient.nextRequestId()), "GET_SHOWS",
            () -> lblShowState.setText("Show request failed. Check the connection and retry."),
            reply -> {
                if (reply instanceof ShowListReply result) {
                    showRows.setRowCount(0);
                    result.shows().forEach(show -> showRows.addRow(new Object[] {
                        show.showId(), show.title(), show.startTime(), show.venue(), show.availableSeats(), show.totalSeats()
                    }));
                    lblShowState.setText(result.shows().isEmpty()
                        ? "No shows are currently available."
                        : "Loaded " + result.shows().size() + " show(s). Select a row and open its seat map.");
                } else {
                    lblShowState.setText("Unable to load shows: " + reply.getType());
                    appendLog("[ERROR] Show catalog request returned " + reply.getType());
                }
            });
    }

    private void openSelectedShow() {
        int row = showsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a show first.", "No Show Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String previousShowId = showId;
        showId = showsTable.getValueAt(row, 0).toString();
        if (!previousShowId.equals(showId)) {
            if (activeHoldId != null) {
                sendRequestAsync(new ReleaseHoldRequest(tcpClient.nextRequestId(), activeHoldId),
                    "RELEASE_HOLD", () -> {});
            }
            sendRequestAsync(new com.seatsync.common.protocol.request.UnsubscribeEventsRequest(
                tcpClient.nextRequestId(), previousShowId), "UNSUBSCRIBE_EVENTS", () -> {});
            seatHoldExpiries.clear();
            activeHoldId = null;
            activeHoldSeatId = null;
            activeHoldExpiresAt = 0;
            subscribeToSelectedShow();
        }
        lblShowTitle.setText("Your SeatSync booking overview for " + showId);
        requestSeatSnapshot();
        showPage("Seat Map");
    }

    private void subscribeToSelectedShow() {
        sendRequestAsync(new SubscribeEventsRequest(tcpClient.nextRequestId(), showId), "SUBSCRIBE_EVENTS", () -> {});
    }

    private void requestBookingHistory() {
        lblTicketState.setText("Loading booking history...");
        sendRequestAsync(new GetBookingHistoryRequest(tcpClient.nextRequestId(), 0, 20), "GET_BOOKING_HISTORY",
            () -> lblTicketState.setText("History request failed. Check the connection and retry."),
            reply -> {
                if (reply instanceof BookingHistoryReply result) {
                    ticketRows.setRowCount(0);
                    result.history().items().forEach(item -> ticketRows.addRow(new Object[] {
                        item.bookingId(), item.showId(), item.seatId(), item.showTime(), item.status(), item.ticketId()
                    }));
                    lblTicketState.setText(result.history().items().isEmpty()
                        ? "No booking history found."
                        : "Showing " + result.history().items().size() + " of " + result.history().totalItems() + " booking(s).");
                } else {
                    lblTicketState.setText("Unable to load history: " + reply.getType());
                    appendLog("[ERROR] Booking history request returned " + reply.getType());
                }
            });
    }

    private void viewSelectedTicket() {
        int row = ticketsTable.getSelectedRow();
        if (row < 0 || ticketsTable.getValueAt(row, 5) == null) {
            JOptionPane.showMessageDialog(this, "Select a ticketed booking first.", "No Ticket Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String ticketId = ticketsTable.getValueAt(row, 5).toString();
        sendRequestAsync(new GetTicketRequest(tcpClient.nextRequestId(), ticketId), "GET_TICKET", () -> {},
            reply -> {
                if (reply instanceof TicketReply result) {
                    var ticket = result.ticket();
                    JOptionPane.showMessageDialog(this,
                        "Ticket: " + ticket.ticketId() + "\nBooking: " + ticket.bookingId()
                            + "\nShow: " + ticket.showId() + "\nSeat: " + ticket.seatId()
                            + "\nIssued: " + java.time.Instant.ofEpochMilli(ticket.issuedAt())
                            + "\nVerification: " + ticket.verificationHash(),
                        "Digital Ticket", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    appendLog("[ERROR] Ticket request returned " + reply.getType());
                }
            });
    }

    private void joinWaitlist() {
        sendRequestAsync(new JoinWaitlistRequest(tcpClient.nextRequestId(), showId), "JOIN_WAITLIST", () -> {},
            reply -> {
                if (reply instanceof WaitlistJoinedOkReply result) {
                    lblWaitlistStatus.setText("Joined " + result.showId() + " waitlist at position " + result.position());
                } else {
                    lblWaitlistStatus.setText("Unable to join waitlist: " + reply.getType());
                }
            });
    }

    private void leaveWaitlist() {
        sendRequestAsync(new LeaveWaitlistRequest(tcpClient.nextRequestId(), showId), "LEAVE_WAITLIST", () -> {},
            reply -> lblWaitlistStatus.setText("Waitlist response: " + reply.getType()));
    }

    private void requestWaitlistStatus() {
        if (lblWaitlistStatus == null) {
            return;
        }
        sendRequestAsync(new GetWaitlistStatusRequest(tcpClient.nextRequestId(), showId), "GET_WAITLIST_STATUS", () -> {},
            reply -> {
                if (reply instanceof WaitlistStatusReply result) {
                    lblWaitlistStatus.setText(result.position() <= 0
                        ? "You are not currently in the " + showId + " waitlist."
                        : "Your position for " + showId + ": " + result.position()
                            + " of " + result.totalWaitlistSize());
                } else {
                    lblWaitlistStatus.setText("Waitlist response: " + reply.getType());
                }
            });
    }

    private void requestAdminMetrics() {
        if (role != UserRole.ADMIN) {
            return;
        }
        sendRequestAsync(new GetServerMetricsRequest(tcpClient.nextRequestId()), "GET_SERVER_METRICS", () -> {},
            reply -> {
                if (reply instanceof ServerMetricsReply result) {
                    adminDashboard.append("\n\nServer metrics:\n" + result.metrics());
                } else {
                    adminDashboard.append("\nServer metrics unavailable: " + reply.getType());
                }
            });
        sendRequestAsync(new GetNetworkMetricsRequest(tcpClient.nextRequestId()), "GET_NETWORK_METRICS", () -> {},
            reply -> {
                if (reply instanceof NetworkMetricsReply result) {
                    adminDashboard.append("\n\nNetwork metrics:\n" + result.metrics());
                } else {
                    adminDashboard.append("\nNetwork metrics unavailable: " + reply.getType());
                }
            });
    }

    private void requestAuditLogs() {
        if (role != UserRole.ADMIN) {
            return;
        }
        sendRequestAsync(new AdminGetAuditLogsRequest(tcpClient.nextRequestId(), 20), "ADMIN_GET_AUDIT_LOGS", () -> {},
            reply -> {
                if (reply instanceof AdminAuditLogsReply result) {
                    adminDashboard.append("\n\nRecent audit logs:\n");
                    result.logs().forEach(log -> adminDashboard.append(log + "\n"));
                } else {
                    adminDashboard.append("\nAudit logs unavailable: " + reply.getType());
                }
            });
    }

    private void requestAdminBookings() {
        if (role != UserRole.ADMIN) {
            return;
        }
        sendRequestAsync(new AdminListBookingsRequest(tcpClient.nextRequestId(), showId, 0, 20),
            "ADMIN_LIST_BOOKINGS", () -> {},
            reply -> {
                adminDashboard.append("\n\nBookings for " + showId + ":\n");
                if (reply instanceof AdminBookingsReply result) {
                    result.bookings().items().forEach(booking -> adminDashboard.append(booking + "\n"));
                } else {
                    adminDashboard.append("Booking list unavailable: " + reply.getType());
                }
            });
    }

    private void showCreateMovieDialog() {
        JTextField id = new JTextField();
        JTextField title = new JTextField();
        JTextField genre = new JTextField();
        JTextField duration = new JTextField("120");
        JTextField description = new JTextField();
        Object[] fields = {"Movie ID", id, "Title", title, "Genre", genre,
            "Duration (minutes)", duration, "Description", description};
        if (JOptionPane.showConfirmDialog(this, fields, "Create Movie", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            MovieDetails movie = new MovieDetails(id.getText().trim(), title.getText().trim(),
                description.getText().trim(), genre.getText().trim(), Integer.parseInt(duration.getText().trim()));
            sendRequestAsync(new AdminCreateMovieRequest(tcpClient.nextRequestId(), movie), "ADMIN_CREATE_MOVIE",
                () -> {}, reply -> {
                    if (reply instanceof AdminMovieCreatedReply) {
                        requestMovies();
                    }
                    JOptionPane.showMessageDialog(this, "Movie request: " + reply.getType(),
                        "Administration", JOptionPane.INFORMATION_MESSAGE);
                });
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Enter valid movie details.", "Invalid Movie", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showCreateShowDialog() {
        JTextField show = new JTextField();
        JTextField movie = new JTextField();
        JTextField title = new JTextField();
        JTextField start = new JTextField(java.time.Instant.now().plusSeconds(3600).toString());
        JTextField end = new JTextField(java.time.Instant.now().plusSeconds(10800).toString());
        JTextField venue = new JTextField();
        Object[] fields = {"Show ID", show, "Movie ID", movie, "Title", title,
            "Start (ISO-8601)", start, "End (ISO-8601)", end, "Venue", venue};
        if (JOptionPane.showConfirmDialog(this, fields, "Create Show", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            java.time.Instant.parse(start.getText().trim());
            java.time.Instant.parse(end.getText().trim());
            AuditoriumLayout layout = new AuditoriumLayout("LAYOUT-" + show.getText().trim(), venue.getText().trim(),
                5, 8, 40, List.of(new SeatTier("Standard", TierType.STANDARD, 2500, "Standard seating")));
            ShowDetails details = new ShowDetails(show.getText().trim(), title.getText().trim(),
                "", "General", start.getText().trim(), end.getText().trim(), venue.getText().trim(), layout);
            sendRequestAsync(new AdminCreateShowRequest(tcpClient.nextRequestId(), movie.getText().trim(), details),
                "ADMIN_CREATE_SHOW", () -> {}, reply -> {
                    if (reply instanceof AdminShowAddedReply) {
                        requestShows();
                    }
                    JOptionPane.showMessageDialog(this, "Show request: " + reply.getType(),
                        "Administration", JOptionPane.INFORMATION_MESSAGE);
                });
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, "Enter valid show details and ISO-8601 times.",
                "Invalid Show", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disableSelectedShow() {
        int row = showsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a show first.", "No Show Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String selectedShow = showsTable.getValueAt(row, 0).toString();
        sendRequestAsync(new AdminDisableShowRequest(tcpClient.nextRequestId(), selectedShow),
            "ADMIN_DISABLE_SHOW", () -> {}, reply -> {
                JOptionPane.showMessageDialog(this, "Show administration request: " + reply.getType(),
                    "Administration", JOptionPane.INFORMATION_MESSAGE);
                requestShows();
            });
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
        boolean connected = tcpClient != null && tcpClient.isConnected();
        String transport = tcpClient != null && tcpClient.isTlsEnabled() ? " (TLS 1.3)" : " (plaintext)";
        lblConnectionStatus = new JLabel(connected
            ? "● Connected: " + tcpClient.getHost() + ":" + tcpClient.getPort() + transport
            : "● Disconnected");
        lblConnectionStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblConnectionStatus.setForeground(connected ? new Color(46, 125, 50) : Color.RED);
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

        btnHold = new ThemedButton("Hold Seat", new Color(239, 108, 0), Color.WHITE);
        btnHold.setEnabled(false);
        btnHold.addActionListener(e -> performHoldSeat());

        btnConfirmHold = new ThemedButton("Confirm Hold", new Color(46, 125, 50), Color.WHITE);
        btnConfirmHold.setEnabled(false);
        btnConfirmHold.addActionListener(e -> confirmActiveHold());

        btnReleaseHold = new ThemedButton("Release Hold", new Color(120, 53, 15), Color.WHITE);
        btnReleaseHold.setEnabled(false);
        btnReleaseHold.addActionListener(e -> releaseActiveHold());

        btnCancel = new ThemedButton("Cancel Booking", new Color(198, 40, 40), Color.WHITE);
        btnCancel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> performCancelBooking());

        btnRefresh = new ThemedButton("Refresh", new Color(55, 71, 79), Color.WHITE);
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRefresh.addActionListener(e -> requestSeatSnapshot());

        btnPanel.add(btnBook);
        btnPanel.add(btnHold);
        btnPanel.add(btnConfirmHold);
        btnPanel.add(btnReleaseHold);
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
            btnHold.setEnabled(false);
            btnCancel.setEnabled(false);
            return;
        }

        SeatView view = seatGridPanel.getSeatView(seatId);
        boolean isBooked = view != null && view.status() == SeatStatus.BOOKED;
        boolean isMine = isBooked && userId != null && userId.equals(view.bookedBy());

        boolean isHeld = view != null && view.status() == SeatStatus.HELD;
        boolean connected = tcpClient != null && tcpClient.isConnected();
        String statusDesc;
        if (isMine) {
            statusDesc = "BOOKED BY YOU";
        } else if (isHeld && seatId.equals(activeHoldSeatId)) {
            statusDesc = "HELD BY YOU";
        } else if (isHeld) {
            statusDesc = "HELD";
        } else if (isBooked) {
            statusDesc = "BOOKED (" + (view.bookedBy() != null ? view.bookedBy() : "Other") + ")";
        } else {
            statusDesc = "AVAILABLE";
        }

        lblSelectedSeat.setText("Selected Seat: " + seatId + " [" + statusDesc + "]");
        btnBook.setEnabled(connected && view != null && view.status() == SeatStatus.AVAILABLE);
        btnHold.setEnabled(connected && view != null && view.status() == SeatStatus.AVAILABLE);
        btnCancel.setEnabled(connected && isBooked && (isMine || userBookings.containsKey(seatId)));
    }

    // =========================================================================
    // Protocol Operations
    // =========================================================================

    private void requestSeatSnapshot() {
        GetSeatsRequest request = new GetSeatsRequest(tcpClient.nextRequestId(), showId);
        appendLog("Requesting seat snapshot for " + showId + "...");
        lblSeatState.setText("Loading seats...");
        lblHomeStatus.setText("Loading current seat availability...");
        btnRefresh.setEnabled(false);
        sendRequestAsync(request, "GET_SEATS", () -> {
            lblSeatState.setText("Could not load seats. Check the connection and retry.");
            lblHomeStatus.setText("Unable to load availability. Use Refresh to try again.");
            btnRefresh.setEnabled(true);
        });
    }

    private void performBookSeat() {
        String seatId = seatGridPanel.getSelectedSeatId();
        if (seatId == null) {
            JOptionPane.showMessageDialog(this, "Please select an available seat first.", "No Seat Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BookSeatRequest request = new BookSeatRequest(tcpClient.nextRequestId(), showId, seatId);
        appendLog("Submitting booking request for seat " + seatId + "...");
        btnBook.setEnabled(false);
        sendRequestAsync(request, "BOOK_SEAT", () -> btnBook.setEnabled(true));
    }

    private void performHoldSeat() {
        String seatId = seatGridPanel.getSelectedSeatId();
        if (seatId == null) {
            return;
        }
        btnHold.setEnabled(false);
        sendRequestAsync(new HoldSeatRequest(tcpClient.nextRequestId(), showId, seatId, 120),
            "HOLD_SEAT", () -> btnHold.setEnabled(true), reply -> {
                if (reply instanceof HoldOkReply held) {
                    activeHoldId = held.holdId();
                    activeHoldSeatId = held.seatId();
                    activeHoldExpiresAt = held.expiresAt();
                    seatHoldExpiries.put(held.seatId(), held.expiresAt());
                    seatGridPanel.updateSingleSeat(SeatView.held(held.seatId(), userId));
                    btnConfirmHold.setEnabled(true);
                    btnReleaseHold.setEnabled(true);
                    updateHoldCountdown();
                    updateHomeSummaryFromGrid();
                    appendLog("Seat " + held.seatId() + " held until " + java.time.Instant.ofEpochMilli(held.expiresAt()));
                } else if (reply instanceof HoldRejectedReply rejected) {
                    btnHold.setEnabled(false);
                    JOptionPane.showMessageDialog(this, rejected.message(), "Seat Hold Rejected", JOptionPane.WARNING_MESSAGE);
                }
            });
    }

    private void confirmActiveHold() {
        String holdId = activeHoldId;
        if (holdId == null || activeHoldExpiresAt <= System.currentTimeMillis()) {
            updateHoldCountdown();
            return;
        }
        btnConfirmHold.setEnabled(false);
        sendRequestAsync(new ConfirmHoldRequest(tcpClient.nextRequestId(), holdId, UUID.randomUUID().toString()),
            "CONFIRM_HOLD", () -> btnConfirmHold.setEnabled(activeHoldId != null), reply -> {
                if (reply instanceof ConfirmOkReply confirmed) {
                    userBookings.put(confirmed.seatId(), confirmed.bookingId());
                    seatGridPanel.updateSingleSeat(SeatView.booked(confirmed.seatId(), userId));
                    clearActiveHold();
                    requestBookingHistory();
                    updateHomeSummaryFromGrid();
                    JOptionPane.showMessageDialog(this,
                        "Booking confirmed. Ticket ID: " + confirmed.ticketId(),
                        "Booking Confirmed", JOptionPane.INFORMATION_MESSAGE);
                } else if (reply instanceof ConfirmRejectedReply rejected) {
                    clearActiveHold();
                    JOptionPane.showMessageDialog(this, rejected.message(),
                        "Hold Confirmation Rejected", JOptionPane.WARNING_MESSAGE);
                }
            });
    }

    private void releaseActiveHold() {
        String holdId = activeHoldId;
        if (holdId == null) {
            return;
        }
        btnReleaseHold.setEnabled(false);
        sendRequestAsync(new ReleaseHoldRequest(tcpClient.nextRequestId(), holdId), "RELEASE_HOLD",
            () -> btnReleaseHold.setEnabled(activeHoldId != null), reply -> {
                if (reply instanceof HoldReleasedOkReply) {
                    clearActiveHold();
                    requestSeatSnapshot();
                }
            });
    }

    private void clearActiveHold() {
        if (activeHoldSeatId != null) {
            seatHoldExpiries.remove(activeHoldSeatId);
        }
        activeHoldId = null;
        activeHoldSeatId = null;
        activeHoldExpiresAt = 0;
        lblHoldCountdown.setText("No active seat hold");
        btnConfirmHold.setEnabled(false);
        btnReleaseHold.setEnabled(false);
    }

    private void updateHoldCountdown() {
        if (activeHoldId == null) {
            return;
        }
        long remaining = activeHoldExpiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            clearActiveHold();
            lblSeatState.setText("Your seat hold expired.");
            requestSeatSnapshot();
            return;
        }
        long seconds = (remaining + 999) / 1000;
        lblHoldCountdown.setText(String.format("Your hold expires in %02d:%02d",
            seconds / 60, seconds % 60));
        btnConfirmHold.setEnabled(true);
        btnReleaseHold.setEnabled(true);
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

        CancelBookingRequest request = new CancelBookingRequest(tcpClient.nextRequestId(), bookingId);
        appendLog("Submitting cancellation request for booking " + bookingId + "...");
        btnCancel.setEnabled(false);
        sendRequestAsync(request, "CANCEL_BOOKING", () -> btnCancel.setEnabled(true));
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out?", "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        btnLogout.setEnabled(false);
        networkExecutor.execute(() -> {
            try {
                LogoutRequest request = new LogoutRequest(tcpClient.nextRequestId());
                tcpClient.sendRequest(request).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                appendLog("[WARN] Logout could not be confirmed by the server.");
            } finally {
                String host = tcpClient.getHost();
                int port = tcpClient.getPort();
                tcpClient.disconnect();
                SwingUtilities.invokeLater(() -> {
                    dispose();
                    new LoginFrame(host, port).setVisible(true);
                });
            }
        });
    }

    private void sendRequestAsync(RequestMessage request, String operation, Runnable onFailure) {
        sendRequestAsync(request, operation, onFailure, reply -> {});
    }

    private void sendRequestAsync(RequestMessage request, String operation, Runnable onFailure,
            java.util.function.Consumer<ReplyMessage> onReply) {
        try {
            networkExecutor.execute(() -> {
                try {
                    CompletableFuture<ReplyMessage> response = tcpClient.sendRequest(request);
                    response.whenComplete((reply, failure) -> {
                        if (failure != null) {
                            SwingUtilities.invokeLater(() -> {
                                appendLog("[ERROR] " + operation + " failed: " + failure.getMessage());
                                onFailure.run();
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                if (reply instanceof ErrorReply error) {
                                    appendLog("[SERVER ERROR] " + operation + ": " + error.errorCode());
                                    onFailure.run();
                                }
                                onReply.accept(reply);
                            });
                        }
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("[ERROR] " + operation + " could not be sent: " + e.getMessage());
                        onFailure.run();
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            appendLog("[ERROR] " + operation + " could not be queued.");
            onFailure.run();
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
            lblSeatState.setText(reply.seats().isEmpty() ? "No seat data is available." : "Live seat availability");
            btnRefresh.setEnabled(true);
            updateHomeSummary(reply.seats());
            appendLog("Seat snapshot loaded: " + reply.seats().size() + " seats (Version " + reply.version() + ")");
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onBookSuccess(BookOkReply reply) {
        SwingUtilities.invokeLater(() -> {
            userBookings.put(reply.seatId(), reply.bookingId());
            seatGridPanel.updateSingleSeat(new SeatView(reply.seatId(), SeatStatus.BOOKED, userId));
            updateHomeSummaryFromGrid();
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
            updateHomeSummaryFromGrid();
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
            event.seats().stream()
                .filter(seat -> seat.status() != SeatStatus.HELD)
                .forEach(seat -> seatHoldExpiries.remove(seat.seatId()));
            updateHomeSummaryFromGrid();
            appendLog("BROADCAST: Live seat update received (" + event.seats().size() + " seats updated, Version " + event.version() + ")");
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onSeatHeld(SeatHeldEvent event) {
        if (!showId.equals(event.showId())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            seatHoldExpiries.put(event.seatId(), event.expiresAt());
            String holder = event.holdId().equals(activeHoldId) ? userId : null;
            seatGridPanel.updateSingleSeat(SeatView.held(event.seatId(), holder));
            updateHomeSummaryFromGrid();
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onSeatReleased(SeatReleasedEvent event) {
        if (!showId.equals(event.showId())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            seatHoldExpiries.remove(event.seatId());
            seatGridPanel.updateSingleSeat(SeatView.available(event.seatId()));
            if (event.holdId().equals(activeHoldId)) {
                clearActiveHold();
            }
            updateHomeSummaryFromGrid();
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onWaitlistPromoted(WaitlistPromotedEvent event) {
        if (!userId.equals(event.promotedUserId())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            activeHoldId = event.holdId();
            activeHoldSeatId = event.seatId();
            activeHoldExpiresAt = event.expiresAt();
            seatHoldExpiries.put(event.seatId(), event.expiresAt());
            seatGridPanel.updateSingleSeat(SeatView.held(event.seatId(), userId));
            updateHoldCountdown();
            updateHomeSummaryFromGrid();
            JOptionPane.showMessageDialog(this,
                "A seat is available for you: " + event.seatId() + ". Confirm before the offer expires.",
                "Waitlist Offer", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onShowUpdated(ShowUpdatedEvent event) {
        SwingUtilities.invokeLater(this::requestShows);
    }

    @Override
    public void onMetricsUpdated(com.seatsync.common.protocol.event.MetricsUpdatedEvent event) {
        if (role == UserRole.ADMIN) {
            SwingUtilities.invokeLater(() -> adminDashboard.append("\n\nLive metrics update:\n" + event.metrics()));
        }
    }

    @Override
    public void onError(ErrorReply error) {
        SwingUtilities.invokeLater(() -> {
            if (error.errorCode() == com.seatsync.common.model.ErrorCode.AUTH_REQUIRED) {
                lblSeatState.setText("Your session is not authenticated. Please log in again.");
            } else if (error.errorCode() == com.seatsync.common.model.ErrorCode.INTERNAL_ERROR) {
                lblSeatState.setText("The server could not complete the request. Please retry.");
            }
            btnRefresh.setEnabled(true);
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
            appendLog("[SERVER ERROR] " + error.message() + " (" + error.errorCode() + ")");
        });
    }

    private void updateHomeSummary(List<SeatView> seats) {
        long booked = seats.stream().filter(seat -> seat.status() == SeatStatus.BOOKED).count();
        long held = seats.stream().filter(seat -> seat.status() == SeatStatus.HELD).count();
        long available = seats.stream().filter(seat -> seat.status() == SeatStatus.AVAILABLE).count();
        lblHomeAvailable.setText(Long.toString(available));
        lblHomeBooked.setText(Long.toString(booked));
        lblHomeHeld.setText(Long.toString(held));
        lblHomeStatus.setText(seats.isEmpty()
            ? "No seat availability was returned by the server."
            : "Availability updated from the server.");
    }

    private void updateHomeSummaryFromGrid() {
        java.util.ArrayList<SeatView> seats = new java.util.ArrayList<>();
        for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
            SeatView seat = seatGridPanel.getSeatView(seatId);
            if (seat != null) {
                seats.add(seat);
            }
        }
        updateHomeSummary(seats);
    }

    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> {
            String transport = tcpClient.isTlsEnabled() ? " (TLS 1.3)" : " (plaintext)";
            lblConnectionStatus.setText("● Connected: " + tcpClient.getHost() + ":" + tcpClient.getPort() + transport);
            lblConnectionStatus.setForeground(new Color(46, 125, 50));
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblConnectionStatus.setText("● Disconnected: " + reason);
            lblConnectionStatus.setForeground(Color.RED);
            lblSeatState.setText("Connection unavailable. Reconnect to load seats.");
            lblHomeStatus.setText("Disconnected. Reconnect to refresh availability.");
            btnBook.setEnabled(false);
            btnCancel.setEnabled(false);
            btnRefresh.setEnabled(false);
            appendLog("[DISCONNECT] " + reason);
        });
    }

    @Override
    public void onConnectionError(Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            lblSeatState.setText("Connection error. Check the server address and retry.");
            lblHomeStatus.setText("Unable to reach the server.");
            appendLog("[CONNECTION ERROR] " + cause.getMessage());
        });
    }
}
