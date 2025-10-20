package oyvindbs.zotshelf;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class CollectionTabAdapter extends FragmentStateAdapter {
    private final List<TabStateManager.TabInfo> tabs;

    public CollectionTabAdapter(@NonNull FragmentActivity fragmentActivity,
                                List<TabStateManager.TabInfo> tabs) {
        super(fragmentActivity);
        this.tabs = new ArrayList<>(tabs);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        TabStateManager.TabInfo tab = tabs.get(position);
        return CollectionFragment.newInstance(
                tab.getCollectionKey(),
                tab.getCollectionName(),
                tab.getTags()
        );
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    public void updateTabs(List<TabStateManager.TabInfo> newTabs) {
        tabs.clear();
        tabs.addAll(newTabs);
        notifyDataSetChanged();
    }

    public TabStateManager.TabInfo getTabAt(int position) {
        if (position >= 0 && position < tabs.size()) {
            return tabs.get(position);
        }
        return null;
    }

    public List<TabStateManager.TabInfo> getTabs() {
        return new ArrayList<>(tabs);
    }
}
