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
        
        this.cacheDir = new File(context.getFilesDir(), "epubs");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
            Log.d(TAG, message);
        });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build();
        
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

    public interface FileCallback {
        void onFileDownloaded(ZoteroItem item, String filePath);
        void onError(ZoteroItem item, String errorMessage);
    }

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
                    String errorBody = "";
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                            Log.e(TAG, "Error body: " + errorBody);
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
        });
    }

    public void getCollectionsPaginated(String userId, String apiKey, int start, int limit, ZoteroCallback<List<ZoteroCollection>> callback) {
        executor.execute(() -> {
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
                    String errorBody = "";
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                            Log.e(TAG, "Error body: " + errorBody);
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
        });
    }

    public void getEpubItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
        executor.execute(() -> {
            if (collectionKey == null || collectionKey.isEmpty()) {
                getEpubItems(userId, apiKey, callback);
                return;
            }

            Call<List<ZoteroItem>> call = zoteroService.getItemsByCollection(userId, collectionKey, apiKey, "json", "attachment", 100);

            try {
                Response<List<ZoteroItem>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<ZoteroItem> allItems = response.body();
                    List<ZoteroItem> epubItems = new ArrayList<>();

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
            String fileName = item.getKey() + ".epub";
            File epubFile = new File(cacheDir, fileName);
            
            if (epubFile.exists()) {
                callback.onFileDownloaded(item, epubFile.getAbsolutePath());
                return;
            }
            
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
            
            while (true) {
                int read = inputStream.read(fileReader);
                
                if (read == -1) {
                    break;
                }
                
                outputStream.write(fileReader, 0, read);
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
        ZoteroCallback<List<ZoteroItem>> epubCallback = new ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> epubItems) {
                if (epubItems.isEmpty()) {
                    callback.onSuccess(epubItems);
                    return;
                }
                
                final List<ZoteroItem> processedItems = new ArrayList<>();
                final int[] itemsToProcess = {epubItems.size()};
                
                for (ZoteroItem epubItem : epubItems) {
                    String parentKey = epubItem.getParentItemKey();
                    
                    if (parentKey != null && !parentKey.isEmpty()) {
                        getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                            @Override
                            public void onSuccess(ZoteroItem parentItem) {
                                epubItem.setParentItem(parentItem);
                                processedItems.add(epubItem);
                                
                                itemsToProcess[0]--;
                                if (itemsToProcess[0] == 0) {
                                    callback.onSuccess(processedItems);
                                }
                            }
                            
                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "Error fetching parent item: " + errorMessage);
                                processedItems.add(epubItem);
                                
                                itemsToProcess[0]--;
                                if (itemsToProcess[0] == 0) {
                                    callback.onSuccess(processedItems);
                                }
                            }
                        });
                    } else {
                        processedItems.add(epubItem);
                        
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

    public void getEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
        executor.execute(() -> {
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
                
                if (isValidFileType) {
                    if (booksOnly) {
                        if (item.isBook()) {
                            filteredItems.add(item);
                        }
                    } else {
                        filteredItems.add(item);
                    }
                }
            }
        }
        
        return filteredItems;
    }

    public boolean hasEnabledFileTypes() {
        UserPreferences prefs = new UserPreferences(context);
        return prefs.hasAnyFileTypeEnabled();
    }

    public void downloadEbook(ZoteroItem item, FileCallback callback) {
        executor.execute(() -> {
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
            
            String fileName = item.getKey() + fileExtension;
            File ebookFile = new File(cacheDir, fileName);
            
            if (ebookFile.exists()) {
                callback.onFileDownloaded(item, ebookFile.getAbsolutePath());
                return;
            }
            
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

    public void getEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
        ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> ebookItems) {
                if (ebookItems.isEmpty()) {
                    callback.onSuccess(ebookItems);
                    return;
                }
                
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
        
        if (collectionKey == null || collectionKey.isEmpty()) {
            getEbookItems(userId, apiKey, ebookCallback);
        } else {
            getEbookItemsByCollection(userId, apiKey, collectionKey, ebookCallback);
        }
    }

    public void getAllEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
        getAllEbookItemsByCollection(userId, apiKey, collectionKey, null, callback);
    }

    public void getAllEbookItemsByCollection(String userId, String apiKey, String collectionKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
        executor.execute(() -> {
            if (collectionKey == null || collectionKey.isEmpty()) {
                getAllEbookItems(userId, apiKey, tags, callback);
                return;
            }
            getAllEbookItemsPaginated(userId, apiKey, collectionKey, tags, new ArrayList<>(), 0, callback);
        });
    }

    private void getAllEbookItemsPaginated(String userId, String apiKey, String collectionKey, String tags,
                                          List<ZoteroItem> allItems, int start,
                                          ZoteroCallback<List<ZoteroItem>> callback) {

        Call<List<ZoteroItem>> call;
        List<String> tagList = parseTagsToList(tags);

        Log.d(TAG, "Fetching items - Start: " + start + ", Tags: " + tags + " -> parsed to list: " + tagList);

        // Use different API methods based on whether we have tags
        if (tagList != null && !tagList.isEmpty()) {
            // IMPORTANT: Tags in Zotero are on parent items, not attachments!
            // So we search for ALL items with tags, then filter to attachments
            // Build URL manually to ensure proper tag parameter formatting
            StringBuilder urlBuilder = new StringBuilder(BASE_URL + "users/" + userId);

            if (collectionKey != null && !collectionKey.isEmpty()) {
                urlBuilder.append("/collections/").append(collectionKey);
            }

            // Note: NO itemType=attachment filter here! We need to get parent items with tags
            urlBuilder.append("/items?format=json");
            urlBuilder.append("&start=").append(start);
            urlBuilder.append("&limit=100");

            // Add each tag as a separate parameter
            for (String tag : tagList) {
                try {
                    String encodedTag = java.net.URLEncoder.encode(tag, "UTF-8");
                    urlBuilder.append("&tag=").append(encodedTag);
                } catch (java.io.UnsupportedEncodingException e) {
                    Log.e(TAG, "Error encoding tag: " + tag, e);
                }
            }

            String url = urlBuilder.toString();
            Log.d(TAG, "Built URL with tags (searching ALL items, will filter to attachments): " + url);
            call = zoteroService.getItemsWithDynamicUrl(url, apiKey);
        } else {
            // Without tags - use regular methods
            if (collectionKey == null || collectionKey.isEmpty()) {
                call = zoteroService.getItemsPaginated(userId, apiKey, "json", "attachment", start, 100);
            } else {
                call = zoteroService.getItemsByCollectionPaginated(userId, collectionKey, apiKey, "json", "attachment", start, 100);
            }
            Log.d(TAG, "API Request URL (no tags): " + call.request().url());
        }

        try {
            Response<List<ZoteroItem>> response = call.execute();
            Log.d(TAG, "API Response Code: " + response.code());

            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> items = response.body();
                Log.d(TAG, "Received " + items.size() + " items from API (before filtering)");

                List<ZoteroItem> filteredItems = filterItemsByUserPreferences(items);
                Log.d(TAG, "After user preference filtering: " + filteredItems.size() + " items");

                allItems.addAll(filteredItems);

                if (items.size() == 100) {
                    // More items available, fetch next page
                    getAllEbookItemsPaginated(userId, apiKey, collectionKey, tags, allItems, start + 100, callback);
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
public void getAllEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    getAllEbookItems(userId, apiKey, null, callback);
}

public void getAllEbookItems(String userId, String apiKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        getAllEbookItemsPaginated(userId, apiKey, null, tags, new ArrayList<>(), 0, callback);
    });
}

public void getAllEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    getAllEbookItemsWithMetadata(userId, apiKey, collectionKey, null, callback);
}

public void getAllEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, String tags, ZoteroCallback<List<ZoteroItem>> callback) {
    ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> ebookItems) {
            if (ebookItems.isEmpty()) {
                callback.onSuccess(ebookItems);
                return;
            }
            
            Log.d(TAG, "Processing " + ebookItems.size() + " ebook items for metadata");
            
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

    if (collectionKey == null || collectionKey.isEmpty()) {
        getAllEbookItems(userId, apiKey, tags, ebookCallback);
    } else {
        getAllEbookItemsByCollection(userId, apiKey, collectionKey, tags, ebookCallback);
    }
}

    /**
     * Convert semicolon-separated tags string into a List for Zotero API
     * Zotero API requires multiple tag parameters for AND logic
     */
    private List<String> parseTagsToList(String tags) {
        Log.d(TAG, "parseTagsToList - Input: '" + tags + "'");

        if (tags == null || tags.trim().isEmpty()) {
            Log.d(TAG, "parseTagsToList - Input is null or empty, returning null");
            return null;
        }

        List<String> tagList = new ArrayList<>();
        String[] tagArray = tags.split(";");

        Log.d(TAG, "parseTagsToList - Split into " + tagArray.length + " parts");

        for (int i = 0; i < tagArray.length; i++) {
            String tag = tagArray[i];
            String trimmed = tag.trim();
            Log.d(TAG, "parseTagsToList - Part " + i + ": '" + tag + "' -> trimmed: '" + trimmed + "'");
            if (!trimmed.isEmpty()) {
                tagList.add(trimmed);
            }
        }

        Log.d(TAG, "parseTagsToList - Final list size: " + tagList.size() + ", tags: " + tagList);
        return tagList.isEmpty() ? null : tagList;
    }
}
