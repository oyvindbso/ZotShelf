package oyvindbs.zotshelf;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public class ZoteroApiClient {

    private static final String BASE_URL = "https://api.zotero.org/";
    private static final String TAG = "ZoteroApiClient";
    private static final int PAGE_SIZE = 100;

    private final Context context;
    private final ZoteroService zoteroService;
    private final Executor executor;
    private final File cacheDir;

    public ZoteroApiClient(Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();

        this.cacheDir = CacheManager.getEbookCacheDir(context);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        OkHttpClient client = new OkHttpClient.Builder().build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        zoteroService = retrofit.create(ZoteroService.class);
    }

    public interface ZoteroService {
        @GET("users/{userId}/items")
        Call<List<ZoteroItem>> getItems(
                @Path("userId") String userId,
                @Header("Zotero-API-Key") String apiKey,
                @Query("format") String format,
                @Query("itemType") String itemType,
                @Query("limit") int limit
        );

        @GET("users/{userId}/collections")
        Call<List<ZoteroCollection>> getCollections(
                @Path("userId") String userId,
                @Header("Zotero-API-Key") String apiKey
        );

        @GET("users/{userId}/collections")
        Call<List<ZoteroCollection>> getCollectionsPaginated(
                @Path("userId") String userId,
                @Header("Zotero-API-Key") String apiKey,
                @Query("start") int start,
                @Query("limit") int limit
        );

        @GET("users/{userId}/items")
        Call<List<ZoteroItem>> getItemsPaginated(
                @Path("userId") String userId,
                @Header("Zotero-API-Key") String apiKey,
                @Query("format") String format,
                @Query("itemType") String itemType,
                @Query("start") int start,
                @Query("limit") int limit
        );

        // Using dynamic URL for tag filtering to ensure proper query parameter formatting
        @GET
        Call<List<ZoteroItem>> getItemsWithDynamicUrl(
                @Url String url,
                @Header("Zotero-API-Key") String apiKey
        );

        @GET("users/{userId}/collections/{collectionKey}/items")
        Call<List<ZoteroItem>> getItemsByCollectionPaginated(
                @Path("userId") String userId,
                @Path("collectionKey") String collectionKey,
                @Header("Zotero-API-Key") String apiKey,
                @Query("format") String format,
                @Query("itemType") String itemType,
                @Query("start") int start,
                @Query("limit") int limit
        );

        @GET
        @Streaming
        Call<ResponseBody> downloadFile(@Url String fileUrl, @Header("Zotero-API-Key") String apiKey);

        @GET("users/{userId}/items/{itemKey}")
        Call<ZoteroItem> getItemByKey(
                @Path("userId") String userId,
                @Path("itemKey") String itemKey,
                @Header("Zotero-API-Key") String apiKey
        );
    }

    public interface ZoteroCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    /**
     * Result of {@link #fetchCover}. Callbacks may run on any thread, including the
     * calling thread when the cover is already on disk.
     */
    public interface CoverFetchCallback {
        /** @param coverPath the cover image, or null if the file has no usable cover */
        void onCoverReady(ZoteroItem item, String coverPath);

        void onDownloadFailed(ZoteroItem item, String errorMessage);
    }

    // Books are downloaded a few at a time, so a large collection doesn't start hundreds
    // of downloads at once (and briefly fill the device with books).
    private static final int MAX_CONCURRENT_DOWNLOADS = 4;
    private static final ExecutorService downloadExecutor =
            Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS);

    // Covers being fetched right now, by item key, with everyone waiting for them.
    // Shared by all clients so the same book is never downloaded twice at the same time.
    // Guarded by itself.
    private static final Map<String, List<PendingCover>> coversInFlight = new HashMap<>();

    private static final class PendingCover {
        final ZoteroItem item;
        final CoverFetchCallback callback;

        PendingCover(ZoteroItem item, CoverFetchCallback callback) {
            this.item = item;
            this.callback = callback;
        }
    }

    public void getCollections(String userId, String apiKey, ZoteroCallback<List<ZoteroCollection>> callback) {
        executor.execute(() -> {
            if (!validateCredentials(userId, apiKey, callback)) return;
            executeCollectionsCall(zoteroService.getCollections(userId, apiKey), callback);
        });
    }

    public void getCollectionsPaginated(String userId, String apiKey, int start, int limit, ZoteroCallback<List<ZoteroCollection>> callback) {
        executor.execute(() -> {
            if (!validateCredentials(userId, apiKey, callback)) return;
            executeCollectionsCall(zoteroService.getCollectionsPaginated(userId, apiKey, start, limit), callback);
        });
    }

    private boolean validateCredentials(String userId, String apiKey, ZoteroCallback<?> callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onError("User ID is empty");
            return false;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API Key is empty");
            return false;
        }

        return true;
    }

    private void executeCollectionsCall(Call<List<ZoteroCollection>> call, ZoteroCallback<List<ZoteroCollection>> callback) {
        try {
            Response<List<ZoteroCollection>> response = call.execute();

            if (response.isSuccessful()) {
                List<ZoteroCollection> collections = response.body();
                if (collections != null) {
                    Log.d(TAG, "Received " + collections.size() + " collections");
                    callback.onSuccess(collections);
                } else {
                    callback.onError("Received empty response from Zotero");
                }
            } else {
                Log.e(TAG, "Failed to fetch collections: " + response.code());
                String errorBody = "";
                if (response.errorBody() != null) {
                    try {
                        errorBody = response.errorBody().string();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not read error body", e);
                    }
                }

                if (response.code() == 401) {
                    callback.onError("Authentication failed. Check your API key and user ID. " + errorBody);
                } else if (response.code() == 403) {
                    callback.onError("Access forbidden. Check your API permissions. " + errorBody);
                } else if (response.code() == 404) {
                    callback.onError("User not found. Check your user ID. " + errorBody);
                } else {
                    callback.onError("Failed to fetch collections: HTTP " + response.code() + " " + errorBody);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    }

    public void getEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
        executor.execute(() -> {
            Call<List<ZoteroItem>> call = zoteroService.getItems(userId, apiKey, "json", "attachment", PAGE_SIZE);

            try {
                Response<List<ZoteroItem>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(filterItemsByUserPreferences(response.body()));
                } else {
                    callback.onError("Failed to fetch items: " + response.code());
                }
            } catch (IOException e) {
                Log.e(TAG, "API error", e);
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    private List<ZoteroItem> filterItemsByUserPreferences(List<ZoteroItem> allItems) {
        UserPreferences prefs = new UserPreferences(context);
        boolean showEpubs = prefs.getShowEpubs();
        boolean showPdfs = prefs.getShowPdfs();
        boolean booksOnly = prefs.getBooksOnly();

        List<ZoteroItem> filteredItems = new ArrayList<>();

        for (ZoteroItem item : allItems) {
            String mimeType = item.getMimeType();
            if (mimeType == null) {
                continue;
            }

            boolean isValidFileType = (mimeType.equals("application/epub+zip") && showEpubs)
                    || (mimeType.equals("application/pdf") && showPdfs);

            if (isValidFileType && (!booksOnly || item.isBook())) {
                filteredItems.add(item);
            }
        }

        return filteredItems;
    }

    /**
     * Gets the cover image for an EPUB/PDF attachment. The book is only downloaded if the
     * cover hasn't been extracted before, and it is deleted again as soon as the cover
     * has been taken out of it, so only the small cover images stay on the device.
     */
    public void fetchCover(ZoteroItem item, CoverFetchCallback callback) {
        final String key = item.getKey();
        final String fileExtension = getFileExtension(item.getMimeType());
        if (key == null || fileExtension == null) {
            callback.onDownloadFailed(item, "Unsupported file type: " + item.getMimeType());
            return;
        }

        File coverFile = CacheManager.getCoverFile(context, key);
        File noCoverMarker = CacheManager.getNoCoverMarker(context, key);

        synchronized (coversInFlight) {
            List<PendingCover> waiting = coversInFlight.get(key);
            if (waiting != null) {
                // Already being fetched (e.g. for another tab): share that result
                waiting.add(new PendingCover(item, callback));
                return;
            }

            if (!coverFile.exists() && !noCoverMarker.exists()) {
                waiting = new ArrayList<>();
                waiting.add(new PendingCover(item, callback));
                coversInFlight.put(key, waiting);
                downloadExecutor.execute(() -> downloadAndExtractCover(item, fileExtension));
                return;
            }
        }

        // Done before: no need to download the book again
        callback.onCoverReady(item, coverFile.exists() ? coverFile.getAbsolutePath() : null);
    }

    /** True while the book with this key is being downloaded and its cover extracted. */
    public static boolean isCoverInFlight(String key) {
        synchronized (coversInFlight) {
            return coversInFlight.containsKey(key);
        }
    }

    private static String getFileExtension(String mimeType) {
        if ("application/epub+zip".equals(mimeType)) {
            return ".epub";
        } else if ("application/pdf".equals(mimeType)) {
            return ".pdf";
        }
        return null;
    }

    /** Runs on the download executor. */
    private void downloadAndExtractCover(ZoteroItem item, String fileExtension) {
        final String key = item.getKey();
        final File ebookFile = new File(cacheDir, key + fileExtension);
        String coverPath = null;
        String downloadError = null;

        try {
            downloadError = downloadEbookSync(item, ebookFile);

            if (downloadError == null) {
                final String[] extractedCover = {null};
                final boolean[] hasNoCover = {false};

                CoverExtractor.extractCoverSync(ebookFile.getAbsolutePath(),
                        new CoverExtractor.CoverCallback() {
                    @Override
                    public void onCoverExtracted(String path) {
                        extractedCover[0] = path;
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.w(TAG, "No cover for " + key + ": " + errorMessage);
                        // Only remember that there is no cover if the book was really there.
                        // If it vanished (e.g. the cache was cleared meanwhile), retry next time.
                        hasNoCover[0] = ebookFile.exists();
                    }
                });

                coverPath = extractedCover[0];
                if (hasNoCover[0]) {
                    markNoCover(key);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error fetching cover for " + key, t);
        } finally {
            // The book is only needed to get the cover out of it; don't keep it
            if (ebookFile.exists() && !ebookFile.delete()) {
                Log.w(TAG, "Could not delete " + ebookFile.getAbsolutePath());
            }
            finishCoverFetch(key, coverPath, downloadError);
        }
    }

    /** Remembers that a book has no usable cover, so it isn't downloaded again on every refresh. */
    private void markNoCover(String key) {
        File marker = CacheManager.getNoCoverMarker(context, key);
        try {
            File dir = marker.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            marker.createNewFile();
        } catch (IOException e) {
            Log.w(TAG, "Could not write no-cover marker for " + key, e);
        }
    }

    private static void finishCoverFetch(String key, String coverPath, String downloadError) {
        List<PendingCover> waiting;
        synchronized (coversInFlight) {
            waiting = coversInFlight.remove(key);
        }
        if (waiting == null) {
            return;
        }

        for (PendingCover pending : waiting) {
            try {
                if (downloadError != null) {
                    pending.callback.onDownloadFailed(pending.item, downloadError);
                } else {
                    pending.callback.onCoverReady(pending.item, coverPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in cover callback for " + key, e);
            }
        }
    }

    /** Downloads the book to ebookFile. Returns null on success, otherwise an error message. */
    private String downloadEbookSync(ZoteroItem item, File ebookFile) {
        // The cache may have been emptied from Settings while this client was alive
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        // Only one download per book runs at a time, so an existing file is left over
        // from an interrupted run and may be incomplete: start over
        if (ebookFile.exists()) {
            ebookFile.delete();
        }

        if (item.getLinks() == null || item.getLinks().getEnclosure() == null) {
            return "No download link available";
        }

        String downloadUrl = item.getLinks().getEnclosure().getHref();
        String apiKey = new UserPreferences(context).getZoteroApiKey();

        Call<ResponseBody> call = zoteroService.downloadFile(downloadUrl, apiKey);

        try {
            Response<ResponseBody> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                return writeResponseBodyToDisk(response.body(), ebookFile) ? null : "Failed to save file";
            }
            return "Failed to download file: " + response.code();
        } catch (IOException e) {
            Log.e(TAG, "Download error", e);
            return "Network error: " + e.getMessage();
        }
    }

    private boolean writeResponseBodyToDisk(ResponseBody body, File outputFile) {
        try (InputStream inputStream = body.byteStream();
             OutputStream outputStream = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            outputStream.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "File write error", e);
            // Don't leave a partial file behind; it would be treated as a valid cache hit
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;
        }
    }

    private void getParentItem(String userId, String apiKey, String parentItemKey, ZoteroCallback<ZoteroItem> callback) {
        if (parentItemKey == null || parentItemKey.isEmpty()) {
            callback.onError("Parent item key is null or empty");
            return;
        }

        executor.execute(() -> {
            Call<ZoteroItem> call = zoteroService.getItemByKey(userId, parentItemKey, apiKey);

            try {
                Response<ZoteroItem> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to fetch parent item: " + response.code());
                }
            } catch (IOException e) {
                Log.e(TAG, "API error", e);
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    private void getAllEbookItems(String userId, String apiKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
        executor.execute(() -> {
            if (tags != null && !tags.trim().isEmpty()) {
                getAllEbookItemsWithTagFilter(userId, apiKey, null, tags, callback);
            } else {
                getAllEbookItemsPaginated(userId, apiKey, null, new ArrayList<>(), 0, callback);
            }
        });
    }

    private void getAllEbookItemsByCollection(String userId, String apiKey, String collectionKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
        executor.execute(() -> {
            if (tags != null && !tags.trim().isEmpty()) {
                getAllEbookItemsWithTagFilter(userId, apiKey, collectionKey, tags, callback);
            } else if (collectionKey == null || collectionKey.isEmpty()) {
                getAllEbookItems(userId, apiKey, null, callback);
            } else {
                getAllEbookItemsPaginated(userId, apiKey, collectionKey, new ArrayList<>(), 0, callback);
            }
        });
    }

    /**
     * Get ebook items by finding parent items with tags, then getting their attachments.
     * Tags in Zotero live on parent items, not on the attachments themselves.
     */
    private void getAllEbookItemsWithTagFilter(String userId, String apiKey, String collectionKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
        getParentItemsWithTags(userId, apiKey, collectionKey, tags, new ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> parentItems) {
                Log.d(TAG, "Found " + parentItems.size() + " parent items with tags: " + tags);

                if (parentItems.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                getAttachmentsForParentItems(userId, apiKey, parentItems, callback);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Get top-level items (books, articles, etc.) that have all the specified tags
     */
    private void getParentItemsWithTags(String userId, String apiKey, String collectionKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
        List<String> tagList = parseTagsToList(tags);

        if (tagList == null || tagList.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "users/" + userId);

        if (collectionKey != null && !collectionKey.isEmpty()) {
            urlBuilder.append("/collections/").append(collectionKey);
        }

        urlBuilder.append("/items/top?format=json"); // /top gets only top-level items (not children)

        for (String tag : tagList) {
            try {
                String encodedTag = java.net.URLEncoder.encode(tag, "UTF-8");
                urlBuilder.append("&tag=").append(encodedTag);
            } catch (java.io.UnsupportedEncodingException e) {
                Log.e(TAG, "Error encoding tag: " + tag, e);
            }
        }

        Call<List<ZoteroItem>> call = zoteroService.getItemsWithDynamicUrl(urlBuilder.toString(), apiKey);

        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                callback.onSuccess(response.body());
            } else {
                String errorMsg = "Failed to fetch parent items: HTTP " + response.code();
                try {
                    if (response.errorBody() != null) {
                        errorMsg = response.errorBody().string();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not read error body", e);
                }
                callback.onError(errorMsg);
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    }

    /**
     * Get child attachments for a list of parent items
     */
    private void getAttachmentsForParentItems(String userId, String apiKey, List<ZoteroItem> parentItems, ZoteroCallback<List<ZoteroItem>> callback) {
        List<ZoteroItem> allAttachments = new ArrayList<>();
        final int[] itemsProcessed = {0};
        final int totalItems = parentItems.size();

        for (ZoteroItem parentItem : parentItems) {
            getChildAttachments(userId, apiKey, parentItem.getKey(), new ZoteroCallback<List<ZoteroItem>>() {
                @Override
                public void onSuccess(List<ZoteroItem> attachments) {
                    synchronized (allAttachments) {
                        List<ZoteroItem> filtered = filterItemsByUserPreferences(attachments);

                        for (ZoteroItem attachment : filtered) {
                            attachment.setParentItem(parentItem);
                        }

                        allAttachments.addAll(filtered);
                        itemsProcessed[0]++;

                        if (itemsProcessed[0] == totalItems) {
                            callback.onSuccess(allAttachments);
                        }
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error getting attachments for parent " + parentItem.getKey() + ": " + errorMessage);
                    // Continue processing other items even if one fails
                    synchronized (allAttachments) {
                        itemsProcessed[0]++;
                        if (itemsProcessed[0] == totalItems) {
                            callback.onSuccess(allAttachments);
                        }
                    }
                }
            });
        }
    }

    /**
     * Get child items (attachments) for a specific parent item
     */
    private void getChildAttachments(String userId, String apiKey, String parentKey, ZoteroCallback<List<ZoteroItem>> callback) {
        String url = BASE_URL + "users/" + userId + "/items/" + parentKey + "/children?format=json";

        Call<List<ZoteroItem>> call = zoteroService.getItemsWithDynamicUrl(url, apiKey);

        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                callback.onSuccess(response.body());
            } else {
                callback.onError("Failed to fetch children: HTTP " + response.code());
            }
        } catch (IOException e) {
            callback.onError("Network error: " + e.getMessage());
        }
    }

    private void getAllEbookItemsPaginated(String userId, String apiKey, String collectionKey,
                                           List<ZoteroItem> allItems, int start,
                                           ZoteroCallback<List<ZoteroItem>> callback) {

        Call<List<ZoteroItem>> call;
        if (collectionKey == null || collectionKey.isEmpty()) {
            call = zoteroService.getItemsPaginated(userId, apiKey, "json", "attachment", start, PAGE_SIZE);
        } else {
            call = zoteroService.getItemsByCollectionPaginated(userId, collectionKey, apiKey, "json", "attachment", start, PAGE_SIZE);
        }

        try {
            Response<List<ZoteroItem>> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> items = response.body();
                allItems.addAll(filterItemsByUserPreferences(items));

                if (items.size() == PAGE_SIZE) {
                    // More items available, fetch next page
                    getAllEbookItemsPaginated(userId, apiKey, collectionKey, allItems, start + PAGE_SIZE, callback);
                } else {
                    Log.d(TAG, "Fetched total of " + allItems.size() + " ebook items (all pages)");
                    callback.onSuccess(allItems);
                }
            } else {
                String errorMsg = "Failed to fetch items: HTTP " + response.code();
                try {
                    if (response.errorBody() != null) {
                        errorMsg = response.errorBody().string();
                        Log.e(TAG, "API Error Response: " + errorMsg);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not read error body", e);
                }
                callback.onError(errorMsg);
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    }

    public void getAllEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
        getAllEbookItemsWithMetadata(userId, apiKey, collectionKey, null, callback);
    }

    public void getAllEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
        ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> ebookItems) {
                UserPreferences prefs = new UserPreferences(context);
                if (prefs.getBooksOnly()) {
                    List<ZoteroItem> bookItems = new ArrayList<>();
                    for (ZoteroItem item : ebookItems) {
                        if (item.isBook()) {
                            bookItems.add(item);
                        }
                    }
                    ebookItems = bookItems;
                }

                if (ebookItems.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                final List<ZoteroItem> processedItems = Collections.synchronizedList(new ArrayList<>());
                final AtomicInteger itemsToProcess = new AtomicInteger(ebookItems.size());

                for (ZoteroItem ebookItem : ebookItems) {
                    String parentKey = ebookItem.getParentItemKey();

                    if (parentKey != null && !parentKey.isEmpty() && ebookItem.getParentItem() == null) {
                        getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                            @Override
                            public void onSuccess(ZoteroItem parentItem) {
                                ebookItem.setParentItem(parentItem);
                                processedItems.add(ebookItem);
                                if (itemsToProcess.decrementAndGet() == 0) {
                                    callback.onSuccess(processedItems);
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "Error fetching parent item: " + errorMessage);
                                processedItems.add(ebookItem);
                                if (itemsToProcess.decrementAndGet() == 0) {
                                    callback.onSuccess(processedItems);
                                }
                            }
                        });
                    } else {
                        processedItems.add(ebookItem);
                        if (itemsToProcess.decrementAndGet() == 0) {
                            callback.onSuccess(processedItems);
                        }
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        };

        if (collectionKey == null || collectionKey.isEmpty()) {
            getAllEbookItems(userId, apiKey, tags, ebookCallback);
        } else {
            getAllEbookItemsByCollection(userId, apiKey, collectionKey, tags, ebookCallback);
        }
    }

    /**
     * Convert semicolon-separated tags string into a List for Zotero API.
     * Zotero API requires multiple tag parameters for AND logic.
     */
    private List<String> parseTagsToList(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            return null;
        }

        List<String> tagList = new ArrayList<>();
        for (String tag : tags.split(";")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tagList.add(trimmed);
            }
        }

        return tagList.isEmpty() ? null : tagList;
    }
}
