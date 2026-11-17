package com.example.watchai;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Foreground service that manages BLE watch connection, speech recognition,
 * TTS, and AI communication with Groq.
 */
public class WatchBleService extends Service {

    // Notification constants
    public static final String CHANNEL_ID = "watchai_channel";
    public static final int NOTIF_ID = 1;
    public static final String ACTION_TOGGLE_LISTENING = "com.example.watchai.ACTION_TOGGLE_LISTENING";
    public static final String ACTION_DISCONNECT_WATCH = "com.example.watchai.ACTION_DISCONNECT_WATCH";

    // Binder
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public WatchBleService getService() {
            return WatchBleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // State
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private volatile String status = "Starting...";

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private String apiKey;
    private final List<ConversationTurn> history = new ArrayList<>();
    private ConversationRepository conversationRepository;

    private GroqApiClient groqApiClient;
    private ToolExecutor toolExecutor;

    // A Set, not a List: fragments register from several places (onAttach,
    // setService, and previously onResume), and a List allowed duplicate
    // registrations that removeListener could only clear one at a time,
    // leaking a detached fragment on every nav tap.
    private final Set<ServiceStateListener> listeners = new CopyOnWriteArraySet<>();

    // Executors
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile long uptimeSeconds = 0;

    // Main thread handler for UI callbacks
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // BLE
    private BleConnectionHelper bleHelper;
    private String watchMacAddress;

    // Binder
    public void addListener(ServiceStateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServiceStateListener listener) {
        listeners.remove(listener);
    }

    private void updateStatus(String newStatus) {
        this.status = newStatus;
        final String statusToSend = newStatus;
        for (ServiceStateListener listener : listeners) {
            mainHandler.post(() -> listener.onStatusChanged(statusToSend));
        }
    }

    private void updateHistory() {
        final List<ConversationTurn> copy = new ArrayList<>(history);
        for (ServiceStateListener listener : listeners) {
            mainHandler.post(() -> listener.onHistoryChanged(copy));
        }
    }

    private void updateWaveformLevel(float level) {
        for (ServiceStateListener listener : listeners) {
            mainHandler.post(() -> listener.onWaveformLevelChanged(level));
        }
    }

    private void updateStreamingText(String text) {
        final String textToSend = text;
        for (ServiceStateListener listener : listeners) {
            mainHandler.post(() -> listener.onStreamingTextChanged(textToSend));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            apiKey = PrefsHelper.loadApiKey(this);
            conversationRepository = new ConversationRepository(this);
            groqApiClient = new GroqApiClient();
            toolExecutor = new ToolExecutor(groqApiClient);

            // Load conversation history
            history.addAll(conversationRepository.getAllTurnsList());
            updateHistory();

            createNotificationChannel();
            startForeground(NOTIF_ID, buildNotification("Listening for watch button"));

            tts = new TextToSpeech(this, result -> {
                if (result == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US);
                    tts.setSpeechRate(0.8f);
                    ttsReady = true;
                }
            });

            setupSpeechRecognizer();

            // Check BLE permissions (both CONNECT and SCAN are required)
            boolean hasBtConnect = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED;
            boolean hasBtScan = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED;

            if (hasBtConnect && hasBtScan) {
                startBleListening();
            } else {
                updateStatus("Missing Bluetooth permission — open the app to grant it");
            }

            // Start uptime counter
            scheduler.scheduleAtFixedRate(() -> {
                uptimeSeconds++;
                mainHandler.post(() -> {
                    for (ServiceStateListener listener : listeners) {
                        listener.onUptimeChanged(uptimeSeconds);
                    }
                });
            }, 1, 1, TimeUnit.SECONDS);

        } catch (Exception e) {
            updateStatus("Service error: " + e.getMessage());
        }
    }

