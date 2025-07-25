package oyvindbs.zotshelf;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
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

private final Context context;
private final ZoteroService zoteroService;
private final Executor executor;
private final File cacheDir;

public ZoteroApiClient(Context context) {
    this.context = context;
    this.executor = Executors.newCachedThreadPool();
    
    // Create a PERSISTENT directory for downloaded EPUBs (not cache!)
    // Using getFilesDir() instead of getCacheDir() to persist data across app restarts
    this.cacheDir = new File(context.getFilesDir(), "epubs");
    if (!cacheDir.exists()) {
        cacheDir.mkdirs();
    }
    
    // Setup HTTP client with logging
    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
        // Log all API requests and responses
        Log.d(TAG, message);
    });
    loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // Change to BODY for full request/response logging
    
    OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build();
    
    // Create Retrofit instance
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

    @GET("users/{userId}/collections/{collectionKey}/items")
    Call<List<ZoteroItem>> getItemsByCollection(
            @Path("userId") String userId,
            @Path("collectionKey") String collectionKey,
            @Header("Zotero-API-Key") String apiKey,
            @Query("format") String format,
            @Query("itemType") String itemType,
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
    
    @GET("users/{userId}/items")
Call<List<ZoteroItem>> getItemsPaginated(
        @Path("userId") String userId,
        @Header("Zotero-API-Key") String apiKey,
        @Query("format") String format,
        @Query("itemType") String itemType,
        @Query("start") int start,
        @Query("limit") int limit
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

public interface FileCallback {
    void onFileDownloaded(ZoteroItem item, String filePath);
    void onError(ZoteroItem item, String errorMessage);
}

/**
 * Optional: Clean up old files to manage storage space
 * Call this periodically (e.g., monthly) to prevent unlimited storage growth
 */
public void cleanupOldFiles(int maxAgeInDays) {
    executor.execute(() -> {
        long cutoffTime = System.currentTimeMillis() - (maxAgeInDays * 24 * 60 * 60 * 1000L);
        
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old file: " + file.getName());
                    }
                }
            }
        }
        
        // Also clean up covers directory
        File coversDir = new File(cacheDir, "covers");
        if (coversDir.exists()) {
            File[] coverFiles = coversDir.listFiles();
            if (coverFiles != null) {
                for (File file : coverFiles) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted old cover: " + file.getName());
                        }
                    }
                }
            }
        }
    });
}

