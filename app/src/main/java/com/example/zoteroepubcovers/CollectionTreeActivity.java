package com.example.zotshelf;

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
import java.util.Collections;
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
    private String currentSelectedKey = "";

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
        currentSelectedKey = userPreferences.getSelectedCollectionKey();

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
        
        Log.d(TAG, "Loading collections for user ID: '" + userId + "'");

        zoteroApiClient.getCollections(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                Log.d(TAG, "Received " + collections.size() + " collections from API");
                
                // Always add "All Collections" even if no other collections exist
                treeItems.clear();
                
                // Add "All Collections" as the first item
                CollectionTreeItem allCollectionsItem = new CollectionTreeItem("", "All Collections", 0, !collections.isEmpty());
                // Check if this is currently selected
                if (currentSelectedKey.isEmpty()) {
                    allCollectionsItem.setSelected(true);
                }
                treeItems.add(allCollectionsItem);
                
                if (collections.isEmpty()) {
                    // Just show "All Collections" and finish
                    runOnUiThread(() -> {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyView.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    buildCollectionTree(collections);
                }
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
        
        // Create a map to hold all collections by key for easy lookup
        Map<String, ZoteroCollection> collectionMap = new HashMap<>();
        for (ZoteroCollection collection : collections) {
            collectionMap.put(collection.getKey(), collection);
        }
        
        // Create a map to store children for each collection
        Map<String, List<String>> childrenMap = new HashMap<>();
        // Initialize root list
        childrenMap.put("root", new ArrayList<>());
        
        // First pass: identify parent-child relationships
        for (ZoteroCollection collection : collections) {
            String key = collection.getKey();
            String parentKey = collection.getParentCollection();
            
            // If no parent, this is a top-level collection
            if (parentKey == null || parentKey.isEmpty()) {
                Log.d(TAG, "Adding root collection: " + collection.getName());
                childrenMap.get("root").add(key);
            } else {
                // Make sure the parent exists in our map
                if (!childrenMap.containsKey(parentKey)) {
                    childrenMap.put(parentKey, new ArrayList<>());
                }
                // Add this collection as a child of its parent
                Log.d(TAG, "Adding child collection: " + collection.getName() + " to parent: " + parentKey);
                childrenMap.get(parentKey).add(key);
            }
        }
        
        // Check if we found any root collections
        if (childrenMap.get("root").isEmpty()) {
            Log.w(TAG, "No root-level collections found. This might be an API issue.");
            
            // Try to recover by adding all collections that don't have a valid parent
            for (ZoteroCollection collection : collections) {
                String key = collection.getKey();
                String parentKey = collection.getParentCollection();
                
                // If parent doesn't exist in our collection map, add to root
                if (parentKey != null && !parentKey.isEmpty() && !collectionMap.containsKey(parentKey)) {
                    Log.d(TAG, "Parent not found for collection: " + collection.getName() + ", adding to root");
                    childrenMap.get("root").add(key);
                }
            }
            
            // If we still have no root collections, just add all as root
            if (childrenMap.get("root").isEmpty()) {
                Log.d(TAG, "Still no root collections, adding all collections to root level");
                for (ZoteroCollection collection : collections) {
                    childrenMap.get("root").add(collection.getKey());
                }
            }
        }
        
        // Sort the root level collections alphabetically
        if (!childrenMap.get("root").isEmpty()) {
            sortCollectionKeys(childrenMap.get("root"), collectionMap);
            
            // Now recursively build the tree
            for (String rootKey : childrenMap.get("root")) {
                addCollectionToTree(rootKey, 1, childrenMap, collectionMap);
            }
        }
        
        Log.d(TAG, "Final tree size: " + treeItems.size() + " items");

        runOnUiThread(() -> {
            if (treeItems.size() <= 1) {
                // We have only "All Collections", no actual Zotero collections
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                
                adapter.notifyDataSetChanged();
                
                // Scroll to the selected item if any
                scrollToSelectedItem();
            }
        });
    }
    
    private void sortCollectionKeys(List<String> keys, Map<String, ZoteroCollection> collectionMap) {
        try {
            Collections.sort(keys, (key1, key2) -> {
                String name1 = collectionMap.get(key1) != null ? collectionMap.get(key1).getName() : "";
                String name2 = collectionMap.get(key2) != null ? collectionMap.get(key2).getName() : "";
                return name1.compareToIgnoreCase(name2);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sorting collections", e);
        }
    }

    private void addCollectionToTree(String collectionKey, int level, 
                                    Map<String, List<String>> childrenMap,
                                    Map<String, ZoteroCollection> collectionMap) {
        // Get the collection object
        ZoteroCollection collection = collectionMap.get(collectionKey);
        if (collection == null) {
            Log.e(TAG, "Collection not found for key: " + collectionKey);
            return;
        }
        
        // Check if this collection has children
        boolean hasChildren = childrenMap.containsKey(collectionKey) && 
                             !childrenMap.get(collectionKey).isEmpty();
        
        // Create the tree item
        CollectionTreeItem item = new CollectionTreeItem(
                collectionKey,
                collection.getName(),
                level,
                hasChildren);
        
        // Set selected state if this is the current selection
        if (collectionKey.equals(currentSelectedKey)) {
            item.setSelected(true);
        }
        
        treeItems.add(item);
        
        // If this collection has children, add them recursively
        if (hasChildren) {
            List<String> childKeys = childrenMap.get(collectionKey);
            // Sort children alphabetically
            sortCollectionKeys(childKeys, collectionMap);
            
            for (String childKey : childKeys) {
                addCollectionToTree(childKey, level + 1, childrenMap, collectionMap);
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