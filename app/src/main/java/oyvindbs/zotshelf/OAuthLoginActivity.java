package oyvindbs.zotshelf;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;

/**
 * Activity to handle OAuth 1.0a login flow with Zotero.
 *
 * Flow:
 * 1. Request temporary credentials (request token)
 * 2. Redirect user to Zotero authorization page
 * 3. User authorizes the app
 * 4. Zotero redirects back with oauth_verifier
 * 5. Exchange verifier for access token
 * 6. Save credentials and return to settings
 */
public class OAuthLoginActivity extends AppCompatActivity {

    private static final String TAG = "OAuthLoginActivity";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;

    private OAuthConsumer consumer;
    private OAuthProvider provider;
    private String requestToken;
    private String requestTokenSecret;

    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth_login);

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Check if OAuth is configured
        if (!ZoteroOAuthConfig.isConfigured()) {
            showError("OAuth not configured. Please configure OAuth credentials in Bitrise secrets or environment variables. See BITRISE_SETUP.md for instructions.");
            return;
        }

        Log.d(TAG, "OAuth is configured, starting login flow");
        setupWebView();
        startOAuthFlow();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupWebView() {
        try {
            // Enable JavaScript
            webView.getSettings().setJavaScriptEnabled(true);

            // Enable DOM storage (required for some OAuth pages)
            webView.getSettings().setDomStorageEnabled(true);

            // Allow mixed content (HTTP and HTTPS)
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

            // Enable zoom controls
            webView.getSettings().setBuiltInZoomControls(true);
            webView.getSettings().setDisplayZoomControls(false);

            // Set user agent
            webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " ZotShelf/1.0");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    Log.d(TAG, "URL Loading: " + url);

                    // Check if this is our callback URL
                    if (url.startsWith(ZoteroOAuthConfig.CALLBACK_URL)) {
                        handleCallback(url);
                        return true;
                    }

                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    Log.e(TAG, "WebView error: " + error.getDescription());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up WebView", e);
            showError("Failed to initialize WebView: " + e.getMessage());
        }
    }

    private void startOAuthFlow() {
        updateStatus("Initiating OAuth flow...");
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                // Step 1: Request temporary credentials (request token)
                Log.d(TAG, "Requesting temporary credentials...");
                Log.d(TAG, "Client Key: " + (ZoteroOAuthConfig.CLIENT_KEY != null ? ZoteroOAuthConfig.CLIENT_KEY.substring(0, Math.min(5, ZoteroOAuthConfig.CLIENT_KEY.length())) + "..." : "null"));

                consumer = new DefaultOAuthConsumer(
                        ZoteroOAuthConfig.CLIENT_KEY,
                        ZoteroOAuthConfig.CLIENT_SECRET
                );

                provider = new DefaultOAuthProvider(
                        ZoteroOAuthConfig.REQUEST_TOKEN_URL,
                        ZoteroOAuthConfig.ACCESS_TOKEN_URL,
                        ZoteroOAuthConfig.AUTHORIZE_URL
                );

                // Get the authorization URL
                final String authUrl = provider.retrieveRequestToken(consumer, ZoteroOAuthConfig.CALLBACK_URL);

                // Store the request token and secret for later
                requestToken = consumer.getToken();
                requestTokenSecret = consumer.getTokenSecret();

                Log.d(TAG, "Authorization URL: " + authUrl);

                // Step 2: Load the authorization URL in WebView
                mainHandler.post(() -> {
                    updateStatus("Please authorize ZotShelf...");
                    webView.loadUrl(authUrl);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in OAuth flow", e);
                e.printStackTrace();
                mainHandler.post(() -> showError("Failed to start OAuth: " + e.getMessage() + ". Please check your OAuth credentials."));
            }
        });
    }

    private void handleCallback(String callbackUrl) {
        Log.d(TAG, "Handling callback: " + callbackUrl);
        updateStatus("Completing authorization...");
        progressBar.setVisibility(View.VISIBLE);

        Uri uri = Uri.parse(callbackUrl);
        final String oauthVerifier = uri.getQueryParameter("oauth_verifier");

        if (oauthVerifier == null || oauthVerifier.isEmpty()) {
            showError("Authorization failed: No verifier received");
            return;
        }

        Log.d(TAG, "Verifier received, exchanging for access token...");

        executorService.execute(() -> {
            try {
                // Step 3: Exchange verifier for access token
                Log.d(TAG, "Exchanging verifier for access token...");

                provider.retrieveAccessToken(consumer, oauthVerifier);

                final String accessToken = consumer.getToken();
                final String accessTokenSecret = consumer.getTokenSecret();

                if (accessToken == null || accessToken.isEmpty()) {
                    mainHandler.post(() -> showError("Failed to receive access token"));
                    return;
                }

                Log.d(TAG, "Access token received: " + accessToken.substring(0, Math.min(5, accessToken.length())) + "...");

                // Step 4: Get user info (userID and username)
                getUserInfo(accessToken, accessTokenSecret);

            } catch (Exception e) {
                Log.e(TAG, "Error exchanging verifier for token", e);
                e.printStackTrace();
                mainHandler.post(() -> showError("Failed to complete OAuth: " + e.getMessage()));
            }
        });
    }

    private void getUserInfo(String apiKey, String apiKeySecret) {
        executorService.execute(() -> {
            try {
                mainHandler.post(() -> updateStatus("Fetching user information..."));

                // Use the /keys/current endpoint to get info about the current key
                URL url = new URL("https://api.zotero.org/keys/current");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Zotero uses "Zotero-API-Key" header, not "Authorization: Bearer"
                connection.setRequestProperty("Zotero-API-Key", apiKey);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "User info response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    Log.d(TAG, "User info response: " + response.toString());

                    // Parse the JSON response to get userID and username
                    parseUserInfo(response.toString(), apiKey);
                } else {
                    // Read error response
                    String errorResponse = "";
                    try {
                        if (connection.getErrorStream() != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                            StringBuilder errorBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                errorBuilder.append(line);
                            }
                            reader.close();
                            errorResponse = errorBuilder.toString();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading error stream", e);
                    }

                    Log.e(TAG, "Error response (" + responseCode + "): " + errorResponse);
                    final String finalErrorResponse = errorResponse;
                    mainHandler.post(() -> showError("Failed to get user info: HTTP " + responseCode +
                            (finalErrorResponse.isEmpty() ? "" : " - " + finalErrorResponse)));
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error getting user info", e);
                e.printStackTrace();
                mainHandler.post(() -> showError("Failed to get user info: " + e.getMessage()));
            }
        });
    }

    private void parseUserInfo(String jsonResponse, String apiKey) {
        try {
            // Parse JSON manually (simple parsing without adding Gson dependency)
            // Expected format: {"userID":123456,"username":"exampleuser",...}

            String userID = extractJsonValue(jsonResponse, "userID");
            String username = extractJsonValue(jsonResponse, "username");

            if (userID != null && username != null && !userID.isEmpty() && !username.isEmpty()) {
                Log.d(TAG, "User ID: " + userID + ", Username: " + username);
                saveCredentials(username, userID, apiKey);
            } else {
                Log.e(TAG, "Failed to parse user info. UserID: " + userID + ", Username: " + username);
                Log.e(TAG, "JSON Response: " + jsonResponse);
                mainHandler.post(() -> showError("Failed to parse user info from response. Please try again."));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing user info", e);
            e.printStackTrace();
            mainHandler.post(() -> showError("Failed to parse user info: " + e.getMessage()));
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            // Simple JSON value extraction
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) {
                return null;
            }

            startIndex += searchKey.length();

            // Skip whitespace
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            if (startIndex >= json.length()) {
                return null;
            }

            // Check if value is a string (starts with quote) or number
            boolean isString = json.charAt(startIndex) == '"';
            if (isString) {
                startIndex++; // Skip opening quote
            }

            int endIndex = startIndex;
            if (isString) {
                // Find closing quote
                while (endIndex < json.length() && json.charAt(endIndex) != '"') {
                    endIndex++;
                }
            } else {
                // Find end of number (comma, brace, or whitespace)
                while (endIndex < json.length() &&
                       !Character.isWhitespace(json.charAt(endIndex)) &&
                       json.charAt(endIndex) != ',' &&
                       json.charAt(endIndex) != '}') {
                    endIndex++;
                }
            }

            if (endIndex > startIndex && endIndex <= json.length()) {
                return json.substring(startIndex, endIndex);
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting JSON value for key: " + key, e);
            return null;
        }
    }

    private void saveCredentials(String username, String userId, String apiKey) {
        mainHandler.post(() -> {
            try {
                updateStatus("Saving credentials...");

                UserPreferences userPreferences = new UserPreferences(this);
                userPreferences.setZoteroUsername(username);
                userPreferences.setZoteroUserId(userId);
                userPreferences.setZoteroApiKey(apiKey);

                Toast.makeText(this, "Successfully logged in as " + username + "!", Toast.LENGTH_LONG).show();

                // Return to settings or main activity
                setResult(RESULT_OK);
                finish();
            } catch (Exception e) {
                Log.e(TAG, "Error saving credentials", e);
                showError("Failed to save credentials: " + e.getMessage());
            }
        });
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(message);
            }
            Log.d(TAG, "Status: " + message);
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            if (statusText != null) {
                statusText.setText("Error: " + message);
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error: " + message);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (webView != null) {
            webView.destroy();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle OAuth callback if activity was already running
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(ZoteroOAuthConfig.CALLBACK_URL)) {
            handleCallback(data.toString());
        }
    }
}
