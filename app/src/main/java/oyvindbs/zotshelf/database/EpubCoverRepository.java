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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EpubCoverRepository {

    private static final String TAG = "EpubCoverRepository";
    // One thread for all cover-cache database work, shared by every tab, so that saves and
    // membership updates from different tabs never overwrite each other's collection labels.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final AppDatabase database;
    private final Handler mainHandler;
    private final UserPreferences userPreferences;

    public interface CoverRepositoryCallback {
        void onCoversLoaded(List<EpubCoverItem> covers);
        void onError(String message);
    }

    public interface BooleanCallback {
        void onResult(boolean result);
    }

    public EpubCoverRepository(Context context) {
        database = AppDatabase.getInstance(context);
        mainHandler = new Handler(Looper.getMainLooper());
        userPreferences = new UserPreferences(context);
    }

    /**
     * Saves an item to the cover cache.
     *
     * @param collectionKey the collection the item was loaded for, or null/empty if it wasn't
     *                      loaded through a collection. It is added to the collections the item
     *                      is already known to be in (an item can be in several collections).
     */
    public void saveCoverFromZoteroItem(ZoteroItem item, String coverPath, String collectionKey) {
        if (item.getKey() == null) {
            return;
        }

        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    EpubCoverDao dao = database.epubCoverDao();
                    EpubCoverEntity existing = dao.getById(item.getKey());

                    EpubCoverEntity entity = createEntityFromZoteroItem(item, coverPath);
                    entity.setCollectionKeys(addCollectionKey(
                            existing != null ? existing.getCollectionKeys() : null, collectionKey));
                    dao.insert(entity);
                });
                Log.d(TAG, "Saved cover for item: " + item.getTitle());
            } catch (Exception e) {
                Log.e(TAG, "Error saving cover for item: " + item.getTitle(), e);
            }
        });
    }

    /**
     * Call after fetching the complete contents of a collection. Cached items that are
     * still labelled with the collection but were not in the fetched list (removed from the
     * collection in Zotero) lose the label, so the tab stops showing them from the cache.
     */
    public void syncCollectionMembership(String collectionKey, Set<String> itemIdsInCollection) {
        if (collectionKey == null || collectionKey.isEmpty()) {
            return;
        }
        final Set<String> ids = new HashSet<>(itemIdsInCollection);

        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    EpubCoverDao dao = database.epubCoverDao();
                    for (EpubCoverEntity entity : dao.getCoversLabelledWithCollection(collectionKey)) {
                        if (!ids.contains(entity.getId())) {
                            entity.setCollectionKeys(
                                    removeCollectionKey(entity.getCollectionKeys(), collectionKey));
                            dao.insert(entity);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating cached items for collection " + collectionKey, e);
            }
        });
    }

    // Collection keys are stored as ",KEY1,KEY2," so a LIKE '%,KEY,%' query only matches
    // exact keys. Values in the old format (a single key without commas) are dropped: they
    // held whichever collection had been picked last, not the item's real collection.
    static String addCollectionKey(String storedKeys, String collectionKey) {
        String keys = (storedKeys != null && storedKeys.startsWith(",")) ? storedKeys : ",";
        if (collectionKey == null || collectionKey.isEmpty()
                || keys.contains("," + collectionKey + ",")) {
            return keys;
        }
        return keys + collectionKey + ",";
    }

    static String removeCollectionKey(String storedKeys, String collectionKey) {
        if (storedKeys == null || !storedKeys.startsWith(",")) {
            return ",";
        }
        return storedKeys.replace("," + collectionKey + ",", ",");
    }

    private EpubCoverEntity createEntityFromZoteroItem(ZoteroItem item, String coverPath) {
        EpubCoverEntity entity = new EpubCoverEntity(
                item.getKey(),
                item.getTitle(),
                item.getAuthors(),
                coverPath,
                userPreferences.getZoteroUsername()
        );
        
        entity.setFileName(item.getFilename());
        entity.setMimeType(item.getMimeType());
        entity.setParentItemType(item.getParentItemType());
        entity.setBook(item.isBook());
        entity.setYear(item.getYear());

        if (item.getLinks() != null && item.getLinks().getEnclosure() != null) {
            entity.setDownloadUrl(item.getLinks().getEnclosure().getHref());
        }
        
        return entity;
    }

    public void getFilteredCoversForCollection(String collectionKey, CoverRepositoryCallback callback) {
        executor.execute(() -> {
            try {
                List<EpubCoverEntity> entities = getFilteredEntitiesForCollection(collectionKey);
                List<EpubCoverItem> coverItems = convertEntitiesToCoverItems(entities);
                mainHandler.post(() -> callback.onCoversLoaded(coverItems));
            } catch (Exception e) {
                Log.e(TAG, "Error loading filtered covers for collection", e);
                mainHandler.post(() -> callback.onError("Error loading covers: " + e.getMessage()));
            }
        });
    }

    private List<EpubCoverEntity> getFilteredEntitiesForCollection(String collectionKey) {
        boolean booksOnly = userPreferences.getBooksOnly();
        boolean showEpubs = userPreferences.getShowEpubs();
        boolean showPdfs = userPreferences.getShowPdfs();

        List<EpubCoverEntity> entities;
        if (collectionKey != null && !collectionKey.isEmpty()) {
            entities = database.epubCoverDao().getCoversByCollection(collectionKey, booksOnly, showEpubs, showPdfs);
        } else {
            entities = database.epubCoverDao().getCoversByPreferences(booksOnly, showEpubs, showPdfs);
        }

        Log.d(TAG, "Loaded " + entities.size() + " covers for collection " + collectionKey);
        return entities;
    }

    private List<EpubCoverItem> convertEntitiesToCoverItems(List<EpubCoverEntity> entities) {
        List<EpubCoverItem> coverItems = new ArrayList<>();
        for (EpubCoverEntity entity : entities) {
            String coverPath = entity.getCoverPath();
            if (coverPath != null) {
                File coverFile = new File(coverPath);
                if (!coverFile.exists()) {
                    Log.w(TAG, "Cover file missing for item: " + entity.getTitle() + " at path: " + coverPath);
                    coverPath = null;
                }
            }
            EpubCoverItem item = new EpubCoverItem(
                    entity.getId(),
                    entity.getTitle(),
                    coverPath,
                    entity.getAuthors(),
                    entity.getZoteroUsername(),
                    entity.getYear()
            );
            coverItems.add(item);
        }
        return coverItems;
    }

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

}
