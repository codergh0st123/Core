package ru.core.immunity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;
import ru.core.Core;

public final class ImmunityListener implements Listener {

    private final Core plugin;
    private final ImmunityManager immunity;

    public ImmunityListener(Core plugin) {
        this.plugin = plugin;
        this.immunity = plugin.immunity();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (immunity.protectedPlayer((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player target = (Player) event.getEntity();
        if (!immunity.protectedPlayer(target)) {
            return;
        }
        event.setCancelled(true);
        repel(attacker(event.getDamager()), target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }
        Player target = (Player) event.getRightClicked();
        if (!immunity.protectedPlayer(target)) {
            return;
        }
        event.setCancelled(true);
        repel(event.getPlayer(), target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!immunity.blocks()) {
            return;
        }
        Player target = protectedNearby(event.getBlockPlaced().getLocation(), immunity.blockRadius());
        if (target == null || target.getUniqueId().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        repel(event.getPlayer(), target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!immunity.blocks()) {
            return;
        }
        Location location = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        Player target = protectedNearby(location, immunity.blockRadius());
        if (target == null || target.getUniqueId().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        repel(event.getPlayer(), target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!immunity.items()) {
            return;
        }
        Player target = protectedNearby(event.getPlayer().getLocation(), immunity.itemRadius());
        if (target == null || target.getUniqueId().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        repel(event.getPlayer(), target);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (immunity.protectedPlayer(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_BANNED
                && immunity.allowBannedLogin() && immunity.protectedPlayer(event.getPlayer())) {
            event.allow();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        protectCommand(event.getPlayer(), event.getMessage(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        protectCommand(event.getSender(), event.getCommand(), event);
    }

    private void protectCommand(CommandSender sender, String command, Cancellable event) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 2) {
            return;
        }
        String label = parts[0];
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        if (!immunity.protectedCommand(label)) {
            return;
        }
        for (int index = 1; index < parts.length; index++) {
            String target = parts[index].replace(",", "");
            if (immunity.protectedName(target)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private Player protectedNearby(Location location, double radius) {
        if (radius <= 0.0D) {
            return null;
        }
        double maximum = radius * radius;
        for (String name : immunity.players()) {
            Player player = Bukkit.getPlayer(name);
            if (player == null || !player.isOnline() || !player.getWorld().equals(location.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= maximum) {
                return player;
            }
        }
        return null;
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile && ((Projectile) entity).getShooter() instanceof Player) {
            return (Player) ((Projectile) entity).getShooter();
        }
        return null;
    }

    private void repel(Player attacker, Player target) {
        if (!immunity.knockback() || attacker == null || attacker.getUniqueId().equals(target.getUniqueId())
                || !attacker.getWorld().equals(target.getWorld())) {
            return;
        }
        Vector direction = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            direction = attacker.getLocation().getDirection().multiply(-1.0D);
            direction.setY(0.0D);
        }
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(1.0D, 0.0D, 0.0D);
        }
        attacker.setVelocity(direction.normalize().multiply(immunity.horizontalVelocity()).setY(immunity.verticalVelocity()));
    }
}
