// Integration example for your book loading flow.
// Insert these snippets in your book loading/downloading logic.

// Before downloading a new cover, try loading from disk:
import com.zotshelf.service.BookCoverManager;

public void loadBookCovers(Book book) {
    boolean loadedFromDisk = BookCoverManager.loadCoverFromPersistence(book);
    if (loadedFromDisk) {
        // Cover is loaded from persistence, skip downloading
        return;
    }

    // Otherwise, proceed to download the cover
    BufferedImage downloadedCover = downloadCoverFromWeb(book);
    if (downloadedCover != null) {
        book.setCover(downloadedCover);
        BookCoverManager.persistDownloadedCover(book);
    }
}

// Example downloadCoverFromWeb(Book) method stub (implement as needed)
private BufferedImage downloadCoverFromWeb(Book book) {
    // ... your existing cover download logic ...
    return null;
}