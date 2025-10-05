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

- Enhanced repository class to manage EPUB cover data with offline support
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
  - Save multiple covers from ZoteroItems
    */
    public void saveCoversFromZoteroItems(List<ZoteroItem> items, List<String> coverPaths) {
    executor.execute(() -> {
    try {
    List<EpubCoverEntity> entities = new ArrayList<>();
    
         for (int i = 0; i < items.size() && i < coverPaths.size(); i++) {
             ZoteroItem item = items.get(i);
             String coverPath = coverPaths.get(i);
             EpubCoverEntity entity = createEntityFromZoteroItem(item, coverPath);
             entities.add(entity);
         }
         
         database.epubCoverDao().insertAll(entities);
         Log.d(TAG, "Saved " + entities.size() + " covers to database");
     } catch (Exception e) {
         Log.e(TAG, "Error saving covers", e);
     }
    
    });
    }
  
  
  /**
 * Save a ZoteroItem with its cover to the database (synchronous version for critical saves)
 */
public void saveCoverFromZoteroItemSync(ZoteroItem item, String coverPath) {
    try {
        EpubCoverEntity entity = createEntityFromZoteroItem(item, coverPath);
        database.epubCoverDao().insert(entity);
        Log.d(TAG, "Saved cover for item: " + item.getTitle());
    } catch (Exception e) {
        Log.e(TAG, "Error saving cover for item: " + item.getTitle(), e);
    }
}

  /**
  - Create EpubCoverEntity from ZoteroItem with full metadata
    */
    
    /**
 * Save with collection keys properly set
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
    
    // CRITICAL: Set collection keys - this was missing!
    String currentCollection = userPreferences.getSelectedCollectionKey();
    if (currentCollection != null && !currentCollection.isEmpty()) {
        entity.setCollectionKeys(currentCollection);
    } else {
        entity.setCollectionKeys(""); // Empty means all collections
    }
    
    // Set download URL if available
    if (item.getLinks() != null && item.getLinks().getEnclosure() != null) {
        entity.setDownloadUrl(item.getLinks().getEnclosure().getHref());
    }
    
    return entity;
}

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
    List<EpubCoverEntity> entities = getFilteredEntities();
    List<EpubCoverItem> coverItems = convertEntitiesToCoverItems(entities);
    
         mainHandler.post(() -> callback.onCoversLoaded(coverItems));
     } catch (Exception e) {
         Log.e(TAG, "Error loading filtered covers", e);
         mainHandler.post(() -> callback.onError("Error loading covers: " + e.getMessage()));
     }
    
    });
    }
  
  /**
  - Get entities filtered by current user preferences
    */
    private List<EpubCoverEntity> getFilteredEntities() {
    // Get user preferences
    boolean booksOnly = userPreferences.getBooksOnly();
    boolean showEpubs = userPreferences.getShowEpubs();
    boolean showPdfs = userPreferences.getShowPdfs();
    String collectionKey = userPreferences.getSelectedCollectionKey();
    
    List<EpubCoverEntity> entities;
    
    // Apply collection filter first
    if (collectionKey != null && !collectionKey.isEmpty()) {
    entities = database.epubCoverDao().getCoversByCollection(collectionKey, booksOnly, showEpubs, showPdfs);
    } else {
    entities = database.epubCoverDao().getCoversByPreferences(booksOnly, showEpubs, showPdfs);
    }
    
    Log.d(TAG, "Loaded " + entities.size() + " covers from database with filters applied");
    return entities;
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