    private void startBleListening() {
        watchMacAddress = PrefsHelper.loadWatchMacAddress(this);
        bleHelper = new BleConnectionHelper(this);
        bleHelper.addListener(event -> {
            if (event instanceof BleEvent.ButtonPressed) {
                onWatchTriggered();
            } else if (event instanceof BleEvent.StatusChanged) {
                updateStatus(((BleEvent.StatusChanged) event).getStatus());
            } else if (event instanceof BleEvent.Error) {
                updateStatus("BLE Error: " + ((BleEvent.Error) event).getMessage());
            }
        });
        bleHelper.connect(watchMacAddress);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_TOGGLE_LISTENING:
                    if (isListening.get()) {
                        if (speechRecognizer != null) speechRecognizer.cancel();
                        isListening.set(false);
                        updateWaveformLevel(0f);
                        updateStatus("Ready");
                    } else {
                        onWatchTriggered();
                    }
                    break;

                case ACTION_DISCONNECT_WATCH:
                    if (bleHelper != null) {
                        bleHelper.disconnect();
                    }
                    updateStatus("Watch disconnected");
                    break;
            }
        }
        return START_STICKY;
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                isListening.set(false);
                updateWaveformLevel(0f);
                ArrayList<String> spokenTexts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (spokenTexts != null && !spokenTexts.isEmpty()) {
                    handleSpokenText(spokenTexts.get(0));
                } else {
                    handleMicFailure(7);
                }
            }

            @Override
            public void onError(int error) {
                isListening.set(false);
                updateWaveformLevel(0f);
                handleMicFailure(error);
            }

            @Override
            public void onReadyForSpeech(Bundle params) {
                updateStatus("Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {
                float level = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
                updateWaveformLevel(level);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void handleMicFailure(int errorCode) {
        String spokenMessage;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                spokenMessage = "Sorry, I didn't catch that, Vedant sir.";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                spokenMessage = null;
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                spokenMessage = "Network trouble, Vedant sir. Try again in a moment.";
                break;
            default:
                spokenMessage = "Something went wrong, Vedant sir. Please try again.";
                break;
        }
        updateStatus("Ready");
        if (spokenMessage != null) {
            WatchSounds.playError(this);
            if (ttsReady) {
                tts.speak(spokenMessage, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }
    }

    private void onWatchTriggered() {
        if (isListening.get()) return;
        if (!"Ready".equals(status) && !status.startsWith("Watch connected")) return;

        boolean hasMicPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;

        if (!hasMicPermission) {
            updateStatus("Missing mic permission — open the app to grant it");
            return;
        }

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        try {
            isListening.set(true);
            WatchSounds.playActivate(this);
            if (speechRecognizer != null) {
                speechRecognizer.startListening(recognizerIntent);
            } else {
                isListening.set(false);
                updateStatus("Ready");
            }
        } catch (Exception e) {
            isListening.set(false);
            updateStatus("Ready");
        }
    }

    private void handleSpokenText(String spokenText) {
        updateStatus("Thinking...");
        WatchSounds.playThinking(this);

        // Check for shortcut match first
        String shortcutReply = ShortcutHelper.matchAndExecute(this, spokenText);
        if (shortcutReply != null) {
            respondAndSpeak(spokenText, shortcutReply);
            return;
        }

        // Reload API key
        apiKey = PrefsHelper.loadApiKey(this);
        if (apiKey == null || apiKey.isEmpty()) {
            updateStatus("Error: No API key set. Open the app to add one.");
            return;
        }

        // Use streaming AI response
        final StringBuilder responseBuilder = new StringBuilder();
        final AiResponse.ToolCall[] toolCallResult = {null};

        groqApiClient.askGroqStreaming(apiKey, history, spokenText, event -> {
            if (event instanceof StreamEvent.Token) {
                responseBuilder.append(((StreamEvent.Token) event).getText());
                updateStreamingText(responseBuilder.toString());
                updateStatus("Speaking...");
            } else if (event instanceof StreamEvent.ToolCall) {
                StreamEvent.ToolCall tc = (StreamEvent.ToolCall) event;
                toolCallResult[0] = new AiResponse.ToolCall(tc.getFunctionName(), tc.getArguments());
            } else if (event instanceof StreamEvent.Error) {
                respondAndSpeak(spokenText, "Error: " + ((StreamEvent.Error) event).getMessage());
            } else if (event instanceof StreamEvent.Done) {
                // If a tool call was made, execute it. Any streamed text is
                // preserved and spoken before the tool result so the user
                // hears the assistant's preamble instead of silence.
                if (toolCallResult[0] != null) {
                    final String preamble = responseBuilder.length() > 0
                            ? responseBuilder.toString()
                            : null;
                    executor.execute(() -> {
                        String toolResponse = toolExecutor.executeTool(WatchBleService.this, toolCallResult[0]);
                        String reply = (preamble != null && !preamble.isEmpty())
                                ? preamble + " " + toolResponse
                                : toolResponse;
                        respondAndSpeak(spokenText, reply);
                    });
                } else if (responseBuilder.length() > 0) {
                    respondAndSpeak(spokenText, responseBuilder.toString());
                }
                updateStreamingText("");
            }
        });
    }

    private void respondAndSpeak(String userText, String replyText) {
        String safeReply = replyText != null ? replyText : "I didn't catch that, Vedant sir.";
        ConversationTurn userTurn = new ConversationTurn("user", userText);
        ConversationTurn modelTurn = new ConversationTurn("model", safeReply);

        history.add(userTurn);
        history.add(modelTurn);
        updateHistory();

        conversationRepository.insertTurn(userTurn);
        conversationRepository.insertTurn(modelTurn);

        updateStatus("Speaking...");
        WatchSounds.playDone(this);
        if (ttsReady) {
            tts.speak(safeReply, TextToSpeech.QUEUE_FLUSH, null, null);
        }
        updateStatus("Ready");
    }

    // ---------- Public methods for UI ----------

    public void manualTrigger() {
        onWatchTriggered();
    }

    public int getPhoneBatteryPercent() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return -1;
    }

    public void clearConversationHistory() {
        history.clear();
        updateHistory();
        conversationRepository.deleteAllTurns();
    }

    public String getStatus() {
        return status;
    }

    public List<ConversationTurn> getHistory() {
        return new ArrayList<>(history);
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void refreshWatchConnection() {
        if (bleHelper != null) {
            bleHelper.disconnect();
        }
        startBleListening();
    }

    // ---------- Notification ----------

    private Notification buildNotification(String text) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent toggleIntent = new Intent(this, WatchBleService.class);
        toggleIntent.setAction(ACTION_TOGGLE_LISTENING);
        PendingIntent togglePendingIntent = PendingIntent.getService(
                this, 1, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent disconnectIntent = new Intent(this, WatchBleService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT_WATCH);
        PendingIntent disconnectPendingIntent = PendingIntent.getService(
                this, 2, disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WatchAI")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(openAppPendingIntent)
                .addAction(android.R.drawable.ic_btn_speak_now, "Toggle Listening", togglePendingIntent)
                .addAction(android.R.drawable.ic_delete, "Disconnect", disconnectPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WatchAI Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        WatchSounds.stop();
        if (bleHelper != null) {
            bleHelper.disconnect();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        executor.shutdownNow();
        scheduler.shutdownNow();
        super.onDestroy();
    }
}
