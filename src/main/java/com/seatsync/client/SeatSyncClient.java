package com.seatsync.client;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main application launcher for the SeatSync desktop client.
 * Parses command-line endpoint parameters and launches the Swing presentation layer.
 * <p>
 * Owned by Member 05 (Desktop UI & Presentation).
 */
public final class SeatSyncClient {

    private SeatSyncClient() {
        // utility class
    }

    /**
     * Client main entry point.
     *
     * @param args Command-line arguments: [host] [port] or [port]
     */
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = ProtocolConstants.DEFAULT_PORT;

        if (args.length == 1) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                host = args[0].trim();
            }
        } else if (args.length >= 2) {
            host = args[0].trim();
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Invalid port '" + args[1] + "', defaulting to " + ProtocolConstants.DEFAULT_PORT);
                port = ProtocolConstants.DEFAULT_PORT;
            }
        }

        final String finalHost = host;
        final int finalPort = port;

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            LoginFrame loginFrame = new LoginFrame(finalHost, finalPort);
            loginFrame.setVisible(true);
        });
    }
}
