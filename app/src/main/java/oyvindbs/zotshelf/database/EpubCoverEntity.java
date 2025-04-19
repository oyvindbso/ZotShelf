package oyvindbs.zotshelf.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class representing a cached EPUB book cover
 */
@Entity(tableName = "epub_covers")
public class EpubCoverEntity {
    
    @PrimaryKey
    @NonNull
    private String id; // Zotero item key
    
    private String title;
    private String authors;
    private String coverPath; // Local file path to the cover image
    private String zoteroUsername;
    private long lastUpdated; // Timestamp when this entry was last updated
    
    public EpubCoverEntity(@NonNull String id, String title, String authors, 
                          String coverPath, String zoteroUsername) {
        this.id = id;
        this.title = title;
        this.authors = authors;
        this.coverPath = coverPath;
        this.zoteroUsername = zoteroUsername;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    @NonNull
    public String getId() {
        return id;
    }
    
    public void setId(@NonNull String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getAuthors() {
        return authors;
    }
    
    public void setAuthors(String authors) {
        this.authors = authors;
    }
    
    public String getCoverPath() {
        return coverPath;
    }
    
    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }
    
    public String getZoteroUsername() {
        return zoteroUsername;
    }
    
    public void setZoteroUsername(String zoteroUsername) {
        this.zoteroUsername = zoteroUsername;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
