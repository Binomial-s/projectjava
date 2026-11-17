package com.example.watchai;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Chat screen fragment — displays conversation history, the in-flight
 * streaming response, the listening waveform, and error state.
 */
public class ChatFragment extends Fragment implements ServiceStateListener {

    /**
     * Max scale applied to the 8dp waveform bars. The container is 44dp
     * tall, so a bar can grow to about 5x before it clips.
     */
    private static final float MAX_BAR_SCALE = 4.5f;
    private static final float IDLE_BAR_SCALE = 1f;

    private TextView statusText;
    private LinearLayout waveformContainer;
    private View[] waveBars;
    private View errorCard;
    private TextView errorText;
    private View retryButton;
    private RecyclerView chatRecycler;

    private ChatAdapter chatAdapter;
    private WatchBleService service;

    /**
     * The idle "breathing" animation. Distinct from the per-bar
     * ViewPropertyAnimator used for live mic amplitude — see
     * onWaveformLevelChanged for why only one of them runs at a time.
     */
    private Runnable idlePulse;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusText = view.findViewById(R.id.status_text);
        waveformContainer = view.findViewById(R.id.waveform_container);
        errorCard = view.findViewById(R.id.error_card);
        errorText = view.findViewById(R.id.error_text);
        retryButton = view.findViewById(R.id.retry_button);
        chatRecycler = view.findViewById(R.id.chat_recycler);

        waveBars = new View[]{
                view.findViewById(R.id.wave_bar_1),
                view.findViewById(R.id.wave_bar_2),
                view.findViewById(R.id.wave_bar_3),
                view.findViewById(R.id.wave_bar_4),
                view.findViewById(R.id.wave_bar_5)
        };

        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setReverseLayout(true);
        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(chatAdapter);

        retryButton.setOnClickListener(v -> {
            if (service != null) {
                service.manualTrigger();
            }
        });
    }

    @Override
    public void onDestroyView() {
        // Stop animations before dropping the view references, otherwise a
        // running animator keeps the whole detached hierarchy alive.
        stopWaveform();
        statusText = null;
        waveformContainer = null;
        waveBars = null;
        errorCard = null;
        errorText = null;
        retryButton = null;
        chatRecycler = null;
        chatAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        // Also stop animations here — onDestroyView can be called while
        // onDestroy is still pending, and a leaked waveform keeps the
        // fragment's view hierarchy alive in memory.
        stopWaveform();
        super.onDestroy();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            // null until the service binds; setService handles that ordering
            service = ((MainActivity) context).getService();
            registerListener();
        }
    }

    @Override
    public void onDetach() {
        unregisterListener();
        service = null;
        super.onDetach();
    }

    public void setService(WatchBleService svc) {
        this.service = svc;
        registerListener();
        if (svc != null) {
            onStatusChanged(svc.getStatus());
            onHistoryChanged(svc.getHistory());
        }
    }

    private void registerListener() {
        if (service != null) {
            service.addListener(this);
        }
    }

    private void unregisterListener() {
        if (service != null) {
            service.removeListener(this);
        }
    }

    // ---------- ServiceStateListener ----------

    @Override
    public void onStatusChanged(String status) {
        if (statusText == null || !isAdded()) return;
        statusText.setText(status);

        boolean listening = "Listening...".equals(status);

        if (waveformContainer != null) {
            waveformContainer.setVisibility(listening ? View.VISIBLE : View.GONE);
        }
        if (listening) {
            startWaveform();
        } else {
            stopWaveform();
        }

        boolean isError = status.startsWith("Error") || status.contains("trouble");
        if (errorCard != null) {
            errorCard.setVisibility(isError ? View.VISIBLE : View.GONE);
        }
        if (isError && errorText != null) {
            errorText.setText(status);
        }
    }

    @Override
    public void onHistoryChanged(List<ConversationTurn> history) {
        if (chatAdapter == null || !isAdded()) return;
        chatAdapter.setTurns(history);
    }

    @Override
    public void onWaveformLevelChanged(float level) {
        if (waveBars == null || !isAdded()) return;
        if (!"Listening...".equals(statusOfService())) return;

        // Cancel the idle pulse before driving the bars directly. Both
        // animate scaleY, and whichever starts last wins — previously the
        // per-bar animator silently killed the idle animation on every RMS
        // update, so the idle breathing was never actually visible.
        stopIdlePulse();

        for (int i = 0; i < waveBars.length; i++) {
            float scale = IDLE_BAR_SCALE + (level * (MAX_BAR_SCALE - IDLE_BAR_SCALE));
            waveBars[i].animate()
                    .scaleY(scale)
                    .setDuration(120)
                    .setStartDelay(i * 40L)
                    .start();
        }
    }

    private String statusOfService() {
        return service != null ? service.getStatus() : null;
    }

    @Override
    public void onUptimeChanged(long seconds) {
        // Not used in chat fragment
    }

    @Override
    public void onStreamingTextChanged(String text) {
        if (chatAdapter == null || !isAdded()) return;
        // Streams into the list itself, so a long response stays on screen
        // as it arrives.
        chatAdapter.setStreamingText(text);
        if (chatRecycler != null) {
            chatRecycler.scrollToPosition(0);
        }
    }

    // ---------- Waveform ----------

    /**
     * Starts the idle pulse, used when listening but before/without live
     * amplitude arriving. Real RMS levels take over as soon as they come in.
     */
    private void startWaveform() {
        stopIdlePulse();
        stopBarAnimations();

        if (waveformContainer == null || !isAdded()) return;

        idlePulse = new Runnable() {
            private int phase = 0;

            @Override
            public void run() {
                if (waveBars == null || !isAdded()) return;
                float scale = IDLE_BAR_SCALE + (0.6f * (float) Math.sin(phase * 0.5f));
                for (int i = 0; i < waveBars.length; i++) {
                    float offset = (float) Math.sin((phase + i * 0.6f) * 0.5f);
                    waveBars[i].setScaleY(MAX_BAR_SCALE / 2f + offset * (MAX_BAR_SCALE / 2f - 1f));
                }
                phase += 1;
                if (waveformContainer != null) {
                    waveformContainer.postDelayed(this, 50);
                }
            }
        };
        waveformContainer.post(idlePulse);
    }

    private void stopIdlePulse() {
        if (waveformContainer != null && idlePulse != null) {
            waveformContainer.removeCallbacks(idlePulse);
        }
        idlePulse = null;
    }

    /**
     * Cancels the per-bar animators too. stopWaveform used to only reset the
     * scale, leaving bars stuck at whatever the last RMS update set.
     */
    private void stopBarAnimations() {
        if (waveBars == null) return;
        for (View bar : waveBars) {
            if (bar != null) {
                bar.animate().cancel();
                bar.setScaleY(IDLE_BAR_SCALE);
            }
        }
    }

    private void stopWaveform() {
        stopIdlePulse();
        stopBarAnimations();
    }
}