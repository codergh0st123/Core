package ru.core.text;

import net.md_5.bungee.api.ChatColor;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Colors {

    private static final Pattern GRADIENT = Pattern.compile("<gradient:#([0-9A-Fa-f]{6}):#([0-9A-Fa-f]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern SOLID = Pattern.compile("<(?:solid|color):#([0-9A-Fa-f]{6})>");
    private static final Pattern RAW = Pattern.compile("&x((?:&[0-9A-Fa-f]){6})");
    private static final Pattern HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final String FORMATS = "klmnoKLMNO";
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private Colors() {
    }

    public static String apply(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = gradient(input);
        text = solid(text);
        text = raw(text);
        text = hex(text);
        return legacy(text);
    }

    private static String legacy(String input) {
        char[] chars = input.toCharArray();
        for (int index = 0; index + 1 < chars.length; index++) {
            if (chars[index] != '&' || CODES.indexOf(chars[index + 1]) < 0) {
                continue;
            }
            chars[index] = ChatColor.COLOR_CHAR;
            chars[index + 1] = Character.toLowerCase(chars[index + 1]);
        }
        return new String(chars);
    }

    private static String gradient(String input) {
        if (input.indexOf('<') < 0) {
            return input;
        }
        Matcher matcher = GRADIENT.matcher(input);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(input, last, matcher.start());
            result.append(paint(matcher.group(3), decode(matcher.group(1)), decode(matcher.group(2))));
            last = matcher.end();
        }
        result.append(input.substring(last));
        return result.toString();
    }

    private static String solid(String input) {
        if (input.indexOf('<') < 0) {
            return input;
        }
        Matcher matcher = SOLID.matcher(input);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(input, last, matcher.start());
            result.append(ChatColor.of("#" + matcher.group(1)));
            last = matcher.end();
        }
        result.append(input.substring(last));
        return result.toString();
    }

    private static String raw(String input) {
        Matcher matcher = RAW.matcher(input);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(input, last, matcher.start());
            result.append(matcher.group().replace('&', ChatColor.COLOR_CHAR));
            last = matcher.end();
        }
        result.append(input.substring(last));
        return result.toString();
    }

    private static String hex(String input) {
        Matcher matcher = HEX.matcher(input);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(input, last, matcher.start());
            result.append(ChatColor.of("#" + matcher.group(1)));
            last = matcher.end();
        }
        result.append(input.substring(last));
        return result.toString();
    }

    private static String paint(String text, Color from, Color to) {
        int visible = visible(text);
        if (visible == 0) {
            return "";
        }
        StringBuilder formats = new StringBuilder();
        StringBuilder result = new StringBuilder();
        int index = 0;
        for (int position = 0; position < text.length(); position++) {
            char current = text.charAt(position);
            if ((current == '&' || current == ChatColor.COLOR_CHAR) && position + 1 < text.length()) {
                char code = text.charAt(position + 1);
                if (CODES.indexOf(code) >= 0) {
                    if (FORMATS.indexOf(code) >= 0) {
                        formats.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(code));
                    } else if (code == 'r' || code == 'R') {
                        formats.setLength(0);
                    }
                    position++;
                    continue;
                }
            }
            float ratio = visible == 1 ? 0F : (float) index / (visible - 1);
            result.append(ChatColor.of(mix(from, to, ratio))).append(formats).append(current);
            index++;
        }
        return result.toString();
    }

    private static int visible(String text) {
        int count = 0;
        for (int position = 0; position < text.length(); position++) {
            char current = text.charAt(position);
            if ((current == '&' || current == ChatColor.COLOR_CHAR) && position + 1 < text.length()
                    && CODES.indexOf(text.charAt(position + 1)) >= 0) {
                position++;
                continue;
            }
            count++;
        }
        return count;
    }

    private static Color mix(Color from, Color to, float ratio) {
        int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio);
        int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio);
        int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio);
        return new Color(red, green, blue);
    }

    private static Color decode(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }
}
