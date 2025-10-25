# OAuth Login Setup Instructions

This document explains how to set up OAuth login for ZotShelf so users can log in with their Zotero credentials instead of manually entering API keys.

## Overview

ZotShelf now supports two authentication methods:

1. **OAuth Login (Easy)**: Users click "Login with Zotero" and authenticate through Zotero's website
2. **Manual API Key Entry**: Users manually create an API key and enter their credentials

## Setting Up OAuth (One-Time Setup for Developer)

To enable OAuth login, you need to register your app with Zotero and configure the OAuth credentials.

### Step 1: Register Your App with Zotero

1. Go to https://www.zotero.org/oauth/apps
2. Log in with your Zotero account
3. Click "Register a new OAuth application"
4. Fill in the application details:
   - **Application Name**: ZotShelf
   - **Description**: Android app for displaying Zotero ebook covers in a shelf
   - **Callback URL**: `zotshelf://oauth/callback`
   - **Application Homepage** (optional): https://github.com/oyvindbso/zotshelf
   - **Permissions**: Select "Allow read-only access to the library"
5. Click "Register Application"
6. You will receive:
   - **Client Key** (OAuth Consumer Key)
   - **Client Secret** (OAuth Consumer Secret)

**IMPORTANT**: Keep these credentials secret! Do not commit them to a public repository.

### Step 2: Configure OAuth Credentials

1. Open the file: `app/src/main/java/oyvindbs/zotshelf/ZoteroOAuthConfig.java`
2. Replace the placeholder values with your actual credentials:

```java
public static final String CLIENT_KEY = "your_actual_client_key_here";
public static final String CLIENT_SECRET = "your_actual_client_secret_here";
```

### Step 3: Build and Test

1. Build the app: `./gradlew build`
2. Install on device/emulator: `./gradlew installDebug`
3. Open the app and go to Settings
4. Click "Login with Zotero"
5. You should see Zotero's authorization page
6. Log in and authorize the app
7. The app should receive your credentials automatically

## How It Works

### OAuth 1.0a Flow

ZotShelf implements the standard OAuth 1.0a three-legged authentication flow:

```
1. App → Zotero: Request temporary credentials (request token)
   ↓
2. App → User: Redirect to Zotero authorization page
   ↓
3. User → Zotero: Log in and authorize ZotShelf
   ↓
4. Zotero → App: Redirect with oauth_verifier
   ↓
5. App → Zotero: Exchange verifier for access token
   ↓
6. Zotero → App: Send access token (API key)
   ↓
7. App: Store credentials and use API key for subsequent requests
```

### Components

- **ZoteroOAuthConfig.java**: Stores OAuth credentials and endpoints
- **OAuthLoginActivity.java**: Handles the OAuth flow using WebView
- **activity_oauth_login.xml**: Layout for the OAuth login screen
- **SettingsActivity.java**: Updated to include OAuth login button

### Endpoints Used

- **Request Token**: `https://www.zotero.org/oauth/request`
- **Authorization**: `https://www.zotero.org/oauth/authorize`
- **Access Token**: `https://www.zotero.org/oauth/access`
- **User Info**: `https://api.zotero.org/keys/current`

## Security Considerations

### OAuth Credentials

- **Never commit** `CLIENT_KEY` and `CLIENT_SECRET` to public repositories
- Consider using environment variables or a secure config file
- For open-source projects, provide a template config file with placeholders

### API Key Storage

- API keys are currently stored in SharedPreferences (plaintext)
- For enhanced security, consider:
  - Using Android Keystore for encryption
  - Implementing encrypted SharedPreferences
  - Adding biometric authentication

### Callback URL

- The callback URL `zotshelf://oauth/callback` is registered in:
  - Zotero's OAuth app settings
  - AndroidManifest.xml intent filter
  - ZoteroOAuthConfig.java
- These must all match exactly for OAuth to work

## Troubleshooting

### "OAuth not configured" Error

**Problem**: The app shows "OAuth not configured" when clicking "Login with Zotero"

**Solution**: Make sure you've updated `ZoteroOAuthConfig.java` with your actual client credentials

### "Failed to start OAuth" Error

**Problem**: OAuth flow fails to start

**Solutions**:
- Check internet connection
- Verify CLIENT_KEY and CLIENT_SECRET are correct
- Check Logcat for detailed error messages

### Callback Not Working

**Problem**: After authorizing on Zotero, app doesn't receive the callback

**Solutions**:
- Verify callback URL in Zotero app settings matches `zotshelf://oauth/callback`
- Check AndroidManifest.xml has the correct intent filter
- Ensure OAuthLoginActivity has `android:launchMode="singleTask"`

### "Failed to get user info" Error

**Problem**: OAuth completes but user info can't be retrieved

**Solutions**:
- Check that the API key has proper permissions
- Verify the /keys/current endpoint is accessible
- Check Logcat for HTTP response codes

## Development Tips

### Testing OAuth Flow

1. Clear app data before testing: `adb shell pm clear oyvindbs.zotshelf`
2. Check Logcat for detailed OAuth flow logs: `adb logcat | grep OAuthLoginActivity`
3. Test with different Zotero accounts to ensure it works universally

### Debugging

Enable verbose logging by checking `OAuthLoginActivity.java` - all OAuth steps are logged with the `OAuthLoginActivity` tag.

### Revoking Access

Users can revoke ZotShelf's access at: https://www.zotero.org/settings/applications

## User Experience

### First-Time Users

1. Open ZotShelf
2. Redirected to Settings (no credentials found)
3. See two options:
   - **Easy Login**: Click "Login with Zotero" button
   - **Manual**: Fill in username, user ID, and API key
4. If using OAuth:
   - Click button → Opens Zotero login page
   - Log in → Authorize app
   - Automatically return to app with credentials saved

### Existing Users

- Can continue using manually entered API keys
- Can switch to OAuth login anytime via Settings
- Old credentials are overwritten when using OAuth login

## Additional Resources

- [Zotero OAuth Documentation](https://www.zotero.org/support/dev/web_api/v3/oauth)
- [OAuth 1.0a Specification](https://oauth.net/core/1.0a/)
- [Zotero API Documentation](https://www.zotero.org/support/dev/web_api/v3/start)
- [Signpost Library Documentation](https://github.com/mttkay/signpost)

## Future Enhancements

Potential improvements to the OAuth implementation:

1. **Encrypted Storage**: Use Android Keystore to encrypt stored credentials
2. **Token Refresh**: Implement automatic token refresh (if Zotero supports it)
3. **Better Error Handling**: More user-friendly error messages
4. **Progress Indicators**: Show detailed progress during OAuth flow
5. **Account Management**: Allow switching between multiple Zotero accounts
6. **Biometric Lock**: Optional biometric authentication before accessing library

## Questions?

If you have questions or issues with OAuth setup, please:
- Check the troubleshooting section above
- Review Logcat output for detailed errors
- Create an issue on GitHub: https://github.com/oyvindbso/zotshelf/issues
