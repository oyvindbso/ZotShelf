package com.example.zotshelf;

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
    
    // For debugging
    @Override
    public String toString() {
        return "ZoteroItem{key=" + key + 
               ", title=" + getTitle() + 
               ", authors=" + getAuthors() + 
               ", parentItemKey=" + getParentItemKey() + 
               "}";
    }
}