public void getEpubItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        Call<List<ZoteroItem>> call = zoteroService.getItems(userId, apiKey, "json", "attachment", 100);
        
        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> allItems = response.body();
                List<ZoteroItem> epubItems = new ArrayList<>();
                
                // Filter to only EPUB attachments
                for (ZoteroItem item : allItems) {
                    if (item.getMimeType() != null && 
                        item.getMimeType().equals("application/epub+zip")) {
                        epubItems.add(item);
                    }
                }
                
                callback.onSuccess(epubItems);
            } else {
                callback.onError("Failed to fetch items: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    });
}

public void getCollections(String userId, String apiKey, ZoteroCallback<List<ZoteroCollection>> callback) {
    executor.execute(() -> {
        // Check input parameters
        Log.d(TAG, "Getting collections - UserId: '" + userId + "', API Key: '" + 
              (apiKey != null ? apiKey.substring(0, 5) + "..." : "null") + "'");
        
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "User ID is empty or null!");
            callback.onError("User ID is empty");
            return;
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API Key is empty or null!");
            callback.onError("API Key is empty");
            return;
        }
        
        Call<List<ZoteroCollection>> call = zoteroService.getCollections(userId, apiKey);
        Log.d(TAG, "API Request URL: " + call.request().url());

        try {
            Response<List<ZoteroCollection>> response = call.execute();
            Log.d(TAG, "API Response Code: " + response.code());
            
            if (response.isSuccessful()) {
                List<ZoteroCollection> collections = response.body();
                if (collections != null) {
                    Log.d(TAG, "Received " + collections.size() + " collections");
                    
                    // Log each collection for debugging
                    for (ZoteroCollection collection : collections) {
                        Log.d(TAG, "Collection: " + collection.getName() + 
                              ", Key: " + collection.getKey() + 
                              ", Parent: " + collection.getParentCollection());
                    }
                    
                    callback.onSuccess(collections);
                } else {
                    Log.e(TAG, "Response body is null!");
                    callback.onError("Received empty response from Zotero");
                }
            } else {
                Log.e(TAG, "Failed to fetch collections: " + response.code());
                // Get error body if available
                String errorBody = "";
                if (response.errorBody() != null) {
                    try {
                        errorBody = response.errorBody().string();
                        Log.e(TAG, "Error body: " + errorBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Could not read error body", e);
                    }
                }
                
                // Check for specific error codes
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
    });
}

public void getCollectionsPaginated(String userId, String apiKey, int start, int limit, ZoteroCallback<List<ZoteroCollection>> callback) {
    executor.execute(() -> {
        // Check input parameters
        Log.d(TAG, "Getting paginated collections - UserId: '" + userId + "', Start: " + start + ", Limit: " + limit);
        
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "User ID is empty or null!");
            callback.onError("User ID is empty");
            return;
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API Key is empty or null!");
            callback.onError("API Key is empty");
            return;
        }
        
        Call<List<ZoteroCollection>> call = zoteroService.getCollectionsPaginated(userId, apiKey, start, limit);
        Log.d(TAG, "API Request URL: " + call.request().url());

        try {
            Response<List<ZoteroCollection>> response = call.execute();
            Log.d(TAG, "API Response Code: " + response.code());
            
            if (response.isSuccessful()) {
                List<ZoteroCollection> collections = response.body();
                if (collections != null) {
                    Log.d(TAG, "Received " + collections.size() + " collections (page " + (start/limit + 1) + ")");
                    
                    // Log each collection for debugging
                    for (ZoteroCollection collection : collections) {
                        Log.d(TAG, "Collection: " + collection.getName() + 
                              ", Key: " + collection.getKey() + 
                              ", Parent: " + collection.getParentCollection());
                    }
                    
                    callback.onSuccess(collections);
                } else {
                    Log.e(TAG, "Response body is null!");
                    callback.onError("Received empty response from Zotero");
                }
            } else {
                Log.e(TAG, "Failed to fetch collections: " + response.code());
                // Get error body if available
                String errorBody = "";
                if (response.errorBody() != null) {
                    try {
                        errorBody = response.errorBody().string();
                        Log.e(TAG, "Error body: " + errorBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Could not read error body", e);
                    }
                }
                
                // Check for specific error codes
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
    });
}

public void getEpubItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        // If no collection selected, get all items
        if (collectionKey == null || collectionKey.isEmpty()) {
            getEpubItems(userId, apiKey, callback);
            return;
        }

        // Call the API with the collection filter
        Call<List<ZoteroItem>> call = zoteroService.getItemsByCollection(userId, collectionKey, apiKey, "json", "attachment", 100);

        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> allItems = response.body();
                List<ZoteroItem> epubItems = new ArrayList<>();

                // Filter to only EPUB attachments
                for (ZoteroItem item : allItems) {
                    if (item.getMimeType() != null && 
                        item.getMimeType().equals("application/epub+zip")) {
                        epubItems.add(item);
                    }
                }

                callback.onSuccess(epubItems);
            } else {
                callback.onError("Failed to fetch items: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    });
}

public void downloadEpub(ZoteroItem item, FileCallback callback) {
    executor.execute(() -> {
        // Check if we have the EPUB cached already
        String fileName = item.getKey() + ".epub";
        File epubFile = new File(cacheDir, fileName);
        
        if (epubFile.exists()) {
            callback.onFileDownloaded(item, epubFile.getAbsolutePath());
            return;
        }
        
        // Get the download URL from the item
        String downloadUrl = item.getLinks().getEnclosure().getHref();
        String apiKey = new UserPreferences(context).getZoteroApiKey();
        
        Call<ResponseBody> call = zoteroService.downloadFile(downloadUrl, apiKey);
        
        try {
            Response<ResponseBody> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                boolean success = writeResponseBodyToDisk(response.body(), epubFile);
                
                if (success) {
                    callback.onFileDownloaded(item, epubFile.getAbsolutePath());
                } else {
                    callback.onError(item, "Failed to save EPUB file");
                }
            } else {
                callback.onError(item, "Failed to download EPUB: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Download error", e);
            callback.onError(item, "Network error: " + e.getMessage());
        }
    });
}

private boolean writeResponseBodyToDisk(ResponseBody body, File outputFile) {
    try {
        InputStream inputStream = body.byteStream();
        OutputStream outputStream = new FileOutputStream(outputFile);
        
        byte[] fileReader = new byte[4096];
        long fileSize = body.contentLength();
        long fileSizeDownloaded = 0;
        
        while (true) {
            int read = inputStream.read(fileReader);
            
            if (read == -1) {
                break;
            }
            
            outputStream.write(fileReader, 0, read);
            fileSizeDownloaded += read;
        }
        
        outputStream.flush();
        outputStream.close();
        inputStream.close();
        
        return true;
    } catch (IOException e) {
        Log.e(TAG, "File write error", e);
        return false;
    }
}

public void getParentItem(String userId, String apiKey, String parentItemKey, ZoteroCallback<ZoteroItem> callback) {
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

public void getEpubItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    // First get all EPUB items
    ZoteroCallback<List<ZoteroItem>> epubCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> epubItems) {
            if (epubItems.isEmpty()) {
                callback.onSuccess(epubItems); // No items to process
                return;
            }
            
            // For each EPUB item, fetch its parent item if it has one
            final List<ZoteroItem> processedItems = new ArrayList<>();
            final int[] itemsToProcess = {epubItems.size()};
            
            for (ZoteroItem epubItem : epubItems) {
                String parentKey = epubItem.getParentItemKey();
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    // Fetch parent item metadata
                    getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                        @Override
                        public void onSuccess(ZoteroItem parentItem) {
                            epubItem.setParentItem(parentItem);
                            processedItems.add(epubItem);
                            
                            // Check if all items are processed
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                callback.onSuccess(processedItems);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error fetching parent item: " + errorMessage);
                            // Still add the item even without parent metadata
                            processedItems.add(epubItem);
                            
                            // Check if all items are processed
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                callback.onSuccess(processedItems);
                            }
                        }
                    });
                } else {
                    // No parent item, just add as is
                    processedItems.add(epubItem);
                    
                    // Check if all items are processed
                    itemsToProcess[0]--;
                    if (itemsToProcess[0] == 0) {
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
    
    // Get EPUB items first
    if (collectionKey == null || collectionKey.isEmpty()) {
        getEpubItems(userId, apiKey, epubCallback);
    } else {
        getEpubItemsByCollection(userId, apiKey, collectionKey, epubCallback);
    }
}

public void getEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        Call<List<ZoteroItem>> call = zoteroService.getItems(userId, apiKey, "json", "attachment", 100);
        
        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> allItems = response.body();
                List<ZoteroItem> filteredItems = filterItemsByUserPreferences(allItems);
                
                callback.onSuccess(filteredItems);
            } else {
                callback.onError("Failed to fetch items: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    });
}

/**
 * Get ebook items by collection, filtered by user preferences
 */
public void getEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        // If no collection selected, get all items
        if (collectionKey == null || collectionKey.isEmpty()) {
            getEbookItems(userId, apiKey, callback);
            return;
        }

        Call<List<ZoteroItem>> call = zoteroService.getItemsByCollection(userId, collectionKey, apiKey, "json", "attachment", 100);

        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> allItems = response.body();
                List<ZoteroItem> filteredItems = filterItemsByUserPreferences(allItems);

                callback.onSuccess(filteredItems);
            } else {
                callback.onError("Failed to fetch items: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    });
}

/**
 * Filter items based on user preferences for file types
 */
private List<ZoteroItem> filterItemsByUserPreferences(List<ZoteroItem> allItems) {
    UserPreferences prefs = new UserPreferences(context);
    boolean showEpubs = prefs.getShowEpubs();
    boolean showPdfs = prefs.getShowPdfs();
    boolean booksOnly = prefs.getBooksOnly();
    
    List<ZoteroItem> filteredItems = new ArrayList<>();
    
    for (ZoteroItem item : allItems) {
        String mimeType = item.getMimeType();
        if (mimeType != null) {
            boolean isValidFileType = false;
            
            if (mimeType.equals("application/epub+zip") && showEpubs) {
                isValidFileType = true;
            } else if (mimeType.equals("application/pdf") && showPdfs) {
                isValidFileType = true;
            }
            
            // If file type is valid, check books-only filter
            if (isValidFileType) {
                if (booksOnly) {
                    // Only add if it's a book (not an article)
                    if (item.isBook()) {
                        filteredItems.add(item);
                    }
                } else {
                    // Add all valid file types
                    filteredItems.add(item);
                }
            }
        }
    }
    
    return filteredItems;
}

/**
 * Check if the user has any file types enabled
 */
public boolean hasEnabledFileTypes() {
    UserPreferences prefs = new UserPreferences(context);
    return prefs.hasAnyFileTypeEnabled();
}

/**
 * Download ebook (EPUB or PDF) file
 */
public void downloadEbook(ZoteroItem item, FileCallback callback) {
    executor.execute(() -> {
        // Determine file extension based on MIME type
        String fileExtension;
        String mimeType = item.getMimeType();
        if ("application/epub+zip".equals(mimeType)) {
            fileExtension = ".epub";
        } else if ("application/pdf".equals(mimeType)) {
            fileExtension = ".pdf";
        } else {
            callback.onError(item, "Unsupported file type: " + mimeType);
            return;
        }
        
        // Check if we have the file cached already
        String fileName = item.getKey() + fileExtension;
        File ebookFile = new File(cacheDir, fileName);
        
        if (ebookFile.exists()) {
            callback.onFileDownloaded(item, ebookFile.getAbsolutePath());
            return;
        }
        
        // Get the download URL from the item
        if (item.getLinks() == null || item.getLinks().getEnclosure() == null) {
            callback.onError(item, "No download link available");
            return;
        }
        
        String downloadUrl = item.getLinks().getEnclosure().getHref();
        String apiKey = new UserPreferences(context).getZoteroApiKey();
        
        Call<ResponseBody> call = zoteroService.downloadFile(downloadUrl, apiKey);
        
        try {
            Response<ResponseBody> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                boolean success = writeResponseBodyToDisk(response.body(), ebookFile);
                
                if (success) {
                    callback.onFileDownloaded(item, ebookFile.getAbsolutePath());
                } else {
                    callback.onError(item, "Failed to save file");
                }
            } else {
                callback.onError(item, "Failed to download file: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Download error", e);
            callback.onError(item, "Network error: " + e.getMessage());
        }
    });
}

/**
 * Get ebook items with metadata (replaces getEpubItemsWithMetadata)
 */
public void getEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    // First get all ebook items
    ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> ebookItems) {
            if (ebookItems.isEmpty()) {
                callback.onSuccess(ebookItems);
                return;
            }
            
            // For each ebook item, fetch its parent item if it has one
            final List<ZoteroItem> processedItems = new ArrayList<>();
            final int[] itemsToProcess = {ebookItems.size()};
            
            for (ZoteroItem ebookItem : ebookItems) {
                String parentKey = ebookItem.getParentItemKey();
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                        @Override
                        public void onSuccess(ZoteroItem parentItem) {
                            ebookItem.setParentItem(parentItem);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                callback.onSuccess(processedItems);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error fetching parent item: " + errorMessage);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                callback.onSuccess(processedItems);
                            }
                        }
                    });
                } else {
                    processedItems.add(ebookItem);
                    
                    itemsToProcess[0]--;
                    if (itemsToProcess[0] == 0) {
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
    
    // Get ebook items first
    if (collectionKey == null || collectionKey.isEmpty()) {
        getEbookItems(userId, apiKey, ebookCallback);
    } else {
        getEbookItemsByCollection(userId, apiKey, collectionKey, ebookCallback);
    }
}
// Add these methods to your ZoteroApiClient.java

/**
 * Get all ebook items with pagination support
 */
public void getAllEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        getAllEbookItemsPaginated(userId, apiKey, null, new ArrayList<>(), 0, callback);
    });
}

