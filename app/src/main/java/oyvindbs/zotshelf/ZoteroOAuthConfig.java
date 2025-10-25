package oyvindbs.zotshelf;

/**
 * Configuration class for Zotero OAuth credentials.
 *
 * To use OAuth login:
 * 1. Register your app at https://www.zotero.org/oauth/apps
 * 2. Get your Client Key and Client Secret
 * 3. Update the CLIENT_KEY and CLIENT_SECRET values below
 * 4. Set the callback URL in Zotero to: zotshelf://oauth/callback
 */
public class ZoteroOAuthConfig {

    // TODO: Replace these with your actual OAuth credentials from https://www.zotero.org/oauth/apps
    public static final String CLIENT_KEY = "YOUR_CLIENT_KEY_HERE";
    public static final String CLIENT_SECRET = "YOUR_CLIENT_SECRET_HERE";

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
               !CLIENT_SECRET.equals("YOUR_CLIENT_SECRET_HERE");
    }
}
