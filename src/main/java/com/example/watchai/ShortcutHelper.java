package com.example.watchai;

import android.content.Context;

import java.util.List;

/**
 * Matches spoken text against voice shortcuts and executes them.
 */
public final class ShortcutHelper {

    private ShortcutHelper() {
        // Utility class — no instantiation
    }

    /**
     * Finds a matching shortcut and executes it.
     * Returns the spoken response, or null if no shortcut matched.
     */
    public static String matchAndExecute(Context context, String spokenText) {
        List<VoiceShortcut> shortcuts = PrefsHelper.loadShortcuts(context);
        VoiceShortcut matched = PrefsHelper.findMatchingShortcut(spokenText, shortcuts);

        if (matched == null) {
            return null;
        }

        return executeShortcut(context, matched);
    }

    /**
     * Executes a voice shortcut and returns the spoken response.
     */
    public static String executeShortcut(Context context, VoiceShortcut shortcut) {
        switch (shortcut.getActionType()) {
            case "call":
                String callNumber = ToolExecutor.resolveContactNumber(context, shortcut.getTarget());
                if (callNumber == null) callNumber = shortcut.getTarget();
                ToolExecutor.launchDialIntent(context, callNumber);
                return "Calling " + shortcut.getTarget() + ", Vedant sir.";

            case "message":
                String msgNumber = ToolExecutor.resolveContactNumber(context, shortcut.getTarget());
                if (msgNumber == null) msgNumber = shortcut.getTarget();
                ToolExecutor.launchSmsIntent(context, msgNumber, shortcut.getExtra());
                return "Texting " + shortcut.getTarget() + ", Vedant sir.";

            case "spotify":
                ToolExecutor.launchSpotifySearch(context, shortcut.getTarget());
                return "Playing " + shortcut.getTarget() + " on Spotify, Vedant sir.";

            case "google":
                ToolExecutor.launchGoogleSearch(context, shortcut.getTarget());
                return "Searching Google for " + shortcut.getTarget() + ", Vedant sir.";

            default:
                return "I don't recognize that shortcut action.";
        }
    }
}
