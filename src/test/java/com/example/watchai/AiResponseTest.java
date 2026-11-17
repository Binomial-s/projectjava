package com.example.watchai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for AiResponse and StreamEvent classes.
 */
public class AiResponseTest {

    @Test
    public void spokenReply_containsText() {
        AiResponse.SpokenReply reply = new AiResponse.SpokenReply("Hello there");
        assertEquals("Hello there", reply.getText());
    }

    @Test
    public void toolCall_containsFunctionNameAndArgs() throws Exception {
        JSONObject args = new JSONObject();
        args.put("query", "test song");

        AiResponse.ToolCall toolCall = new AiResponse.ToolCall("play_music", args);
        assertEquals("play_music", toolCall.getFunctionName());
        assertEquals("test song", toolCall.getArguments().getString("query"));
    }

    @Test
    public void streamEvent_token_containsText() {
        StreamEvent.Token token = new StreamEvent.Token("hello");
        assertEquals("hello", token.getText());
    }

    @Test
    public void streamEvent_error_containsMessage() {
        StreamEvent.Error error = new StreamEvent.Error("something went wrong");
        assertEquals("something went wrong", error.getMessage());
    }

    @Test
    public void streamEvent_done_canBeInstantiated() {
        StreamEvent.Done done = new StreamEvent.Done();
        assertNotNull(done);
    }

    @Test
    public void streamEvent_toolCall_containsFields() throws Exception {
        JSONObject args = new JSONObject();
        args.put("contact_name", "Mom");

        StreamEvent.ToolCall tc = new StreamEvent.ToolCall("place_call", args);
        assertEquals("place_call", tc.getFunctionName());
        assertEquals("Mom", tc.getArguments().getString("contact_name"));
    }
}
