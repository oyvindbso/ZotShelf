package com.example.zoteroepubcovers;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class EpubCoversWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Create Remote Views
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        
        // Set up intent for the widget service
        Intent intent = new Intent(context, EpubCoversWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // Use the intent URI as a unique identifier
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        
        // Set the remote adapter on the GridView
        views.setRemoteAdapter(R.id.gridViewWidgetCovers, intent);
        
        // Set empty view
        views.setEmptyView(R.id.gridViewWidgetCovers, R.id.emptyWidgetView);
        
        // Setup intent template for grid item clicks
        Intent clickIntent = new Intent(context, MainActivity.class);
        clickIntent.putExtra("fromWidget", true);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(context, 0, 
                clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setPendingIntentTemplate(R.id.gridViewWidgetCovers, clickPendingIntent);
        
        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);

        // Start loading data
        loadWidgetData(context, appWidgetManager, appWidgetId, views);
    }
    
    private static void loadWidgetData(Context context, AppWidgetManager appWidgetManager, 
                                     int appWidgetId, RemoteViews views) {
        // Show progress indicator
        views.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE);
        views.setViewVisibility(R.id.emptyWidgetView, View.GONE);
        appWidgetManager.updateAppWidget(appWidgetId, views);
        
        // Load data in background
        new Thread(() -> {
            UserPreferences userPreferences = new UserPreferences(context);
            
            if (!userPreferences.hasZoteroCredentials()) {
                views.setViewVisibility(R.id.widgetProgressBar, View.GONE);
                views.setViewVisibility(R.id.emptyWidgetView, View.VISIBLE);
                views.setTextViewText(R.id.emptyWidgetView, "Please set up Zotero credentials");
                appWidgetManager.updateAppWidget(appWidgetId, views);
                return;
            }
            
            // Notify that data set has changed
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.gridViewWidgetCovers);
            
            // Hide progress indicator
            views.setViewVisibility(R.id.widgetProgressBar, View.GONE);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }).start();
    }
}