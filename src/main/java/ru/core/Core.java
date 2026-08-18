package ru.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.core.animation.AnimationManager;
import ru.core.board.BoardManager;
import ru.core.command.AlertCommand;
import ru.core.command.CoreCommand;
import ru.core.command.FindCommand;
import ru.core.command.HubCommand;
import ru.core.command.LangCommand;
import ru.core.command.LogCommand;
import ru.core.command.PlayCommand;
import ru.core.command.PlaytimeCommand;
import ru.core.command.PremiumChatCommand;
import ru.core.command.StaffChatCommand;
import ru.core.command.ServerCommand;
import ru.core.command.ListCommand;
import ru.core.command.TimeCommand;
import ru.core.config.Configs;
import ru.core.api.CoreDebug;
import ru.core.data.DataManager;
import ru.core.debug.DebugManager;
import ru.core.gui.InfoMenu;
import ru.core.group.GroupManager;
import ru.core.listener.PlayerListener;
import ru.core.module.AnnouncerModule;
import ru.core.module.BossBarModule;
import ru.core.module.EntityLimiterModule;
import ru.core.module.ItemCleanerModule;
import ru.core.module.NameTagModule;
import ru.core.module.ScoreboardModule;
import ru.core.module.ScreenTextModule;
import ru.core.module.TabModule;
import ru.core.net.Messenger;
import ru.core.net.ProxyConnector;
import ru.core.placeholder.CoreExpansion;
import ru.core.presence.PresenceManager;
import ru.core.resourcepack.ResourcePackManager;
import ru.core.reconnect.ReconnectManager;
import ru.core.placeholder.Placeholders;
import ru.core.packet.protocollib.ProtocolTrafficOptimizer;
import ru.core.packet.scoreboard.ScoreboardNumberPackets;
import ru.core.storage.MySqlStorage;
import ru.core.storage.SqLiteStorage;
import ru.core.storage.Storage;
import ru.core.version.ClientVersionManager;
import ru.core.wipe.WipeManager;

import java.util.Locale;

public final class Core extends JavaPlugin {

    private Configs configs;
    private AnimationManager animations;
    private GroupManager groups;
    private Placeholders placeholders;
    private Storage storage;
    private DataManager data;
    private DebugManager debug;
    private ClientVersionManager clientVersions;
    private Messenger messenger;
    private ProxyConnector proxyConnector;
    private WipeManager wipeManager;
    private PresenceManager presence;
    private ResourcePackManager resourcePacks;
    private ReconnectManager reconnects;
    private ScoreboardNumberPackets scoreboardPackets;
    private ProtocolTrafficOptimizer protocolOptimizer;
    private BoardManager boards;
    private ScoreboardModule scoreboards;
    private TabModule tab;
    private BossBarModule bossBars;
    private NameTagModule nameTags;
    private AnnouncerModule announcer;
    private ItemCleanerModule itemCleaner;
    private EntityLimiterModule entityLimiter;
    private ScreenTextModule screenText;
    private InfoMenu menu;

