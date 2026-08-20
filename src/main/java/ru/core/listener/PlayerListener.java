package ru.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.world.WorldLoadEvent;
import ru.core.Core;
import ru.core.net.Messenger;
import ru.core.storage.Profile;
import ru.core.text.Msg;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class PlayerListener implements Listener {

    private static final DateTimeFormatter COMMAND_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final String COMMAND_TOKEN = "__CORE_CONSOLE_COMMAND__";

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
        if (!plugin.externalTab()) {
            plugin.boards().create(player);
        }
        plugin.groups().preload(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        plugin.resourcePacks().handle(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player source = event.getPlayer();
        String time = COMMAND_TIME.format(Instant.now());
        String command = event.getMessage();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(source.getUniqueId())) {
                continue;
            }
            Profile profile = plugin.data().profile(viewer);
            if (profile == null || !profile.commandConsole() || !viewer.hasPermission("core.console")) {
                continue;
            }
            for (String line : plugin.configs().messages("CONSOLE-COMMAND")) {
                String text = Msg.format(plugin, viewer, line, "%time%", time, "%player%", source.getName(),
                        "%command%", COMMAND_TOKEN).replace(COMMAND_TOKEN, command);
                viewer.sendMessage(text);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String text;
        String permission;
        String type;
        String usage;
        if (message.startsWith("!!!")) {
            text = message.substring(3).trim();
            permission = "core.chat.staff";
            type = Messenger.STAFF;
            usage = "STAFF-USAGE";
        } else if (message.startsWith("!!")) {
            text = message.substring(2).trim();
            permission = "core.chat.premium";
            type = Messenger.PREMIUM;
            usage = "PREMIUM-USAGE";
        } else {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> sendChatPrefix(player, permission, type, usage, text));
    }

    private void sendChatPrefix(Player player, String permission, String type, String usage, String text) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission(permission)) {
            Msg.send(plugin, player, "NO-PERMISSION");
            return;
        }
        if (text.isBlank()) {
            Msg.send(plugin, player, usage);
            return;
        }
        plugin.messenger().broadcast(type, player, text);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (plugin.resourcePacks().isResourcePackKick(event.getPlayer())
                || plugin.configs().config().getBoolean("PLAY-FALLBACK.KEEP-KICK-SCREEN", true)) {
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
        Msg.send(plugin, player, "PLAY-SERVER-LOST", "%server%", plugin.messenger().server(), "%error%", reason,
                "%lobby%", lobby);
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
