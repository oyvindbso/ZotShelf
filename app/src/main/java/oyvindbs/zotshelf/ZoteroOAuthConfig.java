package oyvindbs.zotshelf;

/**
 * Configuration class for Zotero OAuth credentials.
 *
 * OAuth credentials are injected at build time from environment variables:
 * - ZOTERO_OAUTH_CLIENT_KEY: Your OAuth client key from Zotero
 * - ZOTERO_OAUTH_CLIENT_SECRET: Your OAuth client secret from Zotero
 *
 * For Bitrise CI/CD:
 * 1. Register your app at https://www.zotero.org/oauth/apps
 * 2. Get your Client Key and Client Secret
 * 3. Add them as secrets in Bitrise:
 *    - Secret key: ZOTERO_OAUTH_CLIENT_KEY
 *    - Secret key: ZOTERO_OAUTH_CLIENT_SECRET
 * 4. Set the callback URL in Zotero to: zotshelf://oauth/callback
 *
 * For local development:
 * Set environment variables before building:
 * export ZOTERO_OAUTH_CLIENT_KEY="your_key"
 * export ZOTERO_OAUTH_CLIENT_SECRET="your_secret"
 */
public class ZoteroOAuthConfig {

    // OAuth credentials injected from BuildConfig (environment variables)
    public static final String CLIENT_KEY = BuildConfig.ZOTERO_OAUTH_CLIENT_KEY;
    public static final String CLIENT_SECRET = BuildConfig.ZOTERO_OAUTH_CLIENT_SECRET;

    // OAuth endpoints
    public static final String REQUEST_TOKEN_URL = "https://www.zotero.org/oauth/request";
    public static final String AUTHORIZE_URL = "https://www.zotero.org/oauth/authorize";
    public static final String ACCESS_TOKEN_URL = "https://www.zotero.org/oauth/access";

    // Callback URL - must match what you registered with Zotero
    public static final String CALLBACK_URL = "zotshelf://oauth/callback";

    /**
     * Check if OAuth credentials have been configured
     */
    public static boolean isConfigured() {
        return !CLIENT_KEY.equals("YOUR_CLIENT_KEY_HERE") &&
               !CLIENT_SECRET.equals("YOUR_CLIENT_SECRET_HERE") &&
               !CLIENT_KEY.isEmpty() &&
               !CLIENT_SECRET.isEmpty();
    }
}
