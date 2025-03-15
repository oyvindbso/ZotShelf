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
