package oyvindbs.zotshelf;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.Toast;

import java.util.Collections;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import oyvindbs.zotshelf.database.EpubCoverRepository;
import oyvindbs.zotshelf.utils.NetworkUtils;

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
    
    // Check if user has enabled any file types
    if (!userPreferences.hasAnyFileTypeEnabled()) {
        showEmptyState("Please enable at least one file type (EPUB or PDF) in settings");
        swipeRefreshLayout.setRefreshing(false);
        return;
    }

    showLoading();
    
    // Always try to load from cache first for instant display
    coverRepository.getFilteredCovers(new EpubCoverRepository.CoverRepositoryCallback() {
        @Override
        public void onCoversLoaded(List<EpubCoverItem> cachedCovers) {
            runOnUiThread(() -> {
                if (!cachedCovers.isEmpty()) {
                    // Show cached data immediately
                    updateUI(cachedCovers);
                    
                    // Then try to update from network if available
                    if (NetworkUtils.isNetworkAvailable(MainActivity.this)) {
                        isOfflineMode = false;
                        // Load from API in background to update cache
                        loadCoversFromApiInBackground();
                    } else {
                        isOfflineMode = true;
                        Toast.makeText(MainActivity.this, "Offline mode - showing cached covers", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                        updateTitle();
                    }
                } else {
                    // No cached data, try network
                    if (NetworkUtils.isNetworkAvailable(MainActivity.this)) {
                        isOfflineMode = false;
                        loadCoversFromApi();
                    } else {
                        isOfflineMode = true;
                        showEmptyState("No internet connection and no cached data available");
                        swipeRefreshLayout.setRefreshing(false);
                        updateTitle();
                    }
                }
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                Log.e("MainActivity", "Error loading cached covers: " + message);
                // If cache fails and we have network, try API
                if (NetworkUtils.isNetworkAvailable(MainActivity.this)) {
                    loadCoversFromApi();
                } else {
                    showEmptyState("No cached data available and no internet connection");
                    swipeRefreshLayout.setRefreshing(false);
                }
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

private void loadCoversFromApi() {
    String userId = userPreferences.getZoteroUserId();
    String apiKey = userPreferences.getZoteroApiKey();
    String collectionKey = userPreferences.getSelectedCollectionKey();

    Log.d("MainActivity", "Loading covers from API - Collection: " + collectionKey);

    // Use the new paginated method that fetches ALL items
    zoteroApiClient.getAllEbookItemsWithMetadata(userId, apiKey, collectionKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> zoteroItems) {
            Log.d("MainActivity", "Received " + zoteroItems.size() + " items from API");
            processZoteroItems(zoteroItems);
        }

        @Override
        public void onError(String errorMessage) {
            Log.e("MainActivity", "Error loading covers: " + errorMessage);
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

private void loadCoversFromApiInBackground() {
    // This method updates the cache in background while showing cached data
    String userId = userPreferences.getZoteroUserId();
    String apiKey = userPreferences.getZoteroApiKey();
    String collectionKey = userPreferences.getSelectedCollectionKey();

    zoteroApiClient.getAllEbookItemsWithMetadata(userId, apiKey, collectionKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> zoteroItems) {
            Log.d("MainActivity", "Background update: Received " + zoteroItems.size() + " items from API");
            
            // Process items and update cache, but don't necessarily update UI
            processZoteroItemsForCache(zoteroItems);
            
            runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Library updated from Zotero", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onError(String errorMessage) {
            Log.e("MainActivity", "Background update error: " + errorMessage);
            runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                // Don't show error toast for background updates unless it's critical
            });
        }
    });
}

private void processZoteroItemsForCache(List<ZoteroItem> zoteroItems) {
    if (zoteroItems.isEmpty()) {
        return;
    }

    // Process items and save to cache without necessarily updating UI
    for (ZoteroItem item : zoteroItems) {
        zoteroApiClient.downloadEbook(item, new ZoteroApiClient.FileCallback() {
            @Override
            public void onFileDownloaded(ZoteroItem item, String filePath) {
                // Extract cover and save to database
                CoverExtractor.extractCover(filePath, new CoverExtractor.CoverCallback() {
                    @Override
                    public void onCoverExtracted(String coverPath) {
                        EpubCoverItem coverItem = new EpubCoverItem(
                                item.getKey(),
                                item.getTitle(),
                                coverPath,
                                item.getAuthors(),
                                userPreferences.getZoteroUsername(),
                                item.getYear()  // ADD THIS
                        );

                    @Override
                    public void onError(String errorMessage) {
                        EpubCoverItem coverItem = new EpubCoverItem(
                                item.getKey(),
                                item.getTitle(),
                                null,
                                item.getAuthors(),
                                userPreferences.getZoteroUsername(),
                                item.getYear()  // ADD THIS
                        );
                });
            }
            
            @Override
            public void onError(ZoteroItem item, String errorMessage) {
                // Save item metadata even if download failed
                coverRepository.saveCoverFromZoteroItem(item, null);
            }
        });
    }
}

private void loadCachedCovers() {
    coverRepository.getFilteredCovers(new EpubCoverRepository.CoverRepositoryCallback() {
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

private void processZoteroItems(List<ZoteroItem> zoteroItems) {
    if (zoteroItems.isEmpty()) {
        runOnUiThread(() -> {
            showEmptyState("No EPUB or PDF files found matching your filters");
            swipeRefreshLayout.setRefreshing(false);
        });
        return;
    }

    Log.d("MainActivity", "Processing " + zoteroItems.size() + " Zotero items");

    List<EpubCoverItem> newCoverItems = new ArrayList<>();
    final int totalItems = zoteroItems.size();
    final int[] processedCount = {0};
    
    // Collections for batch database save
    final List<ZoteroItem> itemsToSave = Collections.synchronizedList(new ArrayList<>());
    final List<String> coverPathsToSave = Collections.synchronizedList(new ArrayList<>());
    
    for (ZoteroItem item : zoteroItems) {
        zoteroApiClient.downloadEbook(item, new ZoteroApiClient.FileCallback() {
            @Override
            public void onFileDownloaded(ZoteroItem item, String filePath) {
                CoverExtractor.extractCover(filePath, new CoverExtractor.CoverCallback() {
                    @Override
                    public void onCoverExtracted(String coverPath) {
                        EpubCoverItem coverItem = new EpubCoverItem(
                                item.getKey(),
                                item.getTitle(),
                                coverPath,
                                item.getAuthors(),
                                userPreferences.getZoteroUsername()
                                item.getYear()
                        );
                        
                        synchronized (newCoverItems) {
                            newCoverItems.add(coverItem);
                            
                            // Add to batch save lists
                            itemsToSave.add(item);
                            coverPathsToSave.add(coverPath);
                            
                            processedCount[0]++;
                            
                            // Save to database immediately for each item (ensures persistence)
                            new Thread(() -> {
                                coverRepository.saveCoverFromZoteroItemSync(item, coverPath);
                            }).start();
                            
                            if (processedCount[0] == totalItems) {
                                updateUI(newCoverItems);
                            }
                        }
                    }

                    @Override
                    public void onError(ZoteroItem item, String errorMessage) {
                        EpubCoverItem coverItem = new EpubCoverItem(
                                item.getKey(),
                                item.getTitle() + " (Download failed)",
                                null,
                                item.getAuthors(),
                                userPreferences.getZoteroUsername(),
                                item.getYear()  
                        );
                    }
                        
                        synchronized (newCoverItems) {
                            newCoverItems.add(coverItem);
                            
                            // Save without cover
                            itemsToSave.add(item);
                            coverPathsToSave.add(null);
                            
                            processedCount[0]++;
                            
                            new Thread(() -> {
                                coverRepository.saveCoverFromZoteroItemSync(item, null);
                            }).start();
                            
                            if (processedCount[0] == totalItems) {
                                updateUI(newCoverItems);
                            }
                        }
                    }
                });
            }
            
            @Override
            public void onError(ZoteroItem item, String errorMessage) {
                EpubCoverItem coverItem = new EpubCoverItem(
                        item.getKey(),
                        item.getTitle() + " (Download failed)",
                        null,
                        item.getAuthors(),
                        userPreferences.getZoteroUsername()
                );
                
                synchronized (newCoverItems) {
                    newCoverItems.add(coverItem);
                    
                    itemsToSave.add(item);
                    coverPathsToSave.add(null);
                    
                    processedCount[0]++;
                    
                    new Thread(() -> {
                        coverRepository.saveCoverFromZoteroItemSync(item, null);
                    }).start();
                    
                    if (processedCount[0] == totalItems) {
                        updateUI(newCoverItems);
                    }
                }
            }
        });
    }
}


private void updateUI(final List<EpubCoverItem> newItems) {
    runOnUiThread(() -> {
        coverItems.clear();
        coverItems.addAll(newItems);
        
        // Apply current sort mode
        int sortMode = userPreferences.getSortMode();
        CoverSorter.sortCovers(coverItems, sortMode);
        
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
    
    // Set the checkable state for file type toggles
    MenuItem epubToggle = menu.findItem(R.id.action_toggle_epubs);
    MenuItem pdfToggle = menu.findItem(R.id.action_toggle_pdfs);
    
    if (epubToggle != null) {
        epubToggle.setChecked(userPreferences.getShowEpubs());
    }
    if (pdfToggle != null) {
        pdfToggle.setChecked(userPreferences.getShowPdfs());
    }
    
    return true;
}

@Override
public boolean onPrepareOptionsMenu(Menu menu) {
    // Update the checkable state every time the menu is shown
    MenuItem epubToggle = menu.findItem(R.id.action_toggle_epubs);
    MenuItem pdfToggle = menu.findItem(R.id.action_toggle_pdfs);
    
    if (epubToggle != null) {
        epubToggle.setChecked(userPreferences.getShowEpubs());
    }
    if (pdfToggle != null) {
        pdfToggle.setChecked(userPreferences.getShowPdfs());
    }
    
    return super.onPrepareOptionsMenu(menu);
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
        case R.id.action_sort:  // <-- ADD THIS CASE
            showSortModeDialog();
            return true;
        case R.id.action_toggle_epubs:
            toggleEpubsEnabled(item);
            return true;
        case R.id.action_toggle_pdfs:
            togglePdfsEnabled(item);
            return true;
        default:
            return super.onOptionsItemSelected(item);
    }
}

private void toggleEpubsEnabled(MenuItem item) {
    boolean currentState = userPreferences.getShowEpubs();
    boolean newState = !currentState;
    
    // Don't allow disabling if it's the only enabled type
    if (!newState && !userPreferences.getShowPdfs()) {
        Toast.makeText(this, "At least one file type must be enabled", Toast.LENGTH_SHORT).show();
        return;
    }
    
    userPreferences.setShowEpubs(newState);
    item.setChecked(newState);
    
    // Clear cache and reload
    coverRepository.clearCovers();
    Toast.makeText(this, newState ? "EPUBs enabled" : "EPUBs disabled", Toast.LENGTH_SHORT).show();
    
    if (NetworkUtils.isNetworkAvailable(this)) {
        loadCoversFromApi();
    } else {
        loadCovers(); // This will handle offline mode appropriately
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

private void togglePdfsEnabled(MenuItem item) {
    boolean currentState = userPreferences.getShowPdfs();
    boolean newState = !currentState;
    
    // Don't allow disabling if it's the only enabled type
    if (!newState && !userPreferences.getShowEpubs()) {
        Toast.makeText(this, "At least one file type must be enabled", Toast.LENGTH_SHORT).show();
        return;
    }
    
    userPreferences.setShowPdfs(newState);
    item.setChecked(newState);
    
    // Clear cache and reload
    coverRepository.clearCovers();
    Toast.makeText(this, newState ? "PDFs enabled" : "PDFs disabled", Toast.LENGTH_SHORT).show();
    
    if (NetworkUtils.isNetworkAvailable(this)) {
        loadCoversFromApi();
    } else {
        loadCovers(); // This will handle offline mode appropriately
    }
}

@Override
protected void onResume() {
    super.onResume();
    
    // Check if we need to reload due to settings changes
    boolean needsReload = false;
    
    // If credentials are available but we have no covers, reload
    if (userPreferences.hasZoteroCredentials() && coverItems.isEmpty()) {
        needsReload = true;
    }
    
    // If file type preferences changed, we should reload
    // (This is a simple approach - you could also store the previous preferences and compare)
    if (!coverItems.isEmpty() && userPreferences.hasZoteroCredentials()) {
        // Clear cache when returning from settings to ensure file type changes take effect
        coverRepository.clearCovers();
        needsReload = true;
    }
    
    if (needsReload) {
        loadCovers();
    }
    
    // Always update the title in case collection selection changed
    updateTitle();
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
    
private void showSortModeDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.sort_mode_title);
    
    String[] options = {
        getString(R.string.sort_by_title), 
        getString(R.string.sort_by_author)
        getString(R.string.sort_by_year) 
    };
    int currentMode = userPreferences.getSortMode();
    
    builder.setSingleChoiceItems(options, currentMode, (dialog, which) -> {
        userPreferences.setSortMode(which);
        dialog.dismiss();
        
        // Re-sort and refresh the display
        CoverSorter.sortCovers(coverItems, which);
        adapter.notifyDataSetChanged();
        
        String message = which == UserPreferences.SORT_BY_AUTHOR ? 
            "Sorted by author" : "Sorted by title";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    });
    
    builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
    
    AlertDialog dialog = builder.create();
    dialog.show();
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
