package oyvindbs.zotshelf;


import com.google.gson.annotations.SerializedName;

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
            return parentItem.data.date;
        }
        // Fall back to attachment date if no parent
        return data != null ? data.date : null;
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