/**
 * Get all ebook items by collection with pagination support
 */
public void getAllEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        if (collectionKey == null || collectionKey.isEmpty()) {
            getAllEbookItems(userId, apiKey, callback);
            return;
        }
        getAllEbookItemsPaginated(userId, apiKey, collectionKey, new ArrayList<>(), 0, callback);
    });
}

/**
 * Internal method to handle pagination recursively
 */
private void getAllEbookItemsPaginated(String userId, String apiKey, String collectionKey, 
                                      List<ZoteroItem> allItems, int start, 
                                      ZoteroCallback<List<ZoteroItem>> callback) {
    
    Call<List<ZoteroItem>> call;
    
    if (collectionKey == null || collectionKey.isEmpty()) {
        // Get all items with pagination
        call = zoteroService.getItemsPaginated(userId, apiKey, "json", "attachment", start, 100);
    } else {
        // Get items by collection with pagination
        call = zoteroService.getItemsByCollectionPaginated(userId, collectionKey, apiKey, "json", "attachment", start, 100);
    }
    
    try {
        Response<List<ZoteroItem>> response = call.execute();
        if (response.isSuccessful() && response.body() != null) {
            List<ZoteroItem> items = response.body();
            
            // Filter items by user preferences
            List<ZoteroItem> filteredItems = filterItemsByUserPreferences(items);
            allItems.addAll(filteredItems);
            
            // If we got 100 items, there might be more - fetch next page
            if (items.size() == 100) {
                getAllEbookItemsPaginated(userId, apiKey, collectionKey, allItems, start + 100, callback);
            } else {
                // We've got all items
                Log.d(TAG, "Fetched total of " + allItems.size() + " ebook items");
                callback.onSuccess(allItems);
            }
        } else {
            callback.onError("Failed to fetch items: " + response.code());
        }
    } catch (IOException e) {
        Log.e(TAG, "API error", e);
        callback.onError("Network error: " + e.getMessage());
    }
}

