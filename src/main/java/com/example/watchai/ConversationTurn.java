package com.example.watchai;

/**
 * Represents a single turn in a conversation (either user or model).
 */
public class ConversationTurn {

    private final String role;
    private final String text;
    private final long timestamp;

    public ConversationTurn(String role, String text) {
        this(role, text, System.currentTimeMillis());
    }

    public ConversationTurn(String role, String text, long timestamp) {
        this.role = role;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationTurn that = (ConversationTurn) o;
        return timestamp == that.timestamp
                && role.equals(that.role)
                && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        int result = role.hashCode();
        result = 31 * result + text.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }

    @Override
    public String toString() {
        return "ConversationTurn{role='" + role + "', text='" + text + "', timestamp=" + timestamp + "}";
    }
}
