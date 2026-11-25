package com.seatsync.ui.components;

import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Short messages and confirmations in the same warm card style as the rest of SeatSync.
 */
public final class NoticeDialog {

    public enum Kind {
        INFO(UIColors.ACCENT),
        SUCCESS(UIColors.SUCCESS_GREEN),
        WARNING(new Color(0xD9, 0x77, 0x06)),
        ERROR(UIColors.ERROR_RED);

        final Color accent;

        Kind(Color accent) {
            this.accent = accent;
        }
    }

    private NoticeDialog() {
    }

    public static void show(Component parent, String title, String message, Kind kind) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        JDialog dialog = create(parent);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        ModernButton ok = new ModernButton("OK", kind == Kind.ERROR ? ModernButton.Variant.DANGER : ModernButton.Variant.PRIMARY);
        ok.addActionListener(event -> dialog.dispose());
        actions.add(ok);
        dialog.setContentPane(card(title, message, kind, null, actions));
        finish(dialog, ok);
    }

    public static boolean confirm(Component parent, String title, String message, String acceptLabel, String cancelLabel) {
        return accept(parent, title, messageLabel(message), acceptLabel, cancelLabel, Kind.INFO);
    }

    public static boolean accept(Component parent, String title, JComponent body, String acceptLabel) {
        return accept(parent, title, body, acceptLabel, "Cancel", Kind.INFO);
    }

    public static String choose(Component parent, String title, String message, String[] options) {
        if (GraphicsEnvironment.isHeadless() || options == null || options.length == 0) {
            return null;
        }
        JDialog dialog = create(parent);
        String[] selected = new String[1];
        JPanel list = new JPanel();
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        list.setOpaque(false);
        for (String option : options) {
            ModernButton choice = new ModernButton(option, ModernButton.Variant.SECONDARY);
            choice.setAlignmentX(Component.LEFT_ALIGNMENT);
            choice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            choice.addActionListener(event -> {
                selected[0] = option;
                dialog.dispose();
            });
            list.add(choice);
            list.add(javax.swing.Box.createVerticalStrut(8));
        }
        JPanel stack = new JPanel();
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        JLabel body = messageLabel(message);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(body);
        stack.add(javax.swing.Box.createVerticalStrut(12));
        stack.add(list);
        ModernButton cancel = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);
        cancel.addActionListener(event -> dialog.dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        dialog.setContentPane(card(title, null, Kind.INFO, stack, actions));
        finish(dialog, cancel);
        return selected[0];
    }

    private static boolean accept(Component parent, String title, JComponent body, String acceptLabel,
                                  String cancelLabel, Kind kind) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        JDialog dialog = create(parent);
        boolean[] accepted = new boolean[1];
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        ModernButton cancel = new ModernButton(cancelLabel, ModernButton.Variant.OUTLINE);
        ModernButton accept = new ModernButton(acceptLabel, ModernButton.Variant.PRIMARY);
        cancel.addActionListener(event -> dialog.dispose());
        accept.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });
        actions.add(cancel);
        actions.add(accept);
        dialog.setContentPane(card(title, null, kind, body, actions));
        finish(dialog, accept);
        return accepted[0];
    }

    private static JDialog create(Component parent) {
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return dialog;
    }

    private static void finish(JDialog dialog, JComponent focus) {
        dialog.pack();
        int width = Math.max(380, dialog.getWidth());
        dialog.setSize(width, dialog.getHeight());
        dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), 22, 22));
        dialog.setLocationRelativeTo(dialog.getOwner());
        if (focus instanceof javax.swing.JButton button) {
            dialog.getRootPane().setDefaultButton(button);
        }
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                focus.requestFocusInWindow();
            }
        });
        dialog.setVisible(true);
    }

    private static JLabel messageLabel(String message) {
        String safe = message == null ? "" : message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel label = new JLabel("<html><body style='width:300px'>" + safe + "</body></html>");
        label.setFont(UIFonts.BODY);
        label.setForeground(UIColors.TEXT_MUTED);
        return label;
    }

    private static JPanel card(String title, String message, Kind kind, JComponent body, JComponent actions) {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(UIColors.PURE_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(4, 0, 0, 0, kind.accent),
            BorderFactory.createEmptyBorder(18, 20, 16, 20)
        ));

        JLabel heading = new JLabel(title == null ? "" : title);
        heading.setFont(UIFonts.TITLE_SMALL);
        heading.setForeground(UIColors.TEXT_DARK);
        card.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        if (message != null && !message.isBlank()) {
            center.add(messageLabel(message), BorderLayout.NORTH);
        }
        if (body != null) {
            center.add(body, BorderLayout.CENTER);
        }
        card.add(center, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }
}
