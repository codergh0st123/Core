package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EntityLimiterModule {

    private final Core plugin;
    private BukkitTask task;
    private int minimumAge;

    public EntityLimiterModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("ENTITY-LIMITER.ENABLED", false)) {
            return;
        }
        minimumAge = Math.max(0, plugin.configs().config().getInt("ENTITY-LIMITER.MINIMUM-AGE", 600));
        long interval = Math.max(20L, plugin.configs().config().getLong("ENTITY-LIMITER.CHECK-INTERVAL", 60L) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::limit, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void limit() {
        for (World world : Bukkit.getWorlds()) {
            ConfigurationSection rules = rules(world);
            if (rules == null || !rules.getBoolean("ENABLED", true)) {
                continue;
            }
            List<Entity> items = new ArrayList<>();
            List<Entity> experience = new ArrayList<>();
            List<Entity> projectiles = new ArrayList<>();
            List<Entity> fallingBlocks = new ArrayList<>();
            List<Entity> monsters = new ArrayList<>();
            List<Entity> animals = new ArrayList<>();
            for (Entity entity : world.getEntities()) {
                classify(entity, items, experience, projectiles, fallingBlocks, monsters, animals);
            }
            trim(items, rules.getInt("MAX-ITEMS", 100));
            trim(experience, rules.getInt("MAX-EXPERIENCE", 100));
            trim(projectiles, rules.getInt("MAX-PROJECTILES", 100));
            trim(fallingBlocks, rules.getInt("MAX-FALLING-BLOCKS", 25));
            trim(monsters, rules.getInt("MAX-MONSTERS", 150));
            trim(animals, rules.getInt("MAX-ANIMALS", 80));
        }
    }

    private ConfigurationSection rules(World world) {
        ConfigurationSection worlds = plugin.configs().config().getConfigurationSection("ENTITY-LIMITER.WORLDS");
        if (worlds == null) {
            return null;
        }
        ConfigurationSection rules = worlds.getConfigurationSection(world.getName());
        return rules == null ? worlds.getConfigurationSection("DEFAULT") : rules;
    }

    private void classify(Entity entity, List<Entity> items, List<Entity> experience, List<Entity> projectiles,
                          List<Entity> fallingBlocks, List<Entity> monsters, List<Entity> animals) {
        if (entity instanceof Item) {
            items.add(entity);
            return;
        }
        if (entity instanceof ExperienceOrb) {
            experience.add(entity);
            return;
        }
        if (entity instanceof Projectile) {
            projectiles.add(entity);
            return;
        }
        if (entity instanceof FallingBlock) {
            fallingBlocks.add(entity);
            return;
        }
        if (entity instanceof Monster) {
            monsters.add(entity);
            return;
        }
        if (entity instanceof Animals && !(entity instanceof Tameable) && !(entity instanceof Villager)) {
            animals.add(entity);
        }
    }

    private void trim(List<Entity> entities, int maximum) {
        if (maximum < 0 || entities.size() <= maximum) {
            return;
        }
        entities.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
        int remove = entities.size() - maximum;
        for (Entity entity : entities) {
            if (remove == 0) {
                return;
            }
            if (!entity.isValid() || entity.getTicksLived() < minimumAge) {
                continue;
            }
            entity.remove();
            remove--;
        }
    }
}
