package ru.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.core.Core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InfoMenu implements Listener {

    private final Core plugin;

    public InfoMenu(Core plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ConfigurationSection menu = plugin.configs().config().getConfigurationSection("MENU");
        if (menu == null) {
            menu = new MemoryConfiguration();
        }
        int size = size(menu.getInt("SIZE", 27));
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, size,
                parse(player, menu.getString("TITLE", "&8Данные сервера")));
        holder.inventory = inventory;
        ConfigurationSection items = menu.getConfigurationSection("ITEMS");
        if (items == null) {
            place(player, inventory, holder, menu);
        } else {
            for (String key : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(key);
                if (section != null) {
                    place(player, inventory, holder, section);
                }
            }
        }
        fill(player, inventory, menu.getConfigurationSection("FILL"));
        player.openInventory(inventory);
    }

    private void place(Player player, Inventory inventory, Holder holder, ConfigurationSection section) {
        String permission = section.getString("PERMISSION", "");
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            return;
        }
        ItemStack item = item(player, section);
        List<String> actions = section.getStringList("ACTIONS");
        for (int slot : slots(section, inventory.getSize())) {
            inventory.setItem(slot, item.clone());
            if (!actions.isEmpty()) {
                holder.actions.put(slot, actions);
            }
        }
    }

    private void fill(Player player, Inventory inventory, ConfigurationSection section) {
        if (section == null || !section.getBoolean("ENABLED", true)) {
            return;
        }
        ItemStack item = item(player, section);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, item.clone());
            }
        }
    }

    private ItemStack item(Player player, ConfigurationSection section) {
        Material material = Material.matchMaterial(
                section.getString("MATERIAL", section.getString("ITEM", "BARRIER")).toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(99, section.getInt("AMOUNT", 1))));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = section.getString("NAME", "");
        if (!name.isEmpty()) {
            meta.setDisplayName(parse(player, name));
        }
        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("LORE")) {
            lore.add(parse(player, line));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        String model = section.getString("ITEM-MODEL", "");
        if (!model.isEmpty()) {
            NamespacedKey key = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        int modelData = section.getInt("MODEL-DATA", 0);
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        if (section.getBoolean("GLOW", false)) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (section.getBoolean("HIDE-TOOLTIP", false)) {
            meta.setHideTooltip(true);
        }
        if (section.getBoolean("HIDE-FLAGS", false)) {
            meta.addItemFlags(ItemFlag.values());
        }
        String owner = section.getString("HEAD-OWNER", "");
        if (!owner.isEmpty() && meta instanceof SkullMeta) {
            String target = parse(player, owner);
            if (target.equalsIgnoreCase(player.getName())) {
                ((SkullMeta) meta).setOwningPlayer(player);
            } else {
                ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(target));
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Integer> slots(ConfigurationSection section, int size) {
        List<Integer> slots = new ArrayList<>();
        List<String> raw = new ArrayList<>(section.getStringList("SLOTS"));
        if (section.isSet("SLOT")) {
            raw.add(section.getString("SLOT", String.valueOf(size / 2)));
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            int dash = value.indexOf('-');
            if (dash > 0) {
                Integer from = integer(value.substring(0, dash));
                Integer to = integer(value.substring(dash + 1));
                if (from == null || to == null) {
                    continue;
                }
                for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                    add(slots, slot, size);
                }
                continue;
            }
            Integer single = integer(value);
            if (single != null) {
                add(slots, single, size);
            }
        }
        if (slots.isEmpty()) {
            add(slots, size / 2, size);
        }
        return slots;
    }

    private void add(List<Integer> slots, int slot, int size) {
        if (slot >= 0 && slot < size && !slots.contains(slot)) {
            slots.add(slot);
        }
    }

    private void run(Player player, List<String> actions) {
        for (String action : actions) {
            if (action == null || action.trim().isEmpty()) {
                continue;
            }
            String value = action.trim();
            String type = "MESSAGE";
            String argument = value;
            if (value.startsWith("[")) {
                int end = value.indexOf(']');
                if (end > 0) {
                    type = value.substring(1, end).trim().toUpperCase(Locale.ROOT);
                    argument = value.substring(end + 1).trim();
                }
            }
            switch (type) {
                case "MESSAGE":
                    player.sendMessage(parse(player, argument));
                    break;
                case "COMMAND":
                case "PLAYER":
                    player.performCommand(parse(player, argument));
                    break;
                case "CONSOLE":
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parse(player, argument));
                    break;
                case "SOUND":
                    sound(player, argument);
                    break;
                case "CLOSE":
                    Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                    break;
                default:
                    break;
            }
        }
    }

    private void sound(Player player, String argument) {
        String[] parts = argument.split(";");
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            return;
        }
        String key = parts[0].trim().toLowerCase(Locale.ROOT);
        if (key.indexOf('.') < 0) {
            key = key.replace('_', '.');
        }
        float volume = parts.length > 1 ? decimal(parts[1], 1.0F) : 1.0F;
        float pitch = parts.length > 2 ? decimal(parts[2], 1.0F) : 1.0F;
        player.playSound(player.getLocation(), key, volume, pitch);
    }

    private String parse(Player player, String text) {
        if (text == null) {
            return "";
        }
        String version = plugin.configs().config().getString("MENU.VERSION", plugin.getDescription().getVersion());
        String replaced = text.replace("%VERSION%", version)
                .replace("%LANGS%", String.valueOf(plugin.placeholders().languageCount()))
                .replace("%LANG-LIST%", plugin.placeholders().languageList());
        return plugin.placeholders().apply(player, replaced);
    }

    private int size(int raw) {
        int size = raw - raw % 9;
        return Math.max(9, Math.min(54, size));
    }

    private Integer integer(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private float decimal(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        List<String> actions = ((Holder) event.getInventory().getHolder()).actions.get(event.getSlot());
        if (actions == null) {
            return;
        }
        run((Player) event.getWhoClicked(), actions);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {

        private final Map<Integer, List<String>> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}