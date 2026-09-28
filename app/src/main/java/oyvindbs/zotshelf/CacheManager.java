package oyvindbs.zotshelf;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import oyvindbs.zotshelf.database.AppDatabase;

/**
 * Measures and deletes the files ZotShelf keeps on the device: the downloaded
 * EPUB/PDF files, the extracted cover images, Glide's image cache and the cached
 * cover list in the database. Nothing in the user's Zotero library is touched.
 */
public final class CacheManager {

    private static final String TAG = "CacheManager";

    /** Must match the directory ZoteroApiClient downloads into. */
    static final String EBOOK_CACHE_DIR_NAME = "epubs";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Bumped every time the cache is cleared, so open screens can tell they need to reload.
    private static volatile int clearGeneration = 0;

    public interface SizeCallback {
        void onSizeCalculated(long bytes);
    }

    public interface ClearCallback {
        void onCacheCleared(long bytesFreed);
    }

    private CacheManager() {
    }

    public static int getClearGeneration() {
        return clearGeneration;
    }

    public static File getEbookCacheDir(Context context) {
        return new File(context.getFilesDir(), EBOOK_CACHE_DIR_NAME);
    }

    /** Calculates the size of the cached files in the background; the callback runs on the main thread. */
    public static void calculateCacheSize(Context context, SizeCallback callback) {
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            long size = getCacheSizeSync(appContext);
            mainHandler.post(() -> callback.onSizeCalculated(size));
        });
    }

    /**
     * Deletes all cached files and the cached cover list. Must be called from the main
     * thread; the callback also runs on the main thread.
     */
    public static void clearCache(Context context, ClearCallback callback) {
        final Context appContext = context.getApplicationContext();

        // Glide's memory cache can only be cleared on the main thread
        Glide.get(appContext).clearMemory();

        executor.execute(() -> {
            long sizeBefore = getCacheSizeSync(appContext);

            // Delete the contents but keep the directory itself: ZoteroApiClient
            // instances that are already alive expect it to exist.
            deleteContents(getEbookCacheDir(appContext));

            try {
                Glide.get(appContext).clearDiskCache();
            } catch (Exception e) {
                Log.w(TAG, "Could not clear Glide disk cache", e);
            }

            try {
                AppDatabase.getInstance(appContext).epubCoverDao().deleteAll();
            } catch (Exception e) {
                Log.w(TAG, "Could not clear cached covers from database", e);
            }

            long bytesFreed = Math.max(0, sizeBefore - getCacheSizeSync(appContext));
            clearGeneration++;
            Log.d(TAG, "Cleared cache, freed " + bytesFreed + " bytes");

            mainHandler.post(() -> callback.onCacheCleared(bytesFreed));
        });
    }

    private static long getCacheSizeSync(Context appContext) {
        long size = folderSize(getEbookCacheDir(appContext));
        File glideDir = Glide.getPhotoCacheDir(appContext);
        if (glideDir != null) {
            size += folderSize(glideDir);
        }
        return size;
    }

    private static long folderSize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        if (file.isFile()) {
            return file.length();
        }

        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += folderSize(child);
            }
        }
        return size;
    }

    private static void deleteContents(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            deleteContents(file);
        }
        if (!file.delete() && file.exists()) {
            Log.w(TAG, "Could not delete " + file.getAbsolutePath());
        }
    }
}
