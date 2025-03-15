package com.example.zoteroepubcovers;

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
import retrofit2.Callback;
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
        
        // Create a cache directory for downloaded EPUBs
        this.cacheDir = new File(context.getCacheDir(), "epubs");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        // Setup HTTP client with logging
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
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
        
        @GET
        @Streaming
        Call<ResponseBody> downloadFile(@Url String fileUrl, @Header("Zotero-API-Key") String apiKey);
    }

    public interface ZoteroCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
    
    public interface FileCallback {
        void onFileDownloaded(ZoteroItem item, String filePath);
        void onError(ZoteroItem item, String errorMessage);
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
}