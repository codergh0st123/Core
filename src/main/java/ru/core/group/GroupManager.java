package ru.core.group;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.board.PlayerBoard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GroupManager {

    private static final int GROUP_ORDER_RANGE = 2_000_000;

    private final Core plugin;
    private final Map<UUID, TabState> states = new LinkedHashMap<>();
    private final Map<UUID, TagState> tagStates = new LinkedHashMap<>();
    private final Map<UUID, String> groups = new ConcurrentHashMap<>();
    private final Set<UUID> pendingGroups = ConcurrentHashMap.newKeySet();
    private Map<String, GroupFormat> formats = Map.of();
    private Map<String, Integer> orders = Map.of();
    private List<String> priorityGroups = List.of();
    private boolean alphabetical;
    private LuckPerms luckPerms;
    private EventSubscription<UserDataRecalculateEvent> userDataSubscription;

    public GroupManager(Core plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration configuration, List<String> sortingTypes) {
        closeUserDataSubscription();
        formats = loadFormats(configuration);
        orders = loadOrders(sortingTypes);
        priorityGroups = orders.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        alphabetical = sortingTypes.stream().anyMatch(this::isAlphabeticalPlayerSort);
        luckPerms = resolveLuckPerms();
        subscribeToUserDataChanges();
        states.clear();
        tagStates.clear();
        groups.clear();
        pendingGroups.clear();
    }

    public void preload(Player player) {
        LuckPerms api = luckPerms;
        if (api == null || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (groups.containsKey(uuid) || !pendingGroups.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> loadGroup(api, uuid));
    }

    public void preloadOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            preload(player);
        }
    }

    public void updateTab(Player player) {
        if (plugin.externalTab()) {
            return;
        }
        if (luckPerms == null) {
            reset(player);
            return;
        }
        preload(player);
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

    public void updateTags() {
        if (plugin.externalTab() || !tagEnabled()) {
            return;
        }
        if (luckPerms == null) {
            removeTags();
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            preload(target);
            updateTag(target);
        }
    }

    public void createTags(Player viewer) {
        if (plugin.externalTab() || !tagEnabled() || luckPerms == null) {
            return;
        }
        PlayerBoard board = plugin.boards().get(viewer);
        if (board == null) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            preload(target);
            TagState state = tagState(target);
            board.tag(target.getName(), state.group, state.prefix, state.suffix);
        }
    }

    public void remove(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        states.remove(uuid);
        tagStates.remove(uuid);
        groups.remove(uuid);
        pendingGroups.remove(uuid);
        if (!plugin.externalTab()) {
            for (PlayerBoard board : plugin.boards().all()) {
                board.removeTag(player.getName());
            }
        }
    }

    public void shutdown(Iterable<? extends Player> players) {
        closeUserDataSubscription();
        clear(players);
    }

    public void clear(Iterable<? extends Player> players) {
        if (!plugin.externalTab()) {
            for (Player player : players) {
                player.setPlayerListOrder(0);
                player.setPlayerListName(null);
            }
            removeTags();
        } else {
            tagStates.clear();
        }
        states.clear();
        groups.clear();
        pendingGroups.clear();
    }

    public String group(Player player) {
        if (player == null) {
            return "DEFAULT";
        }
        return groups.getOrDefault(player.getUniqueId(), "DEFAULT");
    }

    public String luckPermsPrefix(Player player) {
        LuckPerms api = luckPerms;
        if (api == null || player == null) {
            return "";
        }
        Group current = api.getGroupManager().getGroup(group(player).toLowerCase(Locale.ROOT));
        if (current != null) {
            String prefix = current.getNodes(NodeType.PREFIX).stream()
                    .max(Comparator.comparingInt(PrefixNode::getPriority))
                    .map(PrefixNode::getMetaValue)
                    .filter(value -> !value.isBlank())
                    .orElse("");
            if (!prefix.isEmpty()) {
                return prefix;
            }
        }
        String resolved = api.getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
        return resolved == null ? "" : resolved;
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

    private void loadGroup(LuckPerms api, UUID uuid) {
        String resolved = "DEFAULT";
        try {
            User user = api.getUserManager().loadUser(uuid).join();
            resolved = resolveGroup(user);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("LuckPerms: не удалось загрузить группу игрока " + uuid + ".");
        }
        String group = resolved;
        Bukkit.getScheduler().runTask(plugin, () -> applyLoadedGroup(uuid, group));
    }

    private void applyLoadedGroup(UUID uuid, String group) {
        pendingGroups.remove(uuid);
        if (!plugin.isEnabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (groups.containsKey(uuid)) {
            return;
        }
        groups.put(uuid, group);
        updateTab(player);
        refreshTag(player);
    }

    private void subscribeToUserDataChanges() {
        if (luckPerms == null) {
            return;
        }
        userDataSubscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            User user = event.getUser();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String group = resolveGroup(user);
                Bukkit.getScheduler().runTask(plugin, () -> applyRecalculatedGroup(uuid, group));
            });
        });
    }

    private void closeUserDataSubscription() {
        if (userDataSubscription != null) {
            userDataSubscription.close();
            userDataSubscription = null;
        }
    }

    private void applyRecalculatedGroup(UUID uuid, String group) {
        if (!plugin.isEnabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        groups.put(uuid, group);
        pendingGroups.remove(uuid);
        updateTab(player);
        refreshTag(player);
    }

    private String resolveGroup(User user) {
        if (user == null) {
            return "DEFAULT";
        }
        List<Group> inheritedGroups = new ArrayList<>(user.getInheritedGroups(QueryOptions.defaultContextualOptions()));
        if (plugin.externalTab()) {
            Group selected = inheritedGroups.stream()
                    .max(Comparator.comparingInt((Group group) -> group.getWeight().orElse(0))
                            .thenComparing(Group::getName, String.CASE_INSENSITIVE_ORDER))
                    .orElse(null);
            if (selected != null) {
                return selected.getName().toUpperCase(Locale.ROOT);
            }
        }
        Set<String> inherited = new HashSet<>();
        for (Group group : inheritedGroups) {
            inherited.add(group.getName().toUpperCase(Locale.ROOT));
        }
        return selectGroup(priorityGroups, inherited, user.getPrimaryGroup());
    }

    private void reset(Player player) {
        if (states.remove(player.getUniqueId()) != null) {
            player.setPlayerListOrder(0);
            player.setPlayerListName(null);
        }
    }

    private void refreshTag(Player target) {
        if (!tagEnabled() || luckPerms == null) {
            return;
        }
        tagStates.remove(target.getUniqueId());
        for (PlayerBoard board : plugin.boards().all()) {
            board.removeTag(target.getName());
        }
        updateTag(target);
    }

    private void updateTag(Player target) {
        if (!tagEnabled() || luckPerms == null) {
            return;
        }
        TagState state = tagState(target);
        if (state.equals(tagStates.get(target.getUniqueId()))) {
            return;
        }
        for (PlayerBoard board : plugin.boards().all()) {
            board.tag(target.getName(), state.group, state.prefix, state.suffix);
        }
        tagStates.put(target.getUniqueId(), state);
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
        List<String> configuredGroups = new ArrayList<>();
        for (String type : sortingTypes) {
            if (!type.regionMatches(true, 0, "GROUPS:", 0, "GROUPS:".length())) {
                continue;
            }
            String[] names = type.substring("GROUPS:".length()).split(",");
            for (String name : names) {
                String group = name.trim().toUpperCase(Locale.ROOT);
                if (!group.isEmpty() && !configuredGroups.contains(group)) {
                    configuredGroups.add(group);
                }
            }
        }
        Map<String, Integer> loaded = new LinkedHashMap<>();
        int priority = 1;
        for (int index = configuredGroups.size() - 1; index >= 0; index--) {
            loaded.put(configuredGroups.get(index), priority++);
        }
        return Map.copyOf(loaded);
    }

    private int playerOrder(String group, String name) {
        int priority = orders.getOrDefault(group, orders.getOrDefault("DEFAULT", 1));
        int groupOrder = Math.max(1, orders.size() - priority + 1);
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
