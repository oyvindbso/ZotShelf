package com.example.zoteroepubcovers;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.zoteroepubcovers.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionTreeActivity extends AppCompatActivity implements CollectionTreeAdapter.CollectionClickListener {

    private static final String TAG = "CollectionTreeActivity";
    private RecyclerView recyclerView;
    private CollectionTreeAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private ZoteroApiClient zoteroApiClient;
    private UserPreferences userPreferences;
    private List<CollectionTreeItem> treeItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection_tree);

        // Enable the back button in the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_select_collection);
        }

        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewCollections);
        progressBar = findViewById(R.id.progressBarCollections);
        emptyView = findViewById(R.id.emptyViewCollections);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CollectionTreeAdapter(this, treeItems, this);
        recyclerView.setAdapter(adapter);

        // Initialize API client and preferences
        zoteroApiClient = new ZoteroApiClient(this);
        userPreferences = new UserPreferences(this);

        // Load collections
        loadCollections();
    }

    private void loadCollections() {
        if (!userPreferences.hasZoteroCredentials()) {
            showEmptyState(getString(R.string.enter_credentials));
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection. Cannot fetch collections.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        showLoading();

        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();
        
        Log.d(TAG, "Loading collections for user ID: " + userId);

        zoteroApiClient.getCollections(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                Log.d(TAG, "Received " + collections.size() + " collections from API");
                
                // Log each collection for debugging
                for (ZoteroCollection collection : collections) {
                    Log.d(TAG, "Collection: " + collection.getName() + ", Key: " + collection.getKey() + 
                          ", Parent: " + collection.getParentCollection());
                }
                
                buildCollectionTree(collections);
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error loading collections: " + errorMessage);
                runOnUiThread(() -> {
                    showEmptyState("Error loading collections: " + errorMessage);
                    Toast.makeText(CollectionTreeActivity.this, 
                            "Error loading collections: " + errorMessage, 
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

private void buildCollectionTree(List<ZoteroCollection> collections) {
    Log.d(TAG, "Building tree with " + collections.size() + " collections");
    
    if (collections.isEmpty()) {
        runOnUiThread(() -> {
            showEmptyState(getString(R.string.no_collections_found));
        });
        return;
    }
    
    // Clear existing tree items
    treeItems.clear();
    
    // Add "All Collections" as the first item (always selected by default if nothing else is)
    CollectionTreeItem allCollectionsItem = new CollectionTreeItem("", "All Collections", 0, true);
    // Check if this is currently selected
    if (userPreferences.getSelectedCollectionKey().isEmpty()) {
        allCollectionsItem.setSelected(true);
    }
    treeItems.add(allCollectionsItem);
    Log.d(TAG, "Added 'All Collections' item");
    
    // Create a map of all collections for easy lookup
    Map<String, ZoteroCollection> collectionMap = new HashMap<>();
    for (ZoteroCollection collection : collections) {
        collectionMap.put(collection.getKey(), collection);
    }

    // Create a map to store parent-child relationships
    Map<String, List<ZoteroCollection>> childrenMap = new HashMap<>();
    
    // Group collections by parent
    for (ZoteroCollection collection : collections) {
        String parentKey = collection.getParentCollection();
        
        // If parentKey is null or empty, this is a top-level collection
        if (parentKey == null || parentKey.isEmpty()) {
            if (!childrenMap.containsKey("root")) {
                childrenMap.put("root", new ArrayList<>());
            }
            childrenMap.get("root").add(collection);
            Log.d(TAG, "Added top-level collection: " + collection.getName());
        } else {
            if (!childrenMap.containsKey(parentKey)) {
                childrenMap.put(parentKey, new ArrayList<>());
            }
            childrenMap.get(parentKey).add(collection);
            Log.d(TAG, "Added child collection: " + collection.getName() + " to parent: " + parentKey);
        }
    }
    
    // Check if we have any top-level collections
    if (!childrenMap.containsKey("root") || childrenMap.get("root").isEmpty()) {
        Log.w(TAG, "No top-level collections found! Showing all collections at root level");
        
        // If no parent-child relationships, show all collections at root level
        childrenMap.put("root", new ArrayList<>(collections));
    }
    
    // Build the tree recursively starting with root collections
    if (childrenMap.containsKey("root")) {
        // Sort root collections alphabetically
        List<ZoteroCollection> rootCollections = childrenMap.get("root");
        rootCollections.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
        
        for (ZoteroCollection rootCollection : rootCollections) {
            addCollectionToTree(rootCollection, childrenMap, collectionMap, 1, userPreferences.getSelectedCollectionKey());
        }
    }
    
    Log.d(TAG, "Final tree size: " + treeItems.size() + " items");

    runOnUiThread(() -> {
        if (treeItems.size() <= 1) {
            Log.d(TAG, "No collections found after building tree");
            showEmptyState(getString(R.string.no_collections_found));
        } else {
            // Make sure RecyclerView is visible and emptyView is hidden
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            
            // Notify the adapter that data has changed
            adapter.notifyDataSetChanged();
            
            // Scroll to the selected item if any
            scrollToSelectedItem();
        }
    });
}


private void addCollectionToTree(ZoteroCollection collection, 
                                Map<String, List<ZoteroCollection>> childrenMap,
                                Map<String, ZoteroCollection> collectionMap,
                                int level, String selectedCollectionKey) {
    String collectionKey = collection.getKey();
    boolean hasChildren = childrenMap.containsKey(collectionKey) && !childrenMap.get(collectionKey).isEmpty();
    
    // Create and add the collection item
    CollectionTreeItem item = new CollectionTreeItem(
            collectionKey,
            collection.getName(),
            level,
            hasChildren);
    
    // Check if this is the selected collection
    if (collectionKey.equals(selectedCollectionKey)) {
        item.setSelected(true);
    }
    
    treeItems.add(item);
    Log.d(TAG, "Added collection to tree: " + collection.getName() + " at level " + level);
    
    // Add children if this collection has any
    if (hasChildren) {
        List<ZoteroCollection> children = childrenMap.get(collectionKey);
        
        // Sort children alphabetically
        children.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
        
        for (ZoteroCollection child : children) {
            // Recursively add this child and its children
            addCollectionToTree(child, childrenMap, collectionMap, level + 1, selectedCollectionKey);
        }
    }
}

    private void scrollToSelectedItem() {
        for (int i = 0; i < treeItems.size(); i++) {
            if (treeItems.get(i).isSelected()) {
                recyclerView.scrollToPosition(i);
                break;
            }
        }
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showEmptyState(String message) {
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setText(message);
    }

    @Override
    public void onCollectionClick(CollectionTreeItem item) {
        // Save the selected collection
        userPreferences.setSelectedCollectionKey(item.getId());
        userPreferences.setSelectedCollectionName(item.getName());
        
        Log.d(TAG, "Selected collection: " + item.getName() + " with key: " + item.getId());
        
        // Return to MainActivity
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}