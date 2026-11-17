package com.example.watchai;

import org.json.JSONObject;

/**
 * Represents an AI response from Groq - either a spoken reply or a tool call.
 */
public abstract class AiResponse {

    public static class SpokenReply extends AiResponse {
        private final String text;

        public SpokenReply(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class ToolCall extends AiResponse {
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
}
