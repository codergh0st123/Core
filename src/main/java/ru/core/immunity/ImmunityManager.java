package ru.core.immunity;

import org.bukkit.entity.Player;
import ru.core.Core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ImmunityManager {

    private final Core plugin;
    private volatile boolean enabled;
    private volatile boolean allowBannedLogin;
    private volatile boolean blocks;
    private volatile boolean items;
    private volatile boolean knockback;
    private volatile double blockRadius;
    private volatile double itemRadius;
    private volatile double horizontalVelocity;
    private volatile double verticalVelocity;
    private volatile Set<String> players = Set.of();
    private volatile Set<String> commands = Set.of();

    public ImmunityManager(Core plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.configs().config().getBoolean("IMMUNITY.ENABLED", true);
        allowBannedLogin = plugin.configs().config().getBoolean("IMMUNITY.ALLOW-BANNED-LOGIN", true);
        blocks = plugin.configs().config().getBoolean("IMMUNITY.BLOCKS.ENABLED", true);
        items = plugin.configs().config().getBoolean("IMMUNITY.ITEMS.ENABLED", true);
        knockback = plugin.configs().config().getBoolean("IMMUNITY.KNOCKBACK.ENABLED", true);
        blockRadius = radius("IMMUNITY.BLOCKS.RADIUS", 6.0D);
        itemRadius = radius("IMMUNITY.ITEMS.RADIUS", 6.0D);
        horizontalVelocity = Math.max(0.0D, plugin.configs().config().getDouble("IMMUNITY.KNOCKBACK.HORIZONTAL", 1.6D));
        verticalVelocity = Math.max(0.0D, plugin.configs().config().getDouble("IMMUNITY.KNOCKBACK.VERTICAL", 0.5D));
        players = values("IMMUNITY.PLAYERS");
        commands = values("IMMUNITY.COMMANDS");
    }

    public boolean protectedPlayer(Player player) {
        return player != null && protectedName(player.getName());
    }

    public boolean protectedName(String name) {
        return enabled && name != null && players.contains(name.toUpperCase(Locale.ROOT));
    }

    public boolean protectedCommand(String command) {
        return command != null && commands.contains(command.toUpperCase(Locale.ROOT));
    }

    public Set<String> players() {
        return players;
    }

    public boolean allowBannedLogin() {
        return enabled && allowBannedLogin;
    }

    public boolean blocks() {
        return enabled && blocks;
    }

    public boolean items() {
        return enabled && items;
    }

    public boolean knockback() {
        return enabled && knockback;
    }

    public double blockRadius() {
        return blockRadius;
    }

    public double itemRadius() {
        return itemRadius;
    }

    public double horizontalVelocity() {
        return horizontalVelocity;
    }

    public double verticalVelocity() {
        return verticalVelocity;
    }

    private double radius(String path, double fallback) {
        return Math.max(0.0D, plugin.configs().config().getDouble(path, fallback));
    }

    private Set<String> values(String path) {
        Set<String> result = new HashSet<>();
        for (String value : plugin.configs().config().getStringList(path)) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
