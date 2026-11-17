package com.example.watchai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encrypted persistent storage for conversation history.
 */
public class ConversationRepository {

    private static final String PREFS_NAME = "watchai_conversations";
    private static final String KEY_HISTORY = "conversation_history";

    private final SharedPreferences prefs;
    private List<ConversationTurn> allTurns;

    public ConversationRepository(Context context) {
        prefs = createEncryptedPrefs(context);
        allTurns = loadAllTurns();
    }

    private SharedPreferences createEncryptedPrefs(Context context) {
        // If PrefsHelper already created a prefs, we could reuse it, but for simplicity
        // we create our own. This handles the case where encrypted preferences
        // fail to initialize (e.g., on first run or data corruption).
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
            try {
                context.deleteSharedPreferences(PREFS_NAME);
            } catch (Exception ignored) {
            }
            try {
                // If first attempt fails (rare), try again to recover
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
                // As a last resort, fall back to unencrypted preferences for basic functionality
                return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
        }
    }

    private List<ConversationTurn> loadAllTurns() {
        String json = prefs.getString(KEY_HISTORY, "[]");
        if (json == null) json = "[]";

        List<ConversationTurn> turns = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                turns.add(new ConversationTurn(
                        obj.getString("role"),
                        obj.getString("text"),
                        obj.getLong("timestamp")
                ));
            }
        } catch (JSONException e) {
            // Return empty list on parse error
        }
        return turns;
    }

    private void saveAllTurns(List<ConversationTurn> turns) {
        JSONArray array = new JSONArray();
        try {
            for (ConversationTurn turn : turns) {
                JSONObject obj = new JSONObject();
                obj.put("role", turn.getRole());
                obj.put("text", turn.getText());
                obj.put("timestamp", turn.getTimestamp());
                array.put(obj);
            }
        } catch (JSONException e) {
            // Should never happen with simple strings/longs
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
        this.allTurns = new ArrayList<>(turns);
    }

    public List<ConversationTurn> getAllTurnsList() {
        return Collections.unmodifiableList(allTurns);
    }

    public void insertTurn(ConversationTurn turn) {
        List<ConversationTurn> current = new ArrayList<>(allTurns);
        current.add(turn);
        saveAllTurns(current);
    }

    public void deleteAllTurns() {
        saveAllTurns(new ArrayList<ConversationTurn>());
    }

    public int getTurnCount() {
        return allTurns.size();
    }
}
