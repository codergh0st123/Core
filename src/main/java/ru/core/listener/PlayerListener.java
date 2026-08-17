package ru.core.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.world.WorldLoadEvent;
import ru.core.Core;
import ru.core.storage.Profile;
import ru.core.text.Msg;

public final class PlayerListener implements Listener {

    private final Core plugin;

    public PlayerListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoinMessage(PlayerJoinEvent event) {
        if (plugin.configs().config().getBoolean("MESSAGES.HIDE.JOIN", false)) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitMessage(PlayerQuitEvent event) {
        if (plugin.configs().config().getBoolean("MESSAGES.HIDE.QUIT", false)) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.applyWorldRules(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.clientVersions().check(player);
        plugin.data().join(player);
        plugin.presence().join(player);
        plugin.resourcePacks().join(player);
        plugin.boards().create(player);
        plugin.groups().preload(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        plugin.resourcePacks().handle(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (plugin.resourcePacks().isResourcePackKick(event.getPlayer())) {
            return;
        }
        String lobby = plugin.proxyConnector().fallbackServer();
        if (lobby == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.proxyConnector().connect(player, lobby)) {
            return;
        }
        event.setCancelled(true);
        String reason = PlainTextComponentSerializer.plainText().serialize(event.reason())
                .replace('\n', ' ').replace('\r', ' ');
        if (reason.isBlank()) {
            reason = "Неизвестная ошибка";
        }
        Msg.send(plugin, player, "PLAY-SERVER-LOST", "%server%", plugin.messenger().server(), "%error%", reason);
        Msg.send(plugin, player, "PLAY-FALLBACK", "%server%", lobby);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.data().quit(player);
        plugin.presence().quit(player);
        plugin.debug().remove(player.getUniqueId());
        plugin.bossBars().remove(player);
        plugin.removePacketState(player);
        plugin.boards().remove(player);
        plugin.boards().forget(player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.nameTags().queueHealthUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.nameTags().queueHealthUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.nameTags().queueHealthUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathMessage(PlayerDeathEvent event) {
        if (plugin.configs().config().getBoolean("MESSAGES.HIDE.DEATHS", false)) {
            event.setDeathMessage(null);
        }
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
