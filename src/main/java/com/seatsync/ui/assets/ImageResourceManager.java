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
 * authentic movie poster artworks in repeat mode, and UI iconography.
 */
public final class ImageResourceManager {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    // Curated catalog of real movie posters used across the application in repeat mode
    public static final String[] REAL_POSTERS = {
        "/images/poster_pirates_caribbean.jpg",
        "/images/poster_raya_dragon.jpg",
        "/images/poster_avatar_water.jpg",
        "/images/poster_zootopia.jpg",
        "/images/poster_phantom_opera.jpg",
        "/images/poster_les_miserables.jpg"
    };

    private static final CollagePoster[] COLLAGE_POSTERS = {
        new CollagePoster("PIRATES OF CARIBBEAN", "ACTION", "★ 8.8", "3D", REAL_POSTERS[0]),
        new CollagePoster("RAYA & DRAGON", "ANIMATION", "★ 8.6", "3D", REAL_POSTERS[1]),
        new CollagePoster("AVATAR: WATER", "ADVENTURE", "★ 8.9", "IMAX", REAL_POSTERS[2]),
        new CollagePoster("ZOOTOPIA", "ANIMATION", "★ 8.7", "3D", REAL_POSTERS[3]),
        new CollagePoster("PHANTOM OF OPERA", "MUSICAL", "★ 9.3", "IMAX", REAL_POSTERS[4]),
        new CollagePoster("LES MISÉRABLES", "DRAMA", "★ 9.1", "4K", REAL_POSTERS[5]),
        new CollagePoster("PIRATES: TIDES", "ACTION", "★ 8.8", "IMAX", REAL_POSTERS[0]),
        new CollagePoster("RAYA: DRAGON", "FANTASY", "★ 8.6", "4K", REAL_POSTERS[1]),
        new CollagePoster("AVATAR II", "SCI-FI", "★ 8.9", "3D", REAL_POSTERS[2]),
        new CollagePoster("ZOOTOPIA CITY", "COMEDY", "★ 8.7", "IMAX", REAL_POSTERS[3]),
        new CollagePoster("GRAND OPERA", "CLASSIC", "★ 9.3", "4K", REAL_POSTERS[4]),
        new CollagePoster("LES MISÉRABLES II", "EPIC", "★ 9.1", "IMAX", REAL_POSTERS[5])
    };

    private record CollagePoster(String title, String genre, String rating, String badge, String resourcePath) {}

    private ImageResourceManager() {
        // utility class
    }

