package oyvindbs.zotshelf;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CoverSorter {
    
    /**
     * Sort a list of EpubCoverItems based on the specified sort mode
     * @param items The list of items to sort (sorted in place)
     * @param sortMode The sort mode (UserPreferences.SORT_BY_TITLE or SORT_BY_AUTHOR)
     */
    public static void sortCovers(List<EpubCoverItem> items, int sortMode) {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        Comparator<EpubCoverItem> comparator;
        
        if (sortMode == UserPreferences.SORT_BY_AUTHOR) {
            comparator = new AuthorComparator();
        } else {
            // Default to title sorting
            comparator = new TitleComparator();
        }
        
        Collections.sort(items, comparator);
    }
    
    /**
     * Comparator for sorting by title (case-insensitive)
     */
    private static class TitleComparator implements Comparator<EpubCoverItem> {
        @Override
        public int compare(EpubCoverItem item1, EpubCoverItem item2) {
            String title1 = item1.getTitle();
            String title2 = item2.getTitle();
            
            // Handle null titles
            if (title1 == null && title2 == null) return 0;
            if (title1 == null) return 1;  // null titles go to end
            if (title2 == null) return -1;
            
            // Remove common articles for better sorting
            title1 = removeArticles(title1);
            title2 = removeArticles(title2);
            
            return title1.compareToIgnoreCase(title2);
        }
    }
    
    /**
     * Comparator for sorting by author last name (case-insensitive)
     */
    private static class AuthorComparator implements Comparator<EpubCoverItem> {
        @Override
        public int compare(EpubCoverItem item1, EpubCoverItem item2) {
            String authors1 = item1.getAuthors();
            String authors2 = item2.getAuthors();
            
            // Handle null authors
            if (authors1 == null && authors2 == null) return 0;
            if (authors1 == null) return 1;  // null authors go to end
            if (authors2 == null) return -1;
            
            // Extract first author's last name for sorting
            String lastName1 = extractFirstAuthorLastName(authors1);
            String lastName2 = extractFirstAuthorLastName(authors2);
            
            int result = lastName1.compareToIgnoreCase(lastName2);
            
            // If last names are the same, sort by title as secondary criteria
            if (result == 0) {
                String title1 = item1.getTitle();
                String title2 = item2.getTitle();
                
                if (title1 == null && title2 == null) return 0;
                if (title1 == null) return 1;
                if (title2 == null) return -1;
                
                title1 = removeArticles(title1);
                title2 = removeArticles(title2);
                
                result = title1.compareToIgnoreCase(title2);
            }
            
            return result;
        }
    }
    
    /**
     * Extract the last name of the first author from the authors string
     * Handles formats like "Smith, John" or "Smith, John; Doe, Jane"
     */
    private static String extractFirstAuthorLastName(String authors) {
        if (authors == null || authors.trim().isEmpty() || authors.equals("Unknown")) {
            return "zzz"; // Put unknown authors at the end
        }
        
        // Split by semicolon or comma to get individual authors
        String firstAuthor;
        if (authors.contains(";")) {
            firstAuthor = authors.split(";")[0].trim();
        } else {
            firstAuthor = authors.trim();
        }
        
        // Handle "Last, First" format
        if (firstAuthor.contains(",")) {
            String lastName = firstAuthor.split(",")[0].trim();
            return lastName.isEmpty() ? "zzz" : lastName;
        }
        
        // Handle "First Last" format (take the last word as last name)
        String[] nameParts = firstAuthor.split("\\s+");
        if (nameParts.length > 0) {
            return nameParts[nameParts.length - 1];
        }
        
        return firstAuthor.isEmpty() ? "zzz" : firstAuthor;
    }
    
    /**
     * Remove common articles from the beginning of titles for better sorting
     */
    private static String removeArticles(String title) {
        if (title == null) return "";
        
        String trimmed = title.trim();
        String lower = trimmed.toLowerCase();
        
        // Remove common English articles
        if (lower.startsWith("the ")) {
            return trimmed.substring(4);
        } else if (lower.startsWith("a ")) {
            return trimmed.substring(2);
        } else if (lower.startsWith("an ")) {
            return trimmed.substring(3);
        }
        
        return trimmed;
    }
}