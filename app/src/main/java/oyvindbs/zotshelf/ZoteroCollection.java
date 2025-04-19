package oyvindbs.zotshelf;

import android.util.Log;
import com.google.gson.annotations.SerializedName;

public class ZoteroCollection {
    private static final String TAG = "ZoteroCollection";
    
    @SerializedName("key")
    private String key;
    
    @SerializedName("data")
    private ZoteroCollectionData data;
    
    public static class ZoteroCollectionData {
        @SerializedName("name")
        private String name;
        
        @SerializedName("parentCollection")
        private String parentCollection;
        
        // For debugging - print all fields
        @Override
        public String toString() {
            return "ZoteroCollectionData{" +
                    "name='" + name + '\'' +
                    ", parentCollection='" + parentCollection + '\'' +
                    '}';
        }
    }
    
    public String getKey() {
        return key;
    }
    
    public String getName() {
        if (data == null) {
            Log.w(TAG, "Collection data is null for key: " + key);
            return key != null ? key : "Unknown Collection";
        }
        
        if (data.name == null || data.name.isEmpty()) {
            Log.w(TAG, "Collection name is empty for key: " + key);
            return "Unnamed Collection";
        }
        
        return data.name;
    }
    
    public String getParentCollection() {
        if (data == null) {
            return null;
        }
        return data.parentCollection;
    }
    
    @Override
    public String toString() {
        return "ZoteroCollection{" +
               "key='" + key + "', " +
               "data=" + (data != null ? data.toString() : "null") +
               '}';
    }
}