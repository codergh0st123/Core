package ru.core.text;

import ru.core.Core;

import java.util.List;

public final class TimeFormat {

    private TimeFormat() {
    }

    public static String compact(Core plugin, long seconds) {
        String pattern;
        if (seconds < 60L) {
            pattern = line(plugin, "TIME.SECONDS", "%secondr% сек");
        } else if (seconds < 3600L) {
            pattern = line(plugin, "TIME.MINUTES", "%minutes% мин %secondr% сек");
        } else {
            pattern = line(plugin, "TIME.HOURS", "%hourse% ч");
        }
        return fill(plugin, pattern, seconds);
    }

    public static String fill(Core plugin, String text, long seconds) {
        long total = Math.max(0L, seconds);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long rest = total % 60L;
        return text.replace("%hourse%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%secondr%", String.valueOf(rest))
                .replace("%hourse-word%", plural(plugin, "HOURS", hours))
                .replace("%minutes-word%", plural(plugin, "MINUTES", minutes))
                .replace("%secondr-word%", plural(plugin, "SECONDS", rest));
    }

    private static String line(Core plugin, String path, String fallback) {
        List<String> lines = plugin.configs().config().getStringList(path);
        return lines.isEmpty() ? fallback : lines.get(0);
    }

    private static String plural(Core plugin, String key, long value) {
        List<String> forms = plugin.configs().config().getStringList("TIME.PLURAL." + key);
        if (forms.size() < 3) {
            return "";
        }
        long hundred = value % 100L;
        long ten = value % 10L;
        if (hundred >= 11L && hundred <= 14L) {
            return forms.get(2);
        }
        if (ten == 1L) {
            return forms.get(0);
        }
        if (ten >= 2L && ten <= 4L) {
            return forms.get(1);
        }
        return forms.get(2);
    }
}
