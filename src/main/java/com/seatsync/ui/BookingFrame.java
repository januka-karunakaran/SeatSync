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
import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.components.EmptyStatePanel;
import com.seatsync.ui.components.ModernButton;
import com.seatsync.ui.components.MovieCard;
import com.seatsync.ui.components.RoundedPanel;
import com.seatsync.ui.components.SectionHeader;
import com.seatsync.ui.components.StatusBadge;
import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import com.seatsync.ui.theme.UISpacing;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Image;
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

    // Stage 2: Cinematic Home & Movie Browsing Components
    private final transient java.util.List<MovieDetails> loadedMovies = new java.util.concurrent.CopyOnWriteArrayList<>();
    private transient JPanel homeMoviesContainer;
    private transient JPanel movieCardsGrid;
    private transient MovieDetails selectedMovie;
    private transient JLabel lblSelectedMovieTitle;
    private transient JLabel lblSelectedMovieGenre;
    private transient JLabel lblSelectedMovieDuration;
    private transient JTextArea txtSelectedMovieDesc;
    private transient JLabel lblSelectedMoviePoster;
    private transient ModernButton btnBookSelectedMovie;

    // Stage 3: Cinema Seat Booking UI Components
    private transient JLabel lblSeatMapPoster;
    private transient JLabel lblSeatMapMovieTitle;
    private transient JLabel lblSeatMapGenre;
    private transient JLabel lblPriceSeat;
    private transient JLabel lblPriceFee;
    private transient JLabel lblPriceTotal;

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
        setSize(1240, 800);
        setMinimumSize(new Dimension(960, 680));
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
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.SOFT_WHITE);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        SectionHeader header = new SectionHeader(
            "Cinema Seat Reservation",
            "Select an available seat to place a hold or book directly in real time",
            actionButton("Refresh Seats", this::requestSeatSnapshot)
        );
        page.add(header, BorderLayout.NORTH);

        // 3-Column Split
        JPanel threeColPanel = new JPanel(new BorderLayout(14, 0));
        threeColPanel.setOpaque(false);

        // 1. LEFT COLUMN: Movie & Show Info Card (FilmTIX style)
        RoundedPanel leftMovieCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        leftMovieCard.setLayout(new BoxLayout(leftMovieCard, BoxLayout.Y_AXIS));
        leftMovieCard.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        leftMovieCard.setPreferredSize(new Dimension(225, 0));

        lblSeatMapPoster = new JLabel();
        lblSeatMapPoster.setAlignmentX(CENTER_ALIGNMENT);

        lblSeatMapMovieTitle = new JLabel("Grand Cinema");
        lblSeatMapMovieTitle.setFont(UIFonts.TITLE_SMALL);
        lblSeatMapMovieTitle.setForeground(UIColors.TEXT_DARK);
        lblSeatMapMovieTitle.setAlignmentX(CENTER_ALIGNMENT);

        lblSeatMapGenre = new JLabel("Feature Presentation");
        lblSeatMapGenre.setFont(UIFonts.BADGE);
        lblSeatMapGenre.setForeground(UIColors.DEEP_PURPLE);
        lblSeatMapGenre.setAlignmentX(CENTER_ALIGNMENT);

        JPanel infoBlock = new JPanel(new GridLayout(4, 1, 0, 6));
        infoBlock.setOpaque(false);
        infoBlock.setBorder(BorderFactory.createEmptyBorder(12, 2, 12, 2));
        infoBlock.add(createInfoRow("Screen:", "Hall 1 (Atmos)"));
        infoBlock.add(createInfoRow("Format:", "Digital 2D"));
        infoBlock.add(createInfoRow("Price:", "$12.50"));
        infoBlock.add(createInfoRow("Sync:", "Live Active"));

        leftMovieCard.add(lblSeatMapPoster);
        leftMovieCard.add(Box.createVerticalStrut(8));
        leftMovieCard.add(lblSeatMapMovieTitle);
        leftMovieCard.add(Box.createVerticalStrut(4));
        leftMovieCard.add(lblSeatMapGenre);
        leftMovieCard.add(Box.createVerticalStrut(8));
        leftMovieCard.add(infoBlock);
        leftMovieCard.add(Box.createVerticalGlue());

        threeColPanel.add(leftMovieCard, BorderLayout.WEST);

        // 2. CENTER COLUMN: Cinema Screen & Seat Grid Card
        RoundedPanel centerCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        centerCard.setLayout(new BorderLayout(8, 8));
        centerCard.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        seatGridPanel = new SeatGridPanel();
        seatGridPanel.setCurrentUserId(userId);
        seatGridPanel.addSelectionListener(this::onSeatSelectionChanged);

        lblSeatState = new JLabel("Real-time availability for show " + showId, SwingConstants.CENTER);
        lblSeatState.setFont(UIFonts.BODY);
        lblSeatState.setForeground(UIColors.TEXT_MUTED);

        centerCard.add(lblSeatState, BorderLayout.NORTH);
        centerCard.add(seatGridPanel, BorderLayout.CENTER);
        threeColPanel.add(centerCard, BorderLayout.CENTER);

        // 3. RIGHT COLUMN: Booking Summary Card & Action Controls
        threeColPanel.add(createActionControlsPanel(), BorderLayout.EAST);

        page.add(threeColPanel, BorderLayout.CENTER);
        updateSeatMapMovieInfo();
        return page;
    }

    private JPanel createMoviesPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.SOFT_WHITE);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        movieRows = tableModel("Movie ID", "Title", "Genre", "Duration (min)");

        // Top actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
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

        SectionHeader header = new SectionHeader("Cinema Movies Catalog", "Browse active movie listings and select a movie to view details", actions);
        page.add(header, BorderLayout.NORTH);

        // Center split layout: Left = movie cards grid, Right = selected movie details
        JPanel splitPanel = new JPanel(new BorderLayout(16, 0));
        splitPanel.setOpaque(false);

        // Left Cards Grid (FilmTIX style)
        movieCardsGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 14));
        movieCardsGrid.setOpaque(false);
        JScrollPane scrollCards = new JScrollPane(movieCardsGrid);
        scrollCards.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.LIGHT_BORDER, 1),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        scrollCards.getViewport().setBackground(UIColors.SOFT_WHITE);
        scrollCards.getVerticalScrollBar().setUnitIncrement(14);
        splitPanel.add(scrollCards, BorderLayout.CENTER);

        // Right Detail Card (FilmTIX style)
        RoundedPanel detailCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        detailCard.setLayout(new BoxLayout(detailCard, BoxLayout.Y_AXIS));
        detailCard.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        detailCard.setPreferredSize(new Dimension(320, 0));

        lblSelectedMoviePoster = new JLabel();
        lblSelectedMoviePoster.setAlignmentX(CENTER_ALIGNMENT);

        lblSelectedMovieTitle = new JLabel("Select a Movie");
        lblSelectedMovieTitle.setFont(UIFonts.TITLE_MEDIUM);
        lblSelectedMovieTitle.setForeground(UIColors.TEXT_DARK);
        lblSelectedMovieTitle.setAlignmentX(CENTER_ALIGNMENT);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        badgeRow.setOpaque(false);
        lblSelectedMovieGenre = new JLabel("");
        lblSelectedMovieGenre.setFont(UIFonts.BADGE);
        lblSelectedMovieGenre.setForeground(UIColors.DEEP_PURPLE);
        lblSelectedMovieDuration = new JLabel("");
        lblSelectedMovieDuration.setFont(UIFonts.BADGE);
        lblSelectedMovieDuration.setForeground(UIColors.TEXT_MUTED);
        badgeRow.add(lblSelectedMovieGenre);
        badgeRow.add(lblSelectedMovieDuration);

        txtSelectedMovieDesc = new JTextArea("Click any movie card to inspect details and view available showtimes.");
        txtSelectedMovieDesc.setEditable(false);
        txtSelectedMovieDesc.setLineWrap(true);
        txtSelectedMovieDesc.setWrapStyleWord(true);
        txtSelectedMovieDesc.setFont(UIFonts.BODY_SMALL);
        txtSelectedMovieDesc.setForeground(UIColors.TEXT_MUTED);
        txtSelectedMovieDesc.setBackground(UIColors.PURE_WHITE);
        txtSelectedMovieDesc.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        btnBookSelectedMovie = new ModernButton("View Shows & Book", ModernButton.Variant.PRIMARY);
        btnBookSelectedMovie.setAlignmentX(CENTER_ALIGNMENT);
        btnBookSelectedMovie.setEnabled(false);
        btnBookSelectedMovie.addActionListener(e -> showPage("Shows"));

        detailCard.add(lblSelectedMoviePoster);
        detailCard.add(Box.createVerticalStrut(12));
        detailCard.add(lblSelectedMovieTitle);
        detailCard.add(Box.createVerticalStrut(6));
        detailCard.add(badgeRow);
        detailCard.add(Box.createVerticalStrut(10));
        detailCard.add(txtSelectedMovieDesc);
        detailCard.add(Box.createVerticalGlue());
        detailCard.add(btnBookSelectedMovie);

        splitPanel.add(detailCard, BorderLayout.EAST);
        page.add(splitPanel, BorderLayout.CENTER);

        lblMovieState = new JLabel("Loading movies...");
        lblMovieState.setFont(UIFonts.CAPTION);
        lblMovieState.setForeground(UIColors.TEXT_SECONDARY);
        page.add(lblMovieState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createShowsPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.SOFT_WHITE);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        showRows = tableModel("Show ID", "Movie Title", "Start Time", "Auditorium", "Available", "Total Capacity");
        showsTable = new JTable(showRows);
        styleTable(showsTable);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Refresh shows", this::requestShows));

        ModernButton btnOpenShow = new ModernButton("Open Selected Show", ModernButton.Variant.PRIMARY);
        btnOpenShow.setFont(UIFonts.custom(Font.BOLD, 12));
        btnOpenShow.addActionListener(e -> openSelectedShow());
        actions.add(btnOpenShow);

        if (role == UserRole.ADMIN) {
            actions.add(actionButton("Create show", this::showCreateShowDialog));
            actions.add(actionButton("Disable show", this::disableSelectedShow));
        }

        SectionHeader header = new SectionHeader(
            "Cinema Showtimes & Screenings",
            "Browse active screenings across cinema auditoriums and select a show to reserve seats",
            actions
        );
        page.add(header, BorderLayout.NORTH);

        RoundedPanel tableCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(showsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIColors.PURE_WHITE);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        page.add(tableCard, BorderLayout.CENTER);

        lblShowState = new JLabel("Loading shows...");
        lblShowState.setFont(UIFonts.CAPTION);
        lblShowState.setForeground(UIColors.TEXT_SECONDARY);
        page.add(lblShowState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createTicketsPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.SOFT_WHITE);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        ticketRows = tableModel("Booking ID", "Show", "Seat", "Show time", "Status", "Ticket ID");
        ticketsTable = new JTable(ticketRows);
        styleTable(ticketsTable);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Refresh history", this::requestBookingHistory));

        ModernButton btnViewTicket = new ModernButton("View Digital Pass", ModernButton.Variant.PRIMARY);
        btnViewTicket.setFont(UIFonts.custom(Font.BOLD, 12));
        btnViewTicket.addActionListener(e -> viewSelectedTicket());
        actions.add(btnViewTicket);

        SectionHeader header = new SectionHeader(
            "My Bookings & Digital Tickets",
            "Review your active reservations and inspect cryptographically verified cinema admission passes",
            actions
        );
        page.add(header, BorderLayout.NORTH);

        RoundedPanel tableCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(ticketsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIColors.PURE_WHITE);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        page.add(tableCard, BorderLayout.CENTER);

        lblTicketState = new JLabel("No booking history loaded.");
        lblTicketState.setFont(UIFonts.CAPTION);
        lblTicketState.setForeground(UIColors.TEXT_SECONDARY);
        page.add(lblTicketState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createWaitlistPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.SOFT_WHITE);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        SectionHeader header = new SectionHeader(
            "Show Waitlist & Standby Queue",
            "Join the automated standby queue for sold-out screenings and receive instant hold allocations"
        );
        page.add(header, BorderLayout.NORTH);

        RoundedPanel queueCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        queueCard.setLayout(new BoxLayout(queueCard, BoxLayout.Y_AXIS));
        queueCard.setBorder(BorderFactory.createEmptyBorder(30, 36, 30, 36));

        JLabel iconLabel = new JLabel("🎟️");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setAlignmentX(CENTER_ALIGNMENT);

        lblWaitlistStatus = new JLabel("Select a show to view or join its waitlist.", SwingConstants.CENTER);
        lblWaitlistStatus.setFont(UIFonts.TITLE_MEDIUM);
        lblWaitlistStatus.setForeground(UIColors.TEXT_DARK);
        lblWaitlistStatus.setAlignmentX(CENTER_ALIGNMENT);

        JLabel explanation = new JLabel("<html><center><p style='width:420px; color:#6C6A78; font-size:12px; line-height:1.5;'>"
            + "When seats in high demand sell out or holds expire, SeatSync's FIFO standby engine automatically "
            + "promotes queued patrons and reserves a temporary 2-minute booking window."
            + "</p></center></html>", SwingConstants.CENTER);
        explanation.setAlignmentX(CENTER_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(CENTER_ALIGNMENT);

        ModernButton btnCheck = new ModernButton("Check Status", ModernButton.Variant.SECONDARY);
        btnCheck.addActionListener(e -> requestWaitlistStatus());

        ModernButton btnJoin = new ModernButton("Join Standby Queue", ModernButton.Variant.PRIMARY);
        btnJoin.addActionListener(e -> joinWaitlist());

        ModernButton btnLeave = new ModernButton("Leave Queue", ModernButton.Variant.OUTLINE);
        btnLeave.addActionListener(e -> leaveWaitlist());

        actions.add(btnCheck);
        actions.add(btnJoin);
        actions.add(btnLeave);

        queueCard.add(Box.createVerticalGlue());
        queueCard.add(iconLabel);
        queueCard.add(Box.createVerticalStrut(16));
        queueCard.add(lblWaitlistStatus);
        queueCard.add(Box.createVerticalStrut(10));
        queueCard.add(explanation);
        queueCard.add(Box.createVerticalStrut(24));
        queueCard.add(actions);
        queueCard.add(Box.createVerticalGlue());

        page.add(queueCard, BorderLayout.CENTER);
        return page;
    }

    private JPanel createAdminPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.SOFT_WHITE);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Refresh metrics", this::requestAdminMetrics));
        actions.add(actionButton("Audit logs", this::requestAuditLogs));
        actions.add(actionButton("List bookings", this::requestAdminBookings));

        SectionHeader header = new SectionHeader(
            "Administrator Telemetry & Operations",
            "Monitor non-blocking Java NIO socket metrics, security audit trails, and master booking registry",
            actions
        );
        page.add(header, BorderLayout.NORTH);

        if (role != UserRole.ADMIN) {
            EmptyStatePanel restricted = new EmptyStatePanel("Administrator Access Required", "This console requires elevated administrative privileges to view telemetry and audit records.");
            page.add(restricted, BorderLayout.CENTER);
            return page;
        }

        RoundedPanel consoleCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.DEEP_CHARCOAL, UIColors.DARK_BORDER, 1, true);
        consoleCard.setLayout(new BorderLayout());
        consoleCard.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        adminDashboard = new JTextArea();
        adminDashboard.setEditable(false);
        adminDashboard.setLineWrap(true);
        adminDashboard.setWrapStyleWord(true);
        adminDashboard.setFont(new Font("Consolas", Font.PLAIN, 12));
        adminDashboard.setBackground(UIColors.DEEP_CHARCOAL);
        adminDashboard.setForeground(new Color(0xD8, 0xD0, 0xF2));
        adminDashboard.setCaretColor(Color.WHITE);
        adminDashboard.setText("SeatSync Administrator Telemetry Console\n"
            + "Press 'Refresh metrics' to query live non-blocking NIO socket metrics.\n"
            + "Press 'Audit logs' to inspect security access events.\n"
            + "Press 'List bookings' to review all system reservations.\n");

        JScrollPane scrollPane = new JScrollPane(adminDashboard);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIColors.DEEP_CHARCOAL);
        consoleCard.add(scrollPane, BorderLayout.CENTER);

        page.add(consoleCard, BorderLayout.CENTER);
        return page;
    }

    private static void styleTable(JTable table) {
        table.setFont(UIFonts.BODY);
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(UIColors.LIGHT_BORDER);
        table.setSelectionBackground(new Color(0xD8, 0xD0, 0xF2));
        table.setSelectionForeground(UIColors.TEXT_DARK);
        table.setFillsViewportHeight(true);
        table.setBackground(UIColors.PURE_WHITE);

        JTableHeader header = table.getTableHeader();
        header.setFont(UIFonts.BODY_BOLD);
        header.setBackground(UIColors.DEEP_CHARCOAL);
        header.setForeground(UIColors.PURE_WHITE);
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? UIColors.PURE_WHITE : new Color(0xFA, 0xFA, 0xFD));
                    setForeground(UIColors.TEXT_DARK);
                }
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
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

    private ModernButton actionButton(String title, Runnable action) {
        ModernButton button = new ModernButton(title, ModernButton.Variant.SECONDARY);
        button.setFont(UIFonts.custom(Font.BOLD, 12));
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel createHomePage() {
        JPanel home = new JPanel(new BorderLayout(14, 14));
        home.setBackground(UIColors.SOFT_WHITE);
        home.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // Top Cinematic Hero Section (Netflix inspiration with tilted movie poster collage)
        RoundedPanel heroPanel = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.SURFACE_DARK, UIColors.DARK_BORDER, 1, true) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    Image heroBg = ImageResourceManager.getCinematicHeroBanner(w, h);
                    if (heroBg != null) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        SeatSyncTheme.enableAntiAliasing(g2);
                        g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, UISpacing.RADIUS_LARGE, UISpacing.RADIUS_LARGE));
                        g2.drawImage(heroBg, 0, 0, w, h, this);
                        // Text scrim overlay to keep buttons and headlines razor sharp
                        GradientPaint textScrim = new GradientPaint(
                            0, 0, new Color(0x10, 0x10, 0x14, 230),
                            (int) (w * 0.60), 0, new Color(0x10, 0x10, 0x14, 75)
                        );
                        g2.setPaint(textScrim);
                        g2.fillRect(0, 0, w, h);
                        g2.dispose();
                    }
                }
            }
        };
        heroPanel.setLayout(new BorderLayout(20, 10));
        heroPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel heroTextPanel = new JPanel();
        heroTextPanel.setLayout(new BoxLayout(heroTextPanel, BoxLayout.Y_AXIS));
        heroTextPanel.setOpaque(false);

        JLabel welcomeLabel = new JLabel("Welcome back, " + username);
        welcomeLabel.setFont(UIFonts.TITLE_MEDIUM);
        welcomeLabel.setForeground(UIColors.LAVENDER_PRIMARY);
        welcomeLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel heroTitle = new JLabel("Grand Cinema Experience");
        heroTitle.setFont(UIFonts.HERO_TITLE);
        heroTitle.setForeground(UIColors.PURE_WHITE);
        heroTitle.setAlignmentX(LEFT_ALIGNMENT);

        lblShowTitle = new JLabel("Active Show: " + showId + " • Real-Time Synchronization Active");
        lblShowTitle.setFont(UIFonts.BODY);
        lblShowTitle.setForeground(new Color(0xD8, 0xD5, 0xE3));
        lblShowTitle.setAlignmentX(LEFT_ALIGNMENT);

        JPanel heroActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        heroActions.setOpaque(false);
        heroActions.setAlignmentX(LEFT_ALIGNMENT);
        ModernButton btnHeroBook = new ModernButton("Book Seats Now", ModernButton.Variant.PRIMARY);
        btnHeroBook.addActionListener(e -> showPage("Seat Map"));
        ModernButton btnHeroBrowse = new ModernButton("Browse Movies", ModernButton.Variant.SECONDARY);
        btnHeroBrowse.addActionListener(e -> showPage("Movies"));
        heroActions.add(btnHeroBook);
        heroActions.add(btnHeroBrowse);

        heroTextPanel.add(welcomeLabel);
        heroTextPanel.add(Box.createVerticalStrut(4));
        heroTextPanel.add(heroTitle);
        heroTextPanel.add(Box.createVerticalStrut(6));
        heroTextPanel.add(lblShowTitle);
        heroTextPanel.add(Box.createVerticalStrut(16));
        heroTextPanel.add(heroActions);

        heroPanel.add(heroTextPanel, BorderLayout.CENTER);

        // Center Content: Stats Cards + "Now Showing" Movies
        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.setOpaque(false);

        // 3 Live Inventory Stats Cards (FilmTIX rounded cards)
        JPanel statsRow = new JPanel(new GridLayout(1, 3, 14, 0));
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        statsRow.add(createSummaryCard("Available seats", UIColors.SUCCESS_GREEN, value -> lblHomeAvailable = value));
        statsRow.add(createSummaryCard("Booked seats", UIColors.DEEP_CHARCOAL, value -> lblHomeBooked = value));
        statsRow.add(createSummaryCard("Held seats", UIColors.WARNING_AMBER, value -> lblHomeHeld = value));

        centerContainer.add(Box.createVerticalStrut(10));
        centerContainer.add(statsRow);
        centerContainer.add(Box.createVerticalStrut(14));

        // Now Showing Section Header
        SectionHeader homeSectionHeader = new SectionHeader("Now Showing in Cinemas", "Select a featured movie to view showtimes and reserve seats");
        homeSectionHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        centerContainer.add(homeSectionHeader);
        centerContainer.add(Box.createVerticalStrut(8));

        // Dynamic Movie Cards Container
        homeMoviesContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        homeMoviesContainer.setOpaque(false);
        JScrollPane scrollMovies = new JScrollPane(homeMoviesContainer);
        scrollMovies.setBorder(BorderFactory.createEmptyBorder());
        scrollMovies.setOpaque(false);
        scrollMovies.getViewport().setOpaque(false);
        scrollMovies.getVerticalScrollBar().setUnitIncrement(12);
        centerContainer.add(scrollMovies);

        home.add(heroPanel, BorderLayout.NORTH);
        home.add(centerContainer, BorderLayout.CENTER);

        // Footer status
        lblHomeStatus = new JLabel("Loading current seat availability...", SwingConstants.CENTER);
        lblHomeStatus.setFont(UIFonts.CAPTION);
        lblHomeStatus.setForeground(UIColors.TEXT_SECONDARY);
        home.add(lblHomeStatus, BorderLayout.SOUTH);

        return home;
    }

    private JPanel createSummaryCard(String title, java.util.function.Consumer<JLabel> valueConsumer) {
        return createSummaryCard(title, UIColors.DEEP_CHARCOAL, valueConsumer);
    }

    private JPanel createSummaryCard(String title, Color accentColor, java.util.function.Consumer<JLabel> valueConsumer) {
        RoundedPanel card = new RoundedPanel(UISpacing.RADIUS_MEDIUM, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        card.setLayout(new BorderLayout(8, 4));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topRow.setOpaque(false);
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                SeatSyncTheme.enableAntiAliasing(g2);
                g2.setColor(accentColor);
                g2.fillOval(0, 3, 8, 8);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(10, 14));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIFonts.BODY_BOLD);
        titleLabel.setForeground(UIColors.TEXT_MUTED);
        topRow.add(dot);
        topRow.add(titleLabel);

        JLabel valueLabel = new JLabel("Loading...");
        valueLabel.setFont(UIFonts.TITLE_LARGE);
        valueLabel.setForeground(UIColors.TEXT_DARK);

        card.add(topRow, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        valueConsumer.accept(valueLabel);
        return card;
    }

    private void selectMovie(MovieDetails movie) {
        if (movie == null) {
            return;
        }
        this.selectedMovie = movie;
        updateSeatMapMovieInfo();
        if (lblSelectedMovieTitle != null) {
            lblSelectedMovieTitle.setText("<html><center>" + movie.title() + "</center></html>");
            lblSelectedMovieGenre.setText(movie.genre());
            lblSelectedMovieDuration.setText("• " + movie.durationMinutes() + " min");
            txtSelectedMovieDesc.setText(movie.description() != null && !movie.description().isBlank()
                ? movie.description()
                : "Experience " + movie.title() + " in premium high-definition audio and projection.");
            lblSelectedMoviePoster.setIcon(new ImageIcon(ImageResourceManager.getMoviePoster(movie.title(), movie.genre(), 180, 220)));
            btnBookSelectedMovie.setEnabled(true);
        }
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
        if (tcpClient == null) {
            return;
        }
        lblMovieState.setText("Loading movies...");
        sendRequestAsync(new GetMoviesRequest(tcpClient.nextRequestId(), moviePage, 5), "GET_MOVIES",
            () -> lblMovieState.setText("Movie request failed. Check the connection and retry."),
            reply -> {
                if (reply instanceof MovieListReply result) {
                    loadedMovies.clear();
                    loadedMovies.addAll(result.movies().items());
                    movieRows.setRowCount(0);
                    if (movieCardsGrid != null) {
                        movieCardsGrid.removeAll();
                    }
                    if (homeMoviesContainer != null) {
                        homeMoviesContainer.removeAll();
                    }

                    for (MovieDetails movie : loadedMovies) {
                        movieRows.addRow(new Object[] {
                            movie.movieId(), movie.title(), movie.genre(), movie.durationMinutes()
                        });
                        if (movieCardsGrid != null) {
                            MovieCard card = new MovieCard(movie, this::selectMovie);
                            movieCardsGrid.add(card);
                        }
                        if (homeMoviesContainer != null) {
                            MovieCard homeCard = new MovieCard(movie, m -> {
                                selectMovie(m);
                                showPage("Movies");
                            });
                            homeMoviesContainer.add(homeCard);
                        }
                    }

                    if (!loadedMovies.isEmpty() && selectedMovie == null) {
                        selectMovie(loadedMovies.get(0));
                    }

                    if (movieCardsGrid != null) {
                        movieCardsGrid.revalidate();
                        movieCardsGrid.repaint();
                    }
                    if (homeMoviesContainer != null) {
                        homeMoviesContainer.revalidate();
                        homeMoviesContainer.repaint();
                    }

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
        if (tcpClient == null) {
            return;
        }
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
        updateSeatMapMovieInfo();
        requestSeatSnapshot();
        showPage("Seat Map");
    }

    private void subscribeToSelectedShow() {
        sendRequestAsync(new SubscribeEventsRequest(tcpClient.nextRequestId(), showId), "SUBSCRIBE_EVENTS", () -> {});
    }

    private void requestBookingHistory() {
        if (tcpClient == null) {
            return;
        }
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
                    showDigitalTicketDialog(result.ticket());
                } else {
                    appendLog("[ERROR] Ticket request returned " + reply.getType());
                }
            });
    }

    private void showDigitalTicketDialog(com.seatsync.common.model.Ticket ticket) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        JDialog dialog = new JDialog(this, "Digital Cinema Admission Pass", true);
        dialog.setSize(420, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIColors.SOFT_WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        RoundedPanel passCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        passCard.setLayout(new BoxLayout(passCard, BoxLayout.Y_AXIS));
        passCard.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        brandRow.setOpaque(false);
        JLabel logo = new JLabel(new ImageIcon(ImageResourceManager.getSeatSyncLogo(28, 28)));
        JLabel brand = new JLabel("SeatSync Cinema Pass");
        brand.setFont(UIFonts.TITLE_SMALL);
        brand.setForeground(UIColors.TEXT_DARK);
        brandRow.add(logo);
        brandRow.add(brand);

        StatusBadge verifiedBadge = new StatusBadge("CRYPTOGRAPHICALLY VERIFIED", StatusBadge.Type.SUCCESS);
        verifiedBadge.setAlignmentX(CENTER_ALIGNMENT);

        RoundedPanel seatBox = new RoundedPanel(UISpacing.RADIUS_MEDIUM, UIColors.DEEP_CHARCOAL, UIColors.DEEP_CHARCOAL, 1, false);
        seatBox.setLayout(new BoxLayout(seatBox, BoxLayout.Y_AXIS));
        seatBox.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        seatBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        seatBox.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblSeatWord = new JLabel("RESERVED SEAT");
        lblSeatWord.setFont(UIFonts.BADGE);
        lblSeatWord.setForeground(UIColors.LAVENDER_PRIMARY);
        lblSeatWord.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblSeatValue = new JLabel(ticket.seatId());
        lblSeatValue.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblSeatValue.setForeground(UIColors.PURE_WHITE);
        lblSeatValue.setAlignmentX(CENTER_ALIGNMENT);

        seatBox.add(lblSeatWord);
        seatBox.add(Box.createVerticalStrut(2));
        seatBox.add(lblSeatValue);

        JPanel detailsGrid = new JPanel(new GridLayout(4, 1, 0, 8));
        detailsGrid.setOpaque(false);
        detailsGrid.setBorder(BorderFactory.createEmptyBorder(14, 4, 14, 4));
        detailsGrid.add(createInfoRow("Ticket ID:", ticket.ticketId()));
        detailsGrid.add(createInfoRow("Booking ID:", ticket.bookingId()));
        detailsGrid.add(createInfoRow("Cinema Show:", ticket.showId()));
        String issuedStr = java.time.Instant.ofEpochMilli(ticket.issuedAt()).toString();
        if (issuedStr.length() >= 19) {
            issuedStr = issuedStr.substring(0, 19).replace('T', ' ');
        }
        detailsGrid.add(createInfoRow("Issued At:", issuedStr));

        JPanel barcodePanel = new JPanel();
        barcodePanel.setLayout(new BoxLayout(barcodePanel, BoxLayout.Y_AXIS));
        barcodePanel.setOpaque(false);
        barcodePanel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel barcodeLines = new JLabel("||| | ||||| || |||||| | |||| ||| |||||||", SwingConstants.CENTER);
        barcodeLines.setFont(new Font("Monospaced", Font.BOLD, 18));
        barcodeLines.setForeground(UIColors.TEXT_DARK);
        barcodeLines.setAlignmentX(CENTER_ALIGNMENT);

        String hash = ticket.verificationHash();
        if (hash != null && hash.length() > 24) {
            hash = hash.substring(0, 24) + "...";
        }
        JLabel hashLabel = new JLabel("SHA-256: " + (hash != null ? hash : "N/A"), SwingConstants.CENTER);
        hashLabel.setFont(UIFonts.CAPTION);
        hashLabel.setForeground(UIColors.TEXT_MUTED);
        hashLabel.setAlignmentX(CENTER_ALIGNMENT);

        barcodePanel.add(barcodeLines);
        barcodePanel.add(Box.createVerticalStrut(4));
        barcodePanel.add(hashLabel);

        passCard.add(brandRow);
        passCard.add(Box.createVerticalStrut(10));
        passCard.add(verifiedBadge);
        passCard.add(Box.createVerticalStrut(14));
        passCard.add(seatBox);
        passCard.add(Box.createVerticalStrut(10));
        passCard.add(detailsGrid);
        passCard.add(Box.createVerticalStrut(10));
        passCard.add(barcodePanel);

        ModernButton btnClose = new ModernButton("Close Pass", ModernButton.Variant.PRIMARY);
        btnClose.setAlignmentX(CENTER_ALIGNMENT);
        btnClose.addActionListener(e -> dialog.dispose());

        content.add(passCard);
        content.add(Box.createVerticalStrut(12));
        content.add(btnClose);

        dialog.setContentPane(content);
        dialog.setVisible(true);
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
        if (tcpClient == null || lblWaitlistStatus == null) {
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
        if (tcpClient == null || role != UserRole.ADMIN) {
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
        if (tcpClient == null || role != UserRole.ADMIN) {
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
        if (tcpClient == null || role != UserRole.ADMIN) {
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
        header.setBackground(UIColors.DEEP_CHARCOAL);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.DARK_BORDER, 1),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        // Left info
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        infoPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Show: " + showId);
        titleLabel.setFont(UIFonts.TITLE_SMALL);
        titleLabel.setForeground(UIColors.PURE_WHITE);
        JLabel userLabel = new JLabel("User: " + username + " (" + role + ")");
        userLabel.setFont(UIFonts.BODY_SMALL);
        userLabel.setForeground(new Color(0xD8, 0xD5, 0xE3));
        infoPanel.add(titleLabel);
        infoPanel.add(userLabel);
        header.add(infoPanel, BorderLayout.WEST);

        // Right connection status
        JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusBox.setOpaque(false);
        boolean connected = tcpClient != null && tcpClient.isConnected();
        String transport = tcpClient != null && tcpClient.isTlsEnabled() ? " (TLS 1.3)" : " (plaintext)";
        lblConnectionStatus = new JLabel(connected
            ? "● Connected: " + tcpClient.getHost() + ":" + tcpClient.getPort() + transport
            : "● Disconnected");
        lblConnectionStatus.setFont(UIFonts.BODY_BOLD);
        lblConnectionStatus.setForeground(connected ? UIColors.SUCCESS_GREEN : UIColors.ERROR_RED);
        statusBox.add(lblConnectionStatus);

        btnLogout = new ModernButton("Logout", ModernButton.Variant.DANGER);
        btnLogout.setFont(UIFonts.custom(Font.BOLD, 12));
        btnLogout.addActionListener(e -> performLogout());
        statusBox.add(btnLogout);

        header.add(statusBox, BorderLayout.EAST);
        return header;
    }

    private JPanel createActionControlsPanel() {
        RoundedPanel summaryCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        summaryCard.setLayout(new BoxLayout(summaryCard, BoxLayout.Y_AXIS));
        summaryCard.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        summaryCard.setPreferredSize(new Dimension(295, 0));

        JLabel summaryTitle = new JLabel("Booking Summary");
        summaryTitle.setFont(UIFonts.TITLE_SMALL);
        summaryTitle.setForeground(UIColors.TEXT_DARK);
        summaryTitle.setAlignmentX(LEFT_ALIGNMENT);

        // Selected seat display
        lblSelectedSeat = new JLabel("Selected Seat: None");
        lblSelectedSeat.setFont(UIFonts.BODY_BOLD);
        lblSelectedSeat.setForeground(UIColors.TEXT_DARK);
        lblSelectedSeat.setAlignmentX(LEFT_ALIGNMENT);

        // Price breakdown box
        JPanel priceBox = new JPanel(new GridLayout(3, 2, 4, 4));
        priceBox.setOpaque(false);
        priceBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.LIGHT_BORDER, 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        priceBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        priceBox.setAlignmentX(LEFT_ALIGNMENT);

        priceBox.add(createPriceLabel("Standard Ticket:"));
        lblPriceSeat = createPriceValue("$0.00");
        priceBox.add(lblPriceSeat);

        priceBox.add(createPriceLabel("Booking Fee:"));
        lblPriceFee = createPriceValue("$0.00");
        priceBox.add(lblPriceFee);

        priceBox.add(createPriceLabel("Total:"));
        lblPriceTotal = createPriceValue("$0.00");
        lblPriceTotal.setFont(UIFonts.BODY_BOLD);
        priceBox.add(lblPriceTotal);

        // Hold countdown status badge / panel
        JPanel holdBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
        holdBox.setBackground(new Color(0xF4, 0xF1, 0xFA));
        holdBox.setBorder(BorderFactory.createLineBorder(UIColors.LIGHT_BORDER, 1));
        holdBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        holdBox.setAlignmentX(LEFT_ALIGNMENT);

        lblHoldCountdown = new JLabel("No active seat hold", SwingConstants.CENTER);
        lblHoldCountdown.setFont(UIFonts.CAPTION);
        lblHoldCountdown.setForeground(UIColors.TEXT_MUTED);
        holdBox.add(lblHoldCountdown);

        // Action Buttons
        btnHold = new ThemedButton("Hold Seat (2 min)", new Color(0xE6, 0xA2, 0x3C), Color.WHITE);
        btnHold.setFont(UIFonts.custom(Font.BOLD, 12));
        btnHold.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnHold.setAlignmentX(LEFT_ALIGNMENT);
        btnHold.setEnabled(false);
        btnHold.addActionListener(e -> performHoldSeat());

        btnConfirmHold = new ThemedButton("Confirm Booking", new Color(0x2E, 0x7D, 0x32), Color.WHITE);
        btnConfirmHold.setFont(UIFonts.custom(Font.BOLD, 12));
        btnConfirmHold.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnConfirmHold.setAlignmentX(LEFT_ALIGNMENT);
        btnConfirmHold.setEnabled(false);
        btnConfirmHold.addActionListener(e -> confirmActiveHold());

        btnBook = new ThemedButton("Book Directly", new Color(0x82, 0x74, 0xAB), Color.WHITE);
        btnBook.setFont(UIFonts.custom(Font.BOLD, 12));
        btnBook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnBook.setAlignmentX(LEFT_ALIGNMENT);
        btnBook.setEnabled(false);
        btnBook.addActionListener(e -> performBookSeat());

        btnReleaseHold = new ThemedButton("Release Hold", new Color(0x78, 0x71, 0x8A), Color.WHITE);
        btnReleaseHold.setFont(UIFonts.custom(Font.BOLD, 11));
        btnReleaseHold.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnReleaseHold.setAlignmentX(LEFT_ALIGNMENT);
        btnReleaseHold.setEnabled(false);
        btnReleaseHold.addActionListener(e -> releaseActiveHold());

        btnCancel = new ThemedButton("Cancel My Booking", new Color(0xC6, 0x28, 0x28), Color.WHITE);
        btnCancel.setFont(UIFonts.custom(Font.BOLD, 11));
        btnCancel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnCancel.setAlignmentX(LEFT_ALIGNMENT);
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> performCancelBooking());

        btnRefresh = new ThemedButton("Refresh Seats", new Color(0x2B, 0x2A, 0x36), Color.WHITE);
        btnRefresh.setFont(UIFonts.custom(Font.BOLD, 11));
        btnRefresh.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnRefresh.setAlignmentX(LEFT_ALIGNMENT);
        btnRefresh.addActionListener(e -> requestSeatSnapshot());

        summaryCard.add(summaryTitle);
        summaryCard.add(Box.createVerticalStrut(8));
        summaryCard.add(lblSelectedSeat);
        summaryCard.add(Box.createVerticalStrut(6));
        summaryCard.add(priceBox);
        summaryCard.add(Box.createVerticalStrut(6));
        summaryCard.add(holdBox);
        summaryCard.add(Box.createVerticalStrut(8));
        summaryCard.add(btnHold);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnConfirmHold);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnBook);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnReleaseHold);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnCancel);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnRefresh);
        summaryCard.add(Box.createVerticalGlue());

        JPanel logContainer = createLogPanel();
        logContainer.setAlignmentX(LEFT_ALIGNMENT);
        summaryCard.add(logContainer);

        return summaryCard;
    }

    private JPanel createLogPanel() {
        RoundedPanel panel = new RoundedPanel(UISpacing.RADIUS_SMALL, UIColors.SOFT_WHITE, UIColors.LIGHT_BORDER, 1, false);
        panel.setLayout(new BorderLayout());
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        panel.setPreferredSize(new Dimension(240, 70));

        logArea = new JTextArea(3, 18);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 10));
        logArea.setBackground(UIColors.SOFT_WHITE);
        logArea.setForeground(UIColors.TEXT_MUTED);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, BorderLayout.CENTER);

        appendLog("Session initialized for user " + username + " (" + userId + ")");
        return panel;
    }

    private void onSeatSelectionChanged(String seatId) {
        if (seatId == null) {
            lblSelectedSeat.setText("Selected Seat: None");
            if (lblPriceSeat != null) lblPriceSeat.setText("$0.00");
            if (lblPriceFee != null) lblPriceFee.setText("$0.00");
            if (lblPriceTotal != null) lblPriceTotal.setText("$0.00");
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
            statusDesc = "Your Booking";
        } else if (isHeld && seatId.equals(activeHoldSeatId)) {
            statusDesc = "Your Hold";
        } else if (isHeld) {
            statusDesc = "Held";
        } else if (isBooked) {
            statusDesc = "Booked";
        } else {
            statusDesc = "Available";
        }

        lblSelectedSeat.setText("Seat " + seatId + " • " + statusDesc);
        if (view != null && view.status() == SeatStatus.AVAILABLE) {
            if (lblPriceSeat != null) lblPriceSeat.setText("$12.50");
            if (lblPriceFee != null) lblPriceFee.setText("$1.50");
            if (lblPriceTotal != null) lblPriceTotal.setText("$14.00");
        } else {
            if (lblPriceSeat != null) lblPriceSeat.setText("$0.00");
            if (lblPriceFee != null) lblPriceFee.setText("$0.00");
            if (lblPriceTotal != null) lblPriceTotal.setText("$0.00");
        }
        btnBook.setEnabled(connected && view != null && view.status() == SeatStatus.AVAILABLE);
        btnHold.setEnabled(connected && view != null && view.status() == SeatStatus.AVAILABLE);
        btnCancel.setEnabled(connected && isBooked && (isMine || userBookings.containsKey(seatId)));
    }

    private void updateSeatMapMovieInfo() {
        if (lblSeatMapMovieTitle == null) {
            return;
        }
        String title = selectedMovie != null ? selectedMovie.title() : "Grand Cinema (" + showId + ")";
        String genre = selectedMovie != null ? selectedMovie.genre() : "Cinema Feature";
        int duration = selectedMovie != null ? selectedMovie.durationMinutes() : 120;

        lblSeatMapMovieTitle.setText("<html><center><b>" + title + "</b></center></html>");
        lblSeatMapGenre.setText(genre + " • " + duration + " min");
        if (lblSeatMapPoster != null) {
            lblSeatMapPoster.setIcon(new ImageIcon(ImageResourceManager.getMoviePoster(title, genre, 130, 160)));
        }
    }

    private JLabel createPriceLabel(String label) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIFonts.CAPTION);
        lbl.setForeground(UIColors.TEXT_MUTED);
        return lbl;
    }

    private JLabel createPriceValue(String value) {
        JLabel lbl = new JLabel(value, SwingConstants.RIGHT);
        lbl.setFont(UIFonts.CAPTION);
        lbl.setForeground(UIColors.TEXT_DARK);
        return lbl;
    }

    private JPanel createInfoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        JLabel lblKey = new JLabel(label);
        lblKey.setFont(UIFonts.CAPTION);
        lblKey.setForeground(UIColors.TEXT_MUTED);
        JLabel lblVal = new JLabel(value, SwingConstants.RIGHT);
        lblVal.setFont(UIFonts.BODY_BOLD);
        lblVal.setForeground(UIColors.TEXT_DARK);
        row.add(lblKey, BorderLayout.WEST);
        row.add(lblVal, BorderLayout.EAST);
        return row;
    }

    // =========================================================================
    // Protocol Operations
    // =========================================================================

    private void requestSeatSnapshot() {
        if (tcpClient == null) {
            return;
        }
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
