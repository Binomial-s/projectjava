package com.example.watchai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the VoiceShortcut data class.
 */
public class VoiceShortcutTest {

    @Test
    public void voiceShortcut_constructorWithFourArgs() {
        VoiceShortcut shortcut = new VoiceShortcut("good night", "google", "query", "body");
        assertEquals("good night", shortcut.getPhrase());
        assertEquals("google", shortcut.getActionType());
        assertEquals("query", shortcut.getTarget());
        assertEquals("body", shortcut.getExtra());
    }

    @Test
    public void voiceShortcut_constructorWithThreeArgs() {
        VoiceShortcut shortcut = new VoiceShortcut("hello", "call", "Mom");
        assertEquals("hello", shortcut.getPhrase());
        assertEquals("call", shortcut.getActionType());
        assertEquals("Mom", shortcut.getTarget());
        assertEquals("", shortcut.getExtra());
    }

    @Test
    public void voiceShortcut_equalsWorksCorrectly() {
        VoiceShortcut s1 = new VoiceShortcut("hello", "call", "Mom", "");
        VoiceShortcut s2 = new VoiceShortcut("hello", "call", "Mom", "");
        VoiceShortcut s3 = new VoiceShortcut("hello", "message", "Mom", "");

        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
    }

    @Test
    public void voiceShortcut_toStringContainsFields() {
        VoiceShortcut shortcut = new VoiceShortcut("hello", "call", "Mom", "");
        String str = shortcut.toString();
        assertTrue(str.contains("hello"));
        assertTrue(str.contains("call"));
        assertTrue(str.contains("Mom"));
    }
}
