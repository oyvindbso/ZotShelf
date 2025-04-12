package com.example.zoteroepubcovers;

import android.content.Intent;
import android.os.Bundle;
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

        zoteroApiClient.getCollections(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
        // In the onSuccess method of loadCollections()
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                Log.d("CollectionTree", "Received " + collections.size() + " collections from API");
                for (ZoteroCollection collection : collections) {
                    Log.d("CollectionTree", "Collection: " + collection.getName() + ", Key: " + collection.getKey() + ", Parent: " + collection.getParentCollection());
                }
                buildCollectionTree(collections);
            }
            @Override
            public void onError(String errorMessage) {
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
    // Log the number of collections
    Log.d("CollectionTree", "Building tree with " + collections.size() + " collections");
    
    // Create a map of all collections for easy lookup
    Map<String, ZoteroCollection> collectionMap = new HashMap<>();
    for (ZoteroCollection collection : collections) {
        collectionMap.put(collection.getKey(), collection);
    }

    // Create a map to store parent-child relationships
    Map<String, List<ZoteroCollection>> childrenMap = new HashMap<>();
    
    // Initialize with "root" as key for top-level collections
    childrenMap.put("root", new ArrayList<>());

    // Organize collections into parent-child relationships
    for (ZoteroCollection collection : collections) {
        String parentKey = collection.getParentCollection();
        
        // If no parent or empty parent, it's a top-level collection
        if (parentKey == null || parentKey.isEmpty()) {
            childrenMap.get("root").add(collection);
            Log.d("CollectionTree", "Added top-level collection: " + collection.getName());
        } else {
            // Add to parent's children list
            if (!childrenMap.containsKey(parentKey)) {
                childrenMap.put(parentKey, new ArrayList<>());
            }
            childrenMap.get(parentKey).add(collection);
            Log.d("CollectionTree", "Added child collection: " + collection.getName() + " to parent: " + parentKey);
        }
    }

    // Clear existing tree items
    treeItems.clear();
    
    // Add "All Collections" as the first item
    treeItems.add(new CollectionTreeItem("", "All Collections", 0, true));
    Log.d("CollectionTree", "Added 'All Collections' item");
    
    // Build the tree recursively, starting with root collections
    buildTreeItems("root", childrenMap, 0);
    Log.d("CollectionTree", "Final tree size: " + treeItems.size() + " items");

    runOnUiThread(() -> {
        if (treeItems.size() <= 1) {
            Log.d("CollectionTree", "No collections found after building tree");
            showEmptyState("No collections found");
        } else {
            hideEmptyState();
            adapter.notifyDataSetChanged();
            
            // Find and highlight the currently selected collection
            String selectedKey = userPreferences.getSelectedCollectionKey();
            for (int i = 0; i < treeItems.size(); i++) {
                if (treeItems.get(i).getId().equals(selectedKey)) {
                    treeItems.get(i).setSelected(true);
                    adapter.notifyItemChanged(i);
                    recyclerView.scrollToPosition(i);
                    break;
                }
            }
        }
        progressBar.setVisibility(View.GONE);
    });
}

    private void buildTreeItems(String parentKey, Map<String, List<ZoteroCollection>> childrenMap, int level) {
        if (!childrenMap.containsKey(parentKey)) {
            return;
        }

        List<ZoteroCollection> children = childrenMap.get(parentKey);
        
        // Sort collections alphabetically
        children.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
        
        for (ZoteroCollection collection : children) {
            // Add this collection to the tree
            String collectionKey = collection.getKey();
            treeItems.add(new CollectionTreeItem(
                    collectionKey, 
                    collection.getName(), 
                    level,
                    childrenMap.containsKey(collectionKey) && !childrenMap.get(collectionKey).isEmpty()));
            
            // Recursively add its children (pre-expanded)
            buildTreeItems(collectionKey, childrenMap, level + 1);
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

    private void hideEmptyState() {
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCollectionClick(CollectionTreeItem item) {
        // Save the selected collection
        userPreferences.setSelectedCollectionKey(item.getId());
        userPreferences.setSelectedCollectionName(item.getName());
        
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

