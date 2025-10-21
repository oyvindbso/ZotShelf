package oyvindbs.zotshelf;

/**
 * Holds diagnostic information about API calls for debugging purposes
 */
public class DiagnosticInfo {
    private String apiUrl;
    private String tags;
    private String collectionKey;
    private String collectionName;
    private int httpResponseCode;
    private String errorMessage;
    private int itemsReceived;
    private int itemsFiltered;

    public DiagnosticInfo() {
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCollectionKey() {
        return collectionKey;
    }

    public void setCollectionKey(String collectionKey) {
        this.collectionKey = collectionKey;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public int getHttpResponseCode() {
        return httpResponseCode;
    }

    public void setHttpResponseCode(int httpResponseCode) {
        this.httpResponseCode = httpResponseCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getItemsReceived() {
        return itemsReceived;
    }

    public void setItemsReceived(int itemsReceived) {
        this.itemsReceived = itemsReceived;
    }

    public int getItemsFiltered() {
        return itemsFiltered;
    }

    public void setItemsFiltered(int itemsFiltered) {
        this.itemsFiltered = itemsFiltered;
    }

    /**
     * Generate a formatted diagnostic report for display or copying
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== ZotShelf Tag Diagnostics ===\n\n");

        if (collectionKey != null && !collectionKey.isEmpty()) {
            report.append("Collection: ").append(collectionName).append("\n");
            report.append("Collection Key: ").append(collectionKey).append("\n");
        } else {
            report.append("Collection: All Collections\n");
        }

        if (tags != null && !tags.isEmpty()) {
            report.append("Tags Filter: ").append(tags).append("\n");
        } else {
            report.append("Tags Filter: None\n");
        }

        report.append("\nAPI Request:\n");
        if (apiUrl != null) {
            report.append(apiUrl).append("\n");
        } else {
            report.append("(URL not captured)\n");
        }

        report.append("\nResponse:\n");
        if (httpResponseCode > 0) {
            report.append("HTTP Status: ").append(httpResponseCode).append("\n");
        }

        if (itemsReceived >= 0) {
            report.append("Items received from API: ").append(itemsReceived).append("\n");
        }

        if (itemsFiltered >= 0) {
            report.append("Items after filtering: ").append(itemsFiltered).append("\n");
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            report.append("\nError Message:\n").append(errorMessage).append("\n");
        }

        report.append("\n=== End Diagnostics ===");

        return report.toString();
    }
}
