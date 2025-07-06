package oyvindbs.zotshelf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class EpubCoversRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private Context context;
    private List<EpubCoverItem> coverItems = new ArrayList<>();
    private UserPreferences userPreferences;
    private ZoteroApiClient zoteroApiClient;

    public EpubCoversRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.userPreferences = new UserPreferences(context);
        this.zoteroApiClient = new ZoteroApiClient(context);
    }

    @Override
    public void onCreate() {
        // Initialize the data set
    }

    // Replace the onDataSetChanged method in EpubCoversRemoteViewsFactory.java:

@Override
public void onDataSetChanged() {
    // Load data from Zotero API
    if (!userPreferences.hasZoteroCredentials()) {
        coverItems.clear();
        return;
    }
    
    // Check if user has any file types enabled
    if (!userPreferences.hasAnyFileTypeEnabled()) {
        coverItems.clear();
        return;
    }

    // Use a latch to make this synchronous since onDataSetChanged must be synchronous
    final CountDownLatch latch = new CountDownLatch(1);
    
    String userId = userPreferences.getZoteroUserId();
    String apiKey = userPreferences.getZoteroApiKey();
    String collectionKey = userPreferences.getSelectedCollectionKey();
    
    coverItems.clear();

    // Use the updated getEbookItemsByCollection method that respects preferences
    zoteroApiClient.getEbookItemsByCollection(userId, apiKey, collectionKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> zoteroItems) {
            for (ZoteroItem item : zoteroItems) {
                final CountDownLatch itemLatch = new CountDownLatch(1);
                
                // Use downloadEbook instead of downloadEpub
                zoteroApiClient.downloadEbook(item, new ZoteroApiClient.FileCallback() {
                    @Override
                    public void onFileDownloaded(ZoteroItem item, String filePath) {
                        // Extract cover using the new universal extractor
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
                                
                                coverItems.add(coverItem);
                                itemLatch.countDown();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                // If cover extraction fails, still add the item but with a placeholder
                                EpubCoverItem coverItem = new EpubCoverItem(
                                        item.getKey(),
                                        item.getTitle(),
                                        null, // null cover path will show placeholder
                                        item.getAuthors(),
                                        userPreferences.getZoteroUsername()
                                );
                                
                                coverItems.add(coverItem);
                                itemLatch.countDown();
                            }
                        });
                    }

                    @Override
                    public void onError(ZoteroItem item, String errorMessage) {
                        // If download fails, still add the item but with placeholder
                        EpubCoverItem coverItem = new EpubCoverItem(
                                item.getKey(),
                                item.getTitle(),
                                null, // null cover path will show placeholder
                                item.getAuthors(),
                                userPreferences.getZoteroUsername()
                        );
                        
                        coverItems.add(coverItem);
                        itemLatch.countDown();
                    }
                });
                
                try {
                    itemLatch.await(); // Wait for this item to complete
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            latch.countDown();
        }

        @Override
        public void onError(String errorMessage) {
            latch.countDown();
        }
    });
    
    try {
        latch.await(); // Wait for data loading to complete
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    }

    @Override
    public void onDestroy() {
        coverItems.clear();
    }

    @Override
    public int getCount() {
        return coverItems.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= coverItems.size()) {
            return null;
        }

        EpubCoverItem item = coverItems.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_grid_item);

        // Set cover image
        if (item.getCoverPath() != null) {
            File coverFile = new File(item.getCoverPath());
            if (coverFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(item.getCoverPath());
                views.setImageViewBitmap(R.id.imageWidgetCover, bitmap);
            } else {
                views.setImageViewResource(R.id.imageWidgetCover, R.drawable.placeholder_cover);
            }
        } else {
            views.setImageViewResource(R.id.imageWidgetCover, R.drawable.placeholder_cover);
        }

        // Set the text according to display mode preference
        int displayMode = userPreferences.getDisplayMode();
        String displayText;

        if (displayMode == UserPreferences.DISPLAY_AUTHOR_ONLY) {
            displayText = item.getAuthors();
        } else if (displayMode == UserPreferences.DISPLAY_AUTHOR_TITLE) {
            displayText = item.getAuthors() + " - " + item.getTitle();
        } else {
            // Default to title only
            displayText = item.getTitle();
        }

        // Set the text to the widget item
        views.setTextViewText(R.id.widgetItemTitle, displayText);

        // Set fill-in intent for each item
        Bundle extras = new Bundle();
        extras.putString("itemId", item.getId());
        extras.putString("username", item.getZoteroUsername());
        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        views.setOnClickFillInIntent(R.id.imageWidgetCover, fillInIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
