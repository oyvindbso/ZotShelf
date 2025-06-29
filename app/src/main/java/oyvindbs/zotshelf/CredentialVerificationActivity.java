package oyvindbs.zotshelf;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class CredentialVerificationActivity extends AppCompatActivity {

    private static final String TAG = "CredentialVerification";
    private TextView statusTextView;
    private Button verifyButton;
    private ProgressBar progressBar;
    private UserPreferences userPreferences;
    private ZoteroApiClient zoteroApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credential_verification);

        statusTextView = findViewById(R.id.textViewStatus);
        verifyButton = findViewById(R.id.buttonVerify);
        progressBar = findViewById(R.id.progressBarVerify);

        userPreferences = new UserPreferences(this);
        zoteroApiClient = new ZoteroApiClient(this);

        verifyButton.setOnClickListener(v -> verifyCredentials());

        // Display current credentials
        displayCurrentCredentials();
    }

    private void displayCurrentCredentials() {
        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();
        String username = userPreferences.getZoteroUsername();

        StringBuilder sb = new StringBuilder("Current Credentials:\n\n");
        sb.append("Username: ").append(username).append("\n");
        sb.append("User ID: ").append(userId).append("\n");
        sb.append("API Key: ").append(apiKey != null && apiKey.length() > 5 ? 
                apiKey.substring(0, 5) + "..." : apiKey).append("\n\n");
        sb.append("Click 'Verify' to test these credentials with the Zotero API.");

        statusTextView.setText(sb.toString());
    }

    private void verifyCredentials() {
        if (!userPreferences.hasZoteroCredentials()) {
            statusTextView.setText("Error: Missing credentials. Please set them in Settings first.");
            return;
        }

        showLoading();
        String userId = userPreferences.getZoteroUserId();
        String apiKey = userPreferences.getZoteroApiKey();
        
        // First try to get collections
        appendToStatus("Testing Collection API...");
        
        zoteroApiClient.getCollections(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroCollection>>() {
            @Override
            public void onSuccess(List<ZoteroCollection> collections) {
                StringBuilder sb = new StringBuilder();
                sb.append("✓ Collections API Success: Found " + collections.size() + " collections.\n");
                
                if (!collections.isEmpty()) {
                    sb.append("\nCollection Examples:\n");
                    int count = Math.min(collections.size(), 3);
                    for (int i = 0; i < count; i++) {
                        ZoteroCollection collection = collections.get(i);
                        sb.append("- ").append(collection.getName())
                          .append(" (Key: ").append(collection.getKey()).append(")\n");
                    }
                }
                
                // Now test items API
                appendToStatus(sb.toString());
                testItemsApi();
            }
            
            @Override
            public void onError(String errorMessage) {
                String error = "✗ Collections API Failed: " + errorMessage;
                appendToStatus(error);
                hideLoading();
            }
        });
    }
    
    // Replace the testItemsApi method in CredentialVerificationActivity.java:

private void testItemsApi() {
    String userId = userPreferences.getZoteroUserId();
    String apiKey = userPreferences.getZoteroApiKey();
    
    appendToStatus("\nTesting Items API...");
    
    // Check what file types are enabled
    boolean showEpubs = userPreferences.getShowEpubs();
    boolean showPdfs = userPreferences.getShowPdfs();
    
    StringBuilder enabledTypes = new StringBuilder();
    if (showEpubs && showPdfs) {
        enabledTypes.append("EPUB and PDF files");
    } else if (showEpubs) {
        enabledTypes.append("EPUB files only");
    } else if (showPdfs) {
        enabledTypes.append("PDF files only");
    } else {
        appendToStatus("⚠ No file types are enabled in settings!");
        hideLoading();
        return;
    }
    
    appendToStatus("Enabled file types: " + enabledTypes.toString());
    
    zoteroApiClient.getEbookItems(userId, apiKey, new ZoteroApiClient.ZoteroCallback<List<ZoteroItem>>() {
        @Override
        public void onSuccess(List<ZoteroItem> items) {
            StringBuilder sb = new StringBuilder();
            sb.append("✓ Items API Success: Found " + items.size() + " ebook items matching your preferences.\n");
            
            if (!items.isEmpty()) {
                sb.append("\nEbook Examples:\n");
                int count = Math.min(items.size(), 3);
                for (int i = 0; i < count; i++) {
                    ZoteroItem item = items.get(i);
                    String fileType = item.getMimeType().equals("application/epub+zip") ? "EPUB" : "PDF";
                    sb.append("- ").append(item.getTitle())
                      .append(" (").append(fileType).append(")")
                      .append(" (Key: ").append(item.getKey()).append(")\n");
                }
            } else {
                sb.append("\nNo items found matching your file type preferences. ");
                sb.append("You may need to adjust your file type settings or check if you have ");
                sb.append(enabledTypes.toString()).append(" in your Zotero library.");
            }
            
            appendToStatus(sb.toString());
            hideLoading();
        }
        
        @Override
        public void onError(String errorMessage) {
            String error = "✗ Items API Failed: " + errorMessage;
            appendToStatus(error);
            hideLoading();
        }
    });
    }
    
    private void appendToStatus(String text) {
        runOnUiThread(() -> {
            String currentText = statusTextView.getText().toString();
            statusTextView.setText(currentText + "\n\n" + text);
        });
    }
    
    private void showLoading() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            verifyButton.setEnabled(false);
        });
    }
    
    private void hideLoading() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            verifyButton.setEnabled(true);
        });
    }
}