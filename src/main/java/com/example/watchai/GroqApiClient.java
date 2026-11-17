package com.example.watchai;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for the Groq API — supports both synchronous and streaming chat completions.
 */
public class GroqApiClient {

    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int CONTEXT_WINDOW_SIZE = 10;
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private static final String SYSTEM_INSTRUCTION =
            "You are a voice assistant running on a smartwatch for a user named Vedant. " +
                    "Always address him respectfully as 'Vedant sir', especially in greetings or when starting a reply. " +
                    "Keep answers concise by default (1 to 3 short sentences), but give a longer, more detailed answer " +
                    "when the question genuinely needs it, such as instructions, explanations, or anything with multiple steps. " +
                    "Never use markdown, bullet points, or headers, since your answer will be read aloud. " +
                    "Be direct and conversational. " +
                    "If Vedant sir asks you to call or message someone, play music on Spotify, search Google, " +
                    "copy text, or download a file, use the matching tool instead of replying in plain text.";

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public GroqApiClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Builds the tools JSON array for Groq function calling.
     */
    public static JSONArray buildToolsArray() {
        JSONArray tools = new JSONArray();
        try {
            // place_call
            tools.put(createTool("place_call",
                    "Open the phone dialer to call a contact or number.",
                    new String[]{"contact_name", "phone_number"},
                    new String[]{"Saved contact name", "Raw phone number"}));

            // send_message
            tools.put(createTool("send_message",
                    "Open the messaging app pre-filled to text a contact or number.",
                    new String[]{"contact_name", "phone_number", "message_body"},
                    new String[]{"Saved contact name", "Raw phone number", "Text to pre-fill"}));

            // play_music
            tools.put(createTool("play_music",
                    "Search and play something on Spotify.",
                    new String[]{"query"},
                    new String[]{"Song, artist, or album"}));

            // google_search
            tools.put(createTool("google_search",
                    "Search Google for something.",
                    new String[]{"query"},
                    new String[]{"Search query"}));

            // copy_text
            tools.put(createTool("copy_text",
                    "Copy text to the clipboard.",
                    new String[]{"text"},
                    new String[]{"Exact text to copy"}));

            // download_file
            tools.put(createTool("download_file",
                    "Download a file from a URL.",
                    new String[]{"url", "file_name"},
                    new String[]{"Direct file URL", "Name to save as"}));
        } catch (JSONException e) {
            // Should never happen
        }
        return tools;
    }

    private static JSONObject createTool(String name, String description,
                                          String[] paramNames, String[] paramDescriptions) throws JSONException {
        JSONObject tool = new JSONObject();
        tool.put("type", "function");

        JSONObject function = new JSONObject();
        function.put("name", name);
        function.put("description", description);

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        for (int i = 0; i < paramNames.length; i++) {
            JSONObject param = new JSONObject();
            param.put("type", "string");
            param.put("description", paramDescriptions[i]);
            properties.put(paramNames[i], param);
        }
        parameters.put("properties", properties);
        parameters.put("required", new JSONArray());  // required is empty

        function.put("parameters", parameters);
        tool.put("function", function);
        return tool;
    }

