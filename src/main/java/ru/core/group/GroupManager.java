package ru.core.group;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.core.Core;
import ru.core.board.PlayerBoard;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GroupManager {

    private final Core plugin;
    private final Map<UUID, TabState> states = new LinkedHashMap<>();
    private final Map<UUID, TagState> tagStates = new LinkedHashMap<>();
    private Map<String, GroupFormat> formats = Map.of();
    private static final int GROUP_ORDER_RANGE = 2_000_000;

    private Map<String, Integer> orders = Map.of();
    private List<String> priorityGroups = List.of();
    private boolean alphabetical;
    private LuckPerms luckPerms;

    public GroupManager(Core plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration configuration, List<String> sortingTypes) {
        formats = loadFormats(configuration);
        orders = loadOrders(sortingTypes);
        priorityGroups = orders.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
        alphabetical = sortingTypes.stream().anyMatch(this::isAlphabeticalPlayerSort);
        luckPerms = resolveLuckPerms();
        states.clear();
        tagStates.clear();
    }

    public void updateTab(Player player) {
        if (luckPerms == null) {
            reset(player);
            return;
        }
        String group = group(player);
        GroupFormat format = formats.getOrDefault(group, formats.getOrDefault("DEFAULT", GroupFormat.EMPTY));
        int order = playerOrder(group, player.getName());
        String name = plugin.placeholders().apply(player, format.tabPrefix()) + player.getName()
                + plugin.placeholders().apply(player, format.tabSuffix());
        TabState state = new TabState(order, name);
        if (state.equals(states.get(player.getUniqueId()))) {
            return;
        }
        player.setPlayerListOrder(order);
        player.setPlayerListName(name.isEmpty() ? null : name);
        states.put(player.getUniqueId(), state);
    }

    private void reset(Player player) {
        if (states.remove(player.getUniqueId()) != null) {
            player.setPlayerListOrder(0);
            player.setPlayerListName(null);
        }
    }

    public void updateTags() {
        if (!tagEnabled()) {
            return;
        }
        if (luckPerms == null) {
            removeTags();
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            TagState state = tagState(target);
            if (state.equals(tagStates.get(target.getUniqueId()))) {
                continue;
            }
            applyTag(target, state);
            tagStates.put(target.getUniqueId(), state);
        }
    }

    public void createTags(Player viewer) {
        if (!tagEnabled() || luckPerms == null) {
            return;
        }
        PlayerBoard board = plugin.boards().get(viewer);
        if (board == null) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            TagState state = tagState(target);
            board.tag(target.getName(), state.group, state.prefix, state.suffix);
        }
    }

    public void remove(Player player) {
        if (player == null) {
            return;
        }
        states.remove(player.getUniqueId());
        tagStates.remove(player.getUniqueId());
        for (PlayerBoard board : plugin.boards().all()) {
            board.removeTag(player.getName());
        }
    }

    public void clear(Iterable<? extends Player> players) {
        for (Player player : players) {
            player.setPlayerListOrder(0);
            player.setPlayerListName(null);
        }
        states.clear();
        removeTags();
    }

    private void applyTag(Player target, TagState state) {
        for (PlayerBoard board : plugin.boards().all()) {
            board.tag(target.getName(), state.group, state.prefix, state.suffix);
        }
    }

    private void removeTags() {
        for (PlayerBoard board : plugin.boards().all()) {
            board.removeTags();
        }
        tagStates.clear();
    }

    private boolean tagEnabled() {
        return plugin.configs().config().getBoolean("NAMETAG.GROUPS.ENABLED", true);
    }

    private TagState tagState(Player player) {
        String group = group(player);
        GroupFormat format = formats.getOrDefault(group, formats.getOrDefault("DEFAULT", GroupFormat.EMPTY));
        return new TagState(
                group,
                plugin.placeholders().apply(player, format.tagPrefix()),
                plugin.placeholders().apply(player, format.tagSuffix())
        );
    }

    public String group(Player player) {
        if (luckPerms == null || player == null) {
            return "DEFAULT";
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return "DEFAULT";
        }
        Set<String> inherited = new HashSet<>();
        for (Group group : user.getInheritedGroups(QueryOptions.defaultContextualOptions())) {
            inherited.add(group.getName().toUpperCase(Locale.ROOT));
        }
        return selectGroup(priorityGroups, inherited, user.getPrimaryGroup());
    }

    static String selectGroup(List<String> priorityGroups, Set<String> inherited, String primary) {
        for (String group : priorityGroups) {
            if (inherited.contains(group)) {
                return group;
            }
        }
        if (primary == null || primary.isBlank()) {
            return "DEFAULT";
        }
        return primary.toUpperCase(Locale.ROOT);
    }

    private Map<String, GroupFormat> loadFormats(FileConfiguration configuration) {
        Map<String, GroupFormat> loaded = new LinkedHashMap<>();
        for (String name : configuration.getKeys(false)) {
            ConfigurationSection section = configuration.getConfigurationSection(name);
            if (section == null) {
                plugin.getLogger().warning("Группа " + name + " должна быть секцией в groups.yml.");
                continue;
            }
            loaded.put(name.toUpperCase(Locale.ROOT), new GroupFormat(
                    section.getString("TAB-PREFIX", ""),
                    section.getString("TAB-SUFFIX", ""),
                    section.getString("TAG-PREFIX", ""),
                    section.getString("TAG-SUFFIX", "")
            ));
        }
        return Map.copyOf(loaded);
    }

    private Map<String, Integer> loadOrders(List<String> sortingTypes) {
        Map<String, Integer> loaded = new LinkedHashMap<>();
        for (String type : sortingTypes) {
            if (!type.regionMatches(true, 0, "GROUPS:", 0, "GROUPS:".length())) {
                continue;
            }
            String[] names = type.substring("GROUPS:".length()).split(",");
            int order = 1;
            for (String name : names) {
                String group = name.trim();
                if (group.isEmpty()) {
                    continue;
                }
                loaded.putIfAbsent(group.toUpperCase(Locale.ROOT), order++);
            }
        }
        return Map.copyOf(loaded);
    }

    private int playerOrder(String group, String name) {
        int groupOrder = orders.getOrDefault(group, orders.getOrDefault("DEFAULT", orders.size() + 1));
        if (!alphabetical) {
            return groupOrder;
        }
        return groupOrder * GROUP_ORDER_RANGE + alphabeticValue(name);
    }

    private int alphabeticValue(String name) {
        int value = 0;
        for (int index = 0; index < 4; index++) {
            int character = index < name.length() ? alphabeticCharacter(name.charAt(index)) : 0;
            value = value * 37 + character;
        }
        return value;
    }

    private int alphabeticCharacter(char character) {
        char lower = Character.toLowerCase(character);
        if (lower >= 'a' && lower <= 'z') {
            return lower - 'a' + 1;
        }
        if (lower >= '0' && lower <= '9') {
            return lower - '0' + 27;
        }
        return 0;
    }

    private boolean isAlphabeticalPlayerSort(String type) {
        return type.replace(" ", "").equalsIgnoreCase("PLACEHOLDER_A_TO_Z:%player%");
    }

    private LuckPerms resolveLuckPerms() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            return null;
        }
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning("LuckPerms: не удалось получить API для сортировки tab-list.");
            return null;
        }
    }

    private static final class TagState {

        private final String group;
        private final String prefix;
        private final String suffix;

        private TagState(String group, String prefix, String suffix) {
            this.group = group;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TagState)) {
                return false;
            }
            TagState other = (TagState) object;
            return group.equals(other.group) && prefix.equals(other.prefix) && suffix.equals(other.suffix);
        }

        @Override
        public int hashCode() {
            int result = group.hashCode();
            result = 31 * result + prefix.hashCode();
            return 31 * result + suffix.hashCode();
        }
    }

    private static final class TabState {

        private final int order;
        private final String name;

        private TabState(int order, String name) {
            this.order = order;
            this.name = name;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TabState)) {
                return false;
            }
            TabState other = (TabState) object;
            return order == other.order && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return 31 * order + name.hashCode();
        }
    }
}
