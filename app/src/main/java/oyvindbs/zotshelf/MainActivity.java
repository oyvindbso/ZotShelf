package oyvindbs.zotshelf;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import oyvindbs.zotshelf.utils.NetworkUtils;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SELECT_COLLECTION = 1001;
    private static final int REQUEST_CODE_SELECT_COLLECTION_WITH_TAGS = 1002;
    private String pendingTags = null;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private FloatingActionButton fabAddTab;
    private CollectionTabAdapter tabAdapter;
    private TabStateManager tabStateManager;
    private UserPreferences userPreferences;
    private TabLayoutMediator tabLayoutMediator;
    private boolean isFirstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userPreferences = new UserPreferences(this);
        tabStateManager = new TabStateManager(this);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        fabAddTab = findViewById(R.id.fabAddTab);

        // Setup tabs regardless of credentials (fragments will handle empty state)
        setupTabs();
        setupFab();

        // Check if we have Zotero credentials, if not show settings first
        if (!userPreferences.hasZoteroCredentials()) {
            startActivity(new Intent(this, SettingsActivity.class));
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
            String url = "https://www.zotero.org/" + username + "/items/" + itemId + "/reader";
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    private void setupTabs() {
        List<TabStateManager.TabInfo> tabs = tabStateManager.getOpenTabs();

        tabAdapter = new CollectionTabAdapter(this, tabs);
        viewPager.setAdapter(tabAdapter);

        // Connect TabLayout with ViewPager2
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }

        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    TabStateManager.TabInfo tabInfo = tabAdapter.getTabAt(position);
                    if (tabInfo != null) {
                        tab.setText(tabInfo.getDisplayName());

                        // Add close button for tabs (except if it's the only tab)
                        if (tabs.size() > 1) {
                            tab.view.setOnLongClickListener(v -> {
                                showCloseTabDialog(position);
                                return true;
                            });
                        }
                    }
                }
        );
        tabLayoutMediator.attach();

        // Restore last selected tab
        int currentTab = tabStateManager.getCurrentTabIndex();
        if (currentTab < tabs.size()) {
            viewPager.setCurrentItem(currentTab, false);
        }

        // Save current tab when user switches
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                tabStateManager.setCurrentTabIndex(position);
            }
        });
    }

    private void setupFab() {
        fabAddTab.setOnClickListener(v -> {
            if (!tabStateManager.canAddMoreTabs()) {
                Toast.makeText(this, "Maximum " + tabStateManager.getMaxTabs() +
                        " tabs reached", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!userPreferences.hasZoteroCredentials()) {
                Toast.makeText(this, R.string.enter_credentials, Toast.LENGTH_SHORT).show();
                return;
            }

            // Show dialog to choose tab type
            showAddTabDialog();
        });
    }

    private void showAddTabDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Tab");

        String[] options = {"By Collection", "By Tags", "By Collection + Tags"};

        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // By Collection only
                if (!NetworkUtils.isNetworkAvailable(this)) {
                    Toast.makeText(this, "No internet connection. Cannot fetch collections.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(this, CollectionTreeActivity.class);
                startActivityForResult(intent, REQUEST_CODE_SELECT_COLLECTION);
            } else if (which == 1) {
                // By Tags only
                showTagInputDialog(false);
            } else {
                // By Collection + Tags
                showTagInputDialog(true);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showTagInputDialog(boolean withCollection) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(withCollection ? "Filter by Tags (then select collection)" : "Filter by Tags");

        // Create input field
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter tags (separated by semicolons)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(50, 0, 50, 0);
        input.setLayoutParams(lp);

        builder.setView(input);

        builder.setPositiveButton(withCollection ? "Next: Select Collection" : "Create Tab", (dialog, which) -> {
            String tags = input.getText().toString().trim();
            if (tags.isEmpty()) {
                Toast.makeText(this, "Please enter at least one tag", Toast.LENGTH_SHORT).show();
                return;
            }

            if (withCollection) {
                // Store tags and show collection selector
                pendingTags = tags;
                if (!NetworkUtils.isNetworkAvailable(this)) {
                    Toast.makeText(this, "No internet connection. Cannot fetch collections.",
                            Toast.LENGTH_LONG).show();
                    pendingTags = null;
                    return;
                }
                Intent intent = new Intent(this, CollectionTreeActivity.class);
                startActivityForResult(intent, REQUEST_CODE_SELECT_COLLECTION_WITH_TAGS);
            } else {
                // Add new tag-only tab
                tabStateManager.addTagTab(tags);
                refreshTabs();

                // Switch to the new tab
                List<TabStateManager.TabInfo> allTabs = tabStateManager.getOpenTabs();
                viewPager.setCurrentItem(allTabs.size() - 1, true);

                Toast.makeText(this, "Created tag filter: " + tags, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showCloseTabDialog(int position) {
        TabStateManager.TabInfo tabInfo = tabAdapter.getTabAt(position);
        if (tabInfo == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Close Tab")
                .setMessage("Close tab '" + tabInfo.getDisplayName() + "'?")
                .setPositiveButton("Close", (dialog, which) -> closeTab(position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void closeTab(int position) {
        tabStateManager.removeTab(position);
        refreshTabs();
    }

    private void refreshTabs() {
        List<TabStateManager.TabInfo> tabs = tabStateManager.getOpenTabs();
        tabAdapter.updateTabs(tabs);

        // Reattach mediator
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }

        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    TabStateManager.TabInfo tabInfo = tabAdapter.getTabAt(position);
                    if (tabInfo != null) {
                        tab.setText(tabInfo.getDisplayName());

                        if (tabs.size() > 1) {
                            tab.view.setOnLongClickListener(v -> {
                                showCloseTabDialog(position);
                                return true;
                            });
                        }
                    }
                }
        );
        tabLayoutMediator.attach();

        // Make sure we're on a valid tab
        int currentTab = viewPager.getCurrentItem();
        if (currentTab >= tabs.size()) {
            viewPager.setCurrentItem(tabs.size() - 1, true);
        }
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
                    Toast.makeText(this, "No internet connection available",
                            Toast.LENGTH_SHORT).show();
                } else {
                    refreshCurrentTab();
                }
                return true;

            case R.id.action_sort:
                showSortDialog();
                return true;

            case R.id.action_info:
                showInfoDialog();
                return true;

            case R.id.action_select_collection:
                fabAddTab.performClick();
                return true;

            case R.id.action_change_display:
                showDisplayModeDialog();
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

    private void refreshCurrentTab() {
        if (tabAdapter == null || viewPager.getAdapter() == null) {
            return;
        }

        // Give ViewPager2 time to create fragments if needed
        viewPager.post(() -> {
            CollectionFragment fragment = getCurrentFragment();
            if (fragment != null && fragment.isAdded()) {
                fragment.refresh();
            }
        });
    }

    private CollectionFragment getCurrentFragment() {
        if (tabAdapter == null) {
            return null;
        }
        int currentPosition = viewPager.getCurrentItem();
        // ViewPager2 uses "f" + itemId as fragment tag
        return (CollectionFragment) getSupportFragmentManager()
                .findFragmentByTag("f" + currentPosition);
    }

    private void toggleEpubsEnabled(MenuItem item) {
        boolean currentState = userPreferences.getShowEpubs();
        boolean newState = !currentState;

        if (!newState && !userPreferences.getShowPdfs()) {
            Toast.makeText(this, "At least one file type must be enabled",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        userPreferences.setShowEpubs(newState);
        item.setChecked(newState);

        Toast.makeText(this, newState ? "EPUBs enabled" : "EPUBs disabled",
                Toast.LENGTH_SHORT).show();

        // Refresh all tabs
        refreshAllTabs();
    }

    private void togglePdfsEnabled(MenuItem item) {
        boolean currentState = userPreferences.getShowPdfs();
        boolean newState = !currentState;

        if (!newState && !userPreferences.getShowEpubs()) {
            Toast.makeText(this, "At least one file type must be enabled",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        userPreferences.setShowPdfs(newState);
        item.setChecked(newState);

        Toast.makeText(this, newState ? "PDFs enabled" : "PDFs disabled",
                Toast.LENGTH_SHORT).show();

        // Refresh all tabs
        refreshAllTabs();
    }

    private void refreshAllTabs() {
        // Recreate the adapter to force all fragments to reload
        setupTabs();
    }

    private void showInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About ZotShelf");

        android.view.LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_info, null);
        builder.setView(dialogView);

        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SELECT_COLLECTION && resultCode == RESULT_OK) {
            String collectionKey = userPreferences.getSelectedCollectionKey();
            String collectionName = userPreferences.getSelectedCollectionName();

            if (collectionName == null || collectionName.isEmpty()) {
                collectionName = "All Collections";
            }

            // Add new tab with the selected collection
            tabStateManager.addTab(collectionKey, collectionName);
            refreshTabs();

            // Switch to the new tab
            List<TabStateManager.TabInfo> tabs = tabStateManager.getOpenTabs();
            viewPager.setCurrentItem(tabs.size() - 1, true);

            Toast.makeText(this, "Opened " + collectionName + " in new tab",
                    Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQUEST_CODE_SELECT_COLLECTION_WITH_TAGS && resultCode == RESULT_OK) {
            String collectionKey = userPreferences.getSelectedCollectionKey();
            String collectionName = userPreferences.getSelectedCollectionName();

            if (collectionName == null || collectionName.isEmpty()) {
                collectionName = "All Collections";
            }

            // Add new tab with both collection and tags
            if (pendingTags != null) {
                tabStateManager.addTab(collectionKey, collectionName, pendingTags);
                refreshTabs();

                // Switch to the new tab
                List<TabStateManager.TabInfo> tabs = tabStateManager.getOpenTabs();
                viewPager.setCurrentItem(tabs.size() - 1, true);

                Toast.makeText(this, "Opened " + collectionName + " with tags: " + pendingTags,
                        Toast.LENGTH_SHORT).show();

                pendingTags = null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Skip refresh on first resume (fragments will load automatically)
        if (isFirstResume) {
            isFirstResume = false;
            return;
        }

        // Check if credentials are available
        if (!userPreferences.hasZoteroCredentials()) {
            // If no credentials, we can't do much
            return;
        }

        // Check if we need to refresh tabs due to settings changes
        if (!userPreferences.hasAnyFileTypeEnabled()) {
            Toast.makeText(this, "Please enable at least one file type (EPUB or PDF)",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Refresh current tab to pick up any changes from settings
        refreshCurrentTab();
    }

    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sort By");

        String[] options = {"Title", "Author"};
        int currentMode = userPreferences.getSortMode();

        builder.setSingleChoiceItems(options, currentMode, (dialog, which) -> {
            userPreferences.setSortMode(which);
            dialog.dismiss();

            // Apply sorting to current tab
            applySortingToCurrentTab();

            Toast.makeText(this,
                    "Sorted by " + (which == UserPreferences.SORT_BY_TITLE ? "Title" : "Author"),
                    Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void applySortingToCurrentTab() {
        viewPager.post(() -> {
            CollectionFragment fragment = getCurrentFragment();
            if (fragment != null && fragment.isAdded()) {
                fragment.applySorting();
            }
        });
    }

    private void showDisplayModeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Display Mode");

        String[] options = {"Title only", "Author only", "Author - Title"};
        int currentMode = userPreferences.getDisplayMode();

        builder.setSingleChoiceItems(options, currentMode, (dialog, which) -> {
            userPreferences.setDisplayMode(which);
            dialog.dismiss();

            // Update display mode in all visible fragments
            updateDisplayModeInAllFragments();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateDisplayModeInAllFragments() {
        // Recreate adapter to update all fragments
        setupTabs();
    }
}
