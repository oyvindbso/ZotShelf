package com.example.zoteroepubcovers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.zoteroepubcovers.database.EpubCoverRepository;
import com.example.zoteroepubcovers.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CoverGridAdapter.CoverClickListener {

    private RecyclerView recyclerView;
    private static final int REQUEST_CODE_SELECT_COLLECTION = 1001;
    private CoverGridAdapter adapter;
    private List<EpubCoverItem> coverItems = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ZoteroApiClient zoteroApiClient;
    private UserPreferences userPreferences;
    private EpubCoverRepository coverRepository;
    private boolean isOfflineMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userPreferences = new UserPreferences(this);
        coverRepository = new EpubCoverRepository(this);
        
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);
        recyclerView = findViewById(R.id.recyclerViewCovers);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Setup RecyclerView with Grid Layout
        int spanCount = calculateSpanCount();
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
        int displayMode = userPreferences.getDisplayMode();
        adapter = new CoverGridAdapter(this, coverItems, this, displayMode);

        recyclerView.setAdapter(adapter);

        // Initialize Zotero API client
        zoteroApiClient = new ZoteroApiClient(this);

        // Setup refresh listener
        swipeRefreshLayout.setOnRefreshListener(this::refreshCovers);
        
        updateTitle();

        // Check if we have Zotero credentials, if not show settings first
        if (!userPreferences.hasZoteroCredentials()) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            loadCovers();
        }
        
        // Handle widget click intent
        if (getIntent().hasExtra("fromWidget")) {
            handleWidgetClick(getIntent());
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("fromWidget")) {
            handleWidgetClick(intent);
        }
    }
    
    private void handleWidgetClick(Intent intent) {
        if (intent.hasExtra("itemId") && intent.hasExtra("username")) {
            String itemId = intent.getStringExtra("itemId");
            String username = intent.getStringExtra("username");
            
            // Open the Zotero web library
            String url = "https://www.zotero.org/" + username + "/items/" + itemId+"/reader";
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    private int calculateSpanCount() {
        // Calculate number of columns based on screen width
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int itemWidthDp = 120; // Target width for each grid item
        return Math.max(2, screenWidthDp / itemWidthDp);
    }

    private void loadCovers() {
        if (!userPreferences.hasZoteroCredentials()) {
            showEmptyState("Please enter your Zotero credentials in settings");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading();
        
        // First check if we have cached covers
        coverRepository.hasCachedCovers(hasCovers -> {
            // If we have covers and we're offline, load from cache
            if (hasCovers && !NetworkUtils.isNetworkAvailable(this)) {
                isOfflineMode = true;
                loadCachedCovers();
                return;
            }
            
            // If we have network, try to load from API
            if (NetworkUtils.isNetworkAvailable(this)) {
                isOfflineMode = false;
                loadCoversFromApi();
            } else {
                // No network, check if we have cached data
                if (hasCovers) {
                    isOfflineMode = true;
                    loadCachedCovers();
                } else {
                    // No cache and no network
                    runOnUiThread(() -> {
                        showEmptyState("No internet connection and no cached data");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }
    
    private void loadCachedCovers() {
        coverRepository.getLocalCovers(new EpubCoverRepository.CoverRepositoryCallback() {
            @Override
            public void onCoversLoaded(List<EpubCoverItem> covers) {
                runOnUiThread(() -> {
                    if (covers.isEmpty()) {
                        showEmptyState("No cached covers found");
                    } else {
                        updateUI(covers);
                        if (isOfflineMode) {
                            Toast.makeText(MainActivity.this, "Offline mode - showing cached covers", Toast.LENGTH_SHORT).show();
                        }
                    }
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showEmptyState("Error loading cached covers: " + message);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }
    
    private void refreshCovers() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection. Showing cached data.", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        
        loadCoversFromApi();
    }
    
    // Update the loadCoversFromApi method in MainActivity.java

    private void loadCoversFromApi() {
        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();
        String collectionKey = userPreferences.getSelectedCollectionKey();

        // Use the new method that fetches parent metadata
        zoteroApiClient.getEpubItemsWithMetadata(userId, apiKey, collectionKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> zoteroItems) {
                processZoteroItems(zoteroItems);
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    // If API fails but we have cached data, show that
                    coverRepository.hasCachedCovers(hasCovers -> {
                        if (hasCovers) {
                            loadCachedCovers();
                            Toast.makeText(MainActivity.this, 
                                    "Failed to update from Zotero: " + errorMessage, 
                                    Toast.LENGTH_LONG).show();
                        } else {
                            showEmptyState("Error: " + errorMessage);
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    });
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
                                // Save the covers to local database
                                coverRepository.saveCovers(newCoverItems);
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
                                // Save the covers to local database
                                coverRepository.saveCovers(newCoverItems);
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
                        // Save the covers to local database
                        coverRepository.saveCovers(newCoverItems);
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
        
        // Re-create the adapter with the current display mode
        int displayMode = userPreferences.getDisplayMode();
        adapter = new CoverGridAdapter(this, coverItems, this, displayMode);
        recyclerView.setAdapter(adapter);
        
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
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_refresh:
                if (!NetworkUtils.isNetworkAvailable(this)) {
                    Toast.makeText(this, "No internet connection available", Toast.LENGTH_SHORT).show();
                } else {
                    refreshCovers();
                }
                return true;
            case R.id.action_info:
                showInfoDialog();
                return true;
            case R.id.action_select_collection:
                showCollectionSelector();
                return true;
            case R.id.action_change_display:
                showDisplayModeDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About Zotero EPUB Covers");
        
        // Use a custom layout with scrolling for the dialog
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_info, null);
        builder.setView(dialogView);
        
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload if settings might have changed
        if (userPreferences.hasZoteroCredentials() && coverItems.isEmpty()) {
            loadCovers();
        }
    }

    
    private void updateTitle() {
        if (getSupportActionBar() != null) {
        String collectionName = userPreferences.getSelectedCollectionName();
        if (collectionName == null || collectionName.isEmpty()) {
            collectionName = "All Collections";
        }
        getSupportActionBar().setSubtitle(collectionName + 
                (isOfflineMode ? " (Offline)" : ""));
    }
}

    @Override
    public void onCoverClick(EpubCoverItem item) {
        // Open the Zotero web library when a cover is clicked
        String zoteroUsername = userPreferences.getZoteroUsername();
        if (zoteroUsername == null || zoteroUsername.isEmpty()) {
            return;
        }
        
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "Cannot open reader - no internet connection", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String url = "https://www.zotero.org/" + zoteroUsername + "/items/" + item.getId()+"/reader";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void showCollectionSelector() {
    if (!userPreferences.hasZoteroCredentials()) {
        Toast.makeText(this, R.string.enter_credentials, Toast.LENGTH_SHORT).show();
        return;
    }
    
    if (!NetworkUtils.isNetworkAvailable(this)) {
        Toast.makeText(this, "No internet connection. Cannot fetch collections.", Toast.LENGTH_LONG).show();
        return;
    }
    
    // Launch the collection tree activity
    Intent intent = new Intent(this, CollectionTreeActivity.class);
    startActivityForResult(intent, REQUEST_CODE_SELECT_COLLECTION);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == REQUEST_CODE_SELECT_COLLECTION && resultCode == RESULT_OK) {
        // Collection was selected, update the UI
        updateTitle();
        
        // Clear the cache when switching collections
        coverRepository.clearCovers();
        
        // Reload covers with the new collection
        if (NetworkUtils.isNetworkAvailable(this)) {
            loadCoversFromApi();
        } else {
            Toast.makeText(this, "No internet connection. Cannot load new collection.", Toast.LENGTH_LONG).show();
        }
    }
}

private void showDisplayModeDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Display Mode");
    
    String[] options = {"Title only", "Author only", "Author - Title"};
    int currentMode = userPreferences.getDisplayMode();
    
    builder.setSingleChoiceItems(options, currentMode, (dialog, which) -> {
        userPreferences.setDisplayMode(which);
        dialog.dismiss();
        
        // Refresh the adapter to show the new display mode
        adapter = new CoverGridAdapter(this, coverItems, this, which);
        recyclerView.setAdapter(adapter);
    });
    
    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
    
    AlertDialog dialog = builder.create();
    dialog.show();
}

}
