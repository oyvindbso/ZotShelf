package com.example.zoteroepubcovers;

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