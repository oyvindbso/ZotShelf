package oyvindbs.zotshelf.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**

- Enhanced entity class representing a cached EPUB book cover with better offline support
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
  
  // Additional fields for better offline support
  private String fileName; // Original filename from Zotero
  private String mimeType; // MIME type (application/epub+zip or application/pdf)
  private String downloadUrl; // Original download URL (for re-downloading if needed)
  private String parentItemType; // Type of parent item (book, article, etc.)
  private boolean isBook; // Cached result of isBook() check
  private String collectionKeys; // Pipe-separated collection keys this item belongs to
  private String year;  // Year
  
  public EpubCoverEntity(@NonNull String id, String title, String authors,
  String coverPath, String zoteroUsername) {
  this.id = id;
  this.title = title;
  this.authors = authors;
  this.coverPath = coverPath;
  this.zoteroUsername = zoteroUsername;
  this.lastUpdated = System.currentTimeMillis();
  }
  
  // Existing getters and setters
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
  

  public String getYear() {
      return year;
  }

  public void setYear(String year) {
      this.year = year;
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
  
  // New getters and setters for enhanced fields
  public String getFileName() {
  return fileName;
  }
  
  public void setFileName(String fileName) {
  this.fileName = fileName;
  }
  
  public String getMimeType() {
  return mimeType;
  }
  
  public void setMimeType(String mimeType) {
  this.mimeType = mimeType;
  }
  
  public String getDownloadUrl() {
  return downloadUrl;
  }
  
  public void setDownloadUrl(String downloadUrl) {
  this.downloadUrl = downloadUrl;
  }
  
  public String getParentItemType() {
  return parentItemType;
  }
  
  public void setParentItemType(String parentItemType) {
  this.parentItemType = parentItemType;
  }
  
  public boolean isBook() {
  return isBook;
  }
  
  public void setBook(boolean book) {
  isBook = book;
  }
  
  public String getCollectionKeys() {
  return collectionKeys;
  }
  
  public void setCollectionKeys(String collectionKeys) {
  this.collectionKeys = collectionKeys;
  }
  
  // Helper methods for collections
  public java.util.List<String> getCollectionKeysAsList() {
  if (collectionKeys == null || collectionKeys.isEmpty()) {
  return new java.util.ArrayList<>();
  }
  return java.util.Arrays.asList(collectionKeys.split("\\|"));
  }
  
  public void setCollectionKeysFromList(java.util.List<String> keys) {
  if (keys == null || keys.isEmpty()) {
  this.collectionKeys = "";
  } else {
  this.collectionKeys = String.join("|", keys);
  }
  }
  }