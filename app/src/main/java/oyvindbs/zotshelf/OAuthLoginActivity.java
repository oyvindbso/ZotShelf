package oyvindbs.zotshelf;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Check if OAuth is configured
        if (!ZoteroOAuthConfig.isConfigured()) {
            showError("OAuth not configured. Please update ZoteroOAuthConfig.java with your credentials from https://www.zotero.org/oauth/apps");
            return;
        }

        setupWebView();
        startOAuthFlow();
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
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
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void startOAuthFlow() {
        updateStatus("Initiating OAuth flow...");
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                // Step 1: Request temporary credentials (request token)
                Log.d(TAG, "Requesting temporary credentials...");

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
                mainHandler.post(() -> showError("Failed to start OAuth: " + e.getMessage()));
            }
        });
    }

    private void handleCallback(String callbackUrl) {
        Log.d(TAG, "Handling callback: " + callbackUrl);
        updateStatus("Completing authorization...");
        progressBar.setVisibility(View.VISIBLE);

        Uri uri = Uri.parse(callbackUrl);
        final String oauthVerifier = uri.getQueryParameter("oauth_verifier");

        if (oauthVerifier == null) {
            showError("Authorization failed: No verifier received");
            return;
        }

        executorService.execute(() -> {
            try {
                // Step 3: Exchange verifier for access token
                Log.d(TAG, "Exchanging verifier for access token...");

                provider.retrieveAccessToken(consumer, oauthVerifier);

                final String accessToken = consumer.getToken();
                final String accessTokenSecret = consumer.getTokenSecret();

                Log.d(TAG, "Access token received: " + accessToken.substring(0, 5) + "...");

                // Step 4: Get user info (userID and username)
                getUserInfo(accessToken, accessTokenSecret);

            } catch (Exception e) {
                Log.e(TAG, "Error exchanging verifier for token", e);
                mainHandler.post(() -> showError("Failed to complete OAuth: " + e.getMessage()));
            }
        });
    }

    private void getUserInfo(String apiKey, String apiKeySecret) {
        executorService.execute(() -> {
            try {
                updateStatus("Fetching user information...");

                // The Zotero API doesn't have a specific "user info" endpoint,
                // but we can use the /keys/current endpoint to get info about the current key
                URL url = new URL("https://api.zotero.org/keys/current");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);

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
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    reader.close();

                    Log.e(TAG, "Error response: " + errorResponse.toString());
                    mainHandler.post(() -> showError("Failed to get user info: " + responseCode));
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error getting user info", e);
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

            if (userID != null && username != null) {
                Log.d(TAG, "User ID: " + userID + ", Username: " + username);
                saveCredentials(username, userID, apiKey);
            } else {
                mainHandler.post(() -> showError("Failed to parse user info from response"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing user info", e);
            mainHandler.post(() -> showError("Failed to parse user info: " + e.getMessage()));
        }
    }

    private String extractJsonValue(String json, String key) {
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

        return json.substring(startIndex, endIndex);
    }

    private void saveCredentials(String username, String userId, String apiKey) {
        mainHandler.post(() -> {
            updateStatus("Saving credentials...");

            UserPreferences userPreferences = new UserPreferences(this);
            userPreferences.setZoteroUsername(username);
            userPreferences.setZoteroUserId(userId);
            userPreferences.setZoteroApiKey(apiKey);

            Toast.makeText(this, "Successfully logged in as " + username + "!", Toast.LENGTH_LONG).show();

            // Return to settings or main activity
            setResult(RESULT_OK);
            finish();
        });
    }

    private void updateStatus(String message) {
        mainHandler.post(() -> {
            statusText.setText(message);
            Log.d(TAG, "Status: " + message);
        });
    }

    private void showError(String message) {
        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);
            statusText.setText("Error: " + message);
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
