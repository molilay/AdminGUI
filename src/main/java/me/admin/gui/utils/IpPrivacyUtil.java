package me.admin.gui.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Presentation-only IP masking. Authorization must still be checked by callers. */
public final class IpPrivacyUtil {

    private static final Pattern IPV4_TOKEN = Pattern.compile("(?<![\\w:.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w:.])");
    private static final Pattern IPV6_CANDIDATE = Pattern.compile("(?<![0-9A-Fa-f:.])[0-9A-Fa-f:.]{3,45}(?![0-9A-Fa-f:.])");

    private IpPrivacyUtil() {}

    public static String mask(String address) {
        if (address == null || address.isBlank()) return "—";
        String value = address.trim();
        if (value.indexOf(':') >= 0) return maskIpv6(value);
        String[] parts = value.split("\\.", -1);
        if (parts.length == 4) return parts[0] + "." + parts[1] + ".x.x";
        return "hidden";
    }

    /** Masks IP literals embedded in audit descriptions without altering names or ordinary numbers. */
    public static String redactText(String text) {
        if (text == null || text.isEmpty()) return text;
        String withoutIpv6 = replaceValid(text, IPV6_CANDIDATE, true);
        return replaceValid(withoutIpv6, IPV4_TOKEN, false);
    }

    private static String replaceValid(String input, Pattern pattern, boolean ipv6) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder(input.length());
        while (matcher.find()) {
            String candidate = matcher.group();
            String replacement = candidate;
            try {
                byte[] bytes = InetAddress.getByName(candidate).getAddress();
                if ((ipv6 && bytes.length == 16 && candidate.chars().filter(ch -> ch == ':').count() >= 2)
                        || (!ipv6 && bytes.length == 4 && validIpv4(candidate))) {
                    replacement = mask(candidate);
                }
            } catch (UnknownHostException ignored) {
                // Keep malformed tokens unchanged; callers must not use this as authorization.
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean validIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                if (part.isEmpty() || Integer.parseInt(part) > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static String maskIpv6(String value) {
        try {
            byte[] bytes = InetAddress.getByName(value).getAddress();
            if (bytes.length != 16) return "hidden";
            return String.format("%x:%x:%x:%x::/64",
                    pair(bytes, 0), pair(bytes, 2), pair(bytes, 4), pair(bytes, 6));
        } catch (UnknownHostException ignored) {
            return "hidden";
        }
    }

    private static int pair(byte[] bytes, int index) {
        return ((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff);
    }
}
