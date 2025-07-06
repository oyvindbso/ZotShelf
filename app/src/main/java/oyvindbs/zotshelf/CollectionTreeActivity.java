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

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_select_collection);
        }

        recyclerView = findViewById(R.id.recyclerViewCollections);
        progressBar = findViewById(R.id.progressBarCollections);
        emptyView = findViewById(R.id.emptyViewCollections);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CollectionTreeAdapter(this, treeItems, this);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadCollections);
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );

        zoteroApiClient = new ZoteroApiClient(this);
        userPreferences = new UserPreferences(this);
        currentSelectedKey = userPreferences.getSelectedCollectionKey();

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

        getAllCollections(userId, apiKey, new ArrayList<>(), 0);
    }

    private void getAllCollections(String userId, String apiKey, List<ZoteroCollection> allCollections, int start) {
        zoteroApiClient.getCollectionsPaginated(userId, apiKey, start, 100, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                Log.d(TAG, "Received " + collections.size() + " collections from API (start: " + start + ")");
                
                allCollections.addAll(collections);
                
                if (collections.size() == 100) {
                    getAllCollections(userId, apiKey, allCollections, start + 100);
                } else {
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
        
        Map<String, ZoteroCollection> collectionMap = new HashMap<>();
        for (ZoteroCollection collection : collections) {
            String key = collection.getKey();
            if (key != null && !key.isEmpty()) {
                collectionMap.put(key, collection);
            }
        }
        
        Map<String, List<String>> childrenMap = new HashMap<>();
        childrenMap.put("root", new ArrayList<>());
        
        for (ZoteroCollection collection : collections) {
            String key = collection.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            
            String parentKey = collection.getParentCollection();
            
            if (parentKey == null || parentKey.isEmpty() || parentKey.equals("false")) {
                childrenMap.get("root").add(key);
            } else {
                if (collectionMap.containsKey(parentKey)) {
                    if (!childrenMap.containsKey(parentKey)) {
                        childrenMap.put(parentKey, new ArrayList<>());
                    }
                    childrenMap.get(parentKey).add(key);
                } else {
                    childrenMap.get("root").add(key);
                }
            }
        }
        
        List<String> rootCollections = childrenMap.get("root");
        if (rootCollections != null && !rootCollections.isEmpty()) {
            sortCollectionKeys(rootCollections, collectionMap);
            
            for (String rootKey : rootCollections) {
                addCollectionToTree(rootKey, 1, childrenMap, collectionMap);
            }
        }
        
        Log.d(TAG, "Final tree size: " + treeItems.size() + " items");

        runOnUiThread(() -> {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            
            adapter.notifyDataSetChanged();
            
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
        ZoteroCollection collection = collectionMap.get(collectionKey);
        if (collection == null) {
            return;
        }
        
        List<String> children = childrenMap.get(collectionKey);
        boolean hasChildren = children != null && !children.isEmpty();
        
        CollectionTreeItem item = new CollectionTreeItem(
                collectionKey,
                collection.getName(),
                level,
                hasChildren);
        
        if (collectionKey.equals(currentSelectedKey)) {
            item.setSelected(true);
        }
        
        treeItems.add(item);
        
        if (hasChildren) {
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
        userPreferences.setSelectedCollectionKey(item.getId());
        userPreferences.setSelectedCollectionName(item.getName());
        
        Log.d(TAG, "Selected collection: " + item.getName() + " with key: " + item.getId());
        
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