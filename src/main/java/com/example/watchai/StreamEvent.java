package com.example.watchai;

import org.json.JSONObject;

/**
 * Represents events emitted during a streaming AI response.
 */
public abstract class StreamEvent {

    public static class Token extends StreamEvent {
        private final String text;

        public Token(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class ToolCall extends StreamEvent {
        private final String functionName;
        private final JSONObject arguments;

        public ToolCall(String functionName, JSONObject arguments) {
            this.functionName = functionName;
            this.arguments = arguments;
        }

        public String getFunctionName() {
            return functionName;
        }

        public JSONObject getArguments() {
            return arguments;
        }
    }

    public static class Error extends StreamEvent {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class Done extends StreamEvent {
        public Done() {
        }
    }
}
