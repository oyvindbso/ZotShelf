package com.example.zoteroepubcovers;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CollectionTreeAdapter extends RecyclerView.Adapter<CollectionTreeAdapter.CollectionViewHolder> {

    private static final String TAG = "CollectionTreeAdapter";
    private final Context context;
    private final List<CollectionTreeItem> items;
    private final CollectionClickListener listener;

    public interface CollectionClickListener {
        void onCollectionClick(CollectionTreeItem item);
    }

    public CollectionTreeAdapter(Context context, List<CollectionTreeItem> items, CollectionClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CollectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_collection_tree, parent, false);
        return new CollectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CollectionViewHolder holder, int position) {
        CollectionTreeItem item = items.get(position);
        
        Log.d(TAG, "Binding item: " + item.getName() + " at level " + item.getLevel() + ", selected: " + item.isSelected());
        
        // Apply indentation based on level
        int indentPx = (int) (16 * context.getResources().getDisplayMetrics().density) * item.getLevel();
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.cardView.getLayoutParams();
        params.setMarginStart(indentPx);
        holder.cardView.setLayoutParams(params);
        
        // Set icon based on whether the item has children and its level
        if (item.getLevel() == 0) {
            // Special case for "All Collections"
            holder.iconView.setVisibility(View.VISIBLE);
            holder.iconView.setImageResource(R.drawable.ic_collections);
        } else if (item.hasChildren()) {
            // Folders with children
            holder.iconView.setVisibility(View.VISIBLE);
            holder.iconView.setImageResource(R.drawable.ic_folder);
        } else {
            // End items/collections with no children
            holder.iconView.setVisibility(View.VISIBLE);
            holder.iconView.setImageResource(R.drawable.ic_collection_item);
        }
        
        // Set name
        holder.nameView.setText(item.getName());
        
        // Highlight selected item
        if (item.isSelected()) {
            holder.cardView.setCardBackgroundColor(context.getResources().getColor(R.color.purple_200, null));
            holder.nameView.setTextColor(context.getResources().getColor(R.color.black, null));
        } else {
            holder.cardView.setCardBackgroundColor(context.getResources().getColor(android.R.color.white, null));
            holder.nameView.setTextColor(context.getResources().getColor(R.color.black, null));
        }
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            // Update selection in data model
            for (CollectionTreeItem treeItem : items) {
                treeItem.setSelected(false);
            }
            item.setSelected(true);
            notifyDataSetChanged();
            
            // Callback
            if (listener != null) {
                listener.onCollectionClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CollectionViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView iconView;
        TextView nameView;

        public CollectionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardViewCollection);
            iconView = itemView.findViewById(R.id.imageViewCollectionIcon);
            nameView = itemView.findViewById(R.id.textViewCollectionName);
        }
    }
}