    /**
     * Builds the messages array for Groq, including conversation history.
     */
    private JSONArray buildMessagesArray(List<ConversationTurn> history, String newQuestion) {
        JSONArray messages = new JSONArray();
        try {
            // System instruction
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_INSTRUCTION);
            messages.put(systemMsg);

            // Context window: only last N turns
            int startIndex = Math.max(0, history.size() - CONTEXT_WINDOW_SIZE);
            for (int i = startIndex; i < history.size(); i++) {
                ConversationTurn turn = history.get(i);
                JSONObject msg = new JSONObject();
                msg.put("role", turn.getRole().equals("model") ? "assistant" : "user");
                msg.put("content", turn.getText());
                messages.put(msg);
            }

            // New question
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", newQuestion);
            messages.put(userMsg);
        } catch (JSONException e) {
            // Should never happen
        }
        return messages;
    }

    /**
     * Non-streaming request to Groq API. Returns an AiResponse on the calling thread.
     */
    public AiResponse askGroq(String apiKey, List<ConversationTurn> history, String newQuestion) {
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("model", MODEL);
            bodyJson.put("messages", buildMessagesArray(history, newQuestion));
            bodyJson.put("tools", buildToolsArray());

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(bodyJson.toString(), JSON_MEDIA))
                    .build();

            Response response = httpClient.newCall(request).execute();
            String text = response.body() != null ? response.body().string() : "";

            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(text);
                JSONArray choices = json.getJSONArray("choices");
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");

                if (message.has("tool_calls")) {
                    JSONArray toolCalls = message.getJSONArray("tool_calls");
                    JSONObject firstToolCall = toolCalls.getJSONObject(0);
                    JSONObject function = firstToolCall.getJSONObject("function");
                    String name = function.getString("name");
                    JSONObject args;
                    try {
                        args = new JSONObject(function.getString("arguments"));
                    } catch (Exception e) {
                        args = new JSONObject();
                    }
                    return new AiResponse.ToolCall(name, args);
                } else {
                    String content = message.getString("content");
                    return new AiResponse.SpokenReply(content);
                }
            } else {
                String errorText;
                try {
                    JSONObject errorJson = new JSONObject(text);
                    JSONObject errorObj = errorJson.optJSONObject("error");
                    errorText = errorObj != null ? errorObj.optString("message") : "Unknown error (" + response.code() + ")";
                } catch (Exception e) {
                    errorText = "Error " + response.code() + ": " + text;
                }
                return new AiResponse.SpokenReply("Groq error (" + response.code() + "): " + errorText);
            }
        } catch (Exception e) {
            return new AiResponse.SpokenReply("Network error: " + e.getMessage() + ". Check your connection.");
        }
    }

    /**
     * Streaming request to Groq API. Calls listener methods on the main thread.
     */
    public void askGroqStreaming(String apiKey, List<ConversationTurn> history,
                                  String newQuestion, final StreamListener listener) {
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("model", MODEL);
            bodyJson.put("messages", buildMessagesArray(history, newQuestion));
            bodyJson.put("tools", buildToolsArray());
            bodyJson.put("stream", true);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(bodyJson.toString(), JSON_MEDIA))
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        listener.onStreamEvent(new StreamEvent.Error("Network error: " + e.getMessage()));
                        listener.onStreamEvent(new StreamEvent.Done());
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        String errorText;
                        try {
                            JSONObject errorJson = new JSONObject(errorBody);
                            JSONObject errorObj = errorJson.optJSONObject("error");
                            errorText = errorObj != null ? errorObj.optString("message") : "Unknown error (" + response.code() + ")";
                        } catch (Exception e) {
                            errorText = "Error " + response.code() + ": " + errorBody;
                        }
                        String finalErrorText = errorText;
                        mainHandler.post(() -> {
                            listener.onStreamEvent(new StreamEvent.Error("Groq error (" + response.code() + "): " + finalErrorText));
                            listener.onStreamEvent(new StreamEvent.Done());
                        });
                        return;
                    }

                    try {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body().byteStream()));

                        StringBuilder toolCallArguments = new StringBuilder();
                        String toolCallName = "";
                        String line;

                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) continue;

                            String data = line.substring(6).trim();
                            if (data.equals("[DONE]")) continue;

                            try {
                                JSONObject json = new JSONObject(data);
                                JSONArray choices = json.optJSONArray("choices");
                                if (choices == null || choices.length() == 0) continue;

                                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                if (delta == null) continue;

                                // Handle text content
                                if (delta.has("content")) {
                                    String content = delta.getString("content");
                                    if (!content.isEmpty()) {
                                        String token = content;
                                        mainHandler.post(() -> listener.onStreamEvent(new StreamEvent.Token(token)));
                                    }
                                }

                                // Handle tool calls (streaming)
                                if (delta.has("tool_calls")) {
                                    JSONArray toolCalls = delta.getJSONArray("tool_calls");
                                    if (toolCalls.length() > 0) {
                                        JSONObject toolCall = toolCalls.getJSONObject(0);
                                        JSONObject function = toolCall.optJSONObject("function");
                                        if (function != null) {
                                            if (function.has("name")) {
                                                toolCallName = function.getString("name");
                                            }
                                            if (function.has("arguments")) {
                                                toolCallArguments.append(function.getString("arguments"));
                                            }
                                        }
                                    }
                                }
                            } catch (JSONException ignored) {
                                // Skip malformed JSON lines
                            }
                        }

                        // Emit any collected tool call
                        if (!toolCallName.isEmpty()) {
                            try {
                                JSONObject args;
                                if (toolCallArguments.length() > 0) {
                                    args = new JSONObject(toolCallArguments.toString());
                                } else {
                                    args = new JSONObject();
                                }
                                String finalToolCallName = toolCallName;
                                JSONObject finalArgs = args;
                                mainHandler.post(() -> listener.onStreamEvent(new StreamEvent.ToolCall(finalToolCallName, finalArgs)));
                            } catch (JSONException ignored) {
                                // Ignore malformed tool call args
                            }
                        }

                        mainHandler.post(() -> listener.onStreamEvent(new StreamEvent.Done()));

                    } catch (Exception e) {
                        String msg = e.getMessage();
                        mainHandler.post(() -> {
                            listener.onStreamEvent(new StreamEvent.Error("Stream error: " + msg));
                            listener.onStreamEvent(new StreamEvent.Done());
                        });
                    }
                }
            });
        } catch (Exception e) {
            mainHandler.post(() -> {
                listener.onStreamEvent(new StreamEvent.Error("Request failed: " + e.getMessage()));
                listener.onStreamEvent(new StreamEvent.Done());
            });
        }
    }

    /**
     * Fetches a search snippet from DuckDuckGo for a given query.
     */
    public String fetchSearchSnippet(String query) {
        try {
            String encoded = Uri.encode(query);
            Request request = new Request.Builder()
                    .url("https://html.duckduckgo.com/html/?q=" + encoded)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (response.body() == null) return null;

            String html = response.body().string();

            // Extract snippet using regex
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "class=\"result__snippet\"[^>]*>(.*?)</a>",
                    java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (!matcher.find()) return null;

            String snippet = matcher.group(1);
            if (snippet == null) return null;

            snippet = snippet.replaceAll("<.*?>", "");
            snippet = snippet.replace("&amp;", "&");
            snippet = snippet.replace("&#x27;", "'");
            snippet = snippet.trim();

            return snippet.length() > 300 ? snippet.substring(0, 300) : snippet;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Listener interface for streaming events.
     */
    public interface StreamListener {
        void onStreamEvent(StreamEvent event);
    }
}