    /**
     * Loads a raster image file from resources or file system fallback.
     */
    public static BufferedImage loadResourceImage(String path) {
        if (path == null) {
            return null;
        }
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

            int notchR = Math.max(4, height / 7);
            g2.setColor(new Color(0x10, 0x10, 0x14, 0));
            g2.fillOval(pad - notchR / 2, height / 2 - notchR / 2, notchR, notchR);
            g2.fillOval(pad + tw - notchR / 2, height / 2 - notchR / 2, notchR, notchR);

            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 180));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(pad, pad, tw, th, 16, 16);

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
     */
    public static Image getCinemaAuditoriumBanner(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String key = "auditorium_banner_v2_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage src = loadResourceImage("/images/cinema_auditorium_banner.jpg");
            if (src == null) {
                return getCinematicHeroBanner(width, height);
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2.drawImage(src, 0, 0, width, height, null);

            GradientPaint leftScrim = new GradientPaint(
                0, 0, new Color(0x10, 0x10, 0x14, 235),
                (int) (width * 0.58), 0, new Color(0x10, 0x10, 0x14, 60)
            );
            g2.setPaint(leftScrim);
            g2.fillRect(0, 0, width, height);

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
     */
    public static Image getPopcornConcessionsBanner(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String key = "popcorn_banner_v2_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage src = loadResourceImage("/images/popcorn_concessions.jpg");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2.setColor(new Color(0x13, 0x15, 0x1E));
            g2.fillRect(0, 0, width, height);

            if (src != null) {
                int imgH = height;
                int imgW = (int) (imgH * ((double) src.getWidth() / src.getHeight()));
                int imgX = width - imgW + 20;
                g2.drawImage(src, imgX, 0, imgW, imgH, null);

                GradientPaint leftFade = new GradientPaint(
                    0, 0, new Color(0x13, 0x15, 0x1E, 255),
                    (int) (width * 0.52), 0, new Color(0x13, 0x15, 0x1E, 40)
                );
                g2.setPaint(leftFade);
                g2.fillRect(0, 0, width, height);
            }

            g2.setColor(new Color(0xD8, 0xD0, 0xF2, 40));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, width - 1, height - 1, 14, 14);

            g2.dispose();
            return image;
        });
    }

    /**
     * Generates a cinematic tilted movie poster collage background (Netflix & Movieto style).
     * In repeat mode, EVERY card across the entire canvas renders one of the 6 real movie posters!
     */
    public static Image getCinematicHeroBanner(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String key = "hero_banner_collage_repeat_" + width + "x" + height;
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            SeatSyncTheme.enableAntiAliasing(g2);

            // 1. Dark base background
            g2.setColor(UIColors.CINEMATIC_BLACK);
            g2.fillRect(0, 0, width, height);

            // 2. Preload the 6 real posters into memory for repeat rendering
            BufferedImage[] realImages = new BufferedImage[REAL_POSTERS.length];
            for (int i = 0; i < REAL_POSTERS.length; i++) {
                realImages[i] = loadResourceImage(REAL_POSTERS[i]);
            }

            // 3. Tilted Movie Posters Grid (Netflix isometric style)
            AffineTransform savedTransform = g2.getTransform();
            double angleRadians = Math.toRadians(-18.0);
            g2.rotate(angleRadians, width * 0.5, height * 0.5);

            int cardW = 120;
            int cardH = 170;
            int gapX = 16;
            int gapY = 18;
            int stepX = cardW + gapX;
            int stepY = cardH + gapY;

            int startX = -width / 2 - 120;
            int endX = width + width + 120;
            int startY = -height - 120;
            int endY = height + height + 120;

            int colIndex = 0;
            for (int x = startX; x < endX; x += stepX) {
                int rowIndex = 0;
                int yOffset = (colIndex % 2 == 1) ? stepY / 2 : 0;
                for (int y = startY + yOffset; y < endY; y += stepY) {
                    // Pick repeated poster cyclically
                    int posterIndex = Math.abs((colIndex * 7 + rowIndex * 3)) % COLLAGE_POSTERS.length;
                    CollagePoster cp = COLLAGE_POSTERS[posterIndex];

                    int imgIndex = posterIndex % REAL_POSTERS.length;
                    BufferedImage realImg = realImages[imgIndex];

                    if (realImg != null) {
                        drawRealPosterCard(g2, x, y, cardW, cardH, realImg, cp);
                    }
                    rowIndex++;
                }
                colIndex++;
            }

            g2.setTransform(savedTransform);

            // 4. Dark cinematic gradient overlays
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

            g2.setPaint(new GradientPaint(
                0, 0, new Color(0x82, 0x74, 0xAB, 40),
                (int) (width * 0.7), (int) (height * 0.6), new Color(0, 0, 0, 0)
            ));
            g2.fillOval(-80, -80, width + 160, (int) (height * 0.75));

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
        gClip.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
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

        // Title at bottom
        gClip.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        gClip.setColor(UIColors.PURE_WHITE);
        String dt = cp.title();
        java.awt.FontMetrics tfm = gClip.getFontMetrics();
        if (tfm.stringWidth(dt) > w - 10) {
            dt = dt.substring(0, Math.min(dt.length(), 11)) + "..";
        }
        int tx = x + (w - tfm.stringWidth(dt)) / 2;
        gClip.drawString(dt, tx, y + h - 6);

        gClip.dispose();

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
     * Creates a movie poster graphic in REPEAT MODE.
     * If a movie specifically matches one of the real posters, it uses that poster.
     * If there is no specific match, it picks one of the 6 real movie posters in repeat mode!
     *
     * @param title  Movie title
     * @param genre  Movie genre
     * @param width  Poster width
     * @param height Poster height
     * @return Real movie poster image
     */
    public static Image getMoviePoster(String title, String genre, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String cleanTitle = (title != null && !title.isBlank()) ? title.trim() : "Cinema Feature";
        String cleanGenre = (genre != null && !genre.isBlank()) ? genre.trim().toUpperCase() : "CINEMA";
        String key = "poster_repeat_" + cleanTitle + "_" + cleanGenre + "_" + width + "x" + height;

        return CACHE.computeIfAbsent(key, k -> {
            String norm = cleanTitle.toLowerCase();
            BufferedImage realPoster = null;

            if (norm.contains("phantom") || norm.contains("opera") || norm.contains("fantôme")) {
                realPoster = loadResourceImage("/images/poster_phantom_opera.jpg");
            } else if (norm.contains("misérables") || norm.contains("miserables") || norm.contains("les mis")) {
                realPoster = loadResourceImage("/images/poster_les_miserables.jpg");
            } else if (norm.contains("pirate") || norm.contains("caribbean") || norm.contains("jack sparrow")) {
                realPoster = loadResourceImage("/images/poster_pirates_caribbean.jpg");
            } else if (norm.contains("avatar") || norm.contains("water") || norm.contains("pandora")) {
                realPoster = loadResourceImage("/images/poster_avatar_water.jpg");
            } else if (norm.contains("zootopia") || norm.contains("judy hopps") || norm.contains("nick wilde")) {
                realPoster = loadResourceImage("/images/poster_zootopia.jpg");
            } else if (norm.contains("raya") || norm.contains("dragon")) {
                realPoster = loadResourceImage("/images/poster_raya_dragon.jpg");
            } else {
                // Repeat Mode: Select one of the 6 real posters cyclically based on title hash!
                int fallbackIndex = Math.abs(cleanTitle.hashCode()) % REAL_POSTERS.length;
                realPoster = loadResourceImage(REAL_POSTERS[fallbackIndex]);
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

            // Fallback safe solid image
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(new Color(0x1B, 0x1B, 0x24));
            g2.fillRoundRect(0, 0, width, height, 12, 12);
            g2.dispose();
            return image;
        });
    }
}
