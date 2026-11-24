package com.seatsync.ui.components;

import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Clean non-blocking loading state panel with modern circular progress animation.
 */
@SuppressWarnings({"serial", "this-escape"})
public class LoadingPanel extends JPanel {

    private final JLabel lblMessage;
    private int angle;
    private Timer animationTimer;

    public LoadingPanel(String message) {
        super(new BorderLayout(10, 10));
        setOpaque(false);

        lblMessage = new JLabel(message, SwingConstants.CENTER);
        lblMessage.setFont(UIFonts.BODY);
        lblMessage.setForeground(UIColors.TEXT_SECONDARY);

        JPanel spinner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                SeatSyncTheme.enableAntiAliasing(g2);

                int size = 32;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                g2.setColor(new Color(0xD8, 0xD0, 0xF2, 80));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(x, y, size, size);

                g2.setColor(UIColors.MUTED_PURPLE);
                g2.drawArc(x, y, size, size, angle, 90);

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(48, 48);
            }
        };
        spinner.setOpaque(false);

        add(spinner, BorderLayout.CENTER);
        add(lblMessage, BorderLayout.SOUTH);

        animationTimer = new Timer(30, e -> {
            angle = (angle + 8) % 360;
            spinner.repaint();
        });
        animationTimer.start();
    }

    public void setMessage(String message) {
        lblMessage.setText(message);
    }

    public void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }
}
