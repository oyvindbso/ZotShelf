package oyvindbs.zotshelf;


import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText editZoteroUsername;
    private TextInputEditText editZoteroUserId;
    private TextInputEditText editZoteroApiKey;
    private CheckBox checkBoxShowEpubs;
    private CheckBox checkBoxShowPdfs;
    private CheckBox checkBoxBooksOnly;
    private RadioGroup radioGroupDisplayMode;
    private RadioButton radioTitleOnly;
    private RadioButton radioAuthorOnly;
    private RadioButton radioAuthorTitle;
    private Button buttonSave;
    private Button buttonOAuthLogin;
    private UserPreferences userPreferences;

    private static final int OAUTH_LOGIN_REQUEST = 1001;
    private static final String API_KEY_PLACEHOLDER = "••••••••••••••••";

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
        checkBoxShowEpubs = findViewById(R.id.checkBoxShowEpubs);
        checkBoxShowPdfs = findViewById(R.id.checkBoxShowPdfs);
        checkBoxBooksOnly = findViewById(R.id.checkBoxBooksOnly);
        radioGroupDisplayMode = findViewById(R.id.radioGroupDisplayMode);
        radioTitleOnly = findViewById(R.id.radioTitleOnly);
        radioAuthorOnly = findViewById(R.id.radioAuthorOnly);
        radioAuthorTitle = findViewById(R.id.radioAuthorTitle);
        buttonSave = findViewById(R.id.buttonSave);
        buttonOAuthLogin = findViewById(R.id.buttonOAuthLogin);

        // Initialize preferences
        userPreferences = new UserPreferences(this);

        // Load existing values
        loadPreferences();

        // Setup save button
        buttonSave.setOnClickListener(v -> savePreferences());

        // Setup OAuth login button
        buttonOAuthLogin.setOnClickListener(v -> startOAuthLogin());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add a menu option for verifying credentials
        menu.add(Menu.NONE, 1, Menu.NONE, "Verify Credentials")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == 1) { // Verify Credentials option
            // Launch the credential verification activity
            Intent intent = new Intent(this, CredentialVerificationActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadPreferences() {
        // Load Zotero credentials
        editZoteroUsername.setText(userPreferences.getZoteroUsername());
        editZoteroUserId.setText(userPreferences.getZoteroUserId());

        // Show placeholder for API key if it exists, never show the actual key
        String existingApiKey = userPreferences.getZoteroApiKey();
        if (existingApiKey != null && !existingApiKey.isEmpty()) {
            editZoteroApiKey.setHint("API Key configured (enter new key to update)");
            editZoteroApiKey.setText("");
        } else {
            editZoteroApiKey.setHint("Zotero API Key");
            editZoteroApiKey.setText("");
        }

        // Load file type preferences
        checkBoxShowEpubs.setChecked(userPreferences.getShowEpubs());
        checkBoxShowPdfs.setChecked(userPreferences.getShowPdfs());
        checkBoxBooksOnly.setChecked(userPreferences.getBooksOnly());

        // Set the display mode radio button
        int displayMode = userPreferences.getDisplayMode();
        switch (displayMode) {
            case UserPreferences.DISPLAY_AUTHOR_ONLY:
                radioAuthorOnly.setChecked(true);
                break;
            case UserPreferences.DISPLAY_AUTHOR_TITLE:
                radioAuthorTitle.setChecked(true);
                break;
            case UserPreferences.DISPLAY_TITLE_ONLY:
            default:
                radioTitleOnly.setChecked(true);
                break;
        }
    }

    private void savePreferences() {
        String username = editZoteroUsername.getText().toString().trim();
        String userId = editZoteroUserId.getText().toString().trim();
        String apiKey = editZoteroApiKey.getText().toString().trim();

        // Check if API key field is empty - if so, keep the existing one
        String existingApiKey = userPreferences.getZoteroApiKey();
        boolean hasExistingApiKey = existingApiKey != null && !existingApiKey.isEmpty();

        // Validate inputs
        if (username.isEmpty() || userId.isEmpty()) {
            Toast.makeText(this, R.string.enter_credentials, Toast.LENGTH_SHORT).show();
            return;
        }

        // If API key field is empty and there's no existing key, show error
        if (apiKey.isEmpty() && !hasExistingApiKey) {
            Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate file type selection
        boolean showEpubs = checkBoxShowEpubs.isChecked();
        boolean showPdfs = checkBoxShowPdfs.isChecked();
        boolean booksOnly = checkBoxBooksOnly.isChecked();

        if (!showEpubs && !showPdfs) {
            Toast.makeText(this, "Please select at least one file type to display", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save Zotero credentials
        userPreferences.setZoteroUsername(username);
        userPreferences.setZoteroUserId(userId);

        // Only update API key if a new one was entered
        if (!apiKey.isEmpty()) {
            userPreferences.setZoteroApiKey(apiKey);
        }

        // Save file type preferences
        userPreferences.setShowEpubs(showEpubs);
        userPreferences.setShowPdfs(showPdfs);
        userPreferences.setBooksOnly(booksOnly);

        // Save display mode
        int displayMode;
        int selectedRadioButtonId = radioGroupDisplayMode.getCheckedRadioButtonId();
        if (selectedRadioButtonId == R.id.radioAuthorOnly) {
            displayMode = UserPreferences.DISPLAY_AUTHOR_ONLY;
        } else if (selectedRadioButtonId == R.id.radioAuthorTitle) {
            displayMode = UserPreferences.DISPLAY_AUTHOR_TITLE;
        } else {
            displayMode = UserPreferences.DISPLAY_TITLE_ONLY;
        }
        userPreferences.setDisplayMode(displayMode);

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void startOAuthLogin() {
        Intent intent = new Intent(this, OAuthLoginActivity.class);
        startActivityForResult(intent, OAUTH_LOGIN_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OAUTH_LOGIN_REQUEST && resultCode == RESULT_OK) {
            // OAuth login was successful, reload preferences to show the new credentials
            loadPreferences();
            Toast.makeText(this, "Successfully logged in with Zotero!", Toast.LENGTH_LONG).show();
        }
    }
}