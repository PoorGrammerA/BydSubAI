package com.poorgrammera.bydsubai.gemini;

/** Thread-safe transcript for one Gemini Live session. */
final class GeminiConversationTranscript {
    private final StringBuilder buffer = new StringBuilder();

    synchronized void appendDriver(String text) {
        buffer.append("Driver: ").append(text).append('\n');
    }

    synchronized void appendAssistant(String text) {
        buffer.append("AI: ").append(text).append('\n');
    }

    synchronized String drain() {
        String transcript = buffer.toString();
        buffer.setLength(0);
        return transcript;
    }
}
