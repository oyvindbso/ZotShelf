package com.example.zotshelf.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.zoteroepubcovers.EpubCoverItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository class to manage EPUB cover data from local database
 */
public class EpubCoverRepository {
    
    private final AppDatabase database;
    private final Executor executor;
    private final Handler mainHandler;
    
    public interface CoverRepositoryCallback {
        void onCoversLoaded(List<EpubCoverItem> covers);
        void onError(String message);
    }
    
    public EpubCoverRepository(Context context) {
        database = AppDatabase.getInstance(context);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Save a list of cover items to the database
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
     * Load covers from the local database
     */
    public void getLocalCovers(CoverRepositoryCallback callback) {
        executor.execute(() -> {
            try {
                List<EpubCoverEntity> entities = database.epubCoverDao().getAllCovers();
                List<EpubCoverItem> coverItems = new ArrayList<>();
                
                for (EpubCoverEntity entity : entities) {
                    EpubCoverItem item = new EpubCoverItem(
                            entity.getId(),
                            entity.getTitle(),
                            entity.getCoverPath(),
                            entity.getAuthors(),
                            entity.getZoteroUsername()
                    );
                    coverItems.add(item);
                }
                
                mainHandler.post(() -> callback.onCoversLoaded(coverItems));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error loading covers: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Check if there are any covers in the local database
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
     * Clear all covers from the database
     */
    public void clearCovers() {
        executor.execute(() -> database.epubCoverDao().deleteAll());
    }
    
    public interface BooleanCallback {
        void onResult(boolean result);
    }
}
