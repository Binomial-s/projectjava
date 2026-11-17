package com.example.watchai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the ConversationTurn data class.
 */
public class ConversationTurnTest {

    @Test
    public void conversationTurn_hasDefaultTimestamp() {
        ConversationTurn turn = new ConversationTurn("user", "Hello");
        assertTrue(turn.getTimestamp() > 0);
    }

    @Test
    public void conversationTurn_canBeCreatedWithCustomTimestamp() {
        long customTime = 1234567890L;
        ConversationTurn turn = new ConversationTurn("model", "Hi there", customTime);
        assertEquals(customTime, turn.getTimestamp());
    }

    @Test
    public void conversationTurn_rolesAreCorrect() {
        ConversationTurn userTurn = new ConversationTurn("user", "What time is it?");
        ConversationTurn modelTurn = new ConversationTurn("model", "It's 3 PM");

        assertEquals("user", userTurn.getRole());
        assertEquals("model", modelTurn.getRole());
    }

    @Test
    public void conversationTurn_textIsCorrect() {
        ConversationTurn turn = new ConversationTurn("user", "Hello world");
        assertEquals("Hello world", turn.getText());
    }

    @Test
    public void conversationTurn_equalsWorksCorrectly() {
        long timestamp = 1000L;
        ConversationTurn turn1 = new ConversationTurn("user", "Hello", timestamp);
        ConversationTurn turn2 = new ConversationTurn("user", "Hello", timestamp);
        ConversationTurn turn3 = new ConversationTurn("model", "Hello", timestamp);

        assertEquals(turn1, turn2);
        assertNotEquals(turn1, turn3);
    }

    @Test
    public void conversationTurn_toStringContainsFields() {
        ConversationTurn turn = new ConversationTurn("user", "Test", 1000L);
        String str = turn.toString();
        assertTrue(str.contains("user"));
        assertTrue(str.contains("Test"));
    }
}
