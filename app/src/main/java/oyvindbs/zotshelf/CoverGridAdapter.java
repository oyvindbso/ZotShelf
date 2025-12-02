package oyvindbs.zotshelf;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class CoverGridAdapter extends RecyclerView.Adapter<CoverGridAdapter.CoverViewHolder> {

    private final Context context;
    private final List<EpubCoverItem> coverItems;
    private final CoverClickListener listener;
    private final int displayMode;

    public interface CoverClickListener {
        void onCoverClick(EpubCoverItem item);
    }

    public CoverGridAdapter(Context context, List<EpubCoverItem> coverItems, CoverClickListener listener, int displayMode) {
        this.context = context;
        this.coverItems = coverItems;
        this.listener = listener;
        this.displayMode = displayMode;
    }
    
    // Getter for display mode - needed for checking if mode has changed
    public int getDisplayMode() {
        return displayMode;
    }

    @NonNull
    @Override
    public CoverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.grid_item_cover, parent, false);
        return new CoverViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CoverViewHolder holder, int position) {
        EpubCoverItem item = coverItems.get(position);
        
        // Load cover image
        if (item.getCoverPath() != null) {
            Glide.with(context)
                    .load(new File(item.getCoverPath()))
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .centerCrop()
                    .into(holder.coverImage);
        } else {
            Glide.with(context)
                    .load(R.drawable.placeholder_cover)
                    .centerCrop()
                    .into(holder.coverImage);
        }
        
        // Set text based on display mode
        switch (displayMode) {
            case UserPreferences.DISPLAY_AUTHOR_ONLY:
                holder.titleText.setText(item.getAuthors());
                break;
            case UserPreferences.DISPLAY_AUTHOR_TITLE:
                String authorTitle = item.getAuthors() + " - " + item.getTitle();
                holder.titleText.setText(authorTitle);
                break;
            case UserPreferences.DISPLAY_TITLE_ONLY:
            default:
                holder.titleText.setText(item.getTitle());
                break;
        }
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCoverClick(item);
            }
        });

        // Set copy button click listener
        holder.copyButton.setOnClickListener(v -> {
            copyLinkToClipboard(item);
        });
    }

    @Override
    public int getItemCount() {
        return coverItems.size();
    }

    private void copyLinkToClipboard(EpubCoverItem item) {
        UserPreferences userPreferences = new UserPreferences(context);
        int linkType = userPreferences.getLinkType();
        String link;
        String linkLabel;

        if (linkType == UserPreferences.LINK_TYPE_INTERNAL) {
            // Internal Zotero link: zotero://select/library/items/{itemId}
            link = "zotero://select/library/items/" + item.getId();
            linkLabel = "Internal link";
        } else {
            // Web library link: https://www.zotero.org/{username}/items/{itemId}
            String username = item.getZoteroUsername();
            if (username == null || username.isEmpty()) {
                username = userPreferences.getZoteroUsername();
            }
            link = "https://www.zotero.org/" + username + "/items/" + item.getId();
            linkLabel = "Web link";
        }

        // Copy to clipboard
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(linkLabel, link);
        clipboard.setPrimaryClip(clip);

        // Show toast notification
        Toast.makeText(context, linkLabel + " copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    static class CoverViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImage;
        TextView titleText;
        ImageButton copyButton;

        public CoverViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.imageCover);
            titleText = itemView.findViewById(R.id.textTitle);
            copyButton = itemView.findViewById(R.id.buttonCopyLink);
        }
    }
}