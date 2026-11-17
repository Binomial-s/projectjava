package com.example.watchai;

import java.util.List;

/**
 * Listener interface for observing WatchBleService state changes.
 * The UI (Activity/Fragments) implements this to receive updates.
 */
public interface ServiceStateListener {
    void onStatusChanged(String status);
    void onHistoryChanged(List<ConversationTurn> history);
    void onWaveformLevelChanged(float level);
    void onUptimeChanged(long seconds);
    void onStreamingTextChanged(String text);
}
