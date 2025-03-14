/**
 * This project contains multiple files for an Android app that extracts covers from EPUBs 
 * downloaded from Zotero and displays them in a grid view.
 * 
 * File structure:
 * - build.gradle (app level)
 * - AndroidManifest.xml
 * - MainActivity.java
 * - CoverGridAdapter.java
 * - ZoteroApiClient.java
 * - EpubCoverExtractor.java
 * - activity_main.xml
 * - grid_item_cover.xml
 * - UserPreferences.java
 */

// ==================== build.gradle (app level) ====================
/*
plugins {
    id 'com.android.application'
}

android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.example.zoteroepubcovers"
        minSdk 23
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
    
    // Retrofit for API calls
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    
    // Glide for image loading
    implementation 'com.github.bumptech.glide:glide:4.15.1'
    
    // Epublib for EPUB parsing
    implementation 'com.github.psiegman:epublib-core:4.0'
    
    // Room for caching
    implementation 'androidx.room:room-runtime:2.5.2'
    annotationProcessor 'androidx.room:room-compiler:2.5.2'
}
*/

// ==================== AndroidManifest.xml ====================
/*
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.zoteroepubcovers">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
                     android:maxSdkVersion="28" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ZoteroEpubCovers">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <activity
            android:name=".SettingsActivity"
            android:label="@string/title_activity_settings"
            android:parentActivityName=".MainActivity" />
    </application>

</manifest>
*/

