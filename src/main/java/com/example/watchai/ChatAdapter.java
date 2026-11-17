package com.example.watchai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the chat transcript.
 *
 * The in-flight assistant response is a real row at the end of the list
 * rather than a separate view outside it — previously it rendered in a card
 * below the RecyclerView, which meant any response longer than the viewport
 * was invisible while still arriving.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_MODEL = 1;
    private static final int VIEW_TYPE_STREAMING = 2;

    private final List<ConversationTurn> turns = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    /** Text of the in-flight response, or "" when nothing is streaming. */
    private String streamingText = "";
    private boolean isStreaming = false;

    /**
     * Replaces the transcript, preserving streaming state.
     *
     * Diffing is done manually rather than via notifyDataSetChanged: history
     * is append-only, so the previous size tells us exactly which range is
     * new and the rest of the list can keep its bound ViewHolders.
     */
    public void setTurns(List<ConversationTurn> newTurns) {
        int previousCount = turns.size();
        boolean appendedOnly = newTurns.size() >= previousCount
                && startsWithExisting(newTurns, previousCount);

        turns.clear();
        turns.addAll(newTurns);

        if (appendedOnly && !isStreaming) {
            if (newTurns.size() > previousCount) {
                notifyItemRangeInserted(previousCount, newTurns.size() - previousCount);
            }
        } else {
            notifyDataSetChanged();
        }
    }

    private boolean startsWithExisting(List<ConversationTurn> newTurns, int previousCount) {
        for (int i = 0; i < previousCount; i++) {
            ConversationTurn old = turns.get(i);
            ConversationTurn fresh = newTurns.get(i);
            if (!old.getText().equals(fresh.getText())
                    || !old.getRole().equals(fresh.getRole())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Shows or updates the in-flight response row.
     *
     * Each token calls this with the full accumulated text. Only the single
     * streaming row is rebound, so this is cheap enough to run per token.
     */
    public void setStreamingText(@Nullable String text) {
        String safeText = text != null ? text : "";
        boolean nowStreaming = !safeText.isEmpty();

        if (nowStreaming) {
            streamingText = safeText;
            if (!isStreaming) {
                // Row did not exist before: append it.
                isStreaming = true;
                notifyItemInserted(getItemCount() - 1);
            } else {
                int index = getItemCount() - 1;
                notifyItemChanged(index, PAYLOAD_STREAMING);
            }
        } else {
            if (isStreaming) {
                isStreaming = false;
                streamingText = "";
                notifyItemRemoved(getItemCount() - 1);
            } else {
                streamingText = "";
            }
        }
    }

    private static final Object PAYLOAD_STREAMING = new Object();

    @Override
    public int getItemViewType(int position) {
        if (isStreaming && position == getItemCount() - 1) {
            return VIEW_TYPE_STREAMING;
        }
        return "user".equals(turns.get(position).getRole()) ? VIEW_TYPE_USER : VIEW_TYPE_MODEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_USER:
                return new BubbleViewHolder(
                        inflater.inflate(R.layout.item_chat_bubble_user, parent, false));
            case VIEW_TYPE_STREAMING:
                return new StreamingViewHolder(
                        inflater.inflate(R.layout.item_chat_bubble_streaming, parent, false));
            default:
                return new BubbleViewHolder(
                        inflater.inflate(R.layout.item_chat_bubble_model, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof StreamingViewHolder) {
            ((StreamingViewHolder) holder).bind(streamingText);
            return;
        }
        ConversationTurn turn = turns.get(position);
        BubbleViewHolder bubble = (BubbleViewHolder) holder;
        bubble.messageText.setText(turn.getText());
        bubble.timestampText.setText(timeFormat.format(new Date(turn.getTimestamp())));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_STREAMING) && holder instanceof StreamingViewHolder) {
            // Token arrived: rebind text only, skip the full path.
            ((StreamingViewHolder) holder).bind(streamingText);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    /**
     * Item count includes the streaming row when present, so it occupies a
     * position the backing list does not have an entry for.
     */
    @Override
    public int getItemCount() {
        return turns.size() + (isStreaming ? 1 : 0);
    }

    static class BubbleViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timestampText;

        BubbleViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            timestampText = itemView.findViewById(R.id.timestamp_text);
        }
    }

    static class StreamingViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        StreamingViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
        }

        void bind(String text) {
            messageText.setText(text);
        }
    }
}