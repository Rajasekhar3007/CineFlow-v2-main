package com.cineflow.util;

import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for resolving, caching, and managing high-resolution movie posters.
 * Supports external 'posters/' directory as well as bundled classpath resources.
 */
public class PosterHelper {

    private static final Map<String, Image> IMAGE_CACHE = new HashMap<>();
    private static final String[] SUPPORTED_EXTENSIONS = {".jpg", ".png", ".jpeg", ".webp"};

    /**
     * Resolves the poster directory. Checks 'posters' in current directory,
     * then 'cineflow/posters', or creates 'posters' if none exists.
     */
    public static File getPostersDirectory() {
        File dir = new File("posters");
        if (dir.exists() && dir.isDirectory()) {
            return dir;
        }
        File subDir = new File("cineflow/posters");
        if (subDir.exists() && subDir.isDirectory()) {
            return subDir;
        }
        dir.mkdirs();
        return dir;
    }

    /**
     * Resolves the poster image for a movie title:
     * 1. Checks in-memory cache.
     * 2. Checks external 'posters/' directory for slug-matched or title-matched image.
     * 3. Checks classpath resources (/images/posters/...).
     * 4. Falls back to default_poster.png.
     */
    public static Image getPoster(String movieTitle) {
        String slug = toSlug(movieTitle);
        if (IMAGE_CACHE.containsKey(slug)) {
            return IMAGE_CACHE.get(slug);
        }

        // 1. Try external posters directory
        Image image = findExternalPoster(movieTitle, slug);

        // 2. Try classpath resources
        if (image == null) {
            image = findClasspathPoster(slug);
        }

        // 3. Fallback to default poster (external first, then classpath)
        if (image == null) {
            image = findDefaultPoster();
        }

        if (image != null) {
            IMAGE_CACHE.put(slug, image);
        }
        return image;
    }

