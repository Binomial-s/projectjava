package com.example.watchai;

import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

/**
 * Plays notification sounds and system sound effects for different events.
 */
public final class WatchSounds {

    private static Ringtone currentRingtone;

    private WatchSounds() {
        // Singleton utility — no instantiation
    }

    /**
     * Play a short notification sound for activation.
     */
    public static void playActivate(Context context) {
        playNotificationSound(context, 0);
    }

    /**
     * Play a "done" sound.
     */
    public static void playDone(Context context) {
        playNotificationSound(context, 1);
    }

    /**
     * Play an error sound.
     */
    public static void playError(Context context) {
        playNotificationSound(context, 2);
    }

    /**
     * Play a subtle "thinking" click.
     */
    public static void playThinking(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f);
        }
    }

    private static void playNotificationSound(Context context, int index) {
        stop();

        try {
            RingtoneManager manager = new RingtoneManager(context);
            manager.setType(RingtoneManager.TYPE_NOTIFICATION);

            android.database.Cursor cursor = manager.getCursor();
            int count = cursor.getCount();

            if (count == 0) return;

            int targetIndex;
            if (index < count) {
                targetIndex = index;
            } else if (count > 0) {
                targetIndex = index % count;
            } else {
                targetIndex = 0;
            }

            cursor.moveToFirst();
            for (int i = 0; i < targetIndex; i++) {
                cursor.moveToNext();
            }

            Uri uri = manager.getRingtoneUri(targetIndex);
            if (uri != null) {
                Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
                currentRingtone = ringtone;
                if (ringtone != null) {
                    ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
                    ringtone.play();
                }
            }
        } catch (Exception e) {
            // Fallback: play a system sound effect
            try {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.7f);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void stop() {
        try {
            if (currentRingtone != null) {
                currentRingtone.stop();
                currentRingtone = null;
            }
        } catch (Exception ignored) {
        }
    }
}
