package com.example.watchai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all encrypted persistent storage: API key, voice shortcuts, watch MAC address, theme.
 */
public final class PrefsHelper {

    private static final String PREFS_NAME = "watchai_prefs";
    private static final String KEY_API_KEY = "groq_api_key";
    private static final String KEY_SHORTCUTS = "voice_shortcuts";
    private static final String KEY_WATCH_MAC = "watch_mac_address";
    private static final String DEFAULT_WATCH_MAC = "E1:6F:15:37:99:08";
    private static final String KEY_DARK_THEME = "dark_theme";

    private PrefsHelper() {
        // Utility class — no instantiation
    }

    private static SharedPreferences getEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Corrupted prefs after data clear — delete and retry
            try {
                context.deleteSharedPreferences(PREFS_NAME);
            } catch (Exception ignored) {
            }
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                return EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception retryEx) {
                throw new RuntimeException("Failed to create encrypted preferences", retryEx);
            }
        }
    }

    // ---------- API Key ----------

    public static String loadApiKey(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        String key = prefs.getString(KEY_API_KEY, "");
        return key != null ? key.trim() : "";
    }

    public static void saveApiKey(Context context, String key) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        prefs.edit().putString(KEY_API_KEY, key).apply();
    }

    // ---------- Watch MAC Address ----------

    public static String loadWatchMacAddress(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        return prefs.getString(KEY_WATCH_MAC, DEFAULT_WATCH_MAC);
    }

    public static void saveWatchMacAddress(Context context, String macAddress) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        prefs.edit().putString(KEY_WATCH_MAC, macAddress).apply();
    }

    // ---------- Dark Theme ----------

    public static boolean loadDarkTheme(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        return prefs.getBoolean(KEY_DARK_THEME, true);
    }

    public static void saveDarkTheme(Context context, boolean isDark) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply();
    }

    // ---------- Voice Shortcuts ----------

    public static List<VoiceShortcut> loadShortcuts(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        String json = prefs.getString(KEY_SHORTCUTS, "[]");
        if (json == null) json = "[]";

        List<VoiceShortcut> shortcuts = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                shortcuts.add(new VoiceShortcut(
                        obj.getString("phrase"),
                        obj.getString("actionType"),
                        obj.getString("target"),
                        obj.optString("extra", "")
                ));
            }
        } catch (JSONException e) {
            // Return empty list on parse error
        }
        return shortcuts;
    }

    public static void saveShortcuts(Context context, List<VoiceShortcut> shortcuts) {
        JSONArray array = new JSONArray();
        try {
            for (VoiceShortcut s : shortcuts) {
                JSONObject obj = new JSONObject();
                obj.put("phrase", s.getPhrase());
                obj.put("actionType", s.getActionType());
                obj.put("target", s.getTarget());
                obj.put("extra", s.getExtra());
                array.put(obj);
            }
        } catch (JSONException e) {
            // Should never happen with simple strings
        }
        SharedPreferences prefs = getEncryptedPrefs(context);
        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply();
    }

    /**
     * Finds the first shortcut whose phrase appears in the spoken text.
     */
    public static VoiceShortcut findMatchingShortcut(String spokenText, List<VoiceShortcut> shortcuts) {
        String lower = spokenText.toLowerCase();
        for (VoiceShortcut shortcut : shortcuts) {
            if (!shortcut.getPhrase().isEmpty() && lower.contains(shortcut.getPhrase().toLowerCase())) {
                return shortcut;
            }
        }
        return null;
    }
}