/**
 * Updated method that gets all ebook items with metadata and pagination
 */
public void getAllEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    // Use the new paginated methods
    ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> ebookItems) {
            if (ebookItems.isEmpty()) {
                callback.onSuccess(ebookItems);
                return;
            }
            
            Log.d(TAG, "Processing " + ebookItems.size() + " ebook items for metadata");
            
            // Apply books-only filter if enabled
            UserPreferences prefs = new UserPreferences(context);
            if (prefs.getBooksOnly()) {
                List<ZoteroItem> bookItems = new ArrayList<>();
                for (ZoteroItem item : ebookItems) {
                    if (item.isBook()) {
                        bookItems.add(item);
                    }
                }
                ebookItems = bookItems;
                Log.d(TAG, "After books-only filter: " + ebookItems.size() + " items");
            }
            
            // For each ebook item, fetch its parent item if it has one
            final List<ZoteroItem> processedItems = new ArrayList<>();
            final int[] itemsToProcess = {ebookItems.size()};
            
            for (ZoteroItem ebookItem : ebookItems) {
                String parentKey = ebookItem.getParentItemKey();
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                        @Override
                        public void onSuccess(ZoteroItem parentItem) {
                            ebookItem.setParentItem(parentItem);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                Log.d(TAG, "Finished processing all items. Final count: " + processedItems.size());
                                callback.onSuccess(processedItems);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error fetching parent item: " + errorMessage);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                Log.d(TAG, "Finished processing all items. Final count: " + processedItems.size());
                                callback.onSuccess(processedItems);
                            }
                        }
                    });
                } else {
                    processedItems.add(ebookItem);
                    
                    itemsToProcess[0]--;
                    if (itemsToProcess[0] == 0) {
                        Log.d(TAG, "Finished processing all items. Final count: " + processedItems.size());
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
    
    // Use the new paginated methods
    if (collectionKey == null || collectionKey.isEmpty()) {
        getAllEbookItems(userId, apiKey, ebookCallback);
    } else {
        getAllEbookItemsByCollection(userId, apiKey, collectionKey, ebookCallback);
    }
}

public void getAllEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        getAllEbookItemsPaginated(userId, apiKey, null, new ArrayList<>(), 0, callback);
    });
}

