package pro.sketchware.activities.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional AI coding assistant. It never writes to project files or executes generated code.
 * API keys are encrypted with an Android Keystore AES-GCM key before being persisted.
 */
public class AiAgentActivity extends BaseAppCompatActivity {
    private static final String PREFS = "ai_agent_secure";
    private static final String KEY_ALIAS = "sketchware_ai_agent_key_v1";
    private static final String KEY_CIPHER = "api_key_ciphertext";
    private static final String KEY_IV = "api_key_iv";
    private static final String KEY_MODEL = "model";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout messages;
    private ScrollView scrollView;
    private EditText promptInput;
    private EditText contextInput;
    private Button sendButton;
    private String model = DEFAULT_MODEL;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = getPrefs().getString(KEY_MODEL, DEFAULT_MODEL);
        buildScreen();
        appendMessage("Sketchware AI", "Hi! I can explain code, diagnose build errors, and propose Java/XML changes. I will not modify project files automatically. Add relevant code or an error log in Project context before sending.");
        if (getApiKey() == null) {
            mainHandler.postDelayed(this::showApiKeyDialog, 350);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("AI Coding Agent");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(0);
        toolbar.setOnMenuItemClickListener(item -> false);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView hint = new TextView(this);
        hint.setText("Ask about Java, XML, Gradle, Android APIs and build errors");
        hint.setTextSize(13);
        hint.setPadding(dp(16), dp(8), dp(16), dp(8));
        hint.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        root.addView(hint);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(8), dp(12), dp(12));
        scrollView.addView(messages);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        contextInput = new EditText(this);
        contextInput.setHint("Optional project context: paste code, file name, or build log");
        contextInput.setTextSize(13);
        contextInput.setMinLines(2);
        contextInput.setMaxLines(5);
        contextInput.setGravity(Gravity.TOP);
        contextInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        contextInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(-1, -2);
        contextParams.setMargins(dp(12), dp(6), dp(12), dp(4));
        root.addView(contextInput, contextParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(4), dp(12), dp(12));
        promptInput = new EditText(this);
        promptInput.setHint("Message your AI agent…");
        promptInput.setTextSize(15);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(4);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.addView(promptInput, new LinearLayout.LayoutParams(0, -2, 1f));
        sendButton = new Button(this);
        sendButton.setText("Send");
        composer.addView(sendButton, new LinearLayout.LayoutParams(-2, -wrap()));
        root.addView(composer);
        sendButton.setOnClickListener(v -> sendPrompt());
        promptInput.setOnEditorActionListener((v, actionId, event) -> {
            sendPrompt();
            return true;
        });
        setContentView(root);
    }

    private void sendPrompt() {
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showApiKeyDialog();
            return;
        }
        String context = contextInput.getText().toString().trim();
        promptInput.setText("");
        contextInput.setText("");
        appendMessage("You", prompt + (context.isEmpty() ? "" : "\n\nProject context:\n" + context));
        appendMessage("Sketchware AI", "Thinking…");
        sendButton.setEnabled(false);
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(promptInput.getWindowToken(), 0);

        executor.execute(() -> {
            String answer;
            try {
                answer = requestOpenAi(apiKey, prompt, context);
            } catch (Exception e) {
                answer = "Request failed: " + safeMessage(e) + "\nCheck your API key, model, internet connection, and provider quota.";
            }
            final String result = answer;
            mainHandler.post(() -> {
                int count = messages.getChildCount();
                if (count > 0) messages.removeViewAt(count - 1);
                appendMessage("Sketchware AI", result);
                sendButton.setEnabled(true);
            });
        });
    }

    private String requestOpenAi(String apiKey, String prompt, String context) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.openai.com/v1/chat/completions").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", "You are an expert Android Studio-style coding assistant embedded in Sketchware Pro. Give complete, compatible Java/XML/Gradle solutions. Preserve existing APIs and project data. Never claim to have edited files or run builds. Prefer safe, reversible changes. If context is insufficient, state assumptions.");

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", prompt + (context.isEmpty() ? "" : "\n\nProject context:\n" + context));

            JSONArray messagesArray = new JSONArray();
            messagesArray.put(system);
            messagesArray.put(user);
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("messages", messagesArray);
            body.put("temperature", 0.2);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            JSONObject json = new JSONObject(response.toString());
            if (status < 200 || status >= 300) {
                String message = json.optJSONObject("error") == null ? "HTTP " + status :
                        json.optJSONObject("error").optString("message", "HTTP " + status);
                throw new IllegalStateException(message);
            }
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new IllegalStateException("The provider returned an empty response.");
            return choices.getJSONObject(0).getJSONObject("message").optString("content", "No text response.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("OpenAI API key (sk-…)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String existing = getApiKey();
        if (existing != null) input.setText(existing);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Configure AI provider")
                .setMessage("Your API key is encrypted using Android Keystore and stored on this device. API usage may incur charges from your provider.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "API key cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        saveApiKey(value);
                        Toast.makeText(this, "API key saved securely on this device.", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not secure the API key: " + safeMessage(e), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Change model", (dialog, which) -> showModelDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showModelDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(DEFAULT_MODEL);
        input.setText(model);
        new MaterialAlertDialogBuilder(this)
                .setTitle("OpenAI model")
                .setMessage("Enter a model available to your API account. Default: " + DEFAULT_MODEL)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        model = value;
                        getPrefs().edit().putString(KEY_MODEL, model).apply();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void appendMessage(String sender, String text) {
        TextView bubble = new TextView(this);
        bubble.setText(sender + "\n" + text);
        bubble.setTextSize(14);
        bubble.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface));
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer));
        card.addView(bubble);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        messages.addView(card, params);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveApiKey(String value) throws Exception {
        byte[] iv;
        byte[] encrypted;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        iv = cipher.getIV();
        encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        getPrefs().edit().putString(KEY_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .putString(KEY_CIPHER, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)).apply();
    }

    private String getApiKey() {
        try {
            String encoded = getPrefs().getString(KEY_CIPHER, null);
            String encodedIv = getPrefs().getString(KEY_IV, null);
            if (encoded == null || encodedIv == null) return null;
            byte[] encrypted = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP);
            byte[] iv = android.util.Base64.decode(encodedIv, android.util.Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private int resolveColor(int attribute) {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(attribute, value, true);
        if (value.resourceId != 0) return getColor(value.resourceId);
        return value.data;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int wrap() {
        return dp(48);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
