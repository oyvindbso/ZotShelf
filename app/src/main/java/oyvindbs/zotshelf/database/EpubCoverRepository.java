package oyvindbs.zotshelf.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import oyvindbs.zotshelf.EpubCoverItem;
import oyvindbs.zotshelf.UserPreferences;
import oyvindbs.zotshelf.ZoteroItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**

- Simplified repository class to manage EPUB cover data with offline support
  */
  public class EpubCoverRepository {
  
  private static final String TAG = "EpubCoverRepository";
  private final AppDatabase database;
  private final Executor executor;
  private final Handler mainHandler;
  private final UserPreferences userPreferences;
  
  public interface CoverRepositoryCallback {
  void onCoversLoaded(List<EpubCoverItem> covers);
  void onError(String message);
  }
  
  public EpubCoverRepository(Context context) {
  database = AppDatabase.getInstance(context);
  executor = Executors.newSingleThreadExecutor();
  mainHandler = new Handler(Looper.getMainLooper());
  userPreferences = new UserPreferences(context);
  }
  
  /**
  - Save a ZoteroItem with its cover to the database
    */
    public void saveCoverFromZoteroItem(ZoteroItem item, String coverPath) {
    executor.execute(() -> {
    try {
    EpubCoverEntity entity = createEntityFromZoteroItem(item, coverPath);
    database.epubCoverDao().insert(entity);
    Log.d(TAG, "Saved cover for item: " + item.getTitle());
    } catch (Exception e) {
    Log.e(TAG, "Error saving cover for item: " + item.getTitle(), e);
    }
    });
    }
  
  /**
  - Create EpubCoverEntity from ZoteroItem with full metadata
    */
    private EpubCoverEntity createEntityFromZoteroItem(ZoteroItem item, String coverPath) {
    EpubCoverEntity entity = new EpubCoverEntity(
    item.getKey(),
    item.getTitle(),
    item.getAuthors(),
    coverPath,
    userPreferences.getZoteroUsername()
    );
    
    // Set additional metadata for offline support
    entity.setFileName(item.getFilename());
    entity.setMimeType(item.getMimeType());
    entity.setParentItemType(item.getParentItemType());
    entity.setBook(item.isBook());
    
    // Set download URL if available
    if (item.getLinks() != null && item.getLinks().getEnclosure() != null) {
    entity.setDownloadUrl(item.getLinks().getEnclosure().getHref());
    }
    
    return entity;
    }
  
  /**
  - Load covers from database with current user preferences applied
    */
    public void getFilteredCovers(CoverRepositoryCallback callback) {
    executor.execute(() -> {
    try {
    // Get all covers for the current user
    String username = userPreferences.getZoteroUsername();
    List<EpubCoverEntity> entities = database.epubCoverDao().getCoversByPreferences(username);
    
         // Apply filtering in memory
         List<EpubCoverEntity> filteredEntities = applyInMemoryFiltering(entities);
         List<EpubCoverItem> coverItems = convertEntitiesToCoverItems(filteredEntities);
         
         mainHandler.post(() -> callback.onCoversLoaded(coverItems));
     } catch (Exception e) {
         Log.e(TAG, "Error loading filtered covers", e);
         mainHandler.post(() -> callback.onError("Error loading covers: " + e.getMessage()));
     }
    
    });
    }
  
  /**
  - Apply user preferences filtering in memory
    */
    private List<EpubCoverEntity> applyInMemoryFiltering(List<EpubCoverEntity> entities) {
    // Get user preferences
    boolean booksOnly = userPreferences.getBooksOnly();
    boolean showEpubs = userPreferences.getShowEpubs();
    boolean showPdfs = userPreferences.getShowPdfs();
    String collectionKey = userPreferences.getSelectedCollectionKey();
    
    List<EpubCoverEntity> filtered = new ArrayList<>();
    
    for (EpubCoverEntity entity : entities) {
    boolean includeItem = true;
    
     // Apply file type filter
     String mimeType = entity.getMimeType();
     if (mimeType != null) {
         boolean isEpub = "application/epub+zip".equals(mimeType);
         boolean isPdf = "application/pdf".equals(mimeType);
         
         if (isEpub && !showEpubs) {
             includeItem = false;
         } else if (isPdf && !showPdfs) {
             includeItem = false;
         } else if (!isEpub && !isPdf) {
             // Unknown type, include by default if either type is enabled
             includeItem = showEpubs || showPdfs;
         }
     }
     
     // Apply books-only filter
     if (includeItem && booksOnly) {
         // If entity has isBook data, use it; otherwise include by default
         includeItem = entity.isBook();
     }
     
     // Apply collection filter (simplified - just check if collection key is present)
     if (includeItem && collectionKey != null && !collectionKey.isEmpty()) {
         List<String> entityCollections = entity.getCollectionKeysAsList();
         if (!entityCollections.isEmpty() && !entityCollections.contains(collectionKey)) {
             includeItem = false;
         }
     }
     
     if (includeItem) {
         filtered.add(entity);
     }
    
    }
    
    Log.d(TAG, "Filtered " + entities.size() + " → " + filtered.size() + " covers based on preferences");
    return filtered;
    }
  
  /**
  - Convert entities to cover items, validating file existence
    */
    private List<EpubCoverItem> convertEntitiesToCoverItems(List<EpubCoverEntity> entities) {
    List<EpubCoverItem> coverItems = new ArrayList<>();
    
    for (EpubCoverEntity entity : entities) {
    // Validate that cover file still exists
    String coverPath = entity.getCoverPath();
    if (coverPath != null) {
    File coverFile = new File(coverPath);
    if (!coverFile.exists()) {
    Log.w(TAG, "Cover file missing for item: " + entity.getTitle() + " at path: " + coverPath);
    coverPath = null; // Will show placeholder
    }
    }
    
     EpubCoverItem item = new EpubCoverItem(
             entity.getId(),
             entity.getTitle(),
             coverPath,
             entity.getAuthors(),
             entity.getZoteroUsername()
     );
     coverItems.add(item);
    
    }
    
    return coverItems;
    }
  
  /**
  - Legacy method for backward compatibility
    */
    public void saveCovers(List<EpubCoverItem> coverItems) {
    executor.execute(() -> {
    List<EpubCoverEntity> entities = new ArrayList<>();
    
    
     for (EpubCoverItem item : coverItems) {
         EpubCoverEntity entity = new EpubCoverEntity(
                 item.getId(),
                 item.getTitle(),
                 item.getAuthors(),
                 item.getCoverPath(),
                 item.getZoteroUsername()
         );
         entities.add(entity);
     }
     
     database.epubCoverDao().insertAll(entities);
    
    });
    }
  
  /**
  - Load covers from the local database (legacy method)
    */
    public void getLocalCovers(CoverRepositoryCallback callback) {
    getFilteredCovers(callback);
    }
  
  /**
  - Check if there are any covers in the local database
    */
    public void hasCachedCovers(BooleanCallback callback) {
    executor.execute(() -> {
    try {
    int count = database.epubCoverDao().getCount();
    mainHandler.post(() -> callback.onResult(count > 0));
    } catch (Exception e) {
    mainHandler.post(() -> callback.onResult(false));
    }
    });
    }
  
  /**
  - Clear all covers from the database
    */
    public void clearCovers() {
    executor.execute(() -> database.epubCoverDao().deleteAll());
    }
  
  /**
  - Clean up old entries and orphaned files
    */
    public void cleanupOldData(int maxAgeInDays) {
    executor.execute(() -> {
    try {
    long cutoffTime = System.currentTimeMillis() - (maxAgeInDays * 24 * 60 * 60 * 1000L);
    
         // Get entities that will be deleted to clean up their files
         List<EpubCoverEntity> oldEntities = database.epubCoverDao().getCoversModifiedSince(0);
         
         // Delete old database entries
         int deletedCount = database.epubCoverDao().deleteOldEntries(cutoffTime);
         
         // Clean up orphaned cover files
         for (EpubCoverEntity entity : oldEntities) {
             if (entity.getLastUpdated() < cutoffTime && entity.getCoverPath() != null) {
                 File coverFile = new File(entity.getCoverPath());
                 if (coverFile.exists()) {
                     coverFile.delete();
                 }
             }
         }
         
         Log.d(TAG, "Cleaned up " + deletedCount + " old database entries");
     } catch (Exception e) {
         Log.e(TAG, "Error during cleanup", e);
     }
    
    
    });
    }
  
  public interface BooleanCallback {
  void onResult(boolean result);
  }
  }