package com.seatsync.ui;

import com.seatsync.client.TcpClient;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.network.discovery.DiscoveryConstants;
import com.seatsync.network.discovery.DiscoveryResponse;
import com.seatsync.network.discovery.UdpDiscoveryProbe;
import com.seatsync.security.tls.TlsContextFactory;
import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.components.ModernButton;
import com.seatsync.ui.components.RoundedPanel;
import com.seatsync.ui.components.StatusBadge;
import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import com.seatsync.ui.theme.UISpacing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Premium cinematic authentication and connection configuration window for SeatSync.
 * <p>
 * Implements a modern two-column desktop presentation:
 * - Left column: Atmospheric cinematic brand hero with dark gradient and feature highlights.
 * - Right column: Minimalist, card-based credential and server discovery interface.
 * <p>
 * Preserves all underlying TCP/TLS socket transport, UDP discovery, and protocol message flows.
 */
@SuppressWarnings({"serial", "this-escape"})
public final class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient JTextField txtHost;
    private final transient JTextField txtPort;
    private final transient JTextField txtUsername;
    private final transient JPasswordField txtPassword;
    private final transient ModernButton btnLogin;
    private final transient ModernButton btnDiscover;
    private final transient JCheckBox chkTls;
    private final transient JLabel lblStatus;
    private final transient JButton btnTogglePassword;

    private boolean discoveryRequiresTls;
    private String discoveredHost;
    private int discoveredTcpPort;
    private int discoveredTlsPort;
    private boolean passwordVisible = false;

    /**
     * Constructs the redesigned LoginFrame with modern cinematic theme.
     *
     * @param defaultHost Initial server host
     * @param defaultPort Initial server port
     */
    public LoginFrame(String defaultHost, int defaultPort) {
        super("SeatSync - Real-Time Cinema Ticket Booking");
        SeatSyncTheme.applyGlobalTheme();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(940, 580);
        setMinimumSize(new Dimension(860, 520));
        setLocationRelativeTo(null);
        setResizable(true);

        JPanel rootPanel = new JPanel(new GridLayout(1, 2, 0, 0));
        setContentPane(rootPanel);

        // -------------------------------------------------------------
        // LEFT COLUMN: Cinematic Hero Section (Dark & Atmospheric)
        // -------------------------------------------------------------
        JPanel leftHeroPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                SeatSyncTheme.enableAntiAliasing(g2);
                Image hero = ImageResourceManager.getCinematicHeroBanner(getWidth(), getHeight());
                g2.drawImage(hero, 0, 0, getWidth(), getHeight(), null);
                g2.dispose();
            }
        };
        leftHeroPanel.setLayout(new BorderLayout());
        leftHeroPanel.setBorder(BorderFactory.createEmptyBorder(45, 45, 45, 45));

        JPanel heroContent = new JPanel();
        heroContent.setLayout(new BoxLayout(heroContent, BoxLayout.Y_AXIS));
        heroContent.setOpaque(false);

        // Brand Logo & Name
        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandRow.setOpaque(false);
        JLabel logoLabel = new JLabel(new ImageIcon(ImageResourceManager.getSeatSyncLogo(38, 38)));
        JLabel brandLabel = new JLabel("SeatSync");
        brandLabel.setFont(UIFonts.TITLE_LARGE);
        brandLabel.setForeground(UIColors.PURE_WHITE);
        brandRow.add(logoLabel);
        brandRow.add(brandLabel);

        // Headline & Subtitle
        JLabel heroHeadline = new JLabel("<html>Your Cinema Experience<br>Starts Here</html>");
        heroHeadline.setFont(UIFonts.HERO_TITLE);
        heroHeadline.setForeground(UIColors.PURE_WHITE);

        JLabel heroSubtitle = new JLabel(
            "<html>Real-time seat reservations, synchronized across clients with microsecond precision. Select your favorite show and book instantly.</html>"
        );
        heroSubtitle.setFont(UIFonts.BODY);
        heroSubtitle.setForeground(new Color(0xD8, 0xD5, 0xE3));
        heroSubtitle.setMaximumSize(new Dimension(380, 70));

        // Feature Highlights
        JPanel highlightsPanel = new JPanel();
        highlightsPanel.setLayout(new BoxLayout(highlightsPanel, BoxLayout.Y_AXIS));
        highlightsPanel.setOpaque(false);
        highlightsPanel.add(createFeatureItem("Non-blocking Java NIO 21 Server (16 KiB frame ceiling)"));
        highlightsPanel.add(Box.createVerticalStrut(10));
        highlightsPanel.add(createFeatureItem("Zero-configuration UDP LAN Discovery Probe"));
        highlightsPanel.add(Box.createVerticalStrut(10));
        highlightsPanel.add(createFeatureItem("End-to-End TLS 1.3 Transport Security"));

        heroContent.add(brandRow);
        heroContent.add(Box.createVerticalStrut(35));
        heroContent.add(heroHeadline);
        heroContent.add(Box.createVerticalStrut(16));
        heroContent.add(heroSubtitle);
        heroContent.add(Box.createVerticalStrut(35));
        heroContent.add(highlightsPanel);
        heroContent.add(Box.createVerticalGlue());

        JLabel versionLabel = new JLabel("SeatSync v2.0 • Member 02 Network Engine");
        versionLabel.setFont(UIFonts.CAPTION);
        versionLabel.setForeground(new Color(0xA9, 0x9A, 0xCD));

        leftHeroPanel.add(heroContent, BorderLayout.CENTER);
        leftHeroPanel.add(versionLabel, BorderLayout.SOUTH);
        rootPanel.add(leftHeroPanel);

        // -------------------------------------------------------------
        // RIGHT COLUMN: Clean, Elegant Login Card (FilmTIX Light Style)
        // -------------------------------------------------------------
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.setBackground(UIColors.SOFT_WHITE);
        rightContainer.setBorder(BorderFactory.createEmptyBorder(36, 40, 36, 40));

        JPanel formCard = new RoundedPanel(UISpacing.RADIUS_LARGE, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        formCard.setLayout(new BorderLayout(16, 16));
        formCard.setBorder(BorderFactory.createEmptyBorder(28, 28, 24, 28));

        // Form Header
        JPanel cardHeader = new JPanel();
        cardHeader.setLayout(new BoxLayout(cardHeader, BoxLayout.Y_AXIS));
        cardHeader.setOpaque(false);

        JLabel lblLoginTitle = new JLabel("Welcome Back");
        lblLoginTitle.setFont(UIFonts.TITLE_LARGE);
        lblLoginTitle.setForeground(UIColors.TEXT_DARK);

        JLabel lblLoginSub = new JLabel("Connect to SeatSync server to manage and book cinema tickets");
        lblLoginSub.setFont(UIFonts.BODY_SMALL);
        lblLoginSub.setForeground(UIColors.TEXT_SECONDARY);

        cardHeader.add(lblLoginTitle);
        cardHeader.add(Box.createVerticalStrut(4));
        cardHeader.add(lblLoginSub);
        formCard.add(cardHeader, BorderLayout.NORTH);

        // Form Fields Container
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);

        // Server Host & Port row
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        JLabel lblEndpoint = new JLabel("Server Endpoint");
        lblEndpoint.setFont(UIFonts.BODY_BOLD);
        lblEndpoint.setForeground(UIColors.TEXT_DARK);
        fieldsPanel.add(lblEndpoint, gbc);

        gbc.gridy = 1;
        JPanel endpointRow = new JPanel(new BorderLayout(8, 0));
        endpointRow.setOpaque(false);
        txtHost = createStyledTextField(defaultHost != null ? defaultHost : "127.0.0.1");
        txtPort = createStyledTextField(String.valueOf(defaultPort > 0 ? defaultPort : ProtocolConstants.DEFAULT_PORT));
        txtPort.setPreferredSize(new Dimension(80, 36));
        endpointRow.add(txtHost, BorderLayout.CENTER);
        endpointRow.add(txtPort, BorderLayout.EAST);
        fieldsPanel.add(endpointRow, gbc);

        // Connection Options (Discover + TLS)
        gbc.gridy = 2;
        JPanel connectionOptions = new JPanel(new BorderLayout(8, 0));
        connectionOptions.setOpaque(false);
        btnDiscover = new ModernButton("Discover Server", ModernButton.Variant.SECONDARY);
        btnDiscover.setFont(UIFonts.custom(java.awt.Font.BOLD, 12));
        btnDiscover.addActionListener(e -> discoverServers());
        connectionOptions.add(btnDiscover, BorderLayout.WEST);

        chkTls = new JCheckBox("Require TLS 1.3");
        chkTls.setOpaque(false);
        chkTls.setFont(UIFonts.BODY_SMALL);
        chkTls.setForeground(UIColors.TEXT_MUTED);
        chkTls.addActionListener(event -> updateDiscoveredTransport());
        connectionOptions.add(chkTls, BorderLayout.CENTER);
        fieldsPanel.add(connectionOptions, gbc);

        // Username
        gbc.gridy = 3;
        JLabel lblUser = new JLabel("Username");
        lblUser.setFont(UIFonts.BODY_BOLD);
        lblUser.setForeground(UIColors.TEXT_DARK);
        fieldsPanel.add(lblUser, gbc);

        gbc.gridy = 4;
        txtUsername = createStyledTextField("alice");
        fieldsPanel.add(txtUsername, gbc);

        // Password
        gbc.gridy = 5;
        JLabel lblPass = new JLabel("Password");
        lblPass.setFont(UIFonts.BODY_BOLD);
        lblPass.setForeground(UIColors.TEXT_DARK);
        fieldsPanel.add(lblPass, gbc);

        gbc.gridy = 6;
        JPanel passwordRow = new JPanel(new BorderLayout(4, 0));
        passwordRow.setOpaque(false);
        txtPassword = new JPasswordField("", 15);
        styleInputField(txtPassword);
        btnTogglePassword = new JButton(ImageResourceManager.getEyeIcon(false, 16));
        btnTogglePassword.setFocusPainted(false);
        btnTogglePassword.setBorderPainted(false);
        btnTogglePassword.setContentAreaFilled(false);
        btnTogglePassword.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnTogglePassword.setToolTipText("Show or hide password");
        btnTogglePassword.addActionListener(e -> togglePasswordVisibility());
        passwordRow.add(txtPassword, BorderLayout.CENTER);
        passwordRow.add(btnTogglePassword, BorderLayout.EAST);
        fieldsPanel.add(passwordRow, gbc);

        formCard.add(fieldsPanel, BorderLayout.CENTER);

        // Form Footer: Submit button and status
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setOpaque(false);

        btnLogin = new ModernButton("Connect & Log In", ModernButton.Variant.PRIMARY);
        btnLogin.setFont(UIFonts.BUTTON);
        btnLogin.setAlignmentX(CENTER_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnLogin.setPreferredSize(new Dimension(340, 44));
        btnLogin.addActionListener(e -> performLogin());

        lblStatus = new JLabel("Ready to connect", SwingConstants.CENTER);
        lblStatus.setFont(UIFonts.CAPTION);
        lblStatus.setForeground(UIColors.TEXT_SECONDARY);
        lblStatus.setAlignmentX(CENTER_ALIGNMENT);

        footerPanel.add(btnLogin);
        footerPanel.add(Box.createVerticalStrut(10));
        footerPanel.add(lblStatus);
        formCard.add(footerPanel, BorderLayout.SOUTH);

        rightContainer.add(formCard, BorderLayout.CENTER);
        rootPanel.add(rightContainer);

        // Submit form on Enter key
        KeyAdapter enterSubmit = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLogin();
                }
            }
        };
        txtHost.addKeyListener(enterSubmit);
        txtPort.addKeyListener(enterSubmit);
        txtUsername.addKeyListener(enterSubmit);
        txtPassword.addKeyListener(enterSubmit);
    }

    private JPanel createFeatureItem(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        JLabel check = new JLabel("✓");
        check.setFont(UIFonts.BODY_BOLD);
        check.setForeground(UIColors.LAVENDER_PRIMARY);
        JLabel desc = new JLabel(text);
        desc.setFont(UIFonts.BODY_SMALL);
        desc.setForeground(new Color(0xD8, 0xD5, 0xE3));
        row.add(check);
        row.add(desc);
        return row;
    }

    private JTextField createStyledTextField(String text) {
        JTextField tf = new JTextField(text, 15);
        styleInputField(tf);
        return tf;
    }

    private void styleInputField(JTextField tf) {
        tf.setFont(UIFonts.BODY);
        tf.setForeground(UIColors.TEXT_DARK);
        tf.setBackground(UIColors.PURE_WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.LIGHT_BORDER, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        tf.setPreferredSize(new Dimension(tf.getPreferredSize().width, 36));
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPassword.setEchoChar((char) 0);
            btnTogglePassword.setIcon(ImageResourceManager.getEyeIcon(true, 16));
        } else {
            txtPassword.setEchoChar('•');
            btnTogglePassword.setIcon(ImageResourceManager.getEyeIcon(false, 16));
        }
    }

    private void performLogin() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (host.isEmpty()) {
            setStatus("Please provide a valid server host.", UIColors.ERROR_RED);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            setStatus("Port must be a number between 1 and 65535.", UIColors.ERROR_RED);
            return;
        }

        if (username.isEmpty()) {
            setStatus("Username cannot be empty.", UIColors.ERROR_RED);
            return;
        }

        if (discoveryRequiresTls && !chkTls.isSelected()) {
            setStatus("Discovered server requires TLS 1.3. Plaintext connection is disabled.", UIColors.ERROR_RED);
            return;
        }

        setStatus("Connecting to " + host + ":" + port + "...", UIColors.INFO_BLUE);
        setFormEnabled(false);

        boolean useTls = chkTls.isSelected();
        Thread networkThread = new Thread(() -> {
            TcpClient client = null;
            try {
                var tlsContext = useTls ? TlsContextFactory.clientFromEnvironment(System.getenv()) : null;
                client = new TcpClient(host, port, null, tlsContext);
                client.connect(3000);
                TcpClient activeClient = client;
                LoginRequest request = new LoginRequest(activeClient.nextRequestId(), username, password);
                CompletableFuture<ReplyMessage> future = activeClient.sendRequest(request);

                ReplyMessage reply = future.get(5, TimeUnit.SECONDS);
                if (reply instanceof LoginOkReply ok) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Login successful! Loading cinema...", UIColors.SUCCESS_GREEN);
                        dispose();
                        BookingFrame bookingFrame = new BookingFrame(activeClient, ok.username(), ok.userId(), ProtocolConstants.DEFAULT_SHOW_ID, ok.role());
                        bookingFrame.initialize();
                        bookingFrame.setVisible(true);
                    });
                } else if (reply instanceof LoginFailedReply failed) {
                    activeClient.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Login failed: " + failed.message(), UIColors.ERROR_RED);
                        setFormEnabled(true);
                    });
                } else {
                    activeClient.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Unexpected server response: " + reply.getType(), UIColors.ERROR_RED);
                        setFormEnabled(true);
                    });
                }
            } catch (Exception e) {
                if (client != null) {
                    client.disconnect();
                }
                SwingUtilities.invokeLater(() -> {
                    setStatus((useTls ? "Secure connection/login failed: " : "Connection/login failed: ") + e.getMessage(), UIColors.ERROR_RED);
                    setFormEnabled(true);
                });
            }
        }, "seatsync-login-worker");

        networkThread.setDaemon(true);
        networkThread.start();
    }

    private void discoverServers() {
        btnDiscover.setEnabled(false);
        setStatus("Searching for SeatSync servers on the local network...", UIColors.INFO_BLUE);
        Thread discoveryThread = new Thread(() -> {
            try {
                List<DiscoveryResponse> discovered = UdpDiscoveryProbe.discoverAll(
                    DiscoveryConstants.DEFAULT_UDP_PORT, DiscoveryConstants.DEFAULT_TIMEOUT_MS);
                SwingUtilities.invokeLater(() -> {
                    btnDiscover.setEnabled(true);
                    if (discovered.isEmpty()) {
                        setStatus("No server discovered. Enter its address and port manually.", UIColors.ERROR_RED);
                        return;
                    }
                    String[] choices = discovered.stream()
                        .map(server -> server.serverIp() + ":" + server.tcpPort()
                            + (server.tlsRequired() ? " (TLS required, port " + server.tlsPort() + ")" : " (TLS optional)"))
                        .toArray(String[]::new);
                    Object choice = JOptionPane.showInputDialog(this,
                        "Select a discovered server:", "SeatSync Server Discovery",
                        JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
                    if (choice == null) {
                        setStatus("Discovery complete. You can enter an address manually.", UIColors.TEXT_SECONDARY);
                        return;
                    }
                    int index = java.util.Arrays.asList(choices).indexOf(choice.toString());
                    DiscoveryResponse selected = discovered.get(index);
                    if (selected.tlsRequired() && (selected.tlsPort() < 1 || selected.tlsPort() > 65535)) {
                        setStatus("Discovered server requires TLS but advertised an invalid TLS port.", UIColors.ERROR_RED);
                        return;
                    }
                    txtHost.setText(selected.serverIp());
                    discoveredHost = selected.serverIp();
                    discoveredTcpPort = selected.tcpPort();
                    discoveredTlsPort = selected.tlsPort();
                    discoveryRequiresTls = selected.tlsRequired();
                    chkTls.setSelected(discoveryRequiresTls);
                    chkTls.setEnabled(!discoveryRequiresTls);
                    updateDiscoveredTransport();
                    setStatus("Selected " + selected.serverIp() + ":" + txtPort.getText()
                        + (discoveryRequiresTls ? " (TLS 1.3 required)" : ""), UIColors.SUCCESS_GREEN);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    btnDiscover.setEnabled(true);
                    setStatus("Server discovery failed: " + e.getMessage(), UIColors.ERROR_RED);
                });
            }
        }, "seatsync-udp-discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    private void updateDiscoveredTransport() {
        if (discoveredHost == null || !discoveredHost.equals(txtHost.getText().trim())) {
            return;
        }
        if (discoveryRequiresTls) {
            chkTls.setSelected(true);
            txtPort.setText(Integer.toString(discoveredTlsPort));
        } else if (chkTls.isSelected() && discoveredTlsPort > 0) {
            txtPort.setText(Integer.toString(discoveredTlsPort));
        } else {
            txtPort.setText(Integer.toString(discoveredTcpPort));
        }
    }

    private void setStatus(String message, Color color) {
        lblStatus.setText(message);
        lblStatus.setForeground(color);
    }

    private void setFormEnabled(boolean enabled) {
        txtHost.setEnabled(enabled);
        txtPort.setEnabled(enabled);
        txtUsername.setEnabled(enabled);
        txtPassword.setEnabled(enabled);
        btnLogin.setEnabled(enabled);
        btnDiscover.setEnabled(enabled);
        chkTls.setEnabled(enabled && !discoveryRequiresTls);
    }
}
