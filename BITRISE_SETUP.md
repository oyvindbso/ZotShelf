# Bitrise Setup for ZotShelf OAuth

This document provides a quick reference for setting up OAuth credentials in Bitrise CI/CD.

## Quick Setup

### 1. Register OAuth App with Zotero

1. Go to https://www.zotero.org/oauth/apps
2. Register a new OAuth application:
   - **Application Name**: ZotShelf
   - **Callback URL**: `zotshelf://oauth/callback`
   - **Permissions**: Read-only access to library
3. Save your **Client Key** and **Client Secret**

### 2. Configure Bitrise Secrets

1. Go to your Bitrise dashboard for ZotShelf
2. Navigate to **Workflow → Secrets** (or click the padlock icon)
3. Click **Add new** and add these two secrets:

   **Secret 1:**
   - **Key**: `ZOTERO_OAUTH_CLIENT_KEY`
   - **Value**: Paste your Client Key from Zotero
   - ✓ Check "Expose for Pull Requests" (if you want OAuth to work in PR builds)

   **Secret 2:**
   - **Key**: `ZOTERO_OAUTH_CLIENT_SECRET`
   - **Value**: Paste your Client Secret from Zotero
   - ✓ Check "Expose for Pull Requests" (if you want OAuth to work in PR builds)

4. Click **Save** for each secret

### 3. Verify Setup

The next build on Bitrise will automatically:
1. Read the secrets as environment variables
2. Inject them into BuildConfig during the Gradle build
3. Make them available to the app via `BuildConfig.ZOTERO_OAUTH_CLIENT_KEY` and `BuildConfig.ZOTERO_OAUTH_CLIENT_SECRET`

### 4. Testing

After the build completes:
1. Install the APK on a device
2. Open ZotShelf → Settings
3. Click "Login with Zotero"
4. If configured correctly, you'll see Zotero's login page
5. After authorizing, you should be logged in automatically

## Important Notes

### Secret Key Names

The secret keys MUST be exactly:
- `ZOTERO_OAUTH_CLIENT_KEY` (not CLIENT-KEY, client_key, or any variation)
- `ZOTERO_OAUTH_CLIENT_SECRET`

These match what's configured in `app/build.gradle`:

```gradle
buildConfigField "String", "ZOTERO_OAUTH_CLIENT_KEY",
    "\"${System.getenv('ZOTERO_OAUTH_CLIENT_KEY') ?: 'YOUR_CLIENT_KEY_HERE'}\""
```

### Expose for Pull Requests

If you enable "Expose for Pull Requests", the OAuth credentials will be available in builds triggered by pull requests from forks.

**Considerations:**
- ✅ **Enable if**: You want contributors to test OAuth functionality
- ⚠️ **Disable if**: You're concerned about credential exposure (though Bitrise doesn't expose secrets in logs)

For open-source projects, it's generally safe to enable this since:
1. The credentials only allow read-only access to users who authorize your app
2. Bitrise masks secrets in build logs
3. Contributors can't extract the credentials from the APK easily

### Build Configuration

The OAuth credentials are configured in these files:

1. **app/build.gradle** - Reads environment variables and generates BuildConfig
2. **app/src/main/java/oyvindbs/zotshelf/ZoteroOAuthConfig.java** - References BuildConfig values
3. **Bitrise Secrets** - Provides the actual credential values

## Troubleshooting

### Build succeeds but OAuth shows "not configured"

**Check:**
1. Secret key names match exactly (case-sensitive)
2. Secrets are marked as "Expose for Pull Requests" if building a PR
3. Build logs show the secrets are being read (they'll be masked but you can see if they're present)

**Solution:**
Rebuild from scratch in Bitrise (click Rebuild with Clean Cache)

### "Cannot find symbol: variable BuildConfig"

**Issue:** BuildConfig isn't being generated

**Solution:**
Verify that `app/build.gradle` contains:
```gradle
buildFeatures {
    buildConfig = true
}
```

This is required in newer versions of Android Gradle Plugin.

### Secrets not available in PR builds

**Issue:** Pull request builds fail or show placeholder credentials

**Solution:**
Enable "Expose for Pull Requests" for both secrets in Bitrise

## Security

### Are the secrets safe?

Yes! Bitrise uses the following security measures:

1. **Encrypted storage** - Secrets are encrypted at rest
2. **Masked in logs** - Secret values are replaced with `[REDACTED]` in build logs
3. **Limited exposure** - Only available to your app's builds
4. **Access control** - Only team members with proper permissions can view/edit secrets

### Can someone extract credentials from the APK?

Technically yes, through APK decompilation, but:
- The OAuth credentials only allow users to authorize YOUR app (not access their accounts directly)
- Users must explicitly grant permission for your app to access their library
- Permissions are read-only if configured correctly
- Users can revoke access anytime at https://www.zotero.org/settings/applications

This is why OAuth is more secure than API key entry - even if someone extracts the credentials, they can't do anything malicious with them.

## Additional Resources

- [OAUTH_SETUP.md](OAUTH_SETUP.md) - Complete OAuth implementation guide
- [Bitrise Secret Management](https://devcenter.bitrise.io/en/builds/secrets.html)
- [Zotero OAuth Documentation](https://www.zotero.org/support/dev/web_api/v3/oauth)
- [Android BuildConfig](https://developer.android.com/build/gradle-tips#share-custom-fields-and-resource-values-with-your-app-code)

## Quick Reference

| What | Where | Purpose |
|------|-------|---------|
| Register OAuth App | https://www.zotero.org/oauth/apps | Get credentials |
| Add Secrets | Bitrise → Workflow → Secrets | Store credentials securely |
| Key 1 | `ZOTERO_OAUTH_CLIENT_KEY` | OAuth client key |
| Key 2 | `ZOTERO_OAUTH_CLIENT_SECRET` | OAuth client secret |
| Callback URL | `zotshelf://oauth/callback` | Must match in Zotero & app |
| Build Config | `app/build.gradle` | Injects secrets |
| Usage | `ZoteroOAuthConfig.java` | References BuildConfig |
