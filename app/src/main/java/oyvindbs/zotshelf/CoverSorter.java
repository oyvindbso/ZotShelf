package oyvindbs.zotshelf;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CoverSorter {

    /**
     * Sort a list of EpubCoverItems in place.
     *
     * @param items      The list of items to sort
     * @param sortMode   UserPreferences.SORT_BY_TITLE, SORT_BY_AUTHOR or SORT_BY_YEAR
     * @param descending false = A to Z / oldest first, true = Z to A / newest first.
     *                   Items missing the value sorted on (no title, author or year)
     *                   are placed last in both directions.
     */
    public static void sortCovers(List<EpubCoverItem> items, int sortMode, boolean descending) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Comparator<EpubCoverItem> comparator;
        if (sortMode == UserPreferences.SORT_BY_AUTHOR) {
            comparator = (a, b) -> compareByAuthor(a, b, descending);
        } else if (sortMode == UserPreferences.SORT_BY_YEAR) {
            comparator = (a, b) -> compareByYear(a, b, descending);
        } else {
            comparator = (a, b) -> compareByTitle(a, b, descending);
        }

        Collections.sort(items, comparator);
    }

    private static int compareByTitle(EpubCoverItem item1, EpubCoverItem item2, boolean descending) {
        String title1 = sortableTitle(item1);
        String title2 = sortableTitle(item2);

        int missing = compareMissing(title1 == null, title2 == null);
        if (missing != 0 || title1 == null) {
            return missing;
        }

        int result = title1.compareToIgnoreCase(title2);
        return descending ? -result : result;
    }

    private static int compareByAuthor(EpubCoverItem item1, EpubCoverItem item2, boolean descending) {
        String lastName1 = sortableAuthor(item1);
        String lastName2 = sortableAuthor(item2);

        int missing = compareMissing(lastName1 == null, lastName2 == null);
        if (missing != 0) {
            return missing;
        }

        int result = lastName1 == null ? 0 : lastName1.compareToIgnoreCase(lastName2);
        if (result == 0) {
            // Same author: order by title
            return compareByTitle(item1, item2, descending);
        }
        return descending ? -result : result;
    }

    private static int compareByYear(EpubCoverItem item1, EpubCoverItem item2, boolean descending) {
        int year1 = parseYear(item1.getYear());
        int year2 = parseYear(item2.getYear());

        int missing = compareMissing(year1 == NO_YEAR, year2 == NO_YEAR);
        if (missing != 0) {
            return missing;
        }

        int result = Integer.compare(year1, year2);
        if (result == 0) {
            // Same year: always order by title A to Z
            return compareByTitle(item1, item2, false);
        }
        return descending ? -result : result;
    }

    /** Orders items that lack the value after items that have it, whatever the direction. */
    private static int compareMissing(boolean missing1, boolean missing2) {
        if (missing1 == missing2) return 0;
        return missing1 ? 1 : -1;
    }

    private static final int NO_YEAR = Integer.MIN_VALUE;

    private static int parseYear(String year) {
        if (year == null) {
            return NO_YEAR;
        }
        try {
            return Integer.parseInt(year.trim());
        } catch (NumberFormatException e) {
            return NO_YEAR;
        }
    }

    /** Title without a leading article, or null if the item has no title. */
    private static String sortableTitle(EpubCoverItem item) {
        String title = item.getTitle();
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        return removeArticles(title);
    }

    /** Last name of the first author, or null if the item has no known author. */
    private static String sortableAuthor(EpubCoverItem item) {
        String authors = item.getAuthors();
        if (authors == null) {
            return null;
        }
        String lastName = extractFirstAuthorLastName(authors);
        return UNKNOWN_AUTHOR.equals(lastName) ? null : lastName;
    }

    private static final String UNKNOWN_AUTHOR = "zzz";

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