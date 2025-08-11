// Add these methods to your Book class (or wherever the cover/ISBN logic is handled).
// The rest of your Book class remains unchanged; insert or adapt as needed.

import java.awt.image.BufferedImage;

public class Book {
    // Existing fields...
    private String isbn; // Should only contain numbers
    private BufferedImage cover;

    // ...existing code...

    /**
     * Returns the ISBN numbers only (digits).
     */
    public String getIsbnNumbersOnly() {
        if (isbn == null) return "";
        return isbn.replaceAll("\D+", ""); // Remove non-digits
    }

    public BufferedImage getCover() {
        return cover;
    }

    public void setCover(BufferedImage cover) {
        this.cover = cover;
    }

    // ...existing code...
}