// ==================== MainActivity.java ====================
package com.example.zoteroepubcovers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CoverGridAdapter.CoverClickListener {

    private RecyclerView recyclerView;
    private CoverGridAdapter adapter;
    private List<EpubCoverItem> coverItems = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ZoteroApiClient zoteroApiClient;
    private UserPreferences userPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userPreferences = new UserPreferences(this);
        
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);
        recyclerView = findViewById(R.id.recyclerViewCovers);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Setup RecyclerView with Grid Layout
        int spanCount = calculateSpanCount();
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
        adapter = new CoverGridAdapter(this, coverItems, this);
        recyclerView.setAdapter(adapter);

        // Initialize Zotero API client
        zoteroApiClient = new ZoteroApiClient(this);

        // Setup refresh listener
        swipeRefreshLayout.setOnRefreshListener(this::loadEpubs);

        // Check if we have Zotero credentials, if not show settings first
        if (!userPreferences.hasZoteroCredentials()) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            loadEpubs();
        }
    }

    private int calculateSpanCount() {
        // Calculate number of columns based on screen width
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int itemWidthDp = 120; // Target width for each grid item
        return Math.max(2, screenWidthDp / itemWidthDp);
    }

    private void loadEpubs() {
        if (!userPreferences.hasZoteroCredentials()) {
            showEmptyState("Please enter your Zotero credentials in settings");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading();

        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();

        zoteroApiClient.getEpubItems(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> zoteroItems) {
                processZoteroItems(zoteroItems);
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showEmptyState("Error: " + errorMessage);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void processZoteroItems(List<ZoteroItem> zoteroItems) {
        if (zoteroItems.isEmpty()) {
            runOnUiThread(() -> {
                showEmptyState("No EPUB files found in your Zotero library");
                swipeRefreshLayout.setRefreshing(false);
            });
            return;
        }

        // Process each Zotero item that has EPUBs
        List<EpubCoverItem> newCoverItems = new ArrayList<>();
        
        for (ZoteroItem item : zoteroItems) {
            zoteroApiClient.downloadEpub(item, new ZoteroApiClient.FileCallback() {
                @Override
                public void onFileDownloaded(ZoteroItem item, String filePath) {
                    // Extract cover from the EPUB
                    EpubCoverExtractor.extractCover(filePath, new EpubCoverExtractor.CoverCallback() {
                        @Override
                        public void onCoverExtracted(String coverPath) {
                            EpubCoverItem coverItem = new EpubCoverItem(
                                    item.getKey(),
                                    item.getTitle(),
                                    coverPath,
                                    item.getAuthors(),
                                    userPreferences.getZoteroUsername()
                            );
                            
                            newCoverItems.add(coverItem);
                            
                            // Update UI when all items are processed
                            if (newCoverItems.size() == zoteroItems.size()) {
                                updateUI(newCoverItems);
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // If cover extraction fails, still add the item but with a placeholder
                            EpubCoverItem coverItem = new EpubCoverItem(
                                    item.getKey(),
                                    item.getTitle(),
                                    null, // null cover path will show placeholder
                                    item.getAuthors(),
                                    userPreferences.getZoteroUsername()
                            );
                            
                            newCoverItems.add(coverItem);
                            
                            // Update UI when all items are processed
                            if (newCoverItems.size() == zoteroItems.size()) {
                                updateUI(newCoverItems);
                            }
                        }
                    });
                }

                @Override
                public void onError(ZoteroItem item, String errorMessage) {
                    // If download fails, still add the item but with error info and placeholder
                    EpubCoverItem coverItem = new EpubCoverItem(
                            item.getKey(),
                            item.getTitle() + " (Download failed)",
                            null, // null cover path will show placeholder
                            item.getAuthors(),
                            userPreferences.getZoteroUsername()
                    );
                    
                    newCoverItems.add(coverItem);
                    
                    // Update UI when all items are processed
                    if (newCoverItems.size() == zoteroItems.size()) {
                        updateUI(newCoverItems);
                    }
                }
            });
        }
    }

    private void updateUI(final List<EpubCoverItem> newItems) {
        runOnUiThread(() -> {
            coverItems.clear();
            coverItems.addAll(newItems);
            adapter.notifyDataSetChanged();
            
            if (coverItems.isEmpty()) {
                showEmptyState("No EPUB files found");
            } else {
                hideEmptyState();
            }
            
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showEmptyState(String message) {
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
    }

    private void hideEmptyState() {
        emptyView.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_refresh) {
            loadEpubs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload if settings might have changed
        if (userPreferences.hasZoteroCredentials() && coverItems.isEmpty()) {
            loadEpubs();
        }
    }

    @Override
    public void onCoverClick(EpubCoverItem item) {
        // Open the Zotero web library when a cover is clicked
        String zoteroUsername = userPreferences.getZoteroUsername();
        if (zoteroUsername == null || zoteroUsername.isEmpty()) {
            return;
        }
        
        String url = "https://www.zotero.org/" + zoteroUsername + "/items/" + item.getId();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}

// ==================== CoverGridAdapter.java ====================
package com.example.zoteroepubcovers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class CoverGridAdapter extends RecyclerView.Adapter<CoverGridAdapter.CoverViewHolder> {

    private final Context context;
    private final List<EpubCoverItem> coverItems;
    private final CoverClickListener listener;

    public interface CoverClickListener {
        void onCoverClick(EpubCoverItem item);
    }

    public CoverGridAdapter(Context context, List<EpubCoverItem> coverItems, CoverClickListener listener) {
        this.context = context;
        this.coverItems = coverItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CoverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.grid_item_cover, parent, false);
        return new CoverViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CoverViewHolder holder, int position) {
        EpubCoverItem item = coverItems.get(position);
        
        // Load cover image
        if (item.getCoverPath() != null) {
            Glide.with(context)
                    .load(new File(item.getCoverPath()))
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .centerCrop()
                    .into(holder.coverImage);
        } else {
            Glide.with(context)
                    .load(R.drawable.placeholder_cover)
                    .centerCrop()
                    .into(holder.coverImage);
        }
        
        // Set title
        holder.titleText.setText(item.getTitle());
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCoverClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return coverItems.size();
    }

    static class CoverViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImage;
        TextView titleText;

        public CoverViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.imageCover);
            titleText = itemView.findViewById(R.id.textTitle);
        }
    }
}

// ==================== ZoteroApiClient.java ====================
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

// ==================== EpubCoverExtractor.java ====================
package com.example.zoteroepubcovers;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.epub.EpubReader;

public class EpubCoverExtractor {

    private static final String TAG = "EpubCoverExtractor";
    private static final Executor executor = Executors.newCachedThreadPool();
    
    public interface CoverCallback {
        void onCoverExtracted(String coverPath);
        void onError(String errorMessage);
    }
    
    public static void extractCover(String epubFilePath, CoverCallback callback) {
        executor.execute(() -> {
            try {
                // Create a unique file for the cover image
                File epubFile = new File(epubFilePath);
                File cacheDir = epubFile.getParentFile();
                File coverDir = new File(cacheDir, "covers");
                
                if (!coverDir.exists()) {
                    coverDir.mkdirs();
                }
                
                String coverFileName = epubFile.getName().replace(".epub", ".jpg");
                File coverFile = new File(coverDir, coverFileName);
                
                // If cover is already extracted, just return its path
                if (coverFile.exists()) {
                    callback.onCoverExtracted(coverFile.getAbsolutePath());
                    return;
                }
                
                // Read the EPUB file
                EpubReader epubReader = new EpubReader();
                Book book = epubReader.readEpub(new java.io.FileInputStream(epubFilePath));
                
                // Extract the cover image
                if (book.getCoverImage() != null) {
                    InputStream coverData = book.getCoverImage().getInputStream();
                    FileOutputStream output = new FileOutputStream(coverFile);
                    
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    
                    while ((bytesRead = coverData.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    
                    output.close();
                    coverData.close();
                    
                    callback.onCoverExtracted(coverFile.getAbsolutePath());
                } else {
                    callback.onError("No cover image found in EPUB");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error extracting cover", e);
                callback.onError("Failed to extract cover: " + e.getMessage());
            }
        });
    }
}

// ==================== EpubCoverItem.java ====================
package com.example.zoteroepubcovers;

public class EpubCoverItem {
    private final String id;
    private final String title;
    private final String coverPath;
    private final String authors;
    private final String zoteroUsername;
    
    public EpubCoverItem(String id, String title, String coverPath, String authors, String zoteroUsername) {
        this.id = id;
        this.title = title;
        this.coverPath = coverPath;
        this.authors = authors;
        this.zoteroUsername = zoteroUsername;
    }
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getCoverPath() {
        return coverPath;
    }
    
    public String getAuthors() {
        return authors;
    }
    
    public String getZoteroUsername() {
        return zoteroUsername;
    }
}

// ==================== ZoteroItem.java ====================
package com.example.zoteroepubcovers;

import com.google.gson.annotations.SerializedName;

public class ZoteroItem {
    
    @SerializedName("key")
    private String key;
    
    @SerializedName("data")
    private ZoteroItemData data;
    
    @SerializedName("links")
    private ZoteroLinks links;
    
    // Nested class to represent item data
    public static class ZoteroItemData {
        @SerializedName("title")
        private String title;
        
        @SerializedName("creators")
        private ZoteroCreator[] creators;
        
        @SerializedName("contentType")
        private String contentType;
        
        @SerializedName("filename")
        private String filename;
    }
    
    // Nested class to represent creator data
    public static class ZoteroCreator {
        @SerializedName("firstName")
        private String firstName;
        
        @SerializedName("lastName")
        private String lastName;
        
        @SerializedName("creatorType")
        private String creatorType;
    }
    
    // Nested class to represent links
    public static class ZoteroLinks {
        @SerializedName("enclosure")
        private ZoteroLink enclosure;
        
        public ZoteroLink getEnclosure() {
            return enclosure;
        }
    }
    
    // Nested class to represent a link
    public static class ZoteroLink {
        @SerializedName("href")
        private String href;
        
        @SerializedName("type")
        private String type;
        
        public String getHref() {
            return href;
        }
    }
    
    public String getKey() {
        return key;
    }
    
    public String getTitle() {
        return data != null ? data.title : "";
    }
    
    public String getMimeType() {
        return data != null ? data.contentType : "";
    }
    
    public String getFilename() {
        return data != null ? data.filename : "";
    }
    
    public String getAuthors() {
        if (data == null || data.creators == null || data.creators.length == 0) {
            return "Unknown";
        }
        
        StringBuilder authorsBuilder = new StringBuilder();
        for (int i = 0; i < data.creators.length; i++) {
            ZoteroCreator creator = data.creators[i];
            if (creator.creatorType.equals("author")) {
                if (authorsBuilder.length() > 0) {
                    authorsBuilder.append(", ");
                }
                authorsBuilder.append(creator.lastName)
                        .append(", ")
                        .append(creator.firstName);
            }
        }
        
        return authorsBuilder.length() > 0 ? authorsBuilder.toString() : "Unknown";
    }
    
    public ZoteroLinks getLinks() {
        return links;
    }
}

// ==================== UserPreferences.java ====================
package com.example.zoteroepubcovers;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    
    private static final String PREF_NAME = "ZoteroEpubCoversPrefs";
    private static final String KEY_ZOTERO_USER_ID = "zotero_user_id";
    private static final String KEY_ZOTERO_API_KEY = "zotero_api_key";
    private static final String KEY_ZOTERO_USERNAME = "zotero_username";
    
    private final SharedPreferences preferences;
    
    public UserPreferences(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public boolean hasZoteroCredentials() {
        String userId = getZoteroUserId();
        String apiKey = getZoteroApiKey();
        String username = getZoteroUsername();
        
        return userId != null && !userId.isEmpty() 
                && apiKey != null && !apiKey.isEmpty()
                && username != null && !username.isEmpty();
    }
    
    public String getZoteroUserId() {
        return preferences.getString(KEY_ZOTERO_USER_ID, "");
    }
    
    public void setZoteroUserId(String userId) {
        preferences.edit