    @Override
    public void onEnable() {
        configs = new Configs(this);
        configs.load();
        applyAdvancementRules();
        animations = new AnimationManager(this);
        animations.reload(configs.animations());

        placeholders = new Placeholders(this);
        placeholders.rebuild();
        groups = new GroupManager(this);
        groups.reload(configs.groups(), configs.config().getStringList("TAB.SORTING_TYPES"));

        storage = createStorage();
        try {
            storage.connect();
        } catch (Exception exception) {
            getLogger().severe("Не удалось подключиться к базе данных: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        data = new DataManager(this);
        data.start();
        clientVersions = new ClientVersionManager(this);
        clientVersions.start();
        debug = new DebugManager(this);
        CoreDebug.register(debug);

        messenger = new Messenger(this);
        messenger.start();
        proxyConnector = new ProxyConnector(this);
        presence = new PresenceManager(this);
        presence.start();
        wipeManager = new WipeManager(this);
        wipeManager.start();
        resourcePacks = new ResourcePackManager(this);
        reconnects = new ReconnectManager(this);
        reconnects.start();

        scoreboardPackets = new ScoreboardNumberPackets(this);
        boards = new BoardManager(scoreboardPackets);
        menu = new InfoMenu(this);
        scoreboards = new ScoreboardModule(this);
        tab = new TabModule(this);
        bossBars = new BossBarModule(this);
        nameTags = new NameTagModule(this);
        announcer = new AnnouncerModule(this);
        itemCleaner = new ItemCleanerModule(this);
        entityLimiter = new EntityLimiterModule(this);
        screenText = new ScreenTextModule(this);
        startModules();
        startProtocolOptimization();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(menu, this);
        resourcePacks.start();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        CoreCommand core = new CoreCommand(this);
        register("core", core, core);
        LogCommand log = new LogCommand(this);
        register("log", log, log);
        PlayCommand play = new PlayCommand(this);
        register("play", play, play);
        register("hub", new HubCommand(this), null);
        FindCommand find = new FindCommand(this);
        register("find", find, find);
        register("alert", new AlertCommand(this), null);
        register("pc", new PremiumChatCommand(this), null);
        register("schat", new StaffChatCommand(this), null);
        register("server", new ServerCommand(this), null);
        register("list", new ListCommand(this), null);
        register("playtime", new PlaytimeCommand(this), null);
        LangCommand lang = new LangCommand(this);
        register("lang", lang, lang);
        TimeCommand time = new TimeCommand(this);
        register("time", time, time);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CoreExpansion(this).register();
            placeholders.hook(true);
            getLogger().info("PlaceholderAPI подключен.");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            data.join(player);
            boards.create(player);
        }
        groups.preloadOnline();
    }

    @Override
    public void onDisable() {
        if (reconnects != null && getServer().isStopping()) {
            reconnects.handoff();
        }
        stopModules();
        stopProtocolOptimization();
        if (debug != null) {
            CoreDebug.unregister(debug);
            debug.clear();
        }
        if (groups != null) {
            groups.shutdown(Bukkit.getOnlinePlayers());
        }
        if (boards != null) {
            boards.clear();
        }
        if (wipeManager != null) {
            wipeManager.shutdown();
        }
        if (resourcePacks != null) {
            resourcePacks.stop();
        }
        if (reconnects != null) {
            reconnects.shutdown();
        }
        if (presence != null) {
            presence.shutdown();
        }
        if (messenger != null) {
            messenger.shutdown();
        }
        if (data != null) {
            data.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public void reloadAll() {
        stopModules();
        stopProtocolOptimization();
        messenger.stop();
        if (wipeManager != null) {
            wipeManager.stop();
        }
        if (presence != null) {
            presence.stop();
        }
        configs.load();
        applyAdvancementRules();
        clientVersions.start();
        animations.reload(configs.animations());
        groups.reload(configs.groups(), configs.config().getStringList("TAB.SORTING_TYPES"));
        groups.preloadOnline();
        placeholders.rebuild();
        data.reload();
        messenger.start();
        if (wipeManager != null) {
            wipeManager.reload();
        }
        if (presence != null) {
            presence.reload();
        }
        if (resourcePacks != null) {
            resourcePacks.reload();
        }
        if (reconnects != null) {
            reconnects.reload();
        }
        startModules();
        startProtocolOptimization();
    }

    public void applyWorldRules(org.bukkit.World world) {
        if (configs.config().getBoolean("MESSAGES.HIDE.ADVANCEMENTS", false)) {
            setBooleanRule(world, "announceAdvancements", false);
        }
        setBooleanRule(world, "locatorBar", configs.config().getBoolean("GAME-RULES.LOCATOR-BAR", false));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setBooleanRule(org.bukkit.World world, String name, boolean value) {
        GameRule rule = GameRule.getByName(name);
        if (rule != null) {
            world.setGameRule(rule, value);
        }
    }

    private void applyAdvancementRules() {
        Bukkit.getWorlds().forEach(this::applyWorldRules);
    }

    private void startModules() {
        scoreboards.start();
        tab.start();
        bossBars.start();
        nameTags.start();
        announcer.start();
        itemCleaner.start();
        entityLimiter.start();
        screenText.start();
    }

    private void startProtocolOptimization() {
        Plugin protocolLib = getServer().getPluginManager().getPlugin("ProtocolLib");
        if (protocolLib == null || !protocolLib.isEnabled()) {
            return;
        }
        if (requiresModernProtocolLib() && !supportsModernProtocolLib(protocolLib.getDescription().getVersion())) {
            getLogger().warning("ProtocolLib: для Minecraft 26.2 используйте dev-сборку 5.5 или новее. Оптимизация пакетов отключена.");
            return;
        }
        try {
            protocolOptimizer = new ProtocolTrafficOptimizer(this);
            protocolOptimizer.start();
        } catch (LinkageError | RuntimeException exception) {
            protocolOptimizer = null;
            getLogger().warning("ProtocolLib: оптимизация пакетов отключена: " + exception.getMessage());
        }
    }

    private boolean requiresModernProtocolLib() {
        String[] parts = getServer().getMinecraftVersion().split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 26 || (major == 26 && minor >= 2);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean supportsModernProtocolLib(String version) {
        String[] parts = version.split("[.-]");
        if (parts.length < 2) {
            return false;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 5 || (major == 5 && minor >= 5);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void stopProtocolOptimization() {
        if (protocolOptimizer != null) {
            protocolOptimizer.stop();
            protocolOptimizer = null;
        }
    }

    private void stopModules() {
        if (scoreboards != null) {
            scoreboards.stop();
        }
        if (tab != null) {
            tab.stop();
        }
        if (bossBars != null) {
            bossBars.stop();
        }
        if (nameTags != null) {
            nameTags.stop();
        }
        if (announcer != null) {
            announcer.stop();
        }
        if (itemCleaner != null) {
            itemCleaner.stop();
        }
        if (entityLimiter != null) {
            entityLimiter.stop();
        }
        if (screenText != null) {
            screenText.stop();
        }
    }

    private Storage createStorage() {
        String type = configs.config().getString("DATABASE.TYPE", "SQLITE").toUpperCase(Locale.ROOT);
        if (type.equals("MYSQL")) {
            return new MySqlStorage(this);
        }
        return new SqLiteStorage(this);
    }

    private void register(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Команда " + name + " не найдена в plugin.yml");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }

    public Configs configs() {
        return configs;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    public AnimationManager animations() {
        return animations;
    }

    public GroupManager groups() {
        return groups;
    }

    public Storage storage() {
        return storage;
    }

    public DataManager data() {
        return data;
    }

    public DebugManager debug() {
        return debug;
    }

    public ClientVersionManager clientVersions() {
        return clientVersions;
    }

    public Messenger messenger() {
        return messenger;
    }

    public ProxyConnector proxyConnector() {
        return proxyConnector;
    }

    public WipeManager wipeManager() {
        return wipeManager;
    }

    public PresenceManager presence() {
        return presence;
    }

    public ResourcePackManager resourcePacks() {
        return resourcePacks;
    }

    public ReconnectManager reconnects() {
        return reconnects;
    }

    public BoardManager boards() {
        return boards;
    }

    public NameTagModule nameTags() {
        return nameTags;
    }

    public void removePacketState(Player player) {
        if (tab != null) {
            tab.remove(player);
        }
        if (protocolOptimizer != null) {
            protocolOptimizer.remove(player);
        }
        if (groups != null) {
            groups.remove(player);
        }
    }

    public BossBarModule bossBars() {
        return bossBars;
    }

    public InfoMenu menu() {
        return menu;
    }
}
