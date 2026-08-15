package ru.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.core.animation.AnimationManager;
import ru.core.board.BoardManager;
import ru.core.command.AlertCommand;
import ru.core.command.CoreCommand;
import ru.core.command.LangCommand;
import ru.core.command.LogCommand;
import ru.core.command.PlayCommand;
import ru.core.command.PlaytimeCommand;
import ru.core.command.PremiumChatCommand;
import ru.core.command.StaffChatCommand;
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
import ru.core.module.ItemCleanerModule;
import ru.core.module.NameTagModule;
import ru.core.module.ScoreboardModule;
import ru.core.module.ScreenTextModule;
import ru.core.module.TabModule;
import ru.core.net.Messenger;
import ru.core.placeholder.CoreExpansion;
import ru.core.placeholder.Placeholders;
import ru.core.packet.protocollib.ProtocolTrafficOptimizer;
import ru.core.packet.scoreboard.ScoreboardNumberPackets;
import ru.core.storage.MySqlStorage;
import ru.core.storage.SqLiteStorage;
import ru.core.storage.Storage;

import java.util.Locale;

public final class Core extends JavaPlugin {

    private Configs configs;
    private AnimationManager animations;
    private GroupManager groups;
    private Placeholders placeholders;
    private Storage storage;
    private DataManager data;
    private DebugManager debug;
    private Messenger messenger;
    private ScoreboardNumberPackets scoreboardPackets;
    private ProtocolTrafficOptimizer protocolOptimizer;
    private BoardManager boards;
    private ScoreboardModule scoreboards;
    private TabModule tab;
    private BossBarModule bossBars;
    private NameTagModule nameTags;
    private AnnouncerModule announcer;
    private ItemCleanerModule itemCleaner;
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
        debug = new DebugManager(this);
        CoreDebug.register(debug);

        messenger = new Messenger(this);
        messenger.start();

        scoreboardPackets = new ScoreboardNumberPackets(this);
        boards = new BoardManager(scoreboardPackets);
        menu = new InfoMenu(this);
        scoreboards = new ScoreboardModule(this);
        tab = new TabModule(this);
        bossBars = new BossBarModule(this);
        nameTags = new NameTagModule(this);
        announcer = new AnnouncerModule(this);
        itemCleaner = new ItemCleanerModule(this);
        screenText = new ScreenTextModule(this);
        startModules();
        startProtocolOptimization();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        CoreCommand core = new CoreCommand(this);
        register("core", core, core);
        LogCommand log = new LogCommand(this);
        register("log", log, log);
        PlayCommand play = new PlayCommand(this);
        register("play", play, play);
        register("alert", new AlertCommand(this), null);
        register("pc", new PremiumChatCommand(this), null);
        register("schat", new StaffChatCommand(this), null);
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
        stopModules();
        stopProtocolOptimization();
        if (debug != null) {
            CoreDebug.unregister(debug);
            debug.clear();
        }
        if (groups != null) {
            groups.clear(Bukkit.getOnlinePlayers());
        }
        if (boards != null) {
            boards.clear();
        }
        if (messenger != null) {
            messenger.stop();
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
        configs.load();
        applyAdvancementRules();
        animations.reload(configs.animations());
        groups.reload(configs.groups(), configs.config().getStringList("TAB.SORTING_TYPES"));
        groups.preloadOnline();
        placeholders.rebuild();
        data.reload();
        messenger.start();
        startModules();
        startProtocolOptimization();
    }

    private void applyAdvancementRules() {
        if (configs.config().getBoolean("MESSAGES.HIDE.ADVANCEMENTS", false)) {
            Bukkit.getWorlds().forEach(world -> world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false));
        }
        boolean locatorBar = configs.config().getBoolean("GAME-RULES.LOCATOR-BAR", false);
        Bukkit.getWorlds().forEach(world -> world.setGameRule(GameRule.LOCATOR_BAR, locatorBar));
    }

    private void startModules() {
        scoreboards.start();
        tab.start();
        bossBars.start();
        nameTags.start();
        announcer.start();
        itemCleaner.start();
        screenText.start();
    }

    private void startProtocolOptimization() {
        if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
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

    public Messenger messenger() {
        return messenger;
    }

    public BoardManager boards() {
        return boards;
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
