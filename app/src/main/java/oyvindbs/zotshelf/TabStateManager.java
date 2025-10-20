package oyvindbs.zotshelf;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TabStateManager {
    private static final String PREF_NAME = "TabStatePref";
    private static final String KEY_TABS = "open_tabs";
    private static final String KEY_CURRENT_TAB = "current_tab_index";
    private static final int MAX_TABS = 10;

    private final SharedPreferences preferences;
    private final Gson gson;

    public TabStateManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static class TabInfo {
        private String collectionKey;
        private String collectionName;
        private String tags;  // Semicolon-separated tags
        private String tabType;  // "collection", "tags", or "both"

        // Constructor for collection-only tabs (backward compatibility)
        public TabInfo(String collectionKey, String collectionName) {
            this.collectionKey = collectionKey;
            this.collectionName = collectionName;
            this.tags = null;
            this.tabType = "collection";
        }

        // Constructor for tag-only tabs
        public TabInfo(String tags) {
            this.collectionKey = null;
            this.collectionName = null;
            this.tags = tags;
            this.tabType = "tags";
        }

        // Constructor for combined collection + tags
        public TabInfo(String collectionKey, String collectionName, String tags) {
            this.collectionKey = collectionKey;
            this.collectionName = collectionName;
            this.tags = tags;
            this.tabType = "both";
        }

        public String getCollectionKey() {
            return collectionKey;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public String getTags() {
            return tags;
        }

        public String getTabType() {
            return tabType != null ? tabType : "collection";
        }

        public String getDisplayName() {
            if ("tags".equals(getTabType())) {
                return "Tags: " + tags;
            } else if ("both".equals(getTabType())) {
                return collectionName + " [" + tags + "]";
            } else {
                return collectionName != null ? collectionName : "All Collections";
            }
        }

        public boolean hasTags() {
            return tags != null && !tags.trim().isEmpty();
        }

        public boolean hasCollection() {
            return collectionKey != null && !collectionKey.isEmpty();
        }
    }

    public List<TabInfo> getOpenTabs() {
        String json = preferences.getString(KEY_TABS, null);
        if (json == null || json.isEmpty()) {
            // Return default tab (All Collections)
            List<TabInfo> defaultTabs = new ArrayList<>();
            defaultTabs.add(new TabInfo(null, "All Collections"));
            return defaultTabs;
        }

        Type type = new TypeToken<List<TabInfo>>(){}.getType();
        List<TabInfo> tabs = gson.fromJson(json, type);
        return tabs != null ? tabs : new ArrayList<>();
    }

    public void saveOpenTabs(List<TabInfo> tabs) {
        String json = gson.toJson(tabs);
        preferences.edit().putString(KEY_TABS, json).apply();
    }

    public void addTab(String collectionKey, String collectionName) {
        addTab(collectionKey, collectionName, null);
    }

    public void addTagTab(String tags) {
        addTab(null, null, tags);
    }

    public void addTab(String collectionKey, String collectionName, String tags) {
        List<TabInfo> tabs = getOpenTabs();

        // Check if tab already exists
        for (TabInfo tab : tabs) {
            boolean collectionMatches = (tab.getCollectionKey() == null && collectionKey == null) ||
                    (tab.getCollectionKey() != null && tab.getCollectionKey().equals(collectionKey));
            boolean tagsMatch = (tab.getTags() == null && tags == null) ||
                    (tab.getTags() != null && tab.getTags().equals(tags));

            if (collectionMatches && tagsMatch) {
                // Tab already exists, don't add duplicate
                return;
            }
        }

        // Check max tabs limit
        if (tabs.size() >= MAX_TABS) {
            return; // Don't add more tabs than the limit
        }

        TabInfo newTab;
        if (tags != null && !tags.trim().isEmpty() && collectionKey != null) {
            newTab = new TabInfo(collectionKey, collectionName, tags);
        } else if (tags != null && !tags.trim().isEmpty()) {
            newTab = new TabInfo(tags);
        } else {
            newTab = new TabInfo(collectionKey, collectionName);
        }

        tabs.add(newTab);
        saveOpenTabs(tabs);
    }

    public void removeTab(int position) {
        List<TabInfo> tabs = getOpenTabs();
        if (position >= 0 && position < tabs.size()) {
            tabs.remove(position);

            // Ensure at least one tab remains
            if (tabs.isEmpty()) {
                tabs.add(new TabInfo(null, "All Collections"));
            }

            saveOpenTabs(tabs);

            // Adjust current tab index if necessary
            int currentTab = getCurrentTabIndex();
            if (currentTab >= tabs.size()) {
                setCurrentTabIndex(tabs.size() - 1);
            }
        }
    }

    public int getCurrentTabIndex() {
        return preferences.getInt(KEY_CURRENT_TAB, 0);
    }

    public void setCurrentTabIndex(int index) {
        preferences.edit().putInt(KEY_CURRENT_TAB, index).apply();
    }

    public void clearAllTabs() {
        preferences.edit()
                .remove(KEY_TABS)
                .remove(KEY_CURRENT_TAB)
                .apply();
    }

    public boolean canAddMoreTabs() {
        return getOpenTabs().size() < MAX_TABS;
    }

    public int getMaxTabs() {
        return MAX_TABS;
    }
}
