package ru.core;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.core.board.BoardManager;
import ru.core.command.Aliases;
import ru.core.command.AlertCommand;
import ru.core.command.CoreCommand;
import ru.core.command.LangCommand;
import ru.core.command.PlayCommand;
import ru.core.command.PlaytimeCommand;
import ru.core.command.PremiumChatCommand;
import ru.core.command.StaffChatCommand;
import ru.core.command.TimeCommand;
import ru.core.config.Configs;
import ru.core.data.DataManager;
import ru.core.gui.InfoMenu;
import ru.core.listener.PlayerListener;
import ru.core.module.AnnouncerModule;
import ru.core.module.BossBarModule;
import ru.core.module.NameTagModule;
import ru.core.module.ScoreboardModule;
import ru.core.module.ScreenTextModule;
import ru.core.module.TabModule;
import ru.core.net.Messenger;
import ru.core.placeholder.CoreExpansion;
import ru.core.placeholder.Placeholders;
import ru.core.storage.MySqlStorage;
import ru.core.storage.SqLiteStorage;
import ru.core.storage.Storage;

import java.util.Locale;

public final class Core extends JavaPlugin {

    private static final String[] MENU_ALIASES = {"ver", "version"};

    private Configs configs;
    private Placeholders placeholders;
    private Storage storage;
    private DataManager data;
    private Messenger messenger;
    private BoardManager boards;
    private ScoreboardModule scoreboards;
    private TabModule tab;
    private BossBarModule bossBars;
    private NameTagModule nameTags;
    private AnnouncerModule announcer;
    private ScreenTextModule screenText;
    private InfoMenu menu;

    @Override
    public void onEnable() {
        configs = new Configs(this);
        configs.load();

        placeholders = new Placeholders(this);
        placeholders.rebuild();

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

        messenger = new Messenger(this);
        messenger.start();

        boards = new BoardManager();
        menu = new InfoMenu(this);
        scoreboards = new ScoreboardModule(this);
        tab = new TabModule(this);
        bossBars = new BossBarModule(this);
        nameTags = new NameTagModule(this);
        announcer = new AnnouncerModule(this);
        screenText = new ScreenTextModule(this);
        startModules();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        CoreCommand core = new CoreCommand(this);
        register("core", core, core);
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

        PluginCommand command = getCommand("core");
        if (command != null) {
            Aliases.force(this, command, MENU_ALIASES);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CoreExpansion(this).register();
            placeholders.hook(true);
            getLogger().info("PlaceholderAPI подключен.");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            data.join(player);
            boards.create(player);
        }
    }

    @Override
    public void onDisable() {
        PluginCommand command = getCommand("core");
        if (command != null) {
            Aliases.release(this, command, MENU_ALIASES);
        }
        stopModules();
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
        configs.load();
        placeholders.rebuild();
        startModules();
    }

    private void startModules() {
        scoreboards.start();
        tab.start();
        bossBars.start();
        nameTags.start();
        announcer.start();
        screenText.start();
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

    public Storage storage() {
        return storage;
    }

    public DataManager data() {
        return data;
    }

    public Messenger messenger() {
        return messenger;
    }

    public BoardManager boards() {
        return boards;
    }

    public BossBarModule bossBars() {
        return bossBars;
    }

    public InfoMenu menu() {
        return menu;
    }
}
