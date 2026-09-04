package com.poorgrammera.bydsubai.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LogParser.java
 * 
 * ADB Logcat의 다양한 출력 포맷을 유연하게 처리하기 위한 파서 클래스입니다.
 */
public class LogParser {

    // 1. Threadtime 포맷 (예: "06-25 23:15:03.123 1234 5678 D BYD_Tag: Message")
    private static final Pattern THREADTIME_PATTERN = Pattern.compile(
        "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEAF])\\s+([^:]+):\\s*(.*)$"
    );

    // 2. Simple 포맷 (예: "D/BYD_Tag(1234): Message")
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
        "^([VDIWEAF])/([^\\(]+)\\s*\\((\\s*\\d+)\\):\\s*(.*)$"
    );

    /**
     * 로그 라인에 특정 태그가 포함되어 있는지 확인합니다.
     */
    public static boolean containsTag(String rawLine, String targetTag) {
        if (rawLine == null || rawLine.isEmpty()) {
            return false;
        }

        String extractedTag = null;

        Matcher threadtimeMatcher = THREADTIME_PATTERN.matcher(rawLine);
        if (threadtimeMatcher.matches()) {
            extractedTag = threadtimeMatcher.group(5).trim();
        } else {
            Matcher simpleMatcher = SIMPLE_PATTERN.matcher(rawLine);
            if (simpleMatcher.matches()) {
                extractedTag = simpleMatcher.group(2).trim();
            }
        }

        if (extractedTag != null) {
            return extractedTag.toLowerCase().contains(targetTag.toLowerCase());
        }

        // 패턴 매칭에 실패한 경우 전체 텍스트에서 검색
        return rawLine.toLowerCase().contains(targetTag.toLowerCase());
    }

    /**
     * 터미널에서 보기 좋게 로그를 포맷팅합니다.
     */
    public static String formatLogForTerminal(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return "";
        }

        Matcher m1 = THREADTIME_PATTERN.matcher(rawLine);
        if (m1.matches()) {
            String time = m1.group(1);
            String level = m1.group(4);
            String tag = m1.group(5).trim();
            String msg = m1.group(6);
            return String.format("[%s] %s/%s: %s", time, getLogLevelEmoji(level), tag, msg);
        }

        Matcher m2 = SIMPLE_PATTERN.matcher(rawLine);
        if (m2.matches()) {
            String level = m2.group(1);
            String tag = m2.group(2).trim();
            String msg = m2.group(4);
            return String.format("%s/%s: %s", getLogLevelEmoji(level), tag, msg);
        }

        return rawLine;
    }

    /**
     * 로그 라인이 SubAI 패키지(PID 또는 패키지명 포함 여부)에 속하는지 검사합니다.
     */
    public static boolean isSubAiLog(String rawLine, int myPid) {
        if (rawLine == null || rawLine.isEmpty()) {
            return false;
        }

        // Exclude system spam logs that mention our package name but are printed by other processes
        String lower = rawLine.toLowerCase();
        if (lower.contains("pm2p5controller") || lower.contains("get running package name")) {
            return false;
        }

        Matcher threadtimeMatcher = THREADTIME_PATTERN.matcher(rawLine);
        if (threadtimeMatcher.matches()) {
            try {
                int pid = Integer.parseInt(threadtimeMatcher.group(2).trim());
                if (pid == myPid) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        Matcher simpleMatcher = SIMPLE_PATTERN.matcher(rawLine);
        if (simpleMatcher.matches()) {
            try {
                int pid = Integer.parseInt(simpleMatcher.group(3).trim());
                if (pid == myPid) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        return lower.contains("bydsubai") || lower.contains("subai");
    }

    private static String getLogLevelEmoji(String level) {
        switch (level) {
            case "V": return "💜 V";
            case "D": return "💙 D";
            case "I": return "💚 I";
            case "W": return "💛 W";
            case "E": return "❤️ E";
            case "A": return "🧡 A";
            default: return level;
        }
    }
}
