package ru.core.version;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.text.Colors;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClientVersionManager {

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final Core plugin;

    public ClientVersionManager(Core plugin) {
        this.plugin = plugin;
    }

    public void check(Player player) {
        if (!plugin.configs().config().getBoolean("CLIENT-VERSIONS.ENABLED", true)
                || !plugin.getServer().getPluginManager().isPluginEnabled("ViaVersion")) {
            return;
        }
        long delay = Math.max(1L, plugin.configs().config().getLong("CLIENT-VERSIONS.CHECK-DELAY", 2L));
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkLater(uuid), delay);
    }

    private void checkLater(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        String clientVersion = clientVersion(uuid);
        Version minimum = Version.parse(plugin.configs().config().getString("CLIENT-VERSIONS.MINIMUM", "1.16.5"));
        Version maximum = Version.parse(plugin.configs().config().getString("CLIENT-VERSIONS.MAXIMUM", "1.21.11"));
        if (clientVersion == null || minimum == null || maximum == null || !allowed(clientVersion, minimum, maximum)) {
            kick(player, clientVersion);
        }
    }

    private boolean allowed(String clientVersion, Version minimum, Version maximum) {
        Version newest = null;
        Matcher matcher = VERSION.matcher(clientVersion);
        while (matcher.find()) {
            Version version = new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
            );
            if (newest == null || version.compareTo(newest) > 0) {
                newest = version;
            }
        }
        return newest != null && newest.compareTo(minimum) >= 0 && newest.compareTo(maximum) <= 0;
    }

    private String clientVersion(UUID uuid) {
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Method versionMethod = api.getClass().getMethod("getPlayerProtocolVersion", UUID.class);
            Object version = versionMethod.invoke(api, uuid);
            return (String) version.getClass().getMethod("getName").invoke(version);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private void kick(Player player, String clientVersion) {
        List<String> messages = plugin.configs().config().getStringList("CLIENT-VERSIONS.MESSAGE");
        if (messages.isEmpty()) {
            messages = List.of("&cПоддерживаемые версии: &f1.16.5-1.21.11");
        }
        String reason = String.join("\n", messages).replace("%MINIMUM%", plugin.configs().config().getString("CLIENT-VERSIONS.MINIMUM", "1.16.5"))
                .replace("%MAXIMUM%", plugin.configs().config().getString("CLIENT-VERSIONS.MAXIMUM", "1.21.11"))
                .replace("%VERSION%", clientVersion == null ? "неизвестно" : clientVersion);
        player.kickPlayer(Colors.apply(reason));
    }

    private static final class Version implements Comparable<Version> {

        private final int major;
        private final int minor;
        private final int patch;

        private Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        private static Version parse(String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = VERSION.matcher(value);
            if (!matcher.matches()) {
                return null;
            }
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
            );
        }

        @Override
        public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(minor, other.minor);
            if (result != 0) {
                return result;
            }
            return Integer.compare(patch, other.patch);
        }
    }
}
