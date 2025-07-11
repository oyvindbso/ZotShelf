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
    loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    
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

    @GET("users/{userId}/collections/{collectionKey}/items")
    Call<List<ZoteroItem>> getItemsByCollection(
            @Path("userId") String userId,
            @Path("collectionKey") String collectionKey,
            @Header("Zotero-API-Key") String apiKey,
            @Query("format") String format,
            @Query("itemType") String itemType,
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

public interface FileCallback {
    void onFileDownloaded(ZoteroItem item, String filePath);
    void onError(ZoteroItem item, String errorMessage);
}

/**
 * Get ebook items filtered by user preferences
 */
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
    
    List<ZoteroItem> filteredItems = new ArrayList<>();
    
    for (ZoteroItem item : allItems) {
        String mimeType = item.getMimeType();
        if (mimeType != null) {
            if (mimeType.equals("application/epub+zip") && showEpubs) {
                filteredItems.add(item);
            } else if (mimeType.equals("application/pdf") && showPdfs) {
                filteredItems.add(item);
            }
        }
    }
    
    return filteredItems;
}

/**
 * Filter items by content type after parent items have been fetched
 */
private List<ZoteroItem> filterByContentType(List<ZoteroItem> items) {
    UserPreferences prefs = new UserPreferences(context);
    boolean booksOnly = prefs.getBooksOnly();
    
    Log.d(TAG, "filterByContentType called with " + items.size() + " items, booksOnly=" + booksOnly);
    
    if (!booksOnly) {
        Log.d(TAG, "Books-only filtering disabled, returning all " + items.size() + " items");
        return items; // No content filtering needed
    }
    
    List<ZoteroItem> bookItems = new ArrayList<>();
    for (ZoteroItem item : items) {
        String parentType = item.getParentItemType();
        boolean isBook = item.isBook();
        
        Log.d(TAG, "Item: " + item.getTitle() + 
              ", ParentType: " + parentType + 
              ", IsBook: " + isBook);
        
        if (isBook) {
            bookItems.add(item);
            Log.d(TAG, "  -> Added as book");
        } else {
            Log.d(TAG, "  -> Filtered out (not a book)");
        }
    }
    
    Log.d(TAG, "Filtered from " + items.size() + " to " + bookItems.size() + " book items");
    return bookItems;
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
            // Update the last accessed time since we're using this file
            touchFile(ebookFile);
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
                    // Touch the file to set initial access time
                    touchFile(ebookFile);
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

/**
 * Get ebook items with metadata and content filtering
 */
public void getEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    Log.d(TAG, "getEbookItemsWithMetadata called");
    
    // First get all ebook items
    ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> ebookItems) {
            Log.d(TAG, "Got " + ebookItems.size() + " ebook items from API");
            
            if (ebookItems.isEmpty()) {
                callback.onSuccess(ebookItems);
                return;
            }
            
            // For each ebook item, fetch its parent item if it has one
            final List<ZoteroItem> processedItems = new ArrayList<>();
            final int[] itemsToProcess = {ebookItems.size()};
            
            for (ZoteroItem ebookItem : ebookItems) {
                String parentKey = ebookItem.getParentItemKey();
                
                Log.d(TAG, "Processing item: " + ebookItem.getTitle() + ", parentKey: " + parentKey);
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                        @Override
                        public void onSuccess(ZoteroItem parentItem) {
                            Log.d(TAG, "Got parent item: " + parentItem.getItemType() + " for " + ebookItem.getTitle());
                            ebookItem.setParentItem(parentItem);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                // Now filter by content type after all parent items are fetched
                                List<ZoteroItem> finalItems = filterByContentType(processedItems);
                                Log.d(TAG, "Final filtered items: " + finalItems.size());
                                callback.onSuccess(finalItems);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error fetching parent item: " + errorMessage);
                            // Still add the item but it won't pass book filtering
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                List<ZoteroItem> finalItems = filterByContentType(processedItems);
                                Log.d(TAG, "Final filtered items: " + finalItems.size());
                                callback.onSuccess(finalItems);
                            }
                        }
                    });
                } else {
                    // No parent item - this won't pass book filtering if books-only is enabled
                    Log.d(TAG, "No parent item for: " + ebookItem.getTitle());
                    processedItems.add(ebookItem);
                    
                    itemsToProcess[0]--;
                    if (itemsToProcess[0] == 0) {
                        List<ZoteroItem> finalItems = filterByContentType(processedItems);
                        Log.d(TAG, "Final filtered items: " + finalItems.size());
                        callback.onSuccess(finalItems);
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

/**
 * Optional: Clean up old files to manage storage space
 * Call this periodically (e.g., monthly) to prevent unlimited storage growth
 * This cleans files that haven't been ACCESSED for the specified number of days
 */
public void cleanupOldFiles(int maxAgeInDays) {
    executor.execute(() -> {
        long cutoffTime = System.currentTimeMillis() - (maxAgeInDays * 24 * 60 * 60 * 1000L);
        
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                // Use lastModified() which gets updated when we "touch" the file on access
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old unused file: " + file.getName() + 
                              " (last accessed " + ((System.currentTimeMillis() - file.lastModified()) / (24 * 60 * 60 * 1000)) + " days ago)");
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
                            Log.d(TAG, "Deleted old unused cover: " + file.getName() + 
                                  " (last accessed " + ((System.currentTimeMillis() - file.lastModified()) / (24 * 60 * 60 * 1000)) + " days ago)");
                        }
                    }
                }
            }
        }
    });
}

/**
 * Update the last accessed time of a file by "touching" it
 */
private void touchFile(File file) {
    try {
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis());
        }
    } catch (Exception e) {
        Log.w(TAG, "Could not update last modified time for: " + file.getName(), e);
    }
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


}