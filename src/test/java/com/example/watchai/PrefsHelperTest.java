package com.example.watchai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for PrefsHelper utility methods.
 */
public class PrefsHelperTest {

    @Test
    public void findMatchingShortcut_returnsMatchingShortcut() {
        List<VoiceShortcut> shortcuts = Arrays.asList(
                new VoiceShortcut("good night", "google", "good night routine"),
                new VoiceShortcut("call mom", "call", "Mom")
        );

        VoiceShortcut result = PrefsHelper.findMatchingShortcut("good night vedant", shortcuts);
        assertNotNull(result);
        assertEquals("good night", result.getPhrase());
        assertEquals("google", result.getActionType());
    }

    @Test
    public void findMatchingShortcut_isCaseInsensitive() {
        List<VoiceShortcut> shortcuts = Arrays.asList(
                new VoiceShortcut("good night", "google", "good night routine")
        );

        VoiceShortcut result = PrefsHelper.findMatchingShortcut("GOOD NIGHT", shortcuts);
        assertNotNull(result);
    }

    @Test
    public void findMatchingShortcut_returnsNullWhenNoMatch() {
        List<VoiceShortcut> shortcuts = Arrays.asList(
                new VoiceShortcut("good night", "google", "good night routine")
        );

        VoiceShortcut result = PrefsHelper.findMatchingShortcut("hello world", shortcuts);
        assertNull(result);
    }

    @Test
    public void findMatchingShortcut_returnsNullForEmptyPhrase() {
        List<VoiceShortcut> shortcuts = Arrays.asList(
                new VoiceShortcut("", "google", "test")
        );

        VoiceShortcut result = PrefsHelper.findMatchingShortcut("test", shortcuts);
        assertNull(result);
    }

    @Test
    public void findMatchingShortcut_returnsFirstMatch() {
        List<VoiceShortcut> shortcuts = Arrays.asList(
                new VoiceShortcut("hello", "google", "hello"),
                new VoiceShortcut("hello world", "spotify", "hello world song")
        );

        VoiceShortcut result = PrefsHelper.findMatchingShortcut("hello world", shortcuts);
        assertNotNull(result);
        assertEquals("hello", result.getPhrase());
    }

    @Test
    public void findMatchingShortcut_returnsNullForEmptyList() {
        List<VoiceShortcut> shortcuts = new ArrayList<>();
        VoiceShortcut result = PrefsHelper.findMatchingShortcut("hello", shortcuts);
        assertNull(result);
    }
}
