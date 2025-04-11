package com.example.zoteroepubcovers;

import com.google.gson.annotations.SerializedName;

public class ZoteroCollection {
    @SerializedName("key")
    private String key;
    
    @SerializedName("data")
    private ZoteroCollectionData data;
    
    public static class ZoteroCollectionData {
        @SerializedName("name")
        private String name;
        
        @SerializedName("parentCollection")
        private String parentCollection;
    }
    
    public String getKey() {
        return key;
    }
    
    public String getName() {
        return data != null ? data.name : "";
    }
    
    public String getParentCollection() {
        return data != null ? data.parentCollection : null;
    }
    
    @Override
    public String toString() {
        return getName();
    }
}
// Verify these fields in ZoteroCollection.java
public class ZoteroCollection {
    @SerializedName("key")
    private String key;
    
    @SerializedName("data")
    private ZoteroCollectionData data;
    
    public static class ZoteroCollectionData {
        @SerializedName("name")
        private String name;
        
        @SerializedName("parentCollection")
        private String parentCollection;
    }
    
    // ...getters and setters
}