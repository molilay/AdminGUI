package me.admin.gui.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([чhдdмmсs])", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input);
        long totalSeconds = 0;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            switch (unit) {
                case "с": case "s": totalSeconds += value; break;
                case "м": case "m": totalSeconds += value * 60; break;
                case "ч": case "h": totalSeconds += value * 3600; break;
                case "д": case "d": totalSeconds += value * 86400; break;
            }
        }
        return totalSeconds;
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
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(millis));
    }

    public static String formatRemaining(long endTimeMillis) {
        long remaining = (endTimeMillis - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) return "Истекло";
        return formatDuration(remaining);
    }
}
