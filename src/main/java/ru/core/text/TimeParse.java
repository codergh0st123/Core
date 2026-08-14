package ru.core.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParse {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdwSMHDW]?)");

    private TimeParse() {
    }

    public static long seconds(String input) {
        if (input == null || input.isEmpty()) {
            return -1L;
        }
        Matcher matcher = PART.matcher(input);
        long total = 0L;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return -1L;
            }
            end = matcher.end();
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return -1L;
            }
            total += value * unit(matcher.group(2));
            if (total < 0L) {
                return -1L;
            }
        }
        if (end != input.length()) {
            return -1L;
        }
        return total;
    }

    private static long unit(String suffix) {
        if (suffix.isEmpty()) {
            return 1L;
        }
        switch (Character.toLowerCase(suffix.charAt(0))) {
            case 'm':
                return 60L;
            case 'h':
                return 3600L;
            case 'd':
                return 86400L;
            case 'w':
                return 604800L;
            default:
                return 1L;
        }
    }
}
