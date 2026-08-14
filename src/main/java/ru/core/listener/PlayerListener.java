package ru.core.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.core.Core;
import ru.core.storage.Profile;

public final class PlayerListener implements Listener {

    private final Core plugin;

    public PlayerListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.data().join(player);
        plugin.boards().create(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.data().quit(player);
        plugin.bossBars().remove(player);
        plugin.boards().remove(player);
        plugin.boards().forget(player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Profile profile = plugin.data().profile(victim);
        if (profile != null) {
            profile.addDeath();
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Profile other = plugin.data().profile(killer);
        if (other != null) {
            other.addKill();
        }
    }
}
