package com.seatsync.ui;

import com.seatsync.client.ClientMessageHandler;
import com.seatsync.client.SessionRecovery;
import com.seatsync.client.TcpClient;
import com.seatsync.reliability.ReconnectCoordinator;
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
import com.seatsync.common.protocol.reply.HoldSeatsOkReply;
import com.seatsync.common.protocol.reply.HoldRejectedReply;
import com.seatsync.common.protocol.reply.HoldReleasedOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.MovieListReply;
import com.seatsync.common.protocol.reply.NetworkMetricsReply;
import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.components.EmptyStatePanel;
import com.seatsync.ui.components.ModernButton;
import com.seatsync.ui.components.NoticeDialog;
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
import com.seatsync.common.protocol.reply.WaitlistLeftOkReply;
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
import com.seatsync.common.protocol.request.HoldSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.common.protocol.request.JoinWaitlistRequest;
import com.seatsync.common.protocol.request.LeaveWaitlistRequest;
import com.seatsync.common.protocol.request.LogoutRequest;
import com.seatsync.common.protocol.request.ReleaseHoldRequest;
import com.seatsync.common.protocol.request.SubscribeEventsRequest;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
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
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
    private final String sessionPassword;
    private final transient ReconnectCoordinator reconnectCoordinator = new ReconnectCoordinator();
    private final transient AtomicBoolean recoveryRunning = new AtomicBoolean(false);
    private volatile boolean keepSession = true;
    private volatile String showId;
    private final transient Map<String, ShowSummary> showsById = new ConcurrentHashMap<>();
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
    private transient JLabel lblSeatGuide;
    private transient JComponent priceBox;
    private transient JComponent holdBox;
    private transient JLabel lblConnectionStatus;
    private transient JLabel lblHeaderShow;
    private transient JLabel lblSeatVenue;
    private transient JLabel lblSeatWhen;
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
    private transient NavigationPanel navigation;
    private transient CardLayout pageLayout;
    private boolean showtimeChosen;
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
    private final transient java.util.List<String> activeHoldIds = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final transient java.util.List<String> activeHeldSeats = new java.util.concurrent.CopyOnWriteArrayList<>();
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
        this(tcpClient, username, userId, showId, role, null);
    }

    public BookingFrame(TcpClient tcpClient, String username, String userId, String showId, UserRole role,
            String sessionPassword) {
        super("SeatSync - Cinema Seat Reservation (" + username + ")");
        this.tcpClient = tcpClient;
        this.username = username;
        this.userId = userId;
        this.sessionPassword = sessionPassword;
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
            requestBookingHistory();
            subscribeToSelectedShow();
        }
        holdTimer = new Timer(1000, event -> updateHoldCountdown());
        holdTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                keepSession = false;
            }
        });
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 800);
        setMinimumSize(new Dimension(960, 680));
        setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(UIColors.CANVAS);
        setContentPane(contentPane);
        contentPane.add(createHeaderPanel(), BorderLayout.NORTH);

        navigation = new NavigationPanel(this::showPage);
        if (role != UserRole.ADMIN) {
            navigation.setPageVisible("Admin", false);
        }
        navigation.setPageEnabled("Seat Map", false);
        contentPane.add(navigation, BorderLayout.WEST);
        pageLayout = new CardLayout();
        pageCards = new JPanel(pageLayout);
        pageCards.setBackground(UIColors.CANVAS);
        pageCards.setOpaque(true);
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
        page.setBackground(UIColors.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        SectionHeader header = new SectionHeader(
            "Choose your seat",
            "Teal seats are open. Blue seats are already yours.",
            actionButton("Refresh seats", this::requestSeatSnapshot)
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
        lblSeatMapGenre.setForeground(UIColors.ACCENT);
        lblSeatMapGenre.setAlignmentX(CENTER_ALIGNMENT);

        lblSeatVenue = new JLabel("Main auditorium", SwingConstants.RIGHT);
        lblSeatWhen = new JLabel("Choose a showtime", SwingConstants.RIGHT);
        JLabel priceValue = new JLabel("$12.50", SwingConstants.RIGHT);
        JLabel liveValue = new JLabel("Updates live", SwingConstants.RIGHT);
        for (JLabel value : List.of(lblSeatVenue, lblSeatWhen, priceValue, liveValue)) {
            value.setFont(UIFonts.BODY_BOLD);
            value.setForeground(UIColors.TEXT_DARK);
        }
        JPanel infoBlock = new JPanel(new GridLayout(4, 1, 0, 6));
        infoBlock.setOpaque(false);
        infoBlock.setBorder(BorderFactory.createEmptyBorder(12, 2, 12, 2));
        infoBlock.add(labeledValue("Where", lblSeatVenue));
        infoBlock.add(labeledValue("When", lblSeatWhen));
        infoBlock.add(labeledValue("Price", priceValue));
        infoBlock.add(labeledValue("Seats", liveValue));

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

        lblSeatState = new JLabel("Teal seats are open. Blue seats are already yours.", SwingConstants.CENTER);
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
        page.setBackground(UIColors.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        movieRows = tableModel("Title", "Genre", "Length");

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

        SectionHeader header = new SectionHeader("Movies", "Step 1. Choose a film. Then open its showtimes.", actions);
        page.add(header, BorderLayout.NORTH);

        // Center split layout: Left = movie cards grid, Right = selected movie details
        JPanel splitPanel = new JPanel(new BorderLayout(16, 0));
        splitPanel.setOpaque(false);

        // Left Cards Grid (FilmTIX style)
        movieCardsGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 14));
        movieCardsGrid.setOpaque(false);
        JScrollPane scrollCards = new JScrollPane(movieCardsGrid);
        scrollCards.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        scrollCards.setOpaque(false);
        scrollCards.getViewport().setOpaque(false);
        scrollCards.getViewport().setBackground(UIColors.CANVAS);
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
        lblSelectedMovieGenre.setForeground(UIColors.ACCENT);
        lblSelectedMovieDuration = new JLabel("");
        lblSelectedMovieDuration.setFont(UIFonts.BADGE);
        lblSelectedMovieDuration.setForeground(UIColors.TEXT_MUTED);
        badgeRow.add(lblSelectedMovieGenre);
        badgeRow.add(lblSelectedMovieDuration);

        txtSelectedMovieDesc = new JTextArea("Select a movie to see what it is about, then continue to showtimes.");
        txtSelectedMovieDesc.setEditable(false);
        txtSelectedMovieDesc.setLineWrap(true);
        txtSelectedMovieDesc.setWrapStyleWord(true);
        txtSelectedMovieDesc.setFont(UIFonts.BODY_SMALL);
        txtSelectedMovieDesc.setForeground(UIColors.TEXT_MUTED);
        txtSelectedMovieDesc.setBackground(UIColors.PURE_WHITE);
        txtSelectedMovieDesc.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        btnBookSelectedMovie = new ModernButton("See showtimes", ModernButton.Variant.PRIMARY);
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
        page.setBackground(UIColors.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        showRows = tableModel("Show ID", "Movie", "When", "Auditorium", "Seats left", "Seats");
        showsTable = new JTable(showRows);
        styleTable(showsTable);
        hideColumn(showsTable, 0);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Refresh showtimes", this::requestShows));

        ModernButton btnOpenShow = new ModernButton("Choose seats", ModernButton.Variant.PRIMARY);
        btnOpenShow.setFont(UIFonts.custom(Font.BOLD, 12));
        btnOpenShow.addActionListener(e -> openSelectedShow());
        actions.add(btnOpenShow);

        if (role == UserRole.ADMIN) {
            actions.add(actionButton("Add showtime", this::showCreateShowDialog));
            actions.add(actionButton("Cancel showtime", this::disableSelectedShow));
        }

        SectionHeader header = new SectionHeader(
            "Showtimes",
            "Step 2. Select this film's showtime, then choose your seats.",
            actions
        );
        page.add(header, BorderLayout.NORTH);

        // Auditorium Screenings Banner (Cinema Auditorium & Stage Art)
        RoundedPanel showsBanner = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.SURFACE_DARK, UIColors.DARK_BORDER, 1, true) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    Image bannerImg = ImageResourceManager.getCinemaAuditoriumBanner(w, h);
                    if (bannerImg != null) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        SeatSyncTheme.enableAntiAliasing(g2);
                        g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, UISpacing.RADIUS_LARGE, UISpacing.RADIUS_LARGE));
                        g2.drawImage(bannerImg, 0, 0, w, h, this);
                        g2.dispose();
                    }
                }
            }
        };
        showsBanner.setLayout(new BorderLayout(14, 6));
        showsBanner.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        showsBanner.setPreferredSize(new Dimension(0, 100));
        showsBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JPanel bannerText = new JPanel();
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        bannerText.setOpaque(false);

        JLabel bannerTag = new JLabel("FEATURED SCREENINGS & AUDITORIUMS");
        bannerTag.setFont(UIFonts.BADGE);
        bannerTag.setForeground(new Color(0xFB, 0xC4, 0xD0));

        JLabel bannerTitle = new JLabel("Experience Blockbusters in IMAX & Dolby Surround");
        bannerTitle.setFont(UIFonts.TITLE_MEDIUM);
        bannerTitle.setForeground(UIColors.PURE_WHITE);

        JLabel bannerSub = new JLabel("Select a showtime below, then choose an open seat.");
        bannerSub.setFont(UIFonts.BODY_SMALL);
        bannerSub.setForeground(new Color(0xD8, 0xD5, 0xE3));

        bannerText.add(bannerTag);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(bannerTitle);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(bannerSub);
        showsBanner.add(bannerText, BorderLayout.CENTER);

        RoundedPanel tableCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(showsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIColors.PURE_WHITE);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.setOpaque(false);
        centerContainer.add(showsBanner);
        centerContainer.add(Box.createVerticalStrut(10));
        centerContainer.add(tableCard);

        page.add(centerContainer, BorderLayout.CENTER);

        lblShowState = new JLabel("Loading shows...");
        lblShowState.setFont(UIFonts.CAPTION);
        lblShowState.setForeground(UIColors.TEXT_SECONDARY);
        page.add(lblShowState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createTicketsPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        ticketRows = tableModel("Booking ID", "Movie", "Seat", "When", "Status", "Ticket ID");
        ticketsTable = new JTable(ticketRows);
        styleTable(ticketsTable);
        hideColumn(ticketsTable, 0);
        hideColumn(ticketsTable, 5);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Refresh tickets", this::requestBookingHistory));

        ModernButton btnViewTicket = new ModernButton("View ticket", ModernButton.Variant.PRIMARY);
        btnViewTicket.setFont(UIFonts.custom(Font.BOLD, 12));
        btnViewTicket.addActionListener(e -> viewSelectedTicket());
        actions.add(btnViewTicket);

        SectionHeader header = new SectionHeader(
            "My tickets",
            "Tickets you have booked. Select one to open it.",
            actions
        );
        page.add(header, BorderLayout.NORTH);

        // Cinema Concessions & Ticket Pass Banner (Popcorn, Soda & Golden Ticket Art)
        RoundedPanel concessionsBanner = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.SURFACE_DARK, UIColors.DARK_BORDER, 1, true) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    Image bannerImg = ImageResourceManager.getPopcornConcessionsBanner(w, h);
                    if (bannerImg != null) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        SeatSyncTheme.enableAntiAliasing(g2);
                        g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, UISpacing.RADIUS_LARGE, UISpacing.RADIUS_LARGE));
                        g2.drawImage(bannerImg, 0, 0, w, h, this);
                        g2.dispose();
                    }
                }
            }
        };
        concessionsBanner.setLayout(new BorderLayout(14, 6));
        concessionsBanner.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        concessionsBanner.setPreferredSize(new Dimension(0, 100));
        concessionsBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JPanel bannerText = new JPanel();
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        bannerText.setOpaque(false);

        JLabel bannerTag = new JLabel("CINEMA CONCESSIONS & ADMISSION");
        bannerTag.setFont(UIFonts.BADGE);
        bannerTag.setForeground(new Color(0xFB, 0xC4, 0xD0));

        JLabel bannerTitle = new JLabel("Your tickets stay with this account");
        bannerTitle.setFont(UIFonts.TITLE_MEDIUM);
        bannerTitle.setForeground(UIColors.PURE_WHITE);

        JLabel bannerSub = new JLabel("Open a ticket to see the movie, seat, and when it was booked.");
        bannerSub.setFont(UIFonts.BODY_SMALL);
        bannerSub.setForeground(new Color(0xD8, 0xD5, 0xE3));

        bannerText.add(bannerTag);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(bannerTitle);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(bannerSub);
        concessionsBanner.add(bannerText, BorderLayout.CENTER);

        RoundedPanel tableCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(ticketsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIColors.PURE_WHITE);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.setOpaque(false);
        centerContainer.add(concessionsBanner);
        centerContainer.add(Box.createVerticalStrut(10));
        centerContainer.add(tableCard);

        page.add(centerContainer, BorderLayout.CENTER);

        lblTicketState = new JLabel("No booking history loaded.");
        lblTicketState.setFont(UIFonts.CAPTION);
        lblTicketState.setForeground(UIColors.TEXT_SECONDARY);
        page.add(lblTicketState, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createWaitlistPage() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(UIColors.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        SectionHeader header = new SectionHeader(
            "Waitlist",
            "If the show you want is full, join the queue. A free seat is held for you for 2 minutes."
        );
        page.add(header, BorderLayout.NORTH);

        RoundedPanel queueCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        queueCard.setLayout(new BoxLayout(queueCard, BoxLayout.Y_AXIS));
        queueCard.setBorder(BorderFactory.createEmptyBorder(30, 36, 30, 36));

        JLabel iconLabel = new JLabel("🎟️");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setAlignmentX(CENTER_ALIGNMENT);

        lblWaitlistStatus = new JLabel("Join the queue for the showtime you selected.", SwingConstants.CENTER);
        lblWaitlistStatus.setFont(UIFonts.TITLE_MEDIUM);
        lblWaitlistStatus.setForeground(UIColors.TEXT_DARK);
        lblWaitlistStatus.setAlignmentX(CENTER_ALIGNMENT);

        JLabel explanation = new JLabel("<html><center><p style='width:420px; color:#6C6A78; font-size:12px; line-height:1.5;'>"
            + "Use this after you have chosen a showtime. If a seat opens, you will get two minutes to confirm it."
            + "</p></center></html>", SwingConstants.CENTER);
        explanation.setAlignmentX(CENTER_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(CENTER_ALIGNMENT);

        ModernButton btnJoin = new ModernButton("Join the queue", ModernButton.Variant.PRIMARY);
        btnJoin.addActionListener(e -> joinWaitlist());

        ModernButton btnCheck = new ModernButton("Check my place", ModernButton.Variant.SECONDARY);
        btnCheck.addActionListener(e -> requestWaitlistStatus());

        ModernButton btnLeave = new ModernButton("Leave the queue", ModernButton.Variant.OUTLINE);
        btnLeave.addActionListener(e -> leaveWaitlist());

        actions.add(btnJoin);
        actions.add(btnCheck);
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
        page.setBackground(UIColors.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Refresh metrics", this::requestAdminMetrics));
        actions.add(actionButton("Audit logs", this::requestAuditLogs));
        actions.add(actionButton("List bookings", this::requestAdminBookings));

        SectionHeader header = new SectionHeader(
            "Staff tools",
            "Check how busy the cinema is, review activity, and look up bookings.",
            actions
        );
        page.add(header, BorderLayout.NORTH);

        if (role != UserRole.ADMIN) {
            EmptyStatePanel restricted = new EmptyStatePanel("Staff only", "These tools are available to cinema staff.");
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
        adminDashboard.setText("Staff tools\n"
            + "Refresh metrics shows how the cinema is running.\n"
            + "Audit logs shows recent sign-in and booking activity.\n"
            + "List bookings shows every reservation.\n");

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
        table.setSelectionBackground(new Color(0xFB, 0xE4, 0xEA));
        table.setSelectionForeground(UIColors.TEXT_DARK);
        table.setFillsViewportHeight(true);
        table.setBackground(UIColors.PURE_WHITE);

        JTableHeader header = table.getTableHeader();
        header.setFont(UIFonts.BODY_BOLD);
        header.setBackground(new Color(0x3F, 0x2A, 0x32));
        header.setForeground(new Color(0xFF, 0xF7, 0xF2));
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer((table1, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value == null ? "" : value.toString());
            label.setOpaque(true);
            label.setBackground(new Color(0x3F, 0x2A, 0x32));
            label.setForeground(new Color(0xFF, 0xF7, 0xF2));
            label.setFont(UIFonts.BODY_BOLD);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return label;
        });

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? UIColors.PURE_WHITE : new Color(0xFB, 0xF7, 0xF3));
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
        home.setBackground(UIColors.CANVAS);
        home.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // Top Cinematic Hero Section (Auditorium Screen & Online Booking Banner)
        RoundedPanel heroPanel = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.SURFACE_DARK, UIColors.DARK_BORDER, 1, true) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    Image heroBg = ImageResourceManager.getCinemaAuditoriumBanner(w, h);
                    if (heroBg != null) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        SeatSyncTheme.enableAntiAliasing(g2);
                        g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, UISpacing.RADIUS_LARGE, UISpacing.RADIUS_LARGE));
                        g2.drawImage(heroBg, 0, 0, w, h, this);
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
        welcomeLabel.setForeground(new Color(0xFB, 0xC4, 0xD0));
        welcomeLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel heroTitle = new JLabel("What would you like to see?");
        heroTitle.setFont(UIFonts.HERO_TITLE);
        heroTitle.setForeground(UIColors.PURE_WHITE);
        heroTitle.setAlignmentX(LEFT_ALIGNMENT);

        lblShowTitle = new JLabel("Start with a movie, then pick a showtime and a seat.");
        lblShowTitle.setFont(UIFonts.BODY);
        lblShowTitle.setForeground(new Color(0xD8, 0xD5, 0xE3));
        lblShowTitle.setAlignmentX(LEFT_ALIGNMENT);

        JPanel heroActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        heroActions.setOpaque(false);
        heroActions.setAlignmentX(LEFT_ALIGNMENT);
        ModernButton btnHeroBrowse = new ModernButton("1   Browse movies", ModernButton.Variant.PRIMARY);
        btnHeroBrowse.addActionListener(e -> showPage("Movies"));
        ModernButton btnHeroBook = new ModernButton("2   Pick a showtime", ModernButton.Variant.SECONDARY);
        btnHeroBook.addActionListener(e -> showPage(selectedMovie == null ? "Movies" : "Shows"));
        ModernButton btnHeroSeats = new ModernButton("3   Choose seats", ModernButton.Variant.SECONDARY);
        btnHeroSeats.addActionListener(e -> showPage("Seat Map"));
        heroActions.add(btnHeroBrowse);
        heroActions.add(btnHeroBook);
        heroActions.add(btnHeroSeats);

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
        SectionHeader homeSectionHeader = new SectionHeader("Now showing", "Choose a film to continue to its showtimes.");
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
        showtimeChosen = false;
        if (navigation != null) {
            navigation.setPageEnabled("Seat Map", false);
        }
        refreshShowLabels();
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

    void showPage(String page) {
        if ("Seat Map".equals(page) && !showtimeChosen) {
            if (selectedMovie == null) {
                page = "Movies";
                if (lblMovieState != null) {
                    lblMovieState.setText("Choose a film first. Then pick its showtime. Seats open only after that.");
                }
            } else {
                page = "Shows";
                if (lblShowState != null) {
                    lblShowState.setText("Choose a showtime for " + selectedMovie.title()
                        + ". Its seats open only after you select that showtime.");
                }
            }
            if (navigation != null) {
                navigation.select(page);
            }
        }
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
                            movie.title(), movie.genre(), movie.durationMinutes() + " min"
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
                        ? "No movies are showing right now."
                        : result.movies().items().size() + " movies. Choose one, then open its showtimes.");
                    btnPreviousMovies.setEnabled(result.movies().pageNumber() > 0);
                    btnNextMovies.setEnabled(result.movies().hasMore());
                } else {
                    lblMovieState.setText("Movies could not be loaded. Try Refresh movies.");
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
                    showsById.clear();
                    int listed = 0;
                    for (ShowSummary show : result.shows()) {
                        showsById.put(show.showId(), show);
                        if (selectedMovie != null && !selectedMovie.title().equalsIgnoreCase(show.title())) {
                            continue;
                        }
                        showRows.addRow(new Object[] {
                            show.showId(),
                            show.title(),
                            friendlyTime(show.startTime()),
                            show.venue(),
                            show.availableSeats() + " left",
                            show.totalSeats()
                        });
                        listed++;
                    }
                    refreshShowLabels();
                    requestBookingHistory();
                    if (selectedMovie != null) {
                        lblShowState.setText(listed == 0
                            ? "No showtimes for " + selectedMovie.title() + "."
                            : listed + (listed == 1 ? " showtime for " : " showtimes for ")
                                + selectedMovie.title() + ". Select one, then choose your seats.");
                    } else {
                        lblShowState.setText(listed == 0
                            ? "No showtimes are available."
                            : listed + " showtimes. Select one, then choose your seats.");
                    }
                } else {
                    lblShowState.setText("Showtimes could not be loaded. Try Refresh showtimes.");
                    appendLog("[ERROR] Show catalog request returned " + reply.getType());
                }
            });
    }

    private void openSelectedShow() {
        int row = showsTable.getSelectedRow();
        if (row < 0) {
            NoticeDialog.show(this, "Choose a showtime", "Select a showtime first.", NoticeDialog.Kind.INFO);
            return;
        }
        String previousShowId = showId;
        showId = showsTable.getValueAt(row, 0).toString();
        if (!previousShowId.equals(showId)) {
            for (String holdId : java.util.List.copyOf(activeHoldIds)) {
                sendRequestAsync(new ReleaseHoldRequest(tcpClient.nextRequestId(), holdId),
                    "RELEASE_HOLD", () -> {});
            }
            activeHoldIds.clear();
            activeHeldSeats.clear();
            reconnectCoordinator.untrackShowSubscription(previousShowId);
            sendRequestAsync(new com.seatsync.common.protocol.request.UnsubscribeEventsRequest(
                tcpClient.nextRequestId(), previousShowId), "UNSUBSCRIBE_EVENTS", () -> {});
            seatHoldExpiries.clear();
            activeHoldId = null;
            activeHoldSeatId = null;
            activeHoldExpiresAt = 0;
            subscribeToSelectedShow();
        }
        String chosenTitle = showTitle(showId);
        for (MovieDetails movie : loadedMovies) {
            if (movie.title() != null && movie.title().equalsIgnoreCase(chosenTitle)) {
                selectedMovie = movie;
                break;
            }
        }
        refreshShowLabels();
        updateSeatMapMovieInfo();
        requestSeatSnapshot();
        showtimeChosen = true;
        if (navigation != null) {
            navigation.setPageEnabled("Seat Map", true);
            navigation.select("Seat Map");
        }
        showPage("Seat Map");
    }

    private void subscribeToSelectedShow() {
        reconnectCoordinator.trackShowSubscription(showId);
        sendRequestAsync(new SubscribeEventsRequest(tcpClient.nextRequestId(), showId), "SUBSCRIBE_EVENTS", () -> {});
    }

    private void requestBookingHistory() {
        if (tcpClient == null) {
            return;
        }
        lblTicketState.setText("Loading your tickets...");
        sendRequestAsync(new GetBookingHistoryRequest(tcpClient.nextRequestId(), 0, 20), "GET_BOOKING_HISTORY",
            () -> lblTicketState.setText("History request failed. Check the connection and retry."),
            reply -> {
                if (reply instanceof BookingHistoryReply result) {
                    ticketRows.setRowCount(0);
                    result.history().items().forEach(item -> {
                        if ("CONFIRMED".equalsIgnoreCase(item.status()) && item.seatId() != null && item.bookingId() != null) {
                            userBookings.putIfAbsent(item.seatId(), item.bookingId());
                        }
                        String when = item.showTime();
                        if (when == null || when.isBlank() || "Scheduled".equals(when)) {
                            ShowSummary show = showsById.get(item.showId());
                            when = show == null ? when : show.startTime();
                        }
                        ticketRows.addRow(new Object[] {
                        item.bookingId(),
                        displayShowTitle(item.showId(), item.showTitle()),
                        item.seatId(),
                        friendlyTime(when),
                        friendlyStatus(item.status()),
                        item.ticketId()
                    });
                    });
                    lblTicketState.setText(result.history().items().isEmpty()
                        ? "You have no tickets yet."
                        : result.history().items().size() + " ticket"
                            + (result.history().items().size() == 1 ? "" : "s") + ".");
                } else {
                    lblTicketState.setText("Tickets could not be loaded. Try Refresh tickets.");
                    appendLog("[ERROR] Booking history request returned " + reply.getType());
                }
            });
    }

    private void viewSelectedTicket() {
        int row = ticketsTable.getSelectedRow();
        if (row < 0 || ticketsTable.getValueAt(row, 5) == null) {
            NoticeDialog.show(this, "No ticket selected", "Select a ticket first.", NoticeDialog.Kind.INFO);
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
        JDialog dialog = new JDialog(this, "Your ticket", true);
        dialog.setSize(420, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIColors.CANVAS);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        RoundedPanel passCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        passCard.setLayout(new BoxLayout(passCard, BoxLayout.Y_AXIS));
        passCard.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        brandRow.setOpaque(false);
        JLabel logo = new JLabel(new ImageIcon(ImageResourceManager.getSeatSyncLogo(28, 28)));
        JLabel brand = new JLabel("SeatSync ticket");
        brand.setFont(UIFonts.TITLE_SMALL);
        brand.setForeground(UIColors.TEXT_DARK);
        brandRow.add(logo);
        brandRow.add(brand);

        StatusBadge verifiedBadge = new StatusBadge("CONFIRMED", StatusBadge.Type.SUCCESS);
        verifiedBadge.setAlignmentX(CENTER_ALIGNMENT);

        RoundedPanel seatBox = new RoundedPanel(UISpacing.RADIUS_MEDIUM, UIColors.DEEP_CHARCOAL, UIColors.DEEP_CHARCOAL, 1, false);
        seatBox.setLayout(new BoxLayout(seatBox, BoxLayout.Y_AXIS));
        seatBox.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        seatBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        seatBox.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblSeatWord = new JLabel("YOUR SEAT");
        lblSeatWord.setFont(UIFonts.BADGE);
        lblSeatWord.setForeground(new Color(0xFB, 0xC4, 0xD0));
        lblSeatWord.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblSeatValue = new JLabel(ticket.seatId());
        lblSeatValue.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblSeatValue.setForeground(UIColors.PURE_WHITE);
        lblSeatValue.setAlignmentX(CENTER_ALIGNMENT);

        seatBox.add(lblSeatWord);
        seatBox.add(Box.createVerticalStrut(2));
        seatBox.add(lblSeatValue);

        JPanel detailsGrid = new JPanel(new GridLayout(2, 1, 0, 8));
        detailsGrid.setOpaque(false);
        detailsGrid.setBorder(BorderFactory.createEmptyBorder(14, 4, 14, 4));
        detailsGrid.add(createInfoRow("Movie", displayShowTitle(ticket.showId(), null)));
        String issuedStr = java.time.Instant.ofEpochMilli(ticket.issuedAt()).toString();
        if (issuedStr.length() >= 16) {
            issuedStr = issuedStr.substring(0, 16).replace('T', ' ');
        }
        detailsGrid.add(createInfoRow("Booked", issuedStr));

        passCard.add(brandRow);
        passCard.add(Box.createVerticalStrut(10));
        passCard.add(verifiedBadge);
        passCard.add(Box.createVerticalStrut(14));
        passCard.add(seatBox);
        passCard.add(Box.createVerticalStrut(10));
        passCard.add(detailsGrid);

        ModernButton btnClose = new ModernButton("Close", ModernButton.Variant.PRIMARY);
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
                    lblWaitlistStatus.setText("You are number " + result.position()
                        + " for " + showTitle(result.showId()) + ".");
                } else {
                    lblWaitlistStatus.setText("Could not join the queue. Choose a showtime and try again.");
                }
            });
    }

    private void leaveWaitlist() {
        sendRequestAsync(new LeaveWaitlistRequest(tcpClient.nextRequestId(), showId), "LEAVE_WAITLIST", () -> {},
            reply -> lblWaitlistStatus.setText(reply instanceof WaitlistLeftOkReply
                ? "You left the queue for " + showTitle(showId) + "."
                : "Could not leave the queue."));
    }

    private void requestWaitlistStatus() {
        if (tcpClient == null || lblWaitlistStatus == null) {
            return;
        }
        sendRequestAsync(new GetWaitlistStatusRequest(tcpClient.nextRequestId(), showId), "GET_WAITLIST_STATUS", () -> {},
            reply -> {
                if (reply instanceof WaitlistStatusReply result) {
                    lblWaitlistStatus.setText(result.position() <= 0
                        ? "You are not in the queue for " + showTitle(showId) + "."
                        : "You are number " + result.position() + " of " + result.totalWaitlistSize()
                            + " for " + showTitle(showId) + ".");
                } else {
                    lblWaitlistStatus.setText("Could not check the queue.");
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
                    adminDashboard.append("\nCould not load how busy the cinema is.");
                }
            });
        sendRequestAsync(new GetNetworkMetricsRequest(tcpClient.nextRequestId()), "GET_NETWORK_METRICS", () -> {},
            reply -> {
                if (reply instanceof NetworkMetricsReply result) {
                    adminDashboard.append("\n\nNetwork metrics:\n" + result.metrics());
                } else {
                    adminDashboard.append("\nCould not load connection details.");
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
                    adminDashboard.append("\nCould not load recent activity.");
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
                    adminDashboard.append("Could not load bookings.");
                }
            });
    }

    private JPanel labeledFields(String[] labels, JTextField[] fields) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        for (int index = 0; index < labels.length; index++) {
            JLabel label = new JLabel(labels[index]);
            label.setFont(UIFonts.BODY_BOLD);
            label.setForeground(UIColors.TEXT_DARK);
            label.setAlignmentX(LEFT_ALIGNMENT);
            JTextField field = fields[index];
            field.setFont(UIFonts.BODY);
            field.setBorder(new com.seatsync.ui.components.RoundedLineBorder(new Color(0xE7, 0xD5, 0xCC), 12, 8));
            field.setMaximumSize(new Dimension(360, 40));
            field.setAlignmentX(LEFT_ALIGNMENT);
            panel.add(label);
            panel.add(Box.createVerticalStrut(4));
            panel.add(field);
            panel.add(Box.createVerticalStrut(10));
        }
        return panel;
    }

    private void showCreateMovieDialog() {
        JTextField id = new JTextField();
        JTextField title = new JTextField();
        JTextField genre = new JTextField();
        JTextField duration = new JTextField("120");
        JTextField description = new JTextField();
        if (!NoticeDialog.accept(this, "Create movie", labeledFields(
            new String[] {"Movie ID", "Title", "Genre", "Duration (minutes)", "Description"},
            new JTextField[] {id, title, genre, duration, description}), "Save movie")) {
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
                    NoticeDialog.show(this,
                        reply instanceof AdminMovieCreatedReply ? "Movie saved" : "Could not save the movie",
                        reply instanceof AdminMovieCreatedReply ? "The movie is now in the catalog." : "Try again with a different movie ID.",
                        reply instanceof AdminMovieCreatedReply ? NoticeDialog.Kind.SUCCESS : NoticeDialog.Kind.ERROR);
                });
        } catch (IllegalArgumentException e) {
            NoticeDialog.show(this, "Check the movie", "Enter valid movie details.", NoticeDialog.Kind.ERROR);
        }
    }

    private void showCreateShowDialog() {
        JTextField show = new JTextField();
        JTextField movie = new JTextField();
        JTextField title = new JTextField();
        JTextField start = new JTextField(java.time.Instant.now().plusSeconds(3600).toString());
        JTextField end = new JTextField(java.time.Instant.now().plusSeconds(10800).toString());
        JTextField venue = new JTextField();
        if (!NoticeDialog.accept(this, "Add a showtime", labeledFields(
            new String[] {"Show ID", "Movie ID", "Title", "Start", "End", "Venue"},
            new JTextField[] {show, movie, title, start, end, venue}), "Save showtime")) {
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
                    NoticeDialog.show(this,
                        reply instanceof AdminShowAddedReply ? "Showtime saved" : "Could not save the showtime",
                        reply instanceof AdminShowAddedReply ? "The showtime is now on the list." : "Check the details and try again.",
                        reply instanceof AdminShowAddedReply ? NoticeDialog.Kind.SUCCESS : NoticeDialog.Kind.ERROR);
                });
        } catch (RuntimeException e) {
            NoticeDialog.show(this, "Check the showtime",
                "Enter a title, a venue, and valid start and end times.", NoticeDialog.Kind.ERROR);
        }
    }

    private void disableSelectedShow() {
        int row = showsTable.getSelectedRow();
        if (row < 0) {
            NoticeDialog.show(this, "Choose a showtime", "Select a showtime first.", NoticeDialog.Kind.INFO);
            return;
        }
        String selectedShow = showsTable.getValueAt(row, 0).toString();
        sendRequestAsync(new AdminDisableShowRequest(tcpClient.nextRequestId(), selectedShow),
            "ADMIN_DISABLE_SHOW", () -> {}, reply -> {
                NoticeDialog.show(this, "Showtime updated", "The showtime was updated.", NoticeDialog.Kind.SUCCESS);
                requestShows();
            });
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x1A, 0x12, 0x18));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, UIColors.ACCENT),
            BorderFactory.createEmptyBorder(12, 18, 12, 18)
        ));

        // Left info
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        infoPanel.setOpaque(false);
        lblHeaderShow = new JLabel("SeatSync");
        lblHeaderShow.setFont(UIFonts.TITLE_SMALL);
        lblHeaderShow.setForeground(UIColors.PURE_WHITE);
        String who = role == UserRole.ADMIN ? username + " · Staff" : username;
        JLabel userLabel = new JLabel("Signed in as " + who);
        userLabel.setFont(UIFonts.BODY_SMALL);
        userLabel.setForeground(new Color(0xD8, 0xD5, 0xE3));
        infoPanel.add(lblHeaderShow);
        infoPanel.add(userLabel);
        header.add(infoPanel, BorderLayout.WEST);

        // Right connection status
        JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusBox.setOpaque(false);
        boolean connected = tcpClient != null && tcpClient.isConnected();
        lblConnectionStatus = new JLabel(connected ? connectionLabel() : "● Not connected");
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

        JLabel summaryTitle = new JLabel("Your seat");
        summaryTitle.setFont(UIFonts.TITLE_SMALL);
        summaryTitle.setForeground(UIColors.TEXT_DARK);
        summaryTitle.setAlignmentX(LEFT_ALIGNMENT);

        lblSeatGuide = new JLabel("<html>Teal seats are open. Blue seats are already yours.</html>");
        lblSeatGuide.setFont(UIFonts.CAPTION);
        lblSeatGuide.setForeground(UIColors.TEXT_MUTED);
        lblSeatGuide.setAlignmentX(LEFT_ALIGNMENT);

        // Selected seat display
        lblSelectedSeat = new JLabel("Selected Seat: None");
        lblSelectedSeat.setFont(UIFonts.BODY_BOLD);
        lblSelectedSeat.setForeground(UIColors.TEXT_DARK);
        lblSelectedSeat.setAlignmentX(LEFT_ALIGNMENT);

        // Price breakdown box
        priceBox = new RoundedPanel(new GridLayout(3, 2, 4, 6), 16, new Color(0xFB, 0xF7, 0xF3));
        priceBox.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        priceBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
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
        holdBox = new RoundedPanel(new FlowLayout(FlowLayout.CENTER, 4, 2), 16, new Color(0xFD, 0xF5, 0xE8));
        holdBox.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        holdBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        holdBox.setAlignmentX(LEFT_ALIGNMENT);

        lblHoldCountdown = new JLabel("No active seat hold", SwingConstants.CENTER);
        lblHoldCountdown.setFont(UIFonts.CAPTION);
        lblHoldCountdown.setForeground(UIColors.TEXT_MUTED);
        holdBox.add(lblHoldCountdown);

        // Action Buttons
        btnHold = new ModernButton("Hold for 2 minutes", new Color(0xD9, 0x77, 0x06), Color.WHITE);
        btnHold.setFont(UIFonts.custom(Font.BOLD, 13));
        btnHold.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnHold.setAlignmentX(LEFT_ALIGNMENT);
        btnHold.setEnabled(false);
        btnHold.addActionListener(e -> performHoldSeat());

        btnConfirmHold = new ModernButton("Confirm this seat", new Color(0x0F, 0x76, 0x6E), Color.WHITE);
        btnConfirmHold.setFont(UIFonts.custom(Font.BOLD, 13));
        btnConfirmHold.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnConfirmHold.setAlignmentX(LEFT_ALIGNMENT);
        btnConfirmHold.setEnabled(false);
        btnConfirmHold.addActionListener(e -> confirmActiveHold());

        btnBook = new ModernButton("Book now", ModernButton.Variant.PRIMARY);
        btnBook.setFont(UIFonts.custom(Font.BOLD, 13));
        btnBook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnBook.setAlignmentX(LEFT_ALIGNMENT);
        btnBook.setEnabled(false);
        btnBook.addActionListener(e -> performBookSeat());

        btnReleaseHold = new ModernButton("Release hold", ModernButton.Variant.OUTLINE);
        btnReleaseHold.setFont(UIFonts.custom(Font.BOLD, 12));
        btnReleaseHold.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnReleaseHold.setAlignmentX(LEFT_ALIGNMENT);
        btnReleaseHold.setEnabled(false);
        btnReleaseHold.addActionListener(e -> releaseActiveHold());

        btnCancel = new ModernButton("Cancel my ticket", ModernButton.Variant.DANGER);
        btnCancel.setFont(UIFonts.custom(Font.BOLD, 12));
        btnCancel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnCancel.setAlignmentX(LEFT_ALIGNMENT);
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> performCancelBooking());

        btnRefresh = new ModernButton("Refresh seats", ModernButton.Variant.SECONDARY);
        btnRefresh.setFont(UIFonts.custom(Font.BOLD, 11));
        btnRefresh.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnRefresh.setAlignmentX(LEFT_ALIGNMENT);
        btnRefresh.addActionListener(e -> requestSeatSnapshot());
        showOnlyRelevantSeatActions(false, false, false, false);

        summaryCard.add(summaryTitle);
        summaryCard.add(Box.createVerticalStrut(6));
        summaryCard.add(lblSeatGuide);
        summaryCard.add(Box.createVerticalStrut(8));
        summaryCard.add(lblSelectedSeat);
        summaryCard.add(Box.createVerticalStrut(6));
        summaryCard.add(priceBox);
        summaryCard.add(Box.createVerticalStrut(6));
        summaryCard.add(holdBox);
        summaryCard.add(Box.createVerticalStrut(8));
        summaryCard.add(btnBook);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnHold);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnConfirmHold);
        summaryCard.add(Box.createVerticalStrut(10));
        summaryCard.add(btnReleaseHold);
        summaryCard.add(Box.createVerticalStrut(4));
        summaryCard.add(btnCancel);
        summaryCard.add(Box.createVerticalGlue());

        createLogPanel();
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

        appendLog("Signed in as " + username);
        return panel;
    }

    private void onSeatSelectionChanged(String seatId) {
        if (seatId == null) {
            lblSelectedSeat.setText("No seat selected");
            if (lblSeatGuide != null) {
                lblSeatGuide.setText("<html>Teal seats are open. Blue seats are already yours.</html>");
            }
            showOnlyRelevantSeatActions(false, false, false, false);
            return;
        }

        java.util.List<String> chosen = seatGridPanel.getSelectedSeatIds();
        if (chosen.size() > 1) {
            boolean connected = tcpClient != null && tcpClient.isConnected();
            boolean allOpen = chosen.stream().allMatch(id -> {
                SeatView seat = seatGridPanel.getSeatView(id);
                return seat != null && seat.status() == SeatStatus.AVAILABLE;
            });
            lblSelectedSeat.setText("Seats " + String.join(", ", chosen));
            lblSeatGuide.setText("<html>Shift-click open seats to hold them together. If one is taken, none are held.</html>");
            btnBook.setEnabled(false);
            btnHold.setEnabled(connected && allOpen);
            btnCancel.setEnabled(false);
            showOnlyRelevantSeatActions(false, false, false, activeHoldId != null);
            if (btnHold != null) {
                btnHold.setVisible(allOpen);
            }
            return;
        }

        SeatView view = seatGridPanel.getSeatView(seatId);
        boolean isBooked = view != null && view.status() == SeatStatus.BOOKED;
        boolean isMine = isBooked && userId != null && userId.equals(view.bookedBy());

        boolean isHeld = view != null && view.status() == SeatStatus.HELD;
        boolean connected = tcpClient != null && tcpClient.isConnected();
        boolean open = view != null && view.status() == SeatStatus.AVAILABLE;
        boolean myHold = isHeld && seatId.equals(activeHoldSeatId);
        boolean canCancel = connected && isBooked && (isMine || userBookings.containsKey(seatId));
        if (isMine) {
            lblSelectedSeat.setText("Seat " + seatId + " is already yours");
            lblSeatGuide.setText("<html>You booked this seat. Cancel the ticket only if you no longer want it.</html>");
        } else if (myHold) {
            lblSelectedSeat.setText("Seat " + seatId + " is held for you");
            lblSeatGuide.setText("<html>Confirm it before the timer ends, or release it back to the hall.</html>");
        } else if (isBooked || isHeld) {
            lblSelectedSeat.setText("Seat " + seatId + " is taken");
            lblSeatGuide.setText("<html>Someone else has this seat. Choose a teal seat instead.</html>");
        } else {
            lblSelectedSeat.setText("Seat " + seatId + " is open");
            lblSeatGuide.setText("<html>Book it now for $14.00, or hold it for 2 minutes. Shift-click to hold several seats together.</html>");
            if (lblPriceSeat != null) lblPriceSeat.setText("$12.50");
            if (lblPriceFee != null) lblPriceFee.setText("$1.50");
            if (lblPriceTotal != null) lblPriceTotal.setText("$14.00");
        }
        btnBook.setEnabled(connected && open);
        btnHold.setEnabled(connected && open);
        btnCancel.setEnabled(canCancel);
        showOnlyRelevantSeatActions(open, myHold, canCancel, activeHoldId != null);
    }

    private void showOnlyRelevantSeatActions(boolean openSeat, boolean myHold, boolean canCancel, boolean holdActive) {
        if (priceBox != null) {
            priceBox.setVisible(openSeat);
        }
        if (holdBox != null) {
            holdBox.setVisible(holdActive);
        }
        if (btnBook != null) {
            btnBook.setVisible(openSeat);
        }
        if (btnHold != null) {
            btnHold.setVisible(openSeat);
        }
        if (btnConfirmHold != null) {
            btnConfirmHold.setVisible(myHold);
        }
        if (btnReleaseHold != null) {
            btnReleaseHold.setVisible(myHold);
        }
        if (btnCancel != null) {
            btnCancel.setVisible(canCancel);
        }
        if (lblSelectedSeat != null && lblSelectedSeat.getParent() != null) {
            lblSelectedSeat.getParent().revalidate();
            lblSelectedSeat.getParent().repaint();
        }
    }

    private void updateSeatMapMovieInfo() {
        if (lblSeatMapMovieTitle == null) {
            return;
        }
        String title = showTitle(showId);
        if ("this screening".equals(title)) {
            title = "Choose a showtime";
        }
        MovieDetails movieForShow = null;
        for (MovieDetails movie : loadedMovies) {
            if (movie.title() != null && movie.title().equalsIgnoreCase(title)) {
                movieForShow = movie;
                break;
            }
        }
        String genre = movieForShow != null ? movieForShow.genre() : "Cinema";
        int duration = movieForShow != null ? movieForShow.durationMinutes() : 0;

        lblSeatMapMovieTitle.setText("<html><center><b>" + title + "</b></center></html>");
        lblSeatMapGenre.setText(movieForShow == null ? "Seats for this showtime" : genre + " • " + duration + " min");
        ShowSummary show = showsById.get(showId);
        if (lblSeatVenue != null) {
            String venue = show != null && show.venue() != null && !show.venue().isBlank()
                ? show.venue() : "Main auditorium";
            lblSeatVenue.setText(venue);
        }
        if (lblSeatWhen != null) {
            lblSeatWhen.setText(show == null ? "Choose a showtime" : friendlyTime(show.startTime()));
        }
        if (lblSeatMapPoster != null) {
            lblSeatMapPoster.setIcon(new ImageIcon(ImageResourceManager.getMoviePoster(title, genre, 130, 160)));
        }
    }

    private void refreshShowLabels() {
        String title = showTitle(showId);
        boolean known = !"this screening".equals(title);
        if (lblHeaderShow != null) {
            if (!showtimeChosen) {
                lblHeaderShow.setText(selectedMovie == null ? "SeatSync" : selectedMovie.title());
            } else {
                lblHeaderShow.setText(known ? title : "SeatSync");
            }
        }
        if (lblShowTitle != null) {
            if (!showtimeChosen) {
                lblShowTitle.setText(selectedMovie == null
                    ? "Choose a movie, then a showtime. Each film has its own seats."
                    : selectedMovie.title() + " is selected. Choose its showtime before picking seats.");
            } else {
                lblShowTitle.setText(known
                    ? "Now booking: " + title
                    : "Start with a movie, then pick a showtime and a seat.");
            }
        }
        updateSeatMapMovieInfo();
    }

    private String showTitle(String id) {
        ShowSummary show = id == null ? null : showsById.get(id);
        if (show != null && show.title() != null && !show.title().isBlank()) {
            return show.title();
        }
        return "this screening";
    }

    private String displayShowTitle(String id, String storedTitle) {
        String known = showTitle(id);
        if (!"this screening".equals(known)) {
            return known;
        }
        if (storedTitle != null && !storedTitle.isBlank() && !storedTitle.startsWith("Show ")) {
            return storedTitle;
        }
        return "Movie";
    }

    private static String friendlyTime(String raw) {
        if (raw == null || raw.isBlank() || "Scheduled".equals(raw)) {
            return "Time to be announced";
        }
        String value = raw.replace('T', ' ');
        if (value.endsWith("Z")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.length() >= 16 ? value.substring(0, 16) : value;
    }

    private static String friendlyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String words = status.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private String connectionLabel() {
        if (tcpClient != null && tcpClient.isTlsEnabled()) {
            return "● Connected securely";
        }
        return "● Connected";
    }

    private static void hideColumn(JTable table, int modelIndex) {
        var column = table.getColumnModel().getColumn(table.convertColumnIndexToView(modelIndex));
        column.setMinWidth(0);
        column.setMaxWidth(0);
        column.setPreferredWidth(0);
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
        JLabel lblVal = new JLabel(value, SwingConstants.RIGHT);
        lblVal.setFont(UIFonts.BODY_BOLD);
        lblVal.setForeground(UIColors.TEXT_DARK);
        return labeledValue(label, lblVal);
    }

    private JPanel labeledValue(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        JLabel lblKey = new JLabel(label);
        lblKey.setFont(UIFonts.CAPTION);
        lblKey.setForeground(UIColors.TEXT_MUTED);
        row.add(lblKey, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
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
            NoticeDialog.show(this, "No seat selected", "Please select an available seat first.", NoticeDialog.Kind.WARNING);
            return;
        }

        BookSeatRequest request = new BookSeatRequest(tcpClient.nextRequestId(), showId, seatId);
        appendLog("Submitting booking request for seat " + seatId + "...");
        btnBook.setEnabled(false);
        sendRequestAsync(request, "BOOK_SEAT", () -> btnBook.setEnabled(true));
    }

    private void performHoldSeat() {
        java.util.List<String> seats = seatGridPanel.getSelectedSeatIds();
        if (seats.isEmpty()) {
            return;
        }
        btnHold.setEnabled(false);
        if (seats.size() == 1) {
            String seatId = seats.get(0);
            sendRequestAsync(new HoldSeatRequest(tcpClient.nextRequestId(), showId, seatId, 120),
                "HOLD_SEAT", () -> btnHold.setEnabled(true), reply -> {
                    if (reply instanceof HoldOkReply held) {
                        rememberHold(held.holdId(), held.seatId(), held.expiresAt());
                        seatGridPanel.updateSingleSeat(SeatView.held(held.seatId(), userId));
                        btnConfirmHold.setEnabled(true);
                        btnReleaseHold.setEnabled(true);
                        updateHoldCountdown();
                        onSeatSelectionChanged(held.seatId());
                        updateHomeSummaryFromGrid();
                        appendLog("Seat " + held.seatId() + " held until " + java.time.Instant.ofEpochMilli(held.expiresAt()));
                    } else if (reply instanceof HoldRejectedReply rejected) {
                        btnHold.setEnabled(false);
                        NoticeDialog.show(this, "Seat hold rejected", rejected.message(), NoticeDialog.Kind.WARNING);
                    }
                });
            return;
        }
        sendRequestAsync(new HoldSeatsRequest(tcpClient.nextRequestId(), showId, seats, 120),
            "HOLD_SEATS", () -> btnHold.setEnabled(true), reply -> {
                if (reply instanceof HoldSeatsOkReply held) {
                    activeHoldIds.clear();
                    activeHeldSeats.clear();
                    activeHoldIds.addAll(held.holdIds());
                    activeHeldSeats.addAll(held.seatIds());
                    activeHoldId = held.holdIds().isEmpty() ? null : held.holdIds().get(0);
                    activeHoldSeatId = held.seatIds().isEmpty() ? null : held.seatIds().get(0);
                    activeHoldExpiresAt = held.expiresAt();
                    for (String seat : held.seatIds()) {
                        seatHoldExpiries.put(seat, held.expiresAt());
                        seatGridPanel.updateSingleSeat(SeatView.held(seat, userId));
                    }
                    btnConfirmHold.setEnabled(true);
                    btnReleaseHold.setEnabled(true);
                    updateHoldCountdown();
                    onSeatSelectionChanged(activeHoldSeatId);
                    updateHomeSummaryFromGrid();
                    appendLog("Held " + String.join(", ", held.seatIds()) + " together.");
                } else if (reply instanceof HoldRejectedReply rejected) {
                    btnHold.setEnabled(true);
                    NoticeDialog.show(this, "Seat hold rejected", rejected.message(), NoticeDialog.Kind.WARNING);
                }
            });
    }

    private void rememberHold(String holdId, String seatId, long expiresAt) {
        activeHoldIds.clear();
        activeHeldSeats.clear();
        activeHoldIds.add(holdId);
        activeHeldSeats.add(seatId);
        activeHoldId = holdId;
        activeHoldSeatId = seatId;
        activeHoldExpiresAt = expiresAt;
        seatHoldExpiries.put(seatId, expiresAt);
    }

    private void confirmActiveHold() {
        java.util.List<String> holdIds = java.util.List.copyOf(activeHoldIds);
        if (holdIds.isEmpty() || activeHoldExpiresAt <= System.currentTimeMillis()) {
            updateHoldCountdown();
            return;
        }
        btnConfirmHold.setEnabled(false);
        networkExecutor.execute(() -> {
            int confirmed = 0;
            for (String holdId : holdIds) {
                try {
                    ReplyMessage reply = tcpClient.sendRequest(new ConfirmHoldRequest(
                        tcpClient.nextRequestId(), holdId, UUID.randomUUID().toString())).get(5, TimeUnit.SECONDS);
                    if (reply instanceof ConfirmOkReply ok) {
                        confirmed++;
                        SwingUtilities.invokeLater(() -> {
                            userBookings.put(ok.seatId(), ok.bookingId());
                            seatGridPanel.updateSingleSeat(SeatView.booked(ok.seatId(), userId));
                        });
                    } else if (reply instanceof ConfirmRejectedReply rejected) {
                        SwingUtilities.invokeLater(() -> {
                            clearActiveHold();
                            NoticeDialog.show(this, "Could not confirm the hold", rejected.message(), NoticeDialog.Kind.WARNING);
                        });
                        return;
                    }
                } catch (Exception failure) {
                    SwingUtilities.invokeLater(() -> btnConfirmHold.setEnabled(activeHoldId != null));
                    return;
                }
            }
            int booked = confirmed;
            SwingUtilities.invokeLater(() -> {
                clearActiveHold();
                requestBookingHistory();
                updateHomeSummaryFromGrid();
                NoticeDialog.show(this, "Booking confirmed",
                    booked == 1 ? "Your seat is booked. Find it under My tickets."
                        : booked + " seats are booked. Find them under My tickets.",
                    NoticeDialog.Kind.SUCCESS);
            });
        });
    }

    private void releaseActiveHold() {
        java.util.List<String> holdIds = java.util.List.copyOf(activeHoldIds);
        if (holdIds.isEmpty()) {
            return;
        }
        btnReleaseHold.setEnabled(false);
        networkExecutor.execute(() -> {
            for (String holdId : holdIds) {
                try {
                    tcpClient.sendRequest(new ReleaseHoldRequest(tcpClient.nextRequestId(), holdId)).get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    SwingUtilities.invokeLater(() -> btnReleaseHold.setEnabled(activeHoldId != null));
                    return;
                }
            }
            SwingUtilities.invokeLater(() -> {
                clearActiveHold();
                requestSeatSnapshot();
            });
        });
    }

    private void clearActiveHold() {
        for (String heldSeat : activeHeldSeats) {
            seatHoldExpiries.remove(heldSeat);
        }
        activeHeldSeats.clear();
        activeHoldIds.clear();
        activeHoldId = null;
        activeHoldSeatId = null;
        activeHoldExpiresAt = 0;
        lblHoldCountdown.setText("No active seat hold");
        btnConfirmHold.setEnabled(false);
        btnReleaseHold.setEnabled(false);
        if (seatGridPanel != null) {
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        }
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
            NoticeDialog.show(this, "Cannot cancel",
                "Select one of your booked seats. If it is missing, open My tickets and try again.",
                NoticeDialog.Kind.INFO);
            return;
        }

        CancelBookingRequest request = new CancelBookingRequest(tcpClient.nextRequestId(), bookingId);
        appendLog("Submitting cancellation request for booking " + bookingId + "...");
        btnCancel.setEnabled(false);
        sendRequestAsync(request, "CANCEL_BOOKING", () -> btnCancel.setEnabled(true));
    }

    private void performLogout() {
        if (!NoticeDialog.confirm(this, "Log out", "Are you sure you want to log out?", "Log out", "Stay")) {
            return;
        }

        keepSession = false;
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
        Long known = reconnectCoordinator.getLastKnownVersion(reply.showId());
        if (known != null && reply.version() < known) {
            return;
        }
        reconnectCoordinator.setSnapshotVersion(reply.showId(), reply.version());
        SwingUtilities.invokeLater(() -> {
            seatGridPanel.updateSeats(reply.seats());
            lblSeatState.setText(reply.seats().isEmpty()
                ? "No seats are available for this showtime."
                : "Teal seats are open. Blue seats are already yours.");
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
            NoticeDialog.show(this,
                "Booking confirmed",
                "Seat " + reply.seatId() + " is yours. Find it under My tickets.",
                NoticeDialog.Kind.SUCCESS
            );
        });
    }

    @Override
    public void onBookRejected(BookRejectedReply reply) {
        SwingUtilities.invokeLater(() -> {
            appendLog("BOOKING REJECTED: " + reply.message() + " (" + reply.errorCode() + ")");
            btnBook.setEnabled(true);
            NoticeDialog.show(this, "Booking failed", reply.message(), NoticeDialog.Kind.ERROR);
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
            NoticeDialog.show(this,
                "Booking cancelled",
                reply.seatId() == null ? "Your booking was cancelled." : "Seat " + reply.seatId() + " is available again.",
                NoticeDialog.Kind.SUCCESS
            );
        });
    }

    @Override
    public void onCancelRejected(CancelRejectedReply reply) {
        SwingUtilities.invokeLater(() -> {
            appendLog("CANCEL REJECTED: " + reply.message() + " (" + reply.errorCode() + ")");
            btnCancel.setEnabled(true);
            NoticeDialog.show(this, "Could not cancel", reply.message(), NoticeDialog.Kind.ERROR);
        });
    }

    @Override
    public void onSeatUpdated(SeatUpdatedEvent event) {
        if (!acceptLiveEvent(event.showId(), event.version())) {
            return;
        }
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
        if (!acceptLiveEvent(event.showId(), event.version())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            seatHoldExpiries.put(event.seatId(), event.expiresAt());
            String holder = activeHoldIds.contains(event.holdId()) ? userId : null;
            seatGridPanel.updateSingleSeat(SeatView.held(event.seatId(), holder));
            updateHomeSummaryFromGrid();
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onSeatReleased(SeatReleasedEvent event) {
        if (!acceptLiveEvent(event.showId(), event.version())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            seatHoldExpiries.remove(event.seatId());
            seatGridPanel.updateSingleSeat(SeatView.available(event.seatId()));
            if (activeHoldIds.remove(event.holdId()) && activeHoldIds.isEmpty()) {
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
            rememberHold(event.holdId(), event.seatId(), event.expiresAt());
            seatGridPanel.updateSingleSeat(SeatView.held(event.seatId(), userId));
            updateHoldCountdown();
            updateHomeSummaryFromGrid();
            NoticeDialog.show(this, "A seat is ready",
                "A seat is available for you: " + event.seatId() + ". Confirm before the offer expires.",
                NoticeDialog.Kind.SUCCESS);
        });
    }

    @Override
    public void onShowUpdated(ShowUpdatedEvent event) {
        ReconnectCoordinator.EventAction action = reconnectCoordinator.evaluateEventVersion(event.showId(), event.version());
        if (action == ReconnectCoordinator.EventAction.GAP_RESYNC_REQUIRED && showId.equals(event.showId())) {
            requestSeatSnapshot();
        }
        if (action != ReconnectCoordinator.EventAction.DISCARD_STALE) {
            SwingUtilities.invokeLater(this::requestShows);
        }
    }

    private boolean acceptLiveEvent(String eventShowId, long version) {
        if (eventShowId == null || !eventShowId.equals(showId)) {
            return false;
        }
        ReconnectCoordinator.EventAction action = reconnectCoordinator.evaluateEventVersion(eventShowId, version);
        if (action == ReconnectCoordinator.EventAction.GAP_RESYNC_REQUIRED) {
            requestSeatSnapshot();
            return false;
        }
        return action == ReconnectCoordinator.EventAction.APPLY;
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
            ? "Seat counts are not available yet."
            : "Seat counts are up to date.");
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
            lblConnectionStatus.setText(connectionLabel());
            lblConnectionStatus.setForeground(new Color(46, 125, 50));
            onSeatSelectionChanged(seatGridPanel.getSelectedSeatId());
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblConnectionStatus.setText("● Not connected");
            lblConnectionStatus.setForeground(Color.RED);
            lblSeatState.setText(keepSession && sessionPassword != null
                ? "Connection dropped. Signing you back in."
                : "Connection unavailable. Reconnect to load seats.");
            lblHomeStatus.setText("Disconnected. Reconnect to refresh availability.");
            btnBook.setEnabled(false);
            btnCancel.setEnabled(false);
            btnRefresh.setEnabled(false);
            appendLog("[DISCONNECT] " + reason);
        });
        beginSessionRecovery();
    }

    private void beginSessionRecovery() {
        if (!keepSession || sessionPassword == null || tcpClient == null) {
            return;
        }
        if (!recoveryRunning.compareAndSet(false, true)) {
            return;
        }
        Thread recovery = new Thread(() -> {
            try {
                boolean restored = new SessionRecovery().recover(
                    tcpClient, reconnectCoordinator, username, sessionPassword);
                SwingUtilities.invokeLater(() -> {
                    if (!keepSession) {
                        return;
                    }
                    if (restored) {
                        lblConnectionStatus.setText(connectionLabel());
                        lblConnectionStatus.setForeground(new Color(46, 125, 50));
                        lblSeatState.setText("Signed back in. Seats are refreshing.");
                        appendLog("Connection restored.");
                        requestSeatSnapshot();
                        requestBookingHistory();
                    } else {
                        lblSeatState.setText("Could not sign back in. Log in again.");
                        appendLog("Reconnect stopped after several tries.");
                    }
                });
            } finally {
                recoveryRunning.set(false);
            }
        }, "seatsync-reconnect");
        recovery.setDaemon(true);
        recovery.start();
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
