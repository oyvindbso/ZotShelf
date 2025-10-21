package oyvindbs.zotshelf;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import oyvindbs.zotshelf.database.EpubCoverRepository;
import oyvindbs.zotshelf.utils.NetworkUtils;

public class CollectionFragment extends Fragment implements CoverGridAdapter.CoverClickListener {

    private static final String ARG_COLLECTION_KEY = "collection_key";
    private static final String ARG_COLLECTION_NAME = "collection_name";
    private static final String ARG_TAGS = "tags";

    private String collectionKey;
    private String collectionName;
    private String tags;

    private RecyclerView recyclerView;
    private CoverGridAdapter adapter;
    private List<EpubCoverItem> coverItems = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ZoteroApiClient zoteroApiClient;
    private UserPreferences userPreferences;
    private EpubCoverRepository coverRepository;
    private boolean isOfflineMode = false;
    private DiagnosticInfo lastDiagnosticInfo;

    public static CollectionFragment newInstance(String collectionKey, String collectionName) {
        return newInstance(collectionKey, collectionName, null);
    }

    public static CollectionFragment newInstance(String collectionKey, String collectionName, String tags) {
        CollectionFragment fragment = new CollectionFragment();
        Bundle args = new Bundle();
        args.putString(ARG_COLLECTION_KEY, collectionKey);
        args.putString(ARG_COLLECTION_NAME, collectionName);
        args.putString(ARG_TAGS, tags);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            collectionKey = getArguments().getString(ARG_COLLECTION_KEY);
            collectionName = getArguments().getString(ARG_COLLECTION_NAME);
            tags = getArguments().getString(ARG_TAGS);
        }

