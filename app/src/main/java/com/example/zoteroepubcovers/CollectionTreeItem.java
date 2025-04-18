package com.example.zoteroepubcovers;

/**
 * Represents an item in the collection tree display
 */
public class CollectionTreeItem {
    private final String id;
    private final String name;
    private final int level;
    private final boolean hasChildren;
    private boolean selected;
    private boolean expanded;

    /**
     * Constructor for collection tree items
     * 
     * @param id The unique identifier (collection key)
     * @param name The display name of the collection
     * @param level The nesting level (0 for root, 1 for first level, etc.)
     * @param hasChildren Whether this item has child collections
     */
    public CollectionTreeItem(String id, String name, int level, boolean hasChildren) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.hasChildren = hasChildren;
        this.selected = false;
        this.expanded = true; // Start expanded by default
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public boolean hasChildren() {
        return hasChildren;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }
    
    @Override
    public String toString() {
        return "TreeItem{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", level=" + level +
                ", selected=" + selected +
                ", hasChildren=" + hasChildren +
                '}';
    }
}