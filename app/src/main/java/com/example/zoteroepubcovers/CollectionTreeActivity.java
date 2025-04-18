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
import java.util.Collections;
import java.util.Comparator;
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
        
        Log.d(TAG, "Loading collections for user ID: " + userId);

        zoteroApiClient.getCollections(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                Log.d(TAG, "Received " + collections.size() + " collections from API");
                
                // Log all collections for debugging
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
        
        // Add "All Collections" as the first item
        CollectionTreeItem allCollectionsItem = new CollectionTreeItem("", "All Collections", 0, true);
        // Check if this is currently selected
        if (currentSelectedKey.isEmpty()) {
            allCollectionsItem.setSelected(true);
        }
        treeItems.add(allCollectionsItem);
        
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
                childrenMap.get("root").add(key);
            } else {
                // Make sure the parent exists in our map
                if (!childrenMap.containsKey(parentKey)) {
                    childrenMap.put(parentKey, new ArrayList<>());
                }
                // Add this collection as a child of its parent
                childrenMap.get(parentKey).add(key);
            }
        }
        
        // Sort the root level collections alphabetically
        sortCollectionKeys(childrenMap.get("root"), collectionMap);
        
        // Now recursively build the tree
        for (String rootKey : childrenMap.get("root")) {
            addCollectionToTree(rootKey, 1, childrenMap, collectionMap);
        }
        
        Log.d(TAG, "Final tree size: " + treeItems.size() + " items");

        runOnUiThread(() -> {
            if (treeItems.size() <= 1) {
                showEmptyState(getString(R.string.no_collections_found));
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
        Collections.sort(keys, (key1, key2) -> {
            String name1 = collectionMap.get(key1).getName();
            String name2 = collectionMap.get(key2).getName();
            return name1.compareToIgnoreCase(name2);
        });
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