        userPreferences = new UserPreferences(requireContext());
        coverRepository = new EpubCoverRepository(requireContext());
        zoteroApiClient = new ZoteroApiClient(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collection, container, false);

        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);
        recyclerView = view.findViewById(R.id.recyclerViewCovers);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        // Setup RecyclerView with Grid Layout
        int spanCount = calculateSpanCount();
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        int displayMode = userPreferences.getDisplayMode();
        adapter = new CoverGridAdapter(requireContext(), coverItems, this, displayMode);
        recyclerView.setAdapter(adapter);

        // Setup refresh listener
        swipeRefreshLayout.setOnRefreshListener(this::refreshCovers);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadCovers();
    }

    private int calculateSpanCount() {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int itemWidthDp = 120;
        return Math.max(2, screenWidthDp / itemWidthDp);
    }

    private void loadCovers() {
        if (!userPreferences.hasZoteroCredentials()) {
            showEmptyState("Please enter your Zotero credentials in settings");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (!userPreferences.hasAnyFileTypeEnabled()) {
            showEmptyState("Please enable at least one file type (EPUB or PDF) in settings");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading();

        // Load from cache first for instant display
        coverRepository.getFilteredCoversForCollection(collectionKey,
                new EpubCoverRepository.CoverRepositoryCallback() {
            @Override
            public void onCoversLoaded(List<EpubCoverItem> cachedCovers) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    if (!cachedCovers.isEmpty()) {
                        updateUI(cachedCovers);

                        if (NetworkUtils.isNetworkAvailable(requireContext())) {
                            isOfflineMode = false;
                            loadCoversFromApiInBackground();
                        } else {
                            isOfflineMode = true;
                            Toast.makeText(requireContext(), "Offline mode - showing cached covers",
                                    Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    } else {
                        if (NetworkUtils.isNetworkAvailable(requireContext())) {
                            isOfflineMode = false;
                            loadCoversFromApi();
                        } else {
                            isOfflineMode = true;
                            showEmptyState("No internet connection and no cached data available");
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    Log.e("CollectionFragment", "Error loading cached covers: " + message);
                    if (NetworkUtils.isNetworkAvailable(requireContext())) {
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
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "No internet connection. Showing cached data.",
                    Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        loadCoversFromApi();
    }

    private void loadCoversFromApi() {
        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();

        // Prepare diagnostic info
        prepareDiagnosticInfo();

        Log.d("CollectionFragment", "Loading covers from API - Collection: " + collectionKey + ", Tags: " + tags);

        zoteroApiClient.getAllEbookItemsWithMetadata(userId, apiKey, collectionKey, tags,
                new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> zoteroItems) {
                Log.d("CollectionFragment", "Received " + zoteroItems.size() + " items from API");

                // Update diagnostic info
                if (lastDiagnosticInfo != null) {
                    lastDiagnosticInfo.setItemsReceived(zoteroItems.size());
                    lastDiagnosticInfo.setHttpResponseCode(200);
                }

                processZoteroItems(zoteroItems);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e("CollectionFragment", "Error loading covers: " + errorMessage);
                if (getActivity() == null) return;

                // Update diagnostic info with error
                if (lastDiagnosticInfo != null) {
                    lastDiagnosticInfo.setErrorMessage(errorMessage);
                    // Try to extract HTTP code from error message
                    if (errorMessage.contains("HTTP ")) {
                        try {
                            int codeStart = errorMessage.indexOf("HTTP ") + 5;
                            int codeEnd = errorMessage.indexOf(" ", codeStart);
                            if (codeEnd == -1) codeEnd = errorMessage.length();
                            String codeStr = errorMessage.substring(codeStart, codeEnd);
                            lastDiagnosticInfo.setHttpResponseCode(Integer.parseInt(codeStr));
                        } catch (Exception e) {
                            // Couldn't parse code, that's fine
                        }
                    }
                }

                getActivity().runOnUiThread(() -> {
                    coverRepository.hasCachedCovers(hasCovers -> {
                        if (hasCovers) {
                            loadCachedCovers();
                            // Show error dialog if tags are being used
                            if (tags != null && !tags.isEmpty()) {
                                showErrorDialog("Tag Filter Error",
                                    "Failed to load items with tag filter.\n\n" + errorMessage +
                                    "\n\nShowing cached data instead.\n\nClick 'Show Diagnostics' for more details.");
                            } else {
                                Toast.makeText(requireContext(),
                                    "Failed to update from Zotero: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                            }
                        } else {
                            progressBar.setVisibility(View.GONE);
                            swipeRefreshLayout.setRefreshing(false);
                            // Always show error dialog for better visibility
                            showErrorDialog("Error Loading Items", errorMessage);
                        }
                    });
                });
            }
        });
    }

    private void loadCoversFromApiInBackground() {
        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();

        zoteroApiClient.getAllEbookItemsWithMetadata(userId, apiKey, collectionKey, tags,
                new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
            @Override
            public void onSuccess(List<ZoteroItem> zoteroItems) {
                Log.d("CollectionFragment", "Background update: Received " +
                        zoteroItems.size() + " items from API");
                processZoteroItemsForCache(zoteroItems);

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(requireContext(), "Library updated from Zotero",
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e("CollectionFragment", "Background update error: " + errorMessage);
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void processZoteroItemsForCache(List<ZoteroItem> zoteroItems) {
        if (zoteroItems.isEmpty()) {
            return;
        }

        for (ZoteroItem item : zoteroItems) {
            zoteroApiClient.downloadEbook(item, new ZoteroApiClient.FileCallback() {
                @Override
                public void onFileDownloaded(ZoteroItem item, String filePath) {
                    CoverExtractor.extractCover(filePath, new CoverExtractor.CoverCallback() {
                        @Override
                        public void onCoverExtracted(String coverPath) {
                            coverRepository.saveCoverFromZoteroItem(item, coverPath);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            coverRepository.saveCoverFromZoteroItem(item, null);
                        }
                    });
                }

                @Override
                public void onError(ZoteroItem item, String errorMessage) {
                    coverRepository.saveCoverFromZoteroItem(item, null);
                }
            });
        }
    }

    private void loadCachedCovers() {
        coverRepository.getFilteredCoversForCollection(collectionKey,
                new EpubCoverRepository.CoverRepositoryCallback() {
            @Override
            public void onCoversLoaded(List<EpubCoverItem> covers) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    if (covers.isEmpty()) {
                        showEmptyState("No cached covers found");
                    } else {
                        updateUI(covers);
                        if (isOfflineMode) {
                            Toast.makeText(requireContext(), "Offline mode - showing cached covers",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    showEmptyState("Error loading cached covers: " + message);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void processZoteroItems(List<ZoteroItem> zoteroItems) {
        if (zoteroItems.isEmpty()) {
            if (getActivity() == null) return;

            // Update diagnostic info
            if (lastDiagnosticInfo != null) {
                lastDiagnosticInfo.setItemsFiltered(0);
            }

            getActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                // Show diagnostic dialog if using tags, otherwise show simple empty state
                if (tags != null && !tags.isEmpty()) {
                    StringBuilder message = new StringBuilder();
                    message.append("No items found matching your filters.\n\n");
                    message.append("This could mean:\n");
                    message.append("• The tags don't exist in your library\n");
                    message.append("• Tag names are case-sensitive (check capitalization)\n");
                    message.append("• No items have ALL the specified tags\n");
                    if (collectionKey != null && !collectionKey.isEmpty()) {
                        message.append("• The collection doesn't have items with these tags\n");
                    }
                    message.append("\nTags entered: ").append(tags);

                    showErrorDialog("No Items Found", message.toString());
                } else {
                    showEmptyState("No EPUB or PDF files found matching your filters");
                }
            });
            return;
        }

        Log.d("CollectionFragment", "Processing " + zoteroItems.size() + " Zotero items");

        List<EpubCoverItem> newCoverItems = new ArrayList<>();
        final int totalItems = zoteroItems.size();
        final int[] processedCount = {0};

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
                            );

                            synchronized (newCoverItems) {
                                newCoverItems.add(coverItem);
                                itemsToSave.add(item);
                                coverPathsToSave.add(coverPath);
                                processedCount[0]++;

                                new Thread(() -> {
                                    coverRepository.saveCoverFromZoteroItemSync(item, coverPath);
                                }).start();

                                if (processedCount[0] == totalItems) {
                                    updateUI(newCoverItems);
                                }
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            EpubCoverItem coverItem = new EpubCoverItem(
                                    item.getKey(),
                                    item.getTitle(),
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
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            coverItems.clear();
            coverItems.addAll(newItems);

            // Apply current sort mode
            int sortMode = userPreferences.getSortMode();
            CoverSorter.sortCovers(coverItems, sortMode);

            int displayMode = userPreferences.getDisplayMode();
            adapter = new CoverGridAdapter(requireContext(), coverItems, this, displayMode);
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
    public void onCoverClick(EpubCoverItem item) {
        String zoteroUsername = userPreferences.getZoteroUsername();
        if (zoteroUsername == null || zoteroUsername.isEmpty()) {
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Cannot open reader - no internet connection",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "https://www.zotero.org/" + zoteroUsername + "/items/" +
                item.getId() + "/reader";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    public String getCollectionKey() {
        return collectionKey;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void refresh() {
        refreshCovers();
    }

    public void updateDisplayMode() {
        if (adapter != null) {
            int displayMode = userPreferences.getDisplayMode();
            adapter = new CoverGridAdapter(requireContext(), coverItems, this, displayMode);
            recyclerView.setAdapter(adapter);
        }
    }

    public void applySorting() {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (!coverItems.isEmpty()) {
                // Apply current sort mode
                int sortMode = userPreferences.getSortMode();
                CoverSorter.sortCovers(coverItems, sortMode);

                // Refresh the adapter
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                } else {
                    int displayMode = userPreferences.getDisplayMode();
                    adapter = new CoverGridAdapter(requireContext(), coverItems, this, displayMode);
                    recyclerView.setAdapter(adapter);
                }
            }
        });
    }

    /**
     * Show an error dialog with diagnostic information
     */
    private void showErrorDialog(String title, String message) {
        if (getActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);

        // Add diagnostics button if we have diagnostic info
        if (lastDiagnosticInfo != null && (tags != null && !tags.isEmpty())) {
            builder.setNeutralButton("Show Diagnostics", (dialog, which) -> {
                showDiagnosticsDialog();
            });
        }

        builder.show();
    }

    /**
     * Show detailed diagnostics in a dialog
     */
    private void showDiagnosticsDialog() {
        if (getActivity() == null || lastDiagnosticInfo == null) return;

        String diagnosticReport = lastDiagnosticInfo.generateReport();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Diagnostic Information");
        builder.setMessage(diagnosticReport);
        builder.setPositiveButton("Close", null);
        builder.setNegativeButton("Copy to Clipboard", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ZotShelf Diagnostics", diagnosticReport);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    /**
     * Create and populate diagnostic info for the current API call
     */
    private void prepareDiagnosticInfo() {
        lastDiagnosticInfo = new DiagnosticInfo();
        lastDiagnosticInfo.setTags(tags);
        lastDiagnosticInfo.setCollectionKey(collectionKey);
        lastDiagnosticInfo.setCollectionName(collectionName);

        // Construct approximate API URL for diagnostics
        String userId = userPreferences.getZoteroUserId();
        StringBuilder urlBuilder = new StringBuilder("https://api.zotero.org/users/");
        urlBuilder.append(userId);

        if (collectionKey != null && !collectionKey.isEmpty()) {
            urlBuilder.append("/collections/").append(collectionKey);
        }

        urlBuilder.append("/items?format=json&itemType=attachment");

        if (tags != null && !tags.isEmpty()) {
            String[] tagArray = tags.split(";");
            for (String tag : tagArray) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    urlBuilder.append("&tag=").append(trimmed.replace(" ", "%20"));
                }
            }
        }

        lastDiagnosticInfo.setApiUrl(urlBuilder.toString());
    }
}
