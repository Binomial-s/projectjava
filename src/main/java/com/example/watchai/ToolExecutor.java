package com.example.watchai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executes AI tool calls — calling contacts, sending SMS, playing music, etc.
 */
public class ToolExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final GroqApiClient apiClient;

    public ToolExecutor(GroqApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Executes a tool call and returns the spoken response.
     */
    public String executeTool(Context context, AiResponse.ToolCall toolCall) {
        String functionName = toolCall.getFunctionName();
        JSONObject args = toolCall.getArguments();

        switch (functionName) {
            case "place_call":
            case "send_message":
                return executeContactTool(context, toolCall);

            case "play_music":
                String musicQuery = args.optString("query", "");
                launchSpotifySearch(context, musicQuery);
                return "Opening Spotify for " + musicQuery + ", Vedant sir.";

            case "google_search":
                String searchQuery = args.optString("query", "");
                if (searchQuery.isEmpty()) {
                    return "Search query is empty, Vedant sir.";
                }
                launchGoogleSearch(context, searchQuery);
                String snippet = apiClient.fetchSearchSnippet(searchQuery);
                if (snippet != null) {
                    return "Here's what I found, Vedant sir: " + snippet;
                } else {
                    return "I opened the search for " + searchQuery + ", Vedant sir, but couldn't read a quick answer.";
                }

            case "copy_text":
                String text = args.optString("text", "");
                copyToClipboard(context, text);
                return "Copied to your clipboard, Vedant sir.";

            case "download_file":
                String url = args.optString("url", "");
                String fileName = args.optString("file_name", "download");
                downloadFile(context, url, fileName);
                return "Downloading " + fileName + ", Vedant sir.";

            default:
                return "I don't know how to do that yet.";
        }
    }

    /**
     * Executes a contact-related tool (call or message).
     */
    public String executeContactTool(Context context, AiResponse.ToolCall toolCall) {
        JSONObject args = toolCall.getArguments();
        String contactName = args.optString("contact_name", "");
        String rawNumber = args.optString("phone_number", "");

        String number = (rawNumber != null && !rawNumber.trim().isEmpty()) ? rawNumber.trim() : null;
        String displayName = rawNumber;

        if (number == null && contactName != null && !contactName.trim().isEmpty()) {
            // Need contacts permission
            number = resolveContactNumber(context, contactName.trim());
            displayName = contactName;
            if (number == null) {
                return "I couldn't find a contact named " + contactName + ".";
            }
        }

        if (number == null) {
            return "I didn't catch who to contact.";
        }

        switch (toolCall.getFunctionName()) {
            case "place_call":
                launchDialIntent(context, number);
                return "Opening the dialer to call " + displayName + ", Vedant sir.";

            case "send_message":
                String body = args.optString("message_body", "");
                launchSmsIntent(context, number, body);
                return "Opening messages to text " + displayName + ", Vedant sir.";

            default:
                return "I don't know how to do that yet.";
        }
    }

    // ---------- Contacts ----------

    public static String resolveContactNumber(Context context, String name) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    return cursor.getString(idx);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    // ---------- Intent Launchers ----------

    public static void launchDialIntent(Context context, String number) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void launchSmsIntent(Context context, String number, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number));
        intent.putExtra("sms_body", message);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void launchSpotifySearch(Context context, String query) {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
            intent.putExtra(android.app.SearchManager.QUERY, query);
            intent.setPackage("com.spotify.music");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent genericIntent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                genericIntent.putExtra(android.app.SearchManager.QUERY, query);
                genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(genericIntent);
            } catch (Exception e2) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://open.spotify.com/search/" + query));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            }
        }
    }

    public static void launchGoogleSearch(Context context, String query) {
        String encoded = Uri.encode(query);
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + encoded));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("WatchAI", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    public static void downloadFile(Context context, String url, String fileName) {
        android.app.DownloadManager.Request request =
                new android.app.DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        android.app.DownloadManager downloadManager =
                (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
        }
    }
}
