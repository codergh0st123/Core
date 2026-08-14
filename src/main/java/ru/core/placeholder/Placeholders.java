package ru.core.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.storage.Profile;
import ru.core.text.Colors;
import ru.core.text.TimeFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {

    private static final Pattern CUSTOM = Pattern.compile("%CORE_([^%\\s]+)%", Pattern.CASE_INSENSITIVE);

    private final Core plugin;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> languages = new LinkedHashMap<>();
    private boolean hooked;

    public Placeholders(Core plugin) {
        this.plugin = plugin;
    }

    public void hook(boolean hooked) {
        this.hooked = hooked;
    }

    public void rebuild() {
        values.clear();
        languages.clear();
        flatten(plugin.configs().placeholders(), "", values);
        for (String code : plugin.configs().languages()) {
            ConfigurationSection section = plugin.configs().lang().getConfigurationSection(code);
            if (section == null) {
                plugin.getLogger().warning("Локализация " + code + " включена, но не найдена в lang.yml");
                continue;
            }
            Map<String, String> keys = new LinkedHashMap<>();
            flatten(section, "", keys);
            languages.put(code, keys);
        }
    }

    public int languageCount() {
        return languages.size();
    }

    public String languageList() {
        return String.join(", ", languages.keySet());
    }

    public boolean hasLanguage(String code) {
        return languages.containsKey(code.toUpperCase(Locale.ROOT));
    }

    public String apply(Player player, String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = input;
        if (text.indexOf('%') >= 0) {
            text = custom(player, text);
            text = base(player, text);
            if (hooked && player != null) {
                text = PlaceholderAPI.setPlaceholders(player, text);
            }
        }
        return Colors.apply(text);
    }

    public List<String> apply(Player player, List<String> input) {
        List<String> result = new ArrayList<>(input.size());
        for (String line : input) {
            String parsed = apply(player, line);
            if (parsed.indexOf('\n') >= 0) {
                for (String part : parsed.split("\n")) {
                    result.add(part);
                }
                continue;
            }
            result.add(parsed);
        }
        return result;
    }

    public String resolve(Player player, String key) {
        String upper = key.toUpperCase(Locale.ROOT);
        if (upper.equals("TIME")) {
            return time(player);
        }
        if (upper.equals("ONLINE")) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }
        if (upper.equals("KILLS")) {
            return String.valueOf(kills(player));
        }
        if (upper.equals("DEATH") || upper.equals("DEATHS")) {
            return String.valueOf(deaths(player));
        }
        if (upper.equals("NICK")) {
            return player == null ? "" : player.getName();
        }
        if (upper.startsWith("LANG:")) {
            String rest = upper.substring(5);
            int split = rest.indexOf(':');
            if (split > 0) {
                return language(rest.substring(0, split), rest.substring(split + 1));
            }
            return language(language(player), rest);
        }
        String value = values.get(upper);
        if (value != null) {
            return value;
        }
        int split = upper.indexOf(':');
        if (split > 0) {
            return language(upper.substring(0, split), upper.substring(split + 1));
        }
        return null;
    }

    public String time(Player player) {
        Profile profile = plugin.data().profile(player);
        return TimeFormat.compact(plugin, profile == null ? 0L : profile.playtime());
    }

    public String language(String code, String key) {
        Map<String, String> keys = languages.get(code.toUpperCase(Locale.ROOT));
        if (keys == null) {
            return null;
        }
        return keys.get(key.toUpperCase(Locale.ROOT));
    }

    public String language(Player player) {
        Profile profile = plugin.data().profile(player);
        if (profile == null || profile.language() == null) {
            return plugin.configs().defaultLanguage();
        }
        return profile.language();
    }

    private String custom(Player player, String input) {
        Matcher matcher = CUSTOM.matcher(input);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String value = resolve(player, matcher.group(1));
            result.append(input, last, matcher.start());
            result.append(value == null ? matcher.group() : value);
            last = matcher.end();
        }
        if (last == 0) {
            return input;
        }
        result.append(input.substring(last));
        return result.toString();
    }

    private String base(Player player, String input) {
        String text = input;
        if (text.contains("%online%") || text.contains("%ONLINE%")) {
            String online = String.valueOf(Bukkit.getOnlinePlayers().size());
            text = text.replace("%online%", online).replace("%ONLINE%", online);
        }
        if (text.contains("%kills%") || text.contains("%KILLS%")) {
            String kills = String.valueOf(kills(player));
            text = text.replace("%kills%", kills).replace("%KILLS%", kills);
        }
        if (text.contains("%death%") || text.contains("%DEATH%")) {
            String deaths = String.valueOf(deaths(player));
            text = text.replace("%death%", deaths).replace("%DEATH%", deaths);
        }
        if (text.contains("%nick%") || text.contains("%NICK%")) {
            String nick = player == null ? "" : player.getName();
            text = text.replace("%nick%", nick).replace("%NICK%", nick);
        }
        return text;
    }

    private int kills(Player player) {
        Profile profile = plugin.data().profile(player);
        return profile == null ? 0 : profile.kills();
    }

    private int deaths(Player player) {
        Profile profile = plugin.data().profile(player);
        return profile == null ? 0 : profile.deaths();
    }

    private void flatten(ConfigurationSection section, String prefix, Map<String, String> target) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key.toUpperCase(Locale.ROOT) : prefix + ":" + key.toUpperCase(Locale.ROOT);
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    flatten(child, path, target);
                }
                continue;
            }
            if (section.isList(key)) {
                target.put(path, String.join("\n", section.getStringList(key)));
                continue;
            }
            Object value = section.get(key);
            if (value != null) {
                target.put(path, String.valueOf(value));
            }
        }
    }
}
