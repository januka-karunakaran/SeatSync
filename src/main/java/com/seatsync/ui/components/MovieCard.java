package com.seatsync.ui.components;

import com.seatsync.common.model.MovieDetails;
import com.seatsync.ui.assets.ImageResourceManager;
import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;
import com.seatsync.ui.theme.UISpacing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Modern interactive cinema movie poster card with hover elevation and selection feedback.
 */
@SuppressWarnings({"serial", "this-escape"})
public class MovieCard extends RoundedPanel {

    private final MovieDetails movie;
    private final Consumer<MovieDetails> onSelect;
    private boolean isHovered;
    private boolean isSelected;

    public MovieCard(MovieDetails movie, Consumer<MovieDetails> onSelect) {
        super(UISpacing.RADIUS_MEDIUM, UIColors.PURE_WHITE, UIColors.LIGHT_BORDER, 1, true);
        this.movie = Objects.requireNonNull(movie, "movie must not be null");
        this.onSelect = onSelect;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 12, 10));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(170, 270));
        setMaximumSize(new Dimension(180, 280));

        // Poster image
        Image posterImg = ImageResourceManager.getMoviePoster(movie.title(), movie.genre(), 150, 180);
        JLabel posterLabel = new JLabel(new ImageIcon(posterImg));
        posterLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(posterLabel);
        add(Box.createVerticalStrut(8));

        // Movie title
        JLabel titleLabel = new JLabel("<html><center><b>" + movie.title() + "</b></center></html>");
        titleLabel.setFont(UIFonts.BODY_BOLD);
        titleLabel.setForeground(UIColors.TEXT_DARK);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(4));

        // Metadata badges row (Genre + Duration)
        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        metaRow.setOpaque(false);
        metaRow.setAlignmentX(CENTER_ALIGNMENT);

        JLabel genreLabel = new JLabel(movie.genre());
        genreLabel.setFont(UIFonts.BADGE);
        genreLabel.setForeground(UIColors.DEEP_PURPLE);

        JLabel durationLabel = new JLabel(movie.durationMinutes() + "m");
        durationLabel.setFont(UIFonts.BADGE);
        durationLabel.setForeground(UIColors.TEXT_MUTED);

        metaRow.add(genreLabel);
        metaRow.add(new JLabel("•"));
        metaRow.add(durationLabel);
        add(metaRow);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onSelect != null) {
                    onSelect.accept(movie);
                }
            }
        });
    }

    public MovieDetails getMovie() {
        return movie;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        repaint();
    }

    public boolean isSelected() {
        return isSelected;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (isSelected || isHovered) {
            Graphics2D g2 = (Graphics2D) g.create();
            SeatSyncTheme.enableAntiAliasing(g2);
            g2.setColor(isSelected ? UIColors.MUTED_PURPLE : UIColors.LAVENDER_DARK);
            g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.5f));
            int offset = isSelected ? 1 : 0;
            g2.drawRoundRect(offset, offset, getWidth() - 1 - offset * 2, getHeight() - 1 - offset * 2,
                UISpacing.RADIUS_MEDIUM, UISpacing.RADIUS_MEDIUM);
            g2.dispose();
        }
    }
}
