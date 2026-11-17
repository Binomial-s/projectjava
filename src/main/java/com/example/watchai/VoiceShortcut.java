package com.example.watchai;

/**
 * Represents a voice shortcut that triggers an action without AI processing.
 */
public class VoiceShortcut {

    private final String phrase;
    private final String actionType; // "call", "message", "spotify", "google"
    private final String target;
    private final String extra;      // message body, only used for "message"

    public VoiceShortcut(String phrase, String actionType, String target) {
        this(phrase, actionType, target, "");
    }

    public VoiceShortcut(String phrase, String actionType, String target, String extra) {
        this.phrase = phrase;
        this.actionType = actionType;
        this.target = target;
        this.extra = extra;
    }

    public String getPhrase() {
        return phrase;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTarget() {
        return target;
    }

    public String getExtra() {
        return extra;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoiceShortcut that = (VoiceShortcut) o;
        return phrase.equals(that.phrase)
                && actionType.equals(that.actionType)
                && target.equals(that.target)
                && extra.equals(that.extra);
    }

    @Override
    public int hashCode() {
        int result = phrase.hashCode();
        result = 31 * result + actionType.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + extra.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "VoiceShortcut{phrase='" + phrase + "', actionType='" + actionType
                + "', target='" + target + "', extra='" + extra + "'}";
    }
}
