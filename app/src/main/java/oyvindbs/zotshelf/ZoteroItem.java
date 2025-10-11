package oyvindbs.zotshelf;


import com.google.gson.annotations.SerializedName;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ZoteroItem {
    
    @SerializedName("key")
    private String key;
    
    @SerializedName("data")
    private ZoteroItemData data;
    
    @SerializedName("links")
    private ZoteroLinks links;
    
    // Reference to parent item (not from JSON, set programmatically)
    private ZoteroItem parentItem;
    
    // Nested class to represent item data
    public static class ZoteroItemData {
        @SerializedName("title")
        private String title;
        
        @SerializedName("creators")
        private ZoteroCreator[] creators;
        
        @SerializedName("contentType")
        private String contentType;
        
        @SerializedName("filename")
        private String filename;
        
        @SerializedName("parentItem")
        private String parentItemKey;
        
        @SerializedName("itemType")
        private String itemType;

        @SerializedName("date")  
        private String date;

        @SerializedName("year")  
        private String year;
    }
    
    // Nested class to represent creator data
    public static class ZoteroCreator {
        @SerializedName("firstName")
        private String firstName;
        
        @SerializedName("lastName")
        private String lastName;
        
        @SerializedName("creatorType")
        private String creatorType;
    }
    
    // Nested class to represent links
    public static class ZoteroLinks {
        @SerializedName("enclosure")
        private ZoteroLink enclosure;
        
        public ZoteroLink getEnclosure() {
            return enclosure;
        }
    }
    
    // Nested class to represent a link
    public static class ZoteroLink {
        @SerializedName("href")
        private String href;
        
        @SerializedName("type")
        private String type;
        
        public String getHref() {
            return href;
        }
    }
    
    public String getKey() {
        return key;
    }
    
    public String getTitle() {
        // First try to get title from parent if available
        if (parentItem != null && parentItem.data != null && 
            parentItem.data.title != null && !parentItem.data.title.isEmpty()) {
            return parentItem.data.title;
        }
        // Fall back to attachment title
        return data != null ? data.title : "";
    }
    
    public String getMimeType() {
        return data != null ? data.contentType : "";
    }
    
    public String getFilename() {
        return data != null ? data.filename : "";
    }
    
    public String getItemType() {
        return data != null ? data.itemType : "";
    }
    
    public String getParentItemKey() {
        return data != null ? data.parentItemKey : null;
    }
    
    public void setParentItem(ZoteroItem parentItem) {
        this.parentItem = parentItem;
    }
    
    public ZoteroItem getParentItem() {
        return parentItem;
    }
    
    /**
     * Get the item type of the parent item (the actual content type)
     * @return The parent item's type, or null if no parent
     */
    public String getParentItemType() {
        if (parentItem != null && parentItem.data != null) {
            return parentItem.data.itemType;
        }
        return null;
    }
    
    /**
     * Check if this item represents a book based on the parent item type
     * @return true if this is likely a book, false otherwise
     */
    public boolean isBook() {
        String parentType = getParentItemType();
        if (parentType == null) {
            // If no parent, assume it could be a standalone book
            // (some books might not have parent items)
            return true;
        }
        
        // Zotero item types that typically represent books
        return parentType.equals("book") || 
               parentType.equals("bookSection") ||
               parentType.equals("encyclopediaArticle") ||
               parentType.equals("dictionaryEntry") ||
               parentType.equals("manuscript") ||
               parentType.equals("thesis") ||
               parentType.equals("report") ||
               parentType.equals("document"); // Some documents might be books
    }
    
    /**
     * Check if this item represents an article or academic paper
     * @return true if this is likely an article, false otherwise
     */
    public boolean isArticle() {
        String parentType = getParentItemType();
        if (parentType == null) {
            return false;
        }
        
        // Zotero item types that typically represent articles
        return parentType.equals("journalArticle") ||
               parentType.equals("magazineArticle") ||
               parentType.equals("newspaperArticle") ||
               parentType.equals("conferencePaper") ||
               parentType.equals("preprint") ||
               parentType.equals("blogPost") ||
               parentType.equals("forumPost") ||
               parentType.equals("patent") ||
               parentType.equals("case") ||
               parentType.equals("statute");
    }
    
    public String getAuthors() {
        // First try to get authors from parent item
        if (parentItem != null && parentItem.data != null && 
            parentItem.data.creators != null && parentItem.data.creators.length > 0) {
            StringBuilder authorsBuilder = new StringBuilder();
            for (ZoteroCreator creator : parentItem.data.creators) {
                if (creator.creatorType != null && creator.creatorType.equals("author")) {
                    if (authorsBuilder.length() > 0) {
                        authorsBuilder.append(", ");
                    }
                    
                    // Handle potential null values
                    String lastName = (creator.lastName != null) ? creator.lastName : "";
                    String firstName = (creator.firstName != null) ? creator.firstName : "";
                    
                    if (!lastName.isEmpty() || !firstName.isEmpty()) {
                        if (!lastName.isEmpty() && !firstName.isEmpty()) {
                            authorsBuilder.append(lastName)
                                .append(", ")
                                .append(firstName);
                        } else {
                            // Just use whichever part is available
                            authorsBuilder.append(lastName.isEmpty() ? firstName : lastName);
                        }
                    }
                }
            }
            
            if (authorsBuilder.length() > 0) {
                return authorsBuilder.toString();
            }
        }
        
        // Fall back to attachment creators if parent doesn't have any
        if (data == null || data.creators == null || data.creators.length == 0) {
            return "Unknown";
        }
        
        StringBuilder authorsBuilder = new StringBuilder();
        for (int i = 0; i < data.creators.length; i++) {
            ZoteroCreator creator = data.creators[i];
            if (creator.creatorType != null && creator.creatorType.equals("author")) {
                if (authorsBuilder.length() > 0) {
                    authorsBuilder.append(", ");
                }
                
                // Handle potential null values
                String lastName = (creator.lastName != null) ? creator.lastName : "";
                String firstName = (creator.firstName != null) ? creator.firstName : "";
                
                if (!lastName.isEmpty() && !firstName.isEmpty()) {
                    authorsBuilder.append(lastName)
                        .append(", ")
                        .append(firstName);
                } else {
                    // Just use whichever part is available
                    authorsBuilder.append(lastName.isEmpty() ? firstName : lastName);
                }
            }
        }
        
        return authorsBuilder.length() > 0 ? authorsBuilder.toString() : "Unknown";
    }
    
    public ZoteroLinks getLinks() {
        return links;
    }
    
    public String getYear() {
    // First try to get year from parent item
    if (parentItem != null && parentItem.data != null) {
        // Try explicit year field first
        if (parentItem.data.year != null && !parentItem.data.year.isEmpty()) {
            return parentItem.data.year;
        }
        // Try to extract year from date field
        if (parentItem.data.date != null && !parentItem.data.date.isEmpty()) {
            String year = extractYearFromDate(parentItem.data.date);
            if (year != null) {
                return year;
            }
        }
    }
    
    // Fall back to attachment's own data
    if (data != null) {
        if (data.year != null && !data.year.isEmpty()) {
            return data.year;
        }
        if (data.date != null && !data.date.isEmpty()) {
            String year = extractYearFromDate(data.date);
            if (year != null) {
                return year;
            }
        }
    }
    
    return "Unknown";
    }

    private String extractYearFromDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        
        // Remove common prefixes that might interfere
        dateString = dateString.toLowerCase()
            .replace("circa", "")
            .replace("ca.", "")
            .replace("c.", "")
            .trim();
        
        // Pattern to match a 4-digit year (1900-2099)
        Pattern pattern = Pattern.compile("\\b(19|20)\\d{2}\\b");
        Matcher matcher = pattern.matcher(dateString);
        
        if (matcher.find()) {
            String year = matcher.group();
            
            // Validate the year is reasonable (not in the future by more than 1 year)
            try {
                int yearInt = Integer.parseInt(year);
                int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                
                // Allow up to 1 year in the future (for forthcoming publications)
                if (yearInt >= 1800 && yearInt <= currentYear + 1) {
                    return year;
                }
            } catch (NumberFormatException e) {
                // If parsing fails, still return the matched string
                return year;
            }
            
            return year;
        }
        
        // If no 4-digit year found, try to parse common date formats
        // This handles cases where the year might be 2-digit or in an unusual format
        String cleanDate = dateString.replaceAll("[^0-9]", " ").trim();
        String[] parts = cleanDate.split("\\s+");
        
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                // Check if it could be a 4-digit year
                if (num >= 1800 && num <= 2099) {
                    return String.valueOf(num);
                }
                // Check if it's a 2-digit year (00-99)
                if (num >= 0 && num <= 99) {
                    // Assume 00-30 means 2000-2030, 31-99 means 1931-1999
                    int fullYear = num <= 30 ? 2000 + num : 1900 + num;
                    return String.valueOf(fullYear);
                }
            } catch (NumberFormatException e) {
                // Skip this part
                continue;
            }
        }
        
        return null;
    }

    public int getYearAsInt() {
        String yearStr = getYear();
        if (yearStr == null || yearStr.isEmpty()) {
            return 9999; // Put items without year at the end
        }
        
        // Extract first 4 digits (year) from date string
        // Handles formats like "2023", "2023-01-15", "2023/01/15", etc.
        try {
            String year = yearStr.replaceAll("[^0-9]", "").substring(0, 4);
            return Integer.parseInt(year);
        } catch (Exception e) {
            return 9999; // If parsing fails, put at end
        }
    }
    
    // For debugging
    @Override
    public String toString() {
        return "ZoteroItem{key=" + key + 
               ", title=" + getTitle() + 
               ", authors=" + getAuthors() + 
               ", parentItemKey=" + getParentItemKey() + 
               ", parentItemType=" + getParentItemType() +
               ", isBook=" + isBook() +
               "}";
    }
}