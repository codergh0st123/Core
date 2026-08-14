package ru.core.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.core.Core;

public final class CoreExpansion extends PlaceholderExpansion {

    private final Core plugin;

    public CoreExpansion(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "core";
    }

    @Override
    public String getAuthor() {
        return "Core";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        Player player = offline == null ? null : offline.getPlayer();
        String value = plugin.placeholders().resolve(player, params);
        return value == null ? null : value;
    }
}
