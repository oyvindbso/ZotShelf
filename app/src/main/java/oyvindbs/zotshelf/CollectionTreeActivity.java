package oyvindbs.zotshelf;

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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import oyvindbs.zotshelf.utils.NetworkUtils;

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
    private SwipeRefreshLayout swipeRefreshLayout;
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
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CollectionTreeAdapter(this, treeItems, this);
        recyclerView.setAdapter(adapter);

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this::loadCollections);
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );

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
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection. Cannot fetch collections.", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading();

        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();
        
        Log.d(TAG, "Loading collections for user ID: '" + userId + "'");

        // Get ALL collections with pagination if needed
        getAllCollections(userId, apiKey, new ArrayList<>(), 0);
    }

    /**
     * Recursively fetch all collections using pagination
     */
    private void getAllCollections(String userId, String apiKey, List<ZoteroCollection> allCollections, int start) {
        zoteroApiClient.getCollectionsPaginated(userId, apiKey, start, 100, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                Log.d(TAG, "Received " + collections.size() + " collections from API (start: " + start + ")");
                
                allCollections.addAll(collections);
                
                // If we received a full page, there might be more collections
                if (collections.size() == 100) {
                    // Fetch next page
                    getAllCollections(userId, apiKey, allCollections, start + 100);
                } else {
                    // We have all collections, now process them
                    processAllCollections(allCollections);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error loading collections: " + errorMessage);
                runOnUiThread(() -> {
                    showEmptyState("Error loading collections: " + errorMessage);
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(CollectionTreeActivity.this, 
                            "Error loading collections: " + errorMessage, 
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void processAllCollections(List<ZoteroCollection> collections) {
        Log.d(TAG, "Processing " + collections.size() + " total collections");
        
        // Always add "All Collections" as the first item
        treeItems.clear();
        
        CollectionTreeItem allCollectionsItem = new CollectionTreeItem("", "All Collections", 0, !collections.isEmpty());
        if (currentSelectedKey.isEmpty()) {
            allCollectionsItem.setSelected(true);
        }
        treeItems.add(allCollectionsItem);
        
        if (collections.isEmpty()) {
            runOnUiThread(() -> {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        buildCollectionTree(collections);
    }

    private void buildCollectionTree(List<ZoteroCollection> collections) {
        Log.d(TAG, "Building tree with " + collections.size() + " collections");
        
        // Create a map to hold all collections by key for easy lookup
        Map<String, ZoteroCollection> collectionMap = new HashMap<>();
        for (ZoteroCollection collection : collections) {
            String key = collection.getKey();
            String name = collection.getName();
            Log.d(TAG, "Processing collection: '" + name + "' (key: " + key + ", parent: " + collection.getParentCollection() + ")");
            
            if (key != null && !key.isEmpty()) {
                collectionMap.put(key, collection);
            } else {
                Log.w(TAG, "Collection has null or empty key: " + name);
            }
        }
        
        // Create a map to store children for each collection
        Map<String, List<String>> childrenMap = new HashMap<>();
        childrenMap.put("root", new ArrayList<>());
        
        // Process each collection to build parent-child relationships
        for (ZoteroCollection collection : collections) {
            String key = collection.getKey();
            if (key == null || key.isEmpty()) {
                continue; // Skip invalid collections
            }
            
            String parentKey = collection.getParentCollection();
            
            // If no parent or parent is false/empty, this is a top-level collection
            if (parentKey == null || parentKey.isEmpty() || parentKey.equals("false")) {
                Log.d(TAG, "Adding to root: " + collection.getName());
                childrenMap.get("root").add(key);
            } else {
                // Check if parent exists in our collection map
                if (collectionMap.containsKey(parentKey)) {
                    if (!childrenMap.containsKey(parentKey)) {
                        childrenMap.put(parentKey, new ArrayList<>());
                    }
                    Log.d(TAG, "Adding '" + collection.getName() + "' as child of '" + collectionMap.get(parentKey).getName() + "'");
                    childrenMap.get(parentKey).add(key);
                } else {
                    // Parent doesn't exist in our map, treat as root level
                    Log.w(TAG, "Parent '" + parentKey + "' not found for collection '" + collection.getName() + "', adding to root");
                    childrenMap.get("root").add(key);
                }
            }
        }
        
        // Sort root level collections alphabetically
        List<String> rootCollections = childrenMap.get("root");
        if (rootCollections != null && !rootCollections.isEmpty()) {
            sortCollectionKeys(rootCollections, collectionMap);
            
            // Recursively build the tree
            for (String rootKey : rootCollections) {
                addCollectionToTree(rootKey, 1, childrenMap, collectionMap);
            }
        } else {
            Log.w(TAG, "No root collections found!");
        }
        
        Log.d(TAG, "Final tree size: " + treeItems.size() + " items");

        runOnUiThread(() -> {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            
            adapter.notifyDataSetChanged();
            
            // Scroll to the selected item if any
            scrollToSelectedItem();
        });
    }
    
    private void sortCollectionKeys(List<String> keys, Map<String, ZoteroCollection> collectionMap) {
        try {
            Collections.sort(keys, (key1, key2) -> {
                ZoteroCollection col1 = collectionMap.get(key1);
                ZoteroCollection col2 = collectionMap.get(key2);
                
                String name1 = (col1 != null) ? col1.getName() : "";
                String name2 = (col2 != null) ? col2.getName() : "";
                
                // Handle null names
                if (name1 == null) name1 = "";
                if (name2 == null) name2 = "";
                
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
        List<String> children = childrenMap.get(collectionKey);
        boolean hasChildren = children != null && !children.isEmpty();
        
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
        Log.d(TAG, "Added to tree: " + collection.getName() + " (level " + level + ", hasChildren: " + hasChildren + ")");
        
        // If this collection has children, add them recursively
        if (hasChildren) {
            // Sort children alphabetically
            sortCollectionKeys(children, collectionMap);
            
            for (String childKey : children) {
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
        if (!swipeRefreshLayout.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showEmptyState(String message) {
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setText(message);
        swipeRefreshLayout.setRefreshing(false);
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
            return t