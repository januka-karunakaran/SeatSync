package com.seatsync.ui;

import com.seatsync.client.TcpClient;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.request.LoginRequest;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Authentication and endpoint configuration frame for the SeatSync client.
 * <p>
 * Handles network connection attempts, validates host/port input, initiates
 * the TCP handshake, and submits the LOGIN protocol message.
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
@SuppressWarnings("serial")
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient JTextField txtHost;
    private final transient JTextField txtPort;
    private final transient JTextField txtUsername;
    private final transient JPasswordField txtPassword;
    private final transient ThemedButton btnLogin;
    private final transient JLabel lblStatus;

    /**
     * Constructs the LoginFrame with default connection parameters.
     *
     * @param defaultHost Initial server host
     * @param defaultPort Initial server port
     */
    public LoginFrame(String defaultHost, int defaultPort) {
        super("SeatSync - Cinema Ticket Booking Login");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(460, 420);
        setMinimumSize(new Dimension(420, 380));
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        mainPanel.setBackground(new Color(248, 249, 250));
        setContentPane(mainPanel);

        // Header Banner
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("SeatSync Cinema Client");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Real-Time Multi-User Ticket Booking System");
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLabel.setForeground(new Color(100, 116, 139));
        subLabel.setAlignmentX(CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subLabel);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form Fields (GridBagLayout)
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Server Host
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("Server Host:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        txtHost = new JTextField(defaultHost != null ? defaultHost : "127.0.0.1", 15);
        formPanel.add(txtHost, gbc);

        // Server Port
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("Server Port:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        txtPort = new JTextField(String.valueOf(defaultPort > 0 ? defaultPort : ProtocolConstants.DEFAULT_PORT), 15);
        formPanel.add(txtPort, gbc);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        txtUsername = new JTextField("alice", 15);
        formPanel.add(txtUsername, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        txtPassword = new JPasswordField("password123", 15);
        formPanel.add(txtPassword, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom Controls: Button + Status
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);

        btnLogin = new ThemedButton("Connect & Log In", new Color(21, 101, 192), Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setAlignmentX(CENTER_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setPreferredSize(new Dimension(380, 42));
        btnLogin.addActionListener(e -> performLogin());

        lblStatus = new JLabel("Ready to connect", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(100, 116, 139));
        lblStatus.setAlignmentX(CENTER_ALIGNMENT);

        bottomPanel.add(btnLogin);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(lblStatus);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

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

    private void performLogin() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (host.isEmpty()) {
            setStatus("Please provide a valid server host.", Color.RED);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            setStatus("Port must be a number between 1 and 65535.", Color.RED);
            return;
        }

        if (username.isEmpty()) {
            setStatus("Username cannot be empty.", Color.RED);
            return;
        }

        setStatus("Connecting to " + host + ":" + port + "...", new Color(21, 101, 192));
        setFormEnabled(false);

        // Run networking on background thread to keep Swing UI responsive
        Thread networkThread = new Thread(() -> {
            TcpClient client = new TcpClient(host, port);
            try {
                client.connect(3000);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Connection refused: " + e.getMessage(), Color.RED);
                    setFormEnabled(true);
                });
                return;
            }

            try {
                LoginRequest request = new LoginRequest(client.nextRequestId(), username, password);
                CompletableFuture<ReplyMessage> future = client.sendRequest(request);

                ReplyMessage reply = future.get(5, TimeUnit.SECONDS);
                if (reply instanceof LoginOkReply ok) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Login successful! Loading cinema...", new Color(46, 125, 50));
                        dispose();
                        BookingFrame bookingFrame = new BookingFrame(client, ok.username(), ok.userId());
                        bookingFrame.setVisible(true);
                    });
                } else if (reply instanceof LoginFailedReply failed) {
                    client.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Login failed: " + failed.message(), Color.RED);
                        setFormEnabled(true);
                    });
                } else {
                    client.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Unexpected server response: " + reply.getType(), Color.RED);
                        setFormEnabled(true);
                    });
                }
            } catch (Exception e) {
                client.disconnect();
                SwingUtilities.invokeLater(() -> {
                    setStatus("Login error: " + e.getMessage(), Color.RED);
                    setFormEnabled(true);
                });
            }
        }, "seatsync-login-worker");

        networkThread.setDaemon(true);
        networkThread.start();
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
    }
}
