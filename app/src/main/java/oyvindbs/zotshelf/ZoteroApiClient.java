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
}

// --- Only One Definition for Each Method Below ---

public void getAllEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        getAllEbookItemsPaginated(userId, apiKey, null, new ArrayList<>(), 0, callback);
    });
}

public void getAllEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        if (collectionKey == null || collectionKey.isEmpty()) {
            getAllEbookItems(userId, apiKey, callback);
            return;
        }
        getAllEbookItemsPaginated(userId, apiKey, collectionKey, new ArrayList<>(), 0, callback);
    });
}

private void getAllEbookItemsPaginated(String userId, String apiKey, String collectionKey,
                                      List<ZoteroItem> allItems, int start,
                                      ZoteroCallback<List<ZoteroItem>> callback) {
    Call<List<ZoteroItem>> call;

    if (collectionKey == null || collectionKey.isEmpty()) {
        call = zoteroService.getItemsPaginated(userId, apiKey, "json", "attachment", start, 100);
    } else {
        call = zoteroService.getItemsByCollectionPaginated(userId, collectionKey, apiKey, "json", "attachment", start, 100);
    }

    try {
        Response<List<ZoteroItem>> response = call.execute();
        if (response.isSuccessful() && response.body() != null) {
            List<ZoteroItem> items = response.body();

            List<ZoteroItem> filteredItems = filterItemsByUserPreferences(items);
            allItems.addAll(filteredItems);

            if (items.size() == 100) {
                getAllEbookItemsPaginated(userId, apiKey, collectionKey, allItems, start + 100, callback);
            } else {
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
                            callback.onError(errorMessage);
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
        getAllEbookItems(userId, apiKey, ebookCallback);
    } else {
        getAllEbookItemsByCollection(userId, apiKey, collectionKey, ebookCallback);
    }
}
// ...other code, including filterItemsByUserPreferences, getParentItem, etc.
}