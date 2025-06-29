// Add these methods to your existing ZoteroApiClient.java class:
/**
 * Get ebook items filtered by user preferences
 */
public void getEbookItems(String userId, String apiKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        Call<List<ZoteroItem>> call = zoteroService.getItems(userId, apiKey, "json", "attachment", 100);
        
        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> allItems = response.body();
                List<ZoteroItem> filteredItems = filterItemsByUserPreferences(allItems);
                
                callback.onSuccess(filteredItems);
            } else {
                callback.onError("Failed to fetch items: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    });
}
/**
 * Get ebook items by collection, filtered by user preferences
 */
public void getEbookItemsByCollection(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    executor.execute(() -> {
        // If no collection selected, get all items
        if (collectionKey == null || collectionKey.isEmpty()) {
            getEbookItems(userId, apiKey, callback);
            return;
        }

        Call<List<ZoteroItem>> call = zoteroService.getItemsByCollection(userId, collectionKey, apiKey, "json", "attachment", 100);

        try {
            Response<List<ZoteroItem>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ZoteroItem> allItems = response.body();
                List<ZoteroItem> filteredItems = filterItemsByUserPreferences(allItems);

                callback.onSuccess(filteredItems);
            } else {
                callback.onError("Failed to fetch items: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API error", e);
            callback.onError("Network error: " + e.getMessage());
        }
    });
}
/**
 * Filter items based on user preferences for file types
 */
private List<ZoteroItem> filterItemsByUserPreferences(List<ZoteroItem> allItems) {
    UserPreferences prefs = new UserPreferences(context);
    boolean showEpubs = prefs.getShowEpubs();
    boolean showPdfs = prefs.getShowPdfs();
    
    List<ZoteroItem> filteredItems = new ArrayList<>();
    
    for (ZoteroItem item : allItems) {
        String mimeType = item.getMimeType();
        if (mimeType != null) {
            if (mimeType.equals("application/epub+zip") && showEpubs) {
                filteredItems.add(item);
            } else if (mimeType.equals("application/pdf") && showPdfs) {
                filteredItems.add(item);
            }
        }
    }
    
    return filteredItems;
}

/**
 * Check if the user has any file types enabled
 */
public boolean hasEnabledFileTypes() {
    UserPreferences prefs = new UserPreferences(context);
    return prefs.hasAnyFileTypeEnabled();
}

/**
 * Download ebook (EPUB or PDF) file
 */
public void downloadEbook(ZoteroItem item, FileCallback callback) {
    executor.execute(() -> {
        // Determine file extension based on MIME type
        String fileExtension;
        String mimeType = item.getMimeType();
        if ("application/epub+zip".equals(mimeType)) {
            fileExtension = ".epub";
        } else if ("application/pdf".equals(mimeType)) {
            fileExtension = ".pdf";
        } else {
            callback.onError(item, "Unsupported file type: " + mimeType);
            return;
        }
        
        // Check if we have the file cached already
        String fileName = item.getKey() + fileExtension;
        File ebookFile = new File(cacheDir, fileName);
        
        if (ebookFile.exists()) {
            callback.onFileDownloaded(item, ebookFile.getAbsolutePath());
            return;
        }
        
        // Get the download URL from the item
        if (item.getLinks() == null || item.getLinks().getEnclosure() == null) {
            callback.onError(item, "No download link available");
            return;
        }
        
        String downloadUrl = item.getLinks().getEnclosure().getHref();
        String apiKey = new UserPreferences(context).getZoteroApiKey();
        
        Call<ResponseBody> call = zoteroService.downloadFile(downloadUrl, apiKey);
        
        try {
            Response<ResponseBody> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                boolean success = writeResponseBodyToDisk(response.body(), ebookFile);
                
                if (success) {
                    callback.onFileDownloaded(item, ebookFile.getAbsolutePath());
                } else {
                    callback.onError(item, "Failed to save file");
                }
            } else {
                callback.onError(item, "Failed to download file: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Download error", e);
            callback.onError(item, "Network error: " + e.getMessage());
        }
    });
}

/**
 * Get ebook items with metadata (replaces getEpubItemsWithMetadata)
 */
public void getEbookItemsWithMetadata(String userId, String apiKey, String collectionKey, ZoteroCallback<List<ZoteroItem>> callback) {
    // First get all ebook items
    ZoteroCallback<List<ZoteroItem>> ebookCallback = new ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> ebookItems) {
            if (ebookItems.isEmpty()) {
                callback.onSuccess(ebookItems);
                return;
            }
            
            // For each ebook item, fetch its parent item if it has one
            final List<ZoteroItem> processedItems = new ArrayList<>();
            final int[] itemsToProcess = {ebookItems.size()};
            
            for (ZoteroItem ebookItem : ebookItems) {
                String parentKey = ebookItem.getParentItemKey();
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    getParentItem(userId, apiKey, parentKey, new ZoteroCallback<ZoteroItem>() {
                        @Override
                        public void onSuccess(ZoteroItem parentItem) {
                            ebookItem.setParentItem(parentItem);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                callback.onSuccess(processedItems);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error fetching parent item: " + errorMessage);
                            processedItems.add(ebookItem);
                            
                            itemsToProcess[0]--;
                            if (itemsToProcess[0] == 0) {
                                callback.onSuccess(processedItems);
                            }
                        }
                    });
                } else {
                    processedItems.add(ebookItem);
                    
                    itemsToProcess[0]--;
                    if (itemsToProcess[0] == 0) {
                        callback.onSuccess(processedItems);
                    }
                }
            }
        }
        
        @Override
        public void onError(String errorMessage) {
            callback.onError(errorMessage);
        }
    };
    
    // Get ebook items first
    if (collectionKey == null || collectionKey.isEmpty()) {
        getEbookItems(userId, apiKey, ebookCallback);
    } else {
        getEbookItemsByCollection(userId, apiKey, collectionKey, ebookCallback);
    }
}