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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Procedural cinema asset generator and memory image cache.
 * Provides original, branded vector-rendered cinema poster collages,
 * authentic movie poster artworks, and UI iconography inspired by Netflix and Movieto.
 */
public final class ImageResourceManager {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    // Curated catalog of fictional cinematic poster data for background collage rendering
    private static final CollagePoster[] COLLAGE_POSTERS = {
        new CollagePoster("PHANTOM OF OPERA", "MUSICAL", new Color(0x3E, 0x2A, 0x14), new Color(0xC5, 0x9B, 0x27), "★ 9.3", "IMAX"),
        new CollagePoster("LES MISÉRABLES", "DRAMA", new Color(0x1B, 0x24, 0x30), new Color(0x8B, 0x1E, 0x22), "★ 9.1", "4K"),
        new CollagePoster("INTERSTELLAR", "SCI-FI", new Color(0x15, 0x22, 0x5C), new Color(0x5E, 0x35, 0xB1), "★ 8.9", "4K"),
        new CollagePoster("AVATAR II", "ADVENTURE", new Color(0x00, 0x4D, 0x61), new Color(0x00, 0xA8, 0xBC), "★ 8.4", "3D"),
        new CollagePoster("CYBERPUNK 2077", "ACTION", new Color(0x56, 0x00, 0x5E), new Color(0xE9, 0x1E, 0x63), "★ 8.7", "IMAX"),
        new CollagePoster("DUNE: PART TWO", "SCI-FI", new Color(0x6E, 0x2A, 0x0C), new Color(0xF5, 0x7C, 0x00), "★ 9.0", "IMAX"),
        new CollagePoster("DARK KNIGHT", "CRIME", new Color(0x1A, 0x1E, 0x24), new Color(0x54, 0x6E, 0x7A), "★ 9.1", "4K"),
        new CollagePoster("SPIDER-VERSE", "ANIMATION", new Color(0x7B, 0x0D, 0x1E), new Color(0xFF, 0x2A, 0x6D), "★ 8.8", "3D"),
        new CollagePoster("OPPENHEIMER", "DRAMA", new Color(0x5D, 0x2B, 0x08), new Color(0xFF, 0x8F, 0x00), "★ 8.9", "IMAX"),
        new CollagePoster("JOHN WICK: 4", "ACTION", new Color(0x28, 0x1E, 0x4B), new Color(0x8E, 0x61, 0xD4), "★ 8.5", "4K"),
        new CollagePoster("FROZEN REALM", "FANTASY", new Color(0x0D, 0x3B, 0x66), new Color(0x00, 0xD2, 0xEA), "★ 8.1", "3D"),
        new CollagePoster("TOP GUN", "ACTION", new Color(0x5C, 0x38, 0x0E), new Color(0xFF, 0xB3, 0x00), "★ 8.3", "IMAX"),
        new CollagePoster("INCEPTION", "THRILLER", new Color(0x21, 0x2D, 0x3D), new Color(0x60, 0x7D, 0x8B), "★ 8.8", "4K"),
        new CollagePoster("GLADIATOR II", "ACTION", new Color(0x59, 0x12, 0x17), new Color(0xD3, 0x2F, 0x2F), "★ 8.6", "IMAX"),
        new CollagePoster("ZOOTOPIA II", "ANIMATION", new Color(0x45, 0x27, 0x78), new Color(0xBA, 0x68, 0xC8), "★ 8.2", "3D")
    };

    private record CollagePoster(String title, String genre, Color primary, Color secondary, String rating, String badge) {}

    private ImageResourceManager() {
        // utility class
    }