    /**
     * Searches the external posters folder for an image matching the movie title or slug.
     */
    private static Image findExternalPoster(String movieTitle, String slug) {
        File dir = getPostersDirectory();
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        // Direct check by slug
        for (String ext : SUPPORTED_EXTENSIONS) {
            File f = new File(dir, slug + ext);
            if (f.exists() && f.isFile()) {
                Image img = loadFromFile(f);
                if (img != null) return img;
            }
        }

        // Direct check by movie title if title is provided
        if (movieTitle != null && !movieTitle.isBlank()) {
            String sanitized = movieTitle.trim();
            for (String ext : SUPPORTED_EXTENSIONS) {
                File f = new File(dir, sanitized + ext);
                if (f.exists() && f.isFile()) {
                    Image img = loadFromFile(f);
                    if (img != null) return img;
                }
            }

            // Fuzzy scan in directory: compare normalized filenames
            File[] files = dir.listFiles();
            if (files != null) {
                String normalizedTarget = normalize(movieTitle);
                for (File f : files) {
                    if (f.isFile()) {
                        String nameWithoutExt = stripExtension(f.getName());
                        if (normalize(nameWithoutExt).equals(normalizedTarget) ||
                            normalize(nameWithoutExt).equals(slug)) {
                            Image img = loadFromFile(f);
                            if (img != null) return img;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks bundled classpath resources for a matching poster image.
     */
    private static Image findClasspathPoster(String slug) {
        for (String ext : SUPPORTED_EXTENSIONS) {
            Image img = loadClasspathResource("/images/posters/" + slug + ext);
            if (img != null) return img;
        }
        return null;
    }

    private static Image findDefaultPoster() {
        File dir = getPostersDirectory();
        File extDefault = new File(dir, "default_poster.png");
        if (extDefault.exists() && extDefault.isFile()) {
            Image img = loadFromFile(extDefault);
            if (img != null) return img;
        }
        return loadClasspathResource("/images/posters/default_poster.png");
    }

    /**
     * Saves a selected poster image file for a movie.
     * Copies the file into the posters directory and updates the cache.
     *
     * @param movieTitle The title of the movie
     * @param sourceFile The chosen source image file
     * @return The saved File in the posters directory
     */
    public static File savePoster(String movieTitle, File sourceFile) {
        if (movieTitle == null || sourceFile == null || !sourceFile.exists()) {
            return null;
        }

        String slug = toSlug(movieTitle);
        String ext = getFileExtension(sourceFile.getName());
        if (ext.isEmpty()) {
            ext = ".jpg";
        }

        File postersDir = getPostersDirectory();
        File targetFile = new File(postersDir, slug + ext);

        try {
            // Remove any other existing extensions for this slug in posters folder
            for (String otherExt : SUPPORTED_EXTENSIONS) {
                File oldFile = new File(postersDir, slug + otherExt);
                if (oldFile.exists() && !oldFile.equals(targetFile)) {
                    oldFile.delete();
                }
            }

            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Also mirror to src/main/resources and target/classes if present
            mirrorToResources(slug + ext, targetFile.toPath());

            // Refresh cache
            IMAGE_CACHE.remove(slug);
            Image newImage = loadFromFile(targetFile);
            if (newImage != null) {
                IMAGE_CACHE.put(slug, newImage);
            }
            return targetFile;
        } catch (Exception e) {
            System.err.println("[PosterHelper] Failed to save poster for '" + movieTitle + "': " + e.getMessage());
            return null;
        }
    }

    private static void mirrorToResources(String filename, Path sourcePath) {
        // Mirror to src/main/resources/images/posters
        File srcRes = new File("src/main/resources/images/posters");
        if (!srcRes.exists()) {
            srcRes = new File("cineflow/src/main/resources/images/posters");
        }
        if (srcRes.exists() && srcRes.isDirectory()) {
            try {
                Files.copy(sourcePath, new File(srcRes, filename).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }

        // Mirror to target/classes/images/posters
        File targetRes = new File("target/classes/images/posters");
        if (!targetRes.exists()) {
            targetRes = new File("cineflow/target/classes/images/posters");
        }
        if (targetRes.exists() && targetRes.isDirectory()) {
            try {
                Files.copy(sourcePath, new File(targetRes, filename).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }
    }

    public static void clearCache() {
        IMAGE_CACHE.clear();
    }

    /**
     * Creates an Apple Liquid Glass styled ImageView for the movie poster.
     *
     * @param movieTitle   The movie's title to match poster asset
     * @param width        Target display width
     * @param height       Target display height
     * @param cornerRadius Corner radius for Apple rounded glass aesthetic
     * @return Configured and clipped ImageView
     */
    public static ImageView createPosterImageView(String movieTitle, double width, double height, double cornerRadius) {
        Image img = getPoster(movieTitle);
        ImageView view = new ImageView();
        if (img != null) {
            view.setImage(img);
        }
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);
        view.setSmooth(true);

        // Clip with rounded rectangle for Apple liquid glass look
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(cornerRadius * 2);
        clip.setArcHeight(cornerRadius * 2);
        view.setClip(clip);

        // Specular ambient drop shadow
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.45));
        shadow.setRadius(12);
        shadow.setOffsetY(6);
        view.setEffect(shadow);

        return view;
    }

    private static Image loadFromFile(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return new Image(is);
        } catch (Exception e) {
            System.err.println("[PosterHelper] Error reading file: " + file.getAbsolutePath() + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static Image loadClasspathResource(String resourcePath) {
        try (InputStream is = PosterHelper.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception e) {
            System.err.println("[PosterHelper] Error reading classpath: " + resourcePath + " (" + e.getMessage() + ")");
        }
        return null;
    }

    public static String toSlug(String title) {
        if (title == null || title.isBlank()) return "default_poster";
        String lower = title.toLowerCase().trim();
        if (lower.contains("dune")) return "dune_part_two";
        if (lower.contains("inception")) return "inception";
        if (lower.contains("interstellar")) return "interstellar";
        if (lower.contains("dark knight")) return "the_dark_knight";
        if (lower.contains("oppenheimer")) return "oppenheimer";
        if (lower.contains("kantara")) return "kantara";
        if (lower.contains("rrr")) return "rrr";
        if (lower.contains("spirited")) return "spirited_away";
        if (lower.contains("kalki")) return "kalki_2898_ad";
        if (lower.contains("jawan")) return "jawan";
        if (lower.contains("stree")) return "stree_2";
        String fallback = lower.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return fallback.isEmpty() ? "default_poster" : fallback;
    }

    private static String normalize(String str) {
        if (str == null) return "";
        return str.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
