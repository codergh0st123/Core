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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EntityLimiterModule {

    private final Core plugin;
    private static final Comparator<Entity> OLDEST_FIRST = Comparator.comparingInt(Entity::getTicksLived).reversed();
    private static final Limits DISABLED = new Limits(false, 0, 0, 0, 0, 0, 0);

    private final Map<String, Limits> limitsByWorld = new HashMap<>();
    private final List<Entity> items = new ArrayList<>();
    private final List<Entity> experience = new ArrayList<>();
    private final List<Entity> projectiles = new ArrayList<>();
    private final List<Entity> fallingBlocks = new ArrayList<>();
    private final List<Entity> monsters = new ArrayList<>();
    private final List<Entity> animals = new ArrayList<>();
    private BukkitTask task;
    private int minimumAge;

    private record Limits(boolean enabled, int items, int experience, int projectiles,
                          int fallingBlocks, int monsters, int animals) {
    }

    public EntityLimiterModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("ENTITY-LIMITER.ENABLED", false)) {
            return;
        }
        minimumAge = Math.max(0, plugin.configs().config().getInt("ENTITY-LIMITER.MINIMUM-AGE", 600));
        limitsByWorld.clear();
        long interval = Math.max(20L, plugin.configs().config().getLong("ENTITY-LIMITER.CHECK-INTERVAL", 60L) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::limit, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        limitsByWorld.clear();
        clearBuckets();
    }

    private void limit() {
        for (World world : Bukkit.getWorlds()) {
            Limits limits = limitsByWorld.computeIfAbsent(world.getName(), name -> readLimits(world));
            if (!limits.enabled()) {
                continue;
            }
            clearBuckets();
            for (Entity entity : world.getEntities()) {
                classify(entity, items, experience, projectiles, fallingBlocks, monsters, animals);
            }
            trim(items, limits.items());
            trim(experience, limits.experience());
            trim(projectiles, limits.projectiles());
            trim(fallingBlocks, limits.fallingBlocks());
            trim(monsters, limits.monsters());
            trim(animals, limits.animals());
        }
        clearBuckets();
    }

    private void clearBuckets() {
        items.clear();
        experience.clear();
        projectiles.clear();
        fallingBlocks.clear();
        monsters.clear();
        animals.clear();
    }

    private Limits readLimits(World world) {
        ConfigurationSection rules = rules(world);
        if (rules == null || !rules.getBoolean("ENABLED", true)) {
            return DISABLED;
        }
        return new Limits(true,
                rules.getInt("MAX-ITEMS", 100),
                rules.getInt("MAX-EXPERIENCE", 100),
                rules.getInt("MAX-PROJECTILES", 100),
                rules.getInt("MAX-FALLING-BLOCKS", 25),
                rules.getInt("MAX-MONSTERS", 150),
                rules.getInt("MAX-ANIMALS", 80));
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
        entities.sort(OLDEST_FIRST);
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
