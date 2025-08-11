package com.zotshelf.service;

import com.zotshelf.model.Book;
import com.zotshelf.util.CoverPersistence;

import java.awt.image.BufferedImage;

/**
 * Handles cover loading and persistence for books.
 */
public class BookCoverManager {

    /**
     * Attempts to load a cover for the book from disk.
     * Returns true if loaded, false if not found.
     */
    public static boolean loadCoverFromPersistence(Book book) {
        String isbn = book.getIsbnNumbersOnly();
        BufferedImage persistedCover = CoverPersistence.loadCover(isbn);
        if (persistedCover != null) {
            book.setCover(persistedCover);
            return true;
        }
        return false;
    }

    /**
     * After downloading a new cover, save it to disk.
     */
    public static void persistDownloadedCover(Book book) {
        String isbn = book.getIsbnNumbersOnly();
        BufferedImage cover = book.getCover();
        if (cover != null) {
            CoverPersistence.saveCover(isbn, cover);
        }
    }
}