    /**
     * Loads a raster image file from resources or file system fallback.
     */
    public static BufferedImage loadResourceImage(String path) {
        try (var is = ImageResourceManager.class.getResourceAsStream(path)) {
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }
        try {
            File f = new File("src/main/resources" + path);
            if (f.exists()) {
                return ImageIO.read(f);
            }
        } catch (Exception ignored) {
        }
        return null;
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

            int pad = 4;
            int tw = width - pad * 2;
            int th = height - pad * 2;

            GradientPaint gradient = new GradientPaint(
                0, 0, UIColors.MUTED_PURPLE,
                width, height, UIColors.DEEP_PURPLE
            );
            g2.setPaint(gradient);
            g2.fillRoundRect(pad, pad, tw, th, 16, 16);

            // Subtle ticket notch cutouts on sides
            int notchR = Math.max(4, height / 7);
            g2.setColor(new Color(0x10, 0x10, 0x14, 0));
            g2.fillOval(pad - notchR / 2, height / 2 - notchR / 2, notchR, notchR);
            g2.fillOval(pad + tw - notchR / 2, height / 2 - notchR / 2, notchR, notchR);

            // Golden border
            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 180));
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
     * Generates the Cinema Auditorium Banner with screen, red curtains, seats, and "Online Booking".
     *
     * @param width  Banner width
     * @param height Banner height
     * @return Formatted cinema auditorium banner
     */
    public static Image getCinemaAuditoriumBanner(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String key = "auditorium_banner_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage src = loadResourceImage("/images/cinema_auditorium_banner.jpg");
            if (src == null) {
                return getCinematicHeroBanner(width, height);
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Cover the container
            g2.drawImage(src, 0, 0, width, height, null);

            // Left-to-right dark scrim so text on the left is crisp and clear
            GradientPaint leftScrim = new GradientPaint(
                0, 0, new Color(0x10, 0x10, 0x14, 235),
                (int) (width * 0.58), 0, new Color(0x10, 0x10, 0x14, 60)
            );
            g2.setPaint(leftScrim);
            g2.fillRect(0, 0, width, height);

            // Subtle top/bottom edge vignette
            GradientPaint verticalScrim = new GradientPaint(
                0, 0, new Color(0x10, 0x10, 0x14, 140),
                0, height, new Color(0x10, 0x10, 0x14, 180)
            );
            g2.setPaint(verticalScrim);
            g2.fillRect(0, 0, width, height);

            g2.dispose();
            return image;
        });
    }

    /**
     * Generates the Popcorn, Soda, Golden Ticket and Concessions Banner for My Tickets page.
     *
     * @param width  Banner width
     * @param height Banner height
     * @return Formatted popcorn & concessions banner
     */
    public static Image getPopcornConcessionsBanner(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String key = "popcorn_banner_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage src = loadResourceImage("/images/popcorn_concessions.jpg");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Deep cinema dark background
            g2.setColor(new Color(0x13, 0x15, 0x1E));
            g2.fillRect(0, 0, width, height);

            if (src != null) {
                // Draw popcorn image on the right portion of the banner
                int imgH = height;
                int imgW = (int) (imgH * ((double) src.getWidth() / src.getHeight()));
                int imgX = width - imgW + 20;
                g2.drawImage(src, imgX, 0, imgW, imgH, null);

                // Blend gradient overlay fading smoothly towards the left
                GradientPaint leftFade = new GradientPaint(
                    0, 0, new Color(0x13, 0x15, 0x1E, 255),
                    (int) (width * 0.52), 0, new Color(0x13, 0x15, 0x1E, 40)
                );
                g2.setPaint(leftFade);
                g2.fillRect(0, 0, width, height);
            }

            // Golden border accent
            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 40));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, width - 1, height - 1, 14, 14);

            g2.dispose();
            return image;
        });
    }

    /**
     * Generates a cinematic tilted movie poster collage background (Netflix & Movieto style).
     * Populates the canvas with isometric angled movie posters, glowing accents, and dark gradient overlays.
     *
     * @param width  Banner width
     * @param height Banner height
     * @return Cinematic background banner
     */
    public static Image getCinematicHeroBanner(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String key = "hero_banner_collage_v3_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            // 1. Dark base background
            g2.setColor(UIColors.CINEMATIC_BLACK);
            g2.fillRect(0, 0, width, height);

            // 2. Tilted Movie Posters Grid (Netflix isometric style)
            AffineTransform savedTransform = g2.getTransform();
            double angleRadians = Math.toRadians(-18.0);
            g2.rotate(angleRadians, width * 0.5, height * 0.5);

            int cardW = 120;
            int cardH = 170;
            int gapX = 16;
            int gapY = 18;
            int stepX = cardW + gapX;
            int stepY = cardH + gapY;

            // Compute bounds in rotated space
            int startX = -width / 2 - 100;
            int endX = width + width + 100;
            int startY = -height - 100;
            int endY = height + height + 100;

            // Pre-load official movie posters if available for the collage
            BufferedImage phantomSrc = loadResourceImage("/images/poster_phantom_opera.jpg");
            BufferedImage miserablesSrc = loadResourceImage("/images/poster_les_miserables.jpg");

            int colIndex = 0;
            for (int x = startX; x < endX; x += stepX) {
                int rowIndex = 0;
                int yOffset = (colIndex % 2 == 1) ? stepY / 2 : 0;
                for (int y = startY + yOffset; y < endY; y += stepY) {
                    int posterIndex = Math.abs((colIndex * 7 + rowIndex * 3)) % COLLAGE_POSTERS.length;
                    CollagePoster cp = COLLAGE_POSTERS[posterIndex];

                    if (posterIndex == 0 && phantomSrc != null) {
                        drawRealPosterCard(g2, x, y, cardW, cardH, phantomSrc, cp);
                    } else if (posterIndex == 1 && miserablesSrc != null) {
                        drawRealPosterCard(g2, x, y, cardW, cardH, miserablesSrc, cp);
                    } else {
                        drawCollageCard(g2, x, y, cardW, cardH, cp);
                    }
                    rowIndex++;
                }
                colIndex++;
            }

            g2.setTransform(savedTransform);

            // 3. Dark cinematic gradient overlays
            GradientPaint leftScrim = new GradientPaint(
                0, 0, new Color(0x10, 0x10, 0x14, 195),
                (int) (width * 0.65), 0, new Color(0x10, 0x10, 0x14, 50)
            );
            g2.setPaint(leftScrim);
            g2.fillRect(0, 0, width, height);

            // Vertical vignette
            GradientPaint verticalScrim = new GradientPaint(
                0, 0, new Color(0x10, 0x10, 0x14, 150),
                0, height, new Color(0x10, 0x10, 0x14, 215)
            );
            g2.setPaint(verticalScrim);
            g2.fillRect(0, 0, width, height);

            // Ambient cinema projector glow at top-left
            g2.setPaint(new GradientPaint(
                0, 0, new Color(0x82, 0x74, 0xAB, 40),
                (int) (width * 0.7), (int) (height * 0.6), new Color(0, 0, 0, 0)
            ));
            g2.fillOval(-80, -80, width + 160, (int) (height * 0.75));

            // Film strip decorative accents along bottom
            g2.setColor(new Color(255, 255, 255, 14));
            int spSize = 8;
            for (int x = 20; x < width - 20; x += 20) {
                g2.fillRoundRect(x, height - 28, spSize, spSize, 2, 2);
            }
            g2.drawRoundRect(14, height - 34, width - 28, 20, 6, 6);

            g2.dispose();
            return image;
        });
    }

    private static void drawRealPosterCard(Graphics2D g2, int x, int y, int w, int h, BufferedImage src, CollagePoster cp) {
        RoundRectangle2D.Float cardShape = new RoundRectangle2D.Float(x, y, w, h, 10, 10);
        Graphics2D gClip = (Graphics2D) g2.create();
        gClip.clip(cardShape);
        gClip.drawImage(src, x, y, w, h, null);

        // Dark gradient at bottom of poster
        GradientPaint bottomFade = new GradientPaint(
            x, y + (int) (h * 0.55), new Color(0, 0, 0, 0),
            x, y + h, new Color(0, 0, 0, 230)
        );
        gClip.setPaint(bottomFade);
        gClip.fillRect(x, y + (int) (h * 0.50), w, (int) (h * 0.50));

        // Top mini badges
        gClip.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        gClip.setColor(new Color(255, 255, 255, 230));
        gClip.drawString(cp.badge(), x + 8, y + 16);

        gClip.setColor(new Color(255, 215, 0, 240));
        gClip.drawString(cp.rating(), x + w - 34, y + 16);

        gClip.dispose();

        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(cardShape);
    }

    private static void drawCollageCard(Graphics2D g2, int x, int y, int w, int h, CollagePoster cp) {
        RoundRectangle2D.Float cardShape = new RoundRectangle2D.Float(x, y, w, h, 10, 10);

        GradientPaint cardGrad = new GradientPaint(x, y, cp.secondary(), x, y + h, cp.primary());
        g2.setPaint(cardGrad);
        g2.fill(cardShape);

        if ("SCI-FI".equals(cp.genre())) {
            g2.setColor(new Color(255, 255, 255, 180));
            g2.fillOval(x + w / 2 - 18, y + h / 3 - 18, 36, 36);
            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 210));
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawOval(x + w / 2 - 28, y + h / 3 - 8, 56, 16);
            g2.fillOval(x + 16, y + 24, 3, 3);
            g2.fillOval(x + w - 24, y + 36, 3, 3);
        } else if ("ACTION".equals(cp.genre())) {
            g2.setColor(new Color(255, 220, 100, 150));
            g2.fillOval(x + w / 2 - 22, y + h / 3 - 22, 44, 44);
            g2.setColor(new Color(255, 255, 255, 100));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(x + 10, y + h / 2, x + w - 10, y + 20);
            g2.drawLine(x + 20, y + h / 2 + 10, x + w - 15, y + 35);
        } else if ("ANIMATION".equals(cp.genre()) || "FANTASY".equals(cp.genre())) {
            g2.setColor(new Color(255, 255, 255, 160));
            g2.fillOval(x + w / 2 - 16, y + h / 3 - 16, 32, 32);
            g2.fillOval(x + 16, y + 22, 5, 5);
            g2.fillOval(x + w - 22, y + 36, 6, 6);
            g2.fillOval(x + 24, y + h / 2, 4, 4);
        } else {
            g2.setColor(new Color(255, 255, 255, 80));
            Path2D.Float beam = new Path2D.Float();
            beam.moveTo(x + w / 2, y + 16);
            beam.lineTo(x + w - 8, y + h / 2 + 14);
            beam.lineTo(x + 8, y + h / 2 + 14);
            beam.closePath();
            g2.fill(beam);
        }

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g2.setColor(new Color(255, 255, 255, 230));
        g2.drawString(cp.badge(), x + 8, y + 16);

        g2.setColor(new Color(255, 215, 0, 240));
        g2.drawString(cp.rating(), x + w - 34, y + 16);

        GradientPaint bottomFade = new GradientPaint(
            x, y + (int) (h * 0.50), new Color(0, 0, 0, 0),
            x, y + h, new Color(0, 0, 0, 235)
        );
        g2.setPaint(bottomFade);
        g2.fillRoundRect(x, y + (int) (h * 0.48), w, (int) (h * 0.52), 10, 10);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        java.awt.FontMetrics tfm = g2.getFontMetrics();
        String dt = cp.title();
        if (tfm.stringWidth(dt) > w - 10) {
            dt = dt.substring(0, Math.min(dt.length(), 10)) + "..";
        }
        int tx = x + (w - tfm.stringWidth(dt)) / 2;

        g2.setColor(new Color(0, 0, 0, 220));
        g2.drawString(dt, tx + 1, y + h - 17);
        g2.setColor(UIColors.PURE_WHITE);
        g2.drawString(dt, tx, y + h - 18);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g2.setColor(new Color(0xD8, 0xD0, 0xF2, 230));
        int gx = x + (w - g2.getFontMetrics().stringWidth(cp.genre())) / 2;
        g2.drawString(cp.genre(), gx, y + h - 6);

        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(cardShape);
    }

    /**
     * Generates an eye icon for toggling password visibility.
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

            g2.drawArc(pad, y, w, h, 0, 180);
            g2.drawArc(pad, y, w, h, 0, -180);

            int pupilSize = size / 4;
            g2.fillOval((size - pupilSize) / 2, (size - pupilSize) / 2, pupilSize, pupilSize);

            if (!show) {
                g2.setColor(UIColors.TEXT_MUTED);
                g2.drawLine(pad + 1, pad + 1, size - pad - 1, size - pad - 1);
            }

            g2.dispose();
            return image;
        });
        return new ImageIcon(img);
    }

    /**
     * Creates a movie poster graphic. If an official movie poster is available in resources
     * (e.g. Phantom of the Opera or Les Misérables), it renders the real poster; otherwise
     * it procedurally synthesizes a rich cinematic poster.
     *
     * @param title  Movie title
     * @param genre  Movie genre
     * @param width  Poster width
     * @param height Poster height
     * @return Formatted movie poster
     */
    public static Image getMoviePoster(String title, String genre, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String cleanTitle = (title != null && !title.isBlank()) ? title.trim() : "Cinema Feature";
        String cleanGenre = (genre != null && !genre.isBlank()) ? genre.trim().toUpperCase() : "CINEMA";
        String key = "poster_v3_" + cleanTitle + "_" + cleanGenre + "_" + width + "x" + height;

        return CACHE.computeIfAbsent(key, k -> {
            String norm = cleanTitle.toLowerCase();
            BufferedImage realPoster = null;

            if (norm.contains("phantom") || norm.contains("opera") || norm.contains("fantôme")) {
                realPoster = loadResourceImage("/images/poster_phantom_opera.jpg");
            } else if (norm.contains("misérables") || norm.contains("miserables") || norm.contains("les mis")) {
                realPoster = loadResourceImage("/images/poster_les_miserables.jpg");
            }

            if (realPoster != null) {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = image.createGraphics();
                SeatSyncTheme.enableAntiAliasing(g2);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                RoundRectangle2D.Float clip = new RoundRectangle2D.Float(0, 0, width, height, 12, 12);
                g2.clip(clip);
                g2.drawImage(realPoster, 0, 0, width, height, null);

                // Subtle card border
                g2.setColor(new Color(255, 255, 255, 50));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);

                g2.dispose();
                return image;
            }

            // Fallback to stylized vector poster
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            RoundRectangle2D.Float posterClip = new RoundRectangle2D.Float(0, 0, width, height, 12, 12);
            g2.clip(posterClip);

            Color topGrad;
            Color botGrad;
            Color accentColor;

            if (cleanGenre.contains("SCI-FI") || cleanGenre.contains("SPACE")) {
                topGrad = new Color(0x3B, 0x1D, 0x6E);
                botGrad = new Color(0x0A, 0x0F, 0x24);
                accentColor = new Color(0x64, 0xDF, 0xDF);
            } else if (cleanGenre.contains("ACTION") || cleanGenre.contains("ADVENTURE")) {
                topGrad = new Color(0x8A, 0x25, 0x18);
                botGrad = new Color(0x1A, 0x0A, 0x0C);
                accentColor = new Color(0xFF, 0x9F, 0x1C);
            } else if (cleanGenre.contains("ANIMATION") || cleanGenre.contains("COMEDY")) {
                topGrad = new Color(0x7B, 0x1F, 0xB5);
                botGrad = new Color(0x1B, 0x1A, 0x3A);
                accentColor = new Color(0xFF, 0x40, 0x81);
            } else if (cleanGenre.contains("HORROR") || cleanGenre.contains("THRILLER")) {
                topGrad = new Color(0x4A, 0x0E, 0x14);
                botGrad = new Color(0x0D, 0x08, 0x09);
                accentColor = new Color(0xE6, 0x39, 0x46);
            } else if (cleanGenre.contains("DRAMA") || cleanGenre.contains("ROMANCE") || cleanGenre.contains("MUSICAL")) {
                topGrad = new Color(0x28, 0x30, 0x54);
                botGrad = new Color(0x0E, 0x11, 0x1F);
                accentColor = new Color(0xD8, 0xD0, 0xF2);
            } else {
                int hash = Math.abs(cleanTitle.hashCode());
                topGrad = new Color(0x28 + (hash % 30), 0x20 + ((hash / 3) % 30), 0x50 + ((hash / 7) % 40));
                botGrad = new Color(0x10 + (hash % 10), 0x10 + ((hash / 2) % 10), 0x1A + ((hash / 4) % 15));
                accentColor = UIColors.LAVENDER_PRIMARY;
            }

            // 1. Background gradient
            GradientPaint gp = new GradientPaint(0, 0, topGrad, 0, height, botGrad);
            g2.setPaint(gp);
            g2.fillRect(0, 0, width, height);

            // 2. Thematic center illustration
            int centerX = width / 2;
            int centerY = (int) (height * 0.38);

            if (cleanGenre.contains("SCI-FI") || cleanGenre.contains("SPACE")) {
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fillOval(centerX - 48, centerY - 48, 96, 96);
                g2.setColor(accentColor);
                g2.fillOval(centerX - 24, centerY - 24, 48, 48);

                g2.setColor(new Color(255, 255, 255, 160));
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawOval(centerX - 38, centerY - 10, 76, 20);

                g2.setColor(new Color(255, 255, 255, 180));
                g2.fillOval(16, 28, 2, 2);
                g2.fillOval(width - 24, 42, 2, 2);
                g2.fillOval(28, height / 2, 2, 2);
                g2.fillOval(width - 32, height / 2 - 20, 2, 2);
            } else if (cleanGenre.contains("ACTION")) {
                g2.setPaint(new GradientPaint(
                    centerX, centerY - 30, new Color(255, 220, 120, 180),
                    centerX, centerY + 30, new Color(255, 60, 20, 0)
                ));
                g2.fillOval(centerX - 36, centerY - 36, 72, 72);

                g2.setColor(new Color(255, 255, 255, 65));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(14, centerY + 28, width - 14, centerY - 28);
                g2.drawLine(24, centerY + 36, width - 20, centerY - 18);
            } else {
                g2.setColor(new Color(255, 255, 255, 45));
                Path2D.Float cone = new Path2D.Float();
                cone.moveTo(centerX, centerY - 28);
                cone.lineTo(width - 16, centerY + 34);
                cone.lineTo(16, centerY + 34);
                cone.closePath();
                g2.fill(cone);

                g2.setColor(accentColor);
                g2.fillOval(centerX - 16, centerY - 16, 32, 32);
                g2.setColor(botGrad);
                g2.fillOval(centerX - 6, centerY - 6, 12, 12);
            }

            // 3. Top Badges: IMAX / 4K Banner (Right) and Star Rating (Left)
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, width / 18)));
            g2.setColor(new Color(255, 215, 0, 240));
            int ratingValue = 80 + (Math.abs(cleanTitle.hashCode()) % 15);
            String ratingStr = "★ " + (ratingValue / 10) + "." + (ratingValue % 10);
            g2.drawString(ratingStr, 10, 18);

            // IMAX / 4K Badge
            int badgeW = Math.max(34, width / 4);
            int badgeH = 15;
            int badgeX = width - badgeW - 8;
            int badgeY = 7;
            g2.setColor(new Color(0, 0, 0, 130));
            g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6);
            g2.setColor(new Color(255, 255, 255, 80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6);

            g2.setColor(UIColors.PURE_WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(7, width / 22)));
            String badgeText = (cleanTitle.length() % 2 == 0) ? "IMAX" : "4K HDR";
            int btw = g2.getFontMetrics().stringWidth(badgeText);
            g2.drawString(badgeText, badgeX + (badgeW - btw) / 2, badgeY + 11);

            // 4. Dark Bottom Scrim
            GradientPaint bottomScrim = new GradientPaint(
                0, (int) (height * 0.52), new Color(0, 0, 0, 0),
                0, height, new Color(0x0C, 0x0C, 0x12, 245)
            );
            g2.setPaint(bottomScrim);
            g2.fillRect(0, (int) (height * 0.48), width, (int) (height * 0.52));

            // 5. Genre Pill
            int pillH = 16;
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, width / 19)));
            java.awt.FontMetrics gfm = g2.getFontMetrics();
            int pillW = gfm.stringWidth(cleanGenre) + 14;
            int pillX = (width - pillW) / 2;
            int pillY = (int) (height * 0.68);

            g2.setColor(new Color(255, 255, 255, 35));
            g2.fillRoundRect(pillX, pillY, pillW, pillH, 8, 8);
            g2.setColor(new Color(255, 255, 255, 75));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRoundRect(pillX, pillY, pillW, pillH, 8, 8);

            g2.setColor(accentColor);
            g2.drawString(cleanGenre, pillX + 7, pillY + 12);

            // 6. Movie Title
            int titleFontSize = Math.max(10, Math.min(14, width / 12));
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, titleFontSize));
            java.awt.FontMetrics tfm = g2.getFontMetrics();

            String displayTitle = cleanTitle;
            if (tfm.stringWidth(displayTitle) > width - 16) {
                while (displayTitle.length() > 3 && tfm.stringWidth(displayTitle + "...") > width - 16) {
                    displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
                }
                displayTitle += "...";
            }
            int tx = (width - tfm.stringWidth(displayTitle)) / 2;
            int ty = (int) (height * 0.85);

            g2.setColor(new Color(0, 0, 0, 220));
            g2.drawString(displayTitle, tx + 1, ty + 1);

            g2.setColor(UIColors.PURE_WHITE);
            g2.drawString(displayTitle, tx, ty);

            // 7. Footer tag: "NOW SHOWING • CINEMA"
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(7, width / 24)));
            g2.setColor(new Color(0xA9, 0x9A, 0xCD, 190));
            String subText = "NOW SHOWING • CINEMA";
            int stx = (width - g2.getFontMetrics().stringWidth(subText)) / 2;
            g2.drawString(subText, stx, height - 8);

            g2.setColor(new Color(255, 255, 255, 50));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);

            g2.dispose();
            return image;
        });
    }
}
