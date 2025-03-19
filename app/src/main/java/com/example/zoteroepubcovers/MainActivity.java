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
import android.widget.Toast;  // Add this import statement

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
        
        updateTitle();

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
        String collectionKey = userPreferences.getSelectedCollectionKey();
    
        zoteroApiClient.getEpubItemsByCollection(userId, apiKey, collectionKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
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
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_refresh:
                loadEpubs();
                return true;
            case R.id.action_info:
                showInfoDialog();
                return true;
            case R.id.action_select_collection:
                showCollectionSelector();
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
            loadEpubs();
        }
    }

    private void showCollectionDialog(List<ZoteroCollection> collections) {
        // Create items array with "All Collections" as first option
        List<String> collectionNames = new ArrayList<>();
        List<String> collectionKeys = new ArrayList<>();
        
        collectionNames.add("All Collections");
        collectionKeys.add("");
        
        for (ZoteroCollection collection : collections) {
            collectionNames.add(collection.getName());
            collectionKeys.add(collection.getKey());
        }
        
        // Find the currently selected index
                String currentCollectionKey = userPreferences.getSelectedCollectionKey();
        int selectedIndex = 0;
        for (int i = 0; i < collectionKeys.size(); i++) {
            if (collectionKeys.get(i).equals(currentCollectionKey)) {
                selectedIndex = i;
                break;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Collection");
        builder.setSingleChoiceItems(
                collectionNames.toArray(new String[0]), 
                selectedIndex, 
                (dialog, which) -> {
                    userPreferences.setSelectedCollectionKey(collectionKeys.get(which));
                    userPreferences.setSelectedCollectionName(collectionNames.get(which));
                    updateTitle();
                    loadEpubs();
                    dialog.dismiss();
                });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateTitle() {
        getSupportActionBar().setSubtitle(userPreferences.getSelectedCollectionName());
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

    private void showCollectionSelector() {
        if (!userPreferences.hasZoteroCredentials()) {
            Toast.makeText(this, R.string.enter_credentials, Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        
        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();
        
        zoteroApiClient.getCollections(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showCollectionDialog(collections);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Error loading collections: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}