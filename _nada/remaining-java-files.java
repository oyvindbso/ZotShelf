// Complete UserPreferences.java file
package com.example.zotshelf;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    
    private static final String PREF_NAME = "zotshelfPrefs";
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

// SettingsActivity.java
package com.example.zotshelf;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText editZoteroUsername;
    private TextInputEditText editZoteroUserId;
    private TextInputEditText editZoteroApiKey;
    private Button buttonSave;
    private UserPreferences userPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Enable the back button in the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_activity_settings);
        }

        // Initialize views
        editZoteroUsername = findViewById(R.id.editZoteroUsername);
        editZoteroUserId = findViewById(R.id.editZoteroUserId);
        editZoteroApiKey = findViewById(R.id.editZoteroApiKey);
        buttonSave = findViewById(R.id.buttonSave);

        // Initialize preferences
        userPreferences = new UserPreferences(this);

        // Load existing values
        loadPreferences();

        // Setup save button
        buttonSave.setOnClickListener(v -> savePreferences());
    }

    private void loadPreferences() {
        editZoteroUsername.setText(userPreferences.getZoteroUsername());
        editZoteroUserId.setText(userPreferences.getZoteroUserId());
        editZoteroApiKey.setText(userPreferences.getZoteroApiKey());
    }

    private void savePreferences() {
        String username = editZoteroUsername.getText().toString().trim();
        String userId = editZoteroUserId.getText().toString().trim();
        String apiKey = editZoteroApiKey.getText().toString().trim();

        // Validate inputs
        if (username.isEmpty() || userId.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, R.string.enter_credentials, Toast.LENGTH_SHORT).show();
            return;
        }

        // Save preferences
        userPreferences.setZoteroUsername(username);
        userPreferences.setZoteroUserId(userId);
        userPreferences.setZoteroApiKey(apiKey);

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
