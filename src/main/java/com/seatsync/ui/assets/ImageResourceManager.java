package com.seatsync.ui.assets;

import com.seatsync.ui.theme.SeatSyncTheme;
import com.seatsync.ui.theme.UIColors;
import com.seatsync.ui.theme.UIFonts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.ImageIcon;

/**
 * Procedural cinema asset generator and memory image cache.
 * Provides original, branded vector-rendered raster graphics without requiring external web requests.
 */
public final class ImageResourceManager {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private ImageResourceManager() {
        // utility class
    }

    /**
     * Creates a high-resolution SeatSync cinema ticket logo.
     *
     * @param width  Requested logo width
     * @param height Requested logo height
     * @return Cached or freshly rendered logo image
     */
    public static Image getSeatSyncLogo(int width, int height) {
        String key = "logo_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            // Ticket shape with notches
            int pad = 4;
            int tw = width - pad * 2;
            int th = height - pad * 2;

            GradientPaint gradient = new GradientPaint(
                0, 0, UIColors.MUTED_PURPLE,
                width, height, UIColors.DEEP_PURPLE
            );
            g2.setPaint(gradient);
            g2.fillRoundRect(pad, pad, tw, th, 16, 16);

            // Golden border
            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 160));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(pad, pad, tw, th, 16, 16);

            // Lettermark "S"
            g2.setColor(UIColors.PURE_WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (height * 0.55)));
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int sx = (width - fm.stringWidth("S")) / 2;
            int sy = (height + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString("S", sx, sy);

            g2.dispose();
            return image;
        });
    }

    /**
     * Generates a cinematic left-panel banner with dark gradient and subtle cinema projector beams.
     *
     * @param width  Banner width
     * @param height Banner height
     * @return Cinematic background banner
     */
    public static Image getCinematicHeroBanner(int width, int height) {
        String key = "hero_banner_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            // Dark base gradient
            GradientPaint baseGradient = new GradientPaint(
                0, 0, UIColors.CINEMATIC_BLACK,
                width, height, UIColors.DEEP_CHARCOAL
            );
            g2.setPaint(baseGradient);
            g2.fillRect(0, 0, width, height);

            // Subtle purple ambient atmospheric glow at top-left
            g2.setPaint(new GradientPaint(
                0, 0, new Color(0x82, 0x74, 0xAB, 45),
                (int)(width * 0.8), (int)(height * 0.6), new Color(0, 0, 0, 0)
            ));
            g2.fillOval(-100, -100, width + 200, (int)(height * 0.7));

            // Film strip decorative accents along bottom
            g2.setColor(new Color(255, 255, 255, 12));
            int spSize = 10;
            for (int x = 20; x < width - 20; x += 22) {
                g2.fillRoundRect(x, height - 36, spSize, spSize, 2, 2);
            }
            g2.drawRoundRect(14, height - 42, width - 28, 22, 6, 6);

            g2.dispose();
            return image;
        });
    }

    /**
     * Generates an eye icon for toggling password visibility.
     *
     * @param show If true, renders an open eye; if false, renders a slashed eye
     * @return Icon image
     */
    public static ImageIcon getEyeIcon(boolean show, int size) {
        String key = "eye_" + show + "_" + size;
        Image img = CACHE.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            g2.setColor(UIColors.TEXT_SECONDARY);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int pad = 2;
            int w = size - pad * 2;
            int h = size / 2;
            int y = (size - h) / 2;

            // Eye outline
            g2.drawArc(pad, y, w, h, 0, 180);
            g2.drawArc(pad, y, w, h, 0, -180);

            // Pupil
            int pupilSize = size / 4;
            g2.fillOval((size - pupilSize) / 2, (size - pupilSize) / 2, pupilSize, pupilSize);

            if (!show) {
                // Slash across the eye
                g2.setColor(UIColors.TEXT_MUTED);
                g2.drawLine(pad + 1, pad + 1, size - pad - 1, size - pad - 1);
            }

            g2.dispose();
            return image;
        });
        return new ImageIcon(img);
    }

    /**
     * Creates a tasteful cinematic movie poster graphic with title and genre.
     *
     * @param title Movie title
     * @param genre Movie genre
     * @param width Poster width
     * @param height Poster height
     * @return Procedural movie poster
     */
    public static Image getMoviePoster(String title, String genre, int width, int height) {
        String key = "poster_" + title + "_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            // Background based on hash of title
            int hash = Math.abs(title.hashCode());
            Color c1 = new Color(0x1B + (hash % 20), 0x1B + ((hash / 5) % 20), 0x2A + ((hash / 10) % 30));
            Color c2 = new Color(0x3B + ((hash / 2) % 30), 0x2A + ((hash / 4) % 30), 0x56 + ((hash / 8) % 40));

            GradientPaint gp = new GradientPaint(0, 0, c2, 0, height, c1);
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, width, height, 12, 12);

            // Subtle border
            g2.setColor(new Color(255, 255, 255, 30));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);

            // Cinema projector badge icon in upper center
            int iconBoxSize = Math.min(width / 3, 44);
            int iconX = (width - iconBoxSize) / 2;
            int iconY = height / 5;
            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 60));
            g2.fillOval(iconX, iconY, iconBoxSize, iconBoxSize);
            g2.setColor(UIColors.LAVENDER_PRIMARY);
            g2.fillOval(iconX + iconBoxSize / 4, iconY + iconBoxSize / 4, iconBoxSize / 2, iconBoxSize / 2);

            // Genre pill
            if (genre != null && !genre.isBlank()) {
                g2.setFont(UIFonts.custom(Font.BOLD, 10));
                java.awt.FontMetrics gfm = g2.getFontMetrics();
                int gw = gfm.stringWidth(genre) + 12;
                int gh = 18;
                int gx = (width - gw) / 2;
                int gy = iconY + iconBoxSize + 14;

                g2.setColor(new Color(255, 255, 255, 25));
                g2.fillRoundRect(gx, gy, gw, gh, 8, 8);
                g2.setColor(UIColors.LAVENDER_PRIMARY);
                g2.drawString(genre, gx + 6, gy + 13);
            }

            // Movie title at bottom
            g2.setColor(UIColors.PURE_WHITE);
            g2.setFont(UIFonts.custom(Font.BOLD, 13));
            java.awt.FontMetrics tfm = g2.getFontMetrics();
            String displayTitle = title;
            if (tfm.stringWidth(displayTitle) > width - 16) {
                while (displayTitle.length() > 3 && tfm.stringWidth(displayTitle + "...") > width - 16) {
                    displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
                }
                displayTitle += "...";
            }
            int tx = (width - tfm.stringWidth(displayTitle)) / 2;
            int ty = height - 20;
            g2.drawString(displayTitle, tx, ty);

            g2.dispose();
            return image;
        });
    }
}
