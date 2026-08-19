package ru.core.gui;

import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
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
import ru.core.storage.Profile;
import ru.core.text.Msg;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LanguageMenu implements Listener {

    private final Core plugin;

    public LanguageMenu(Core plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ConfigurationSection menu = plugin.configs().config().getConfigurationSection("LANGUAGE-MENU");
        if (menu == null) {
            return;
        }
        int size = size(menu.getInt("SIZE", 44));
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, size, parse(player, menu.getString("TITLE", "&fЛокализация проекта"), "", false));
        holder.inventory = inventory;

        ConfigurationSection languages = menu.getConfigurationSection("LANGUAGES");
        List<Integer> languageSlots = slots(menu.getIntegerList("LANGUAGE-SLOTS"), size);
        Set<Integer> occupied = new HashSet<>();
        int slotIndex = 0;

        for (String code : plugin.configs().languages()) {
            ConfigurationSection section = languages == null ? null : languages.getConfigurationSection(code);
            if (section == null && languages != null) {
                section = languages.getConfigurationSection("DEFAULT");
            }
            int slot = slot(section, languageSlots, occupied, size, slotIndex);
            if (slot < 0) {
                break;
            }
            slotIndex++;
            occupied.add(slot);
            boolean selected = code.equalsIgnoreCase(plugin.placeholders().language(player));
            inventory.setItem(slot, item(player, section, code, selected));
            holder.languages.put(slot, code);
        }

        fill(player, inventory, menu.getConfigurationSection("FILL"));
        player.openInventory(inventory);
    }

    private int slot(ConfigurationSection section, List<Integer> languageSlots, Set<Integer> occupied, int size, int slotIndex) {
        if (section != null && section.isSet("SLOT")) {
            int configured = section.getInt("SLOT", -1);
            if (configured >= 0 && configured < size && !occupied.contains(configured)) {
                return configured;
            }
        }
        for (int index = slotIndex; index < languageSlots.size(); index++) {
            int configured = languageSlots.get(index);
            if (!occupied.contains(configured)) {
                return configured;
            }
        }
        for (int configured : languageSlots) {
            if (!occupied.contains(configured)) {
                return configured;
            }
        }
        for (int configured = 0; configured < size; configured++) {
            if (!occupied.contains(configured)) {
                return configured;
            }
        }
        return -1;
    }

    private List<Integer> slots(List<Integer> raw, int size) {
        List<Integer> result = new ArrayList<>();
        for (int slot : raw) {
            if (slot >= 0 && slot < size && !result.contains(slot)) {
                result.add(slot);
            }
        }
        if (result.isEmpty()) {
            result.add(10);
            result.add(12);
            result.add(14);
            result.add(16);
        }
        return result;
    }

    private void fill(Player player, Inventory inventory, ConfigurationSection section) {
        if (section == null || !section.getBoolean("ENABLED", false)) {
            return;
        }
        ItemStack item = item(player, section, "", false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, item.clone());
            }
        }
    }

    private ItemStack item(Player player, ConfigurationSection section, String language, boolean selected) {
        String materialName = value(section, "MATERIAL", value(section, "ITEM", "PLAYER_HEAD"));
        String texture = texture(materialName);
        int separator = materialName.indexOf(':');
        String materialKey = separator < 0 ? materialName : materialName.substring(0, separator);
        Material material = Material.matchMaterial(materialKey.toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            material = Material.PLAYER_HEAD;
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(99, integer(section, "AMOUNT", 1))));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = value(section, "NAME", "&f%LANG%%SELECTED%");
        if (selected) {
            String selectedName = value(section, "SELECTED-NAME", "");
            if (!selectedName.isEmpty()) {
                name = selectedName;
            }
        }
        if (!name.isEmpty()) {
            meta.setDisplayName(parse(player, name, language, selected));
        }

        List<String> rawLore = selected && section != null && section.isList("SELECTED-LORE")
                ? section.getStringList("SELECTED-LORE")
                : section == null ? List.of() : section.getStringList("LORE");
        if (!rawLore.isEmpty()) {
            List<String> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                lore.add(parse(player, line, language, selected));
            }
            meta.setLore(lore);
        }

        String model = value(section, "ITEM-MODEL", "");
        if (!model.isEmpty()) {
            NamespacedKey key = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        int modelData = integer(section, "MODEL-DATA", 0);
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        boolean glow = selected ? bool(section, "SELECTED-GLOW", true) : bool(section, "GLOW", false);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (bool(section, "HIDE-TOOLTIP", false)) {
            meta.setHideTooltip(true);
        }
        if (bool(section, "HIDE-FLAGS", false)) {
            meta.addItemFlags(ItemFlag.values());
        }

        if (!texture.isEmpty() && meta instanceof SkullMeta) {
            texture((SkullMeta) meta, texture);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String texture(String material) {
        int separator = material.indexOf(':');
        if (separator < 0 || !material.substring(0, separator).equalsIgnoreCase("PLAYER_HEAD")) {
            return "";
        }
        return material.substring(separator + 1).trim();
    }

    private void texture(SkullMeta meta, String texture) {
        try {
            Base64.getDecoder().decode(texture);
        } catch (IllegalArgumentException exception) {
            return;
        }
        org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(
                UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
        if (!(profile instanceof com.destroystokyo.paper.profile.PlayerProfile)) {
            return;
        }
        com.destroystokyo.paper.profile.PlayerProfile paperProfile =
                (com.destroystokyo.paper.profile.PlayerProfile) profile;
        paperProfile.setProperty(new ProfileProperty("textures", texture));
        meta.setPlayerProfile(paperProfile);
    }

    private void select(Player player, String language) {
        Profile profile = plugin.data().profile(player);
        if (profile == null) {
            Msg.send(plugin, player, "PROFILE-LOADING");
            return;
        }
        profile.language(language);
        plugin.data().async(() -> plugin.storage().save(profile));
        player.closeInventory();
        Msg.send(plugin, player, "LANGUAGE-CHANGED-GUI", "%lang%", language);
    }

    private String value(ConfigurationSection section, String path, String fallback) {
        return section == null ? fallback : section.getString(path, fallback);
    }

    private int integer(ConfigurationSection section, String path, int fallback) {
        return section == null ? fallback : section.getInt(path, fallback);
    }

    private boolean bool(ConfigurationSection section, String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }

    private String parse(Player player, String text, String language, boolean selected) {
        if (text == null) {
            return "";
        }
        String marker = selected
                ? plugin.configs().config().getString("LANGUAGE-MENU.SELECTED-PLACEHOLDER", "")
                : "";
        return plugin.placeholders().apply(player, text.replace("%LANG%", language)
                .replace("%LANGUAGE%", language)
                .replace("%SELECTED%", marker));
    }

    private int size(int raw) {
        int size = Math.max(9, Math.min(54, raw));
        int remainder = size % 9;
        if (remainder != 0) {
            size += 9 - remainder;
        }
        return Math.min(54, size);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Holder holder = (Holder) event.getView().getTopInventory().getHolder();
        String language = holder.languages.get(event.getRawSlot());
        if (language != null) {
            select((Player) event.getWhoClicked(), language);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {

        private final Map<Integer, String> languages = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
