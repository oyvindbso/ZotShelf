package com.zotshelf.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility for saving and loading book cover images to disk using ISBN numbers.
 * Covers are saved as {isbn}.jpg in the covers/ directory.
 */
public class CoverPersistence {

    private static final String COVER_DIR = "covers";

    static {
        File dir = new File(COVER_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Loads a cover image from disk using the ISBN.
     * @param isbn The book's ISBN (numbers only).
     * @return BufferedImage if found, otherwise null.
     */
    public static BufferedImage loadCover(String isbn) {
        if (isbn == null || isbn.isEmpty()) return null;
        String filename = COVER_DIR + File.separator + isbn + ".jpg";
        File file = new File(filename);
        if (!file.exists()) return null;
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            System.err.println("Failed to load cover for ISBN " + isbn + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves a cover image to disk using the ISBN.
     * @param isbn The book's ISBN (numbers only).
     * @param cover The cover image to save.
     * @return true if saved successfully, false otherwise.
     */
    public static boolean saveCover(String isbn, BufferedImage cover) {
        if (isbn == null || isbn.isEmpty() || cover == null) return false;
        String filename = COVER_DIR + File.separator + isbn + ".jpg";
        File file = new File(filename);
        try {
            return ImageIO.write(cover, "jpg", file);
        } catch (IOException e) {
            System.err.println("Failed to save cover for ISBN " + isbn + ": " + e.getMessage());
            return false;
        }
    }
}