/**
 * Get all ebook items by collection with pagination support
 */
public void getAllEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        if (collectionKey == null || collectionKey.isEmpty()) {
            getAllEbookItems(userId, apiKey, callback);
            return;
        }
        getAllEbookItemsPaginated(userId, apiKey, collectionKey, new ArrayList<>(), 0, callback);
    });
}

/**
 * Internal method to handle pagination recursively
 */
private void getAllEbookItemsPaginated(String userId, String apiKey, String collectionKey, 
                                      List<ZoteroItem> allItems, int start, 
                                      ZoteroCallback<List<ZoteroItem>> callback) {
    
    Call<List<ZoteroItem>> call;
    
    if (collectionKey == null || collectionKey.isEmpty()) {
        // Get all items with pagination
        call = zoteroService.getItemsPaginated(userId, apiKey, "json", "attachment", start, 100);
    } else {
        // Get items by collection with pagination
        call = zoteroService.getItemsByCollectionPaginated(userId, collectionKey, apiKey, "json", "attachment", start, 100);
    }
    
    try {
        Response<List<ZoteroItem>> response = call.execute();
        if (response.isSuccessful() && response.body() != null) {
            List<ZoteroItem> items = response.body();
            
            // Filter items by user preferences
            List<ZoteroItem> filteredItems = filterItemsByUserPreferences(items);
            allItems.addAll(filteredItems);
            
            // If we got 100 items, there might be more - fetch next page
            if (items.size() == 100) {
                getAllEbookItemsPaginated(userId, apiKey, collectionKey, allItems, start + 100, callback);
            } else {
                // We've got all items
                Log.d(TAG, "Fetched total of " + allItems.size() + " ebook items");
                callback.onSuccess(allItems);
            }
        } else {
            callback.onError("Failed to fetch items: " + response.code());
        }
    } catch (IOException e) {
        Log.e(TAG, "API error", e);
        callback.onError("Network error: " + e.getMessage());
    }
}

/**
 * Get ebook items with metadata and pagination support
 */
public void getAllEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    // Use the new paginated methods
    ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> ebookItems) {
            if (ebookItems.isEmpty()) {
                callback.onSuccess(ebookItems);
                return;
            }
            
            Log.d(TAG, "Processing " + ebookItems.size() + " ebook items for metadata");
            
            // Apply books-only filter if enabled
            UserPreferences prefs = new UserPreferences(context);
            if (prefs.getBooksOnly()) {
                List<ZoteroItem> bookItems = new ArrayList<>();
                for (ZoteroItem item : ebookItems) {
                    if (item.isBook()) {
                        bookItems.add(item);
                    }
                }
                ebookItems = bookItems;
                Log.d(TAG, "After books-only filter: " + ebookItems.size() + " items");
            }
            
            // For each ebook item, fetch its parent item if it has one
            final List<ZoteroItem> processedItems = new ArrayList<>();
            final int[] itemsToProcess = {ebookItems.size()};
            
            if (ebookItems.isEmpty()) {
                callback.onSuccess(new ArrayList<>());
                return;
            }
            
            for (ZoteroItem ebookItem : ebookItems) {
                String parentKey = ebookItem.getParentItemKey();
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                        @Override
                        public void onSuccess(ZoteroItem parentItem) {
                            ebookItem.setParentItem(parentItem);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                Log.d(TAG, "Finished processing all items. Final count: " + processedItems.size());
                                callback.onSuccess(processedItems);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error fetching parent item: " + errorMessage);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                Log.d(TAG, "Finished processing all items. Final count: " + processedItems.size());
                                callback.onSuccess(processedItems);
                            }
                        }
                    });
                } else {
                    processedItems.add(ebookItem);
                    
                    itemsToProcess[0]--;
                    if (itemsToProcess[0] == 0) {
                        Log.d(TAG, "Finished processing all items. Final count: " + processedItems.size());
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
    
    // Use the new paginated methods
    if (collectionKey == null || collectionKey.isEmpty()) {
        getAllEbookItems(userId, apiKey, ebookCallback);
    } else {
        getAllEbookItemsByCollection(userId, apiKey, collectionKey, ebookCallback);
    }
}

}