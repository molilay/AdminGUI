package me.admin.gui.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([чhдdмmсs])", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static long parseDuration(String input) {
        if (input == null || input.isBlank()) return 0;

        String normalized = input.replaceAll("\\s+", "");
        Matcher matcher = DURATION_PATTERN.matcher(normalized);
        long totalSeconds = 0;
        int parsedUntil = 0;
        while (matcher.find()) {
            if (matcher.start() != parsedUntil) return 0;
            try {
                long value = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2).toLowerCase(Locale.ROOT);
                long multiplier = switch (unit) {
                    case "с", "s" -> 1;
                    case "м", "m" -> 60;
                    case "ч", "h" -> 3600;
                    case "д", "d" -> 86400;
                    default -> throw new IllegalStateException("Unexpected duration unit: " + unit);
                };
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(value, multiplier));
            } catch (ArithmeticException | NumberFormatException e) {
                return 0;
            }
            parsedUntil = matcher.end();
        }
        return parsedUntil == normalized.length() ? totalSeconds : 0;
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0с";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        if (minutes > 0) sb.append(minutes).append("м ");
        if (secs > 0) sb.append(secs).append("с");
        return sb.toString().trim();
    }

    public static long toEpochSeconds(Duration duration) {
        return duration.getSeconds();
    }

    public static String formatLogTime(LocalDateTime time) {
        return time.format(LOG_FORMATTER);
    }

    public static String formatLogTime(long millis) {
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public static String formatRemaining(long endTimeMillis) {
        long remaining = (endTimeMillis - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) return "Истекло";
        return formatDuration(remaining);
    }
}
