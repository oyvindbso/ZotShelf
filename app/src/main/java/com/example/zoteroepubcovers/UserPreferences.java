package com.example.zoteroepubcovers;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    
    private static final String PREF_NAME = "ZoteroEpubCoversPrefs";
    private static final String KEY_ZOTERO_USER_ID = "zotero_user_id";
    private static final String KEY_ZOTERO_API_KEY = "zotero_api_key";
    private static final String KEY_ZOTERO_USERNAME = "zotero_username";
    
    private final SharedPreferences preferences;
    
    public UserPreferences(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public boolean hasZoteroCredentials() {
        String userId = getZoteroUserId();
        String apiKey = getZoteroApiKey();
        String username = getZoteroUsername();
        
        return userId != null && !userId.isEmpty() 
                && apiKey != null && !apiKey.isEmpty()
                && username != null && !username.isEmpty();
    }
    
    public String getZoteroUserId() {
        return preferences.getString(KEY_ZOTERO_USER_ID, "");
    }
    
    public void setZoteroUserId(String userId) {
        preferences.edit().putString(KEY_ZOTERO_USER_ID, userId).apply();
    }
    
    public String getZoteroApiKey() {
        return preferences.getString(KEY_ZOTERO_API_KEY, "");
    }
    
    public void setZoteroApiKey(String apiKey) {
        preferences.edit().putString(KEY_ZOTERO_API_KEY, apiKey).apply();
    }
    
    public String getZoteroUsername() {
        return preferences.getString(KEY_ZOTERO_USERNAME, "");
    }
    
    public void setZoteroUsername(String username) {
        preferences.edit().putString(KEY_ZOTERO_USERNAME, username).apply();
    }
    
    public void clearAll() {
        preferences.edit().clear().apply();
    }
}