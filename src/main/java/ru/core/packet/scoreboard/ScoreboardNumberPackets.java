package ru.core.packet.scoreboard;

import org.bukkit.scoreboard.Objective;
import ru.core.Core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Applies scoreboard number formats through the server packet implementation.
 *
 * <p>Paper exposes the client number-format packet through a stable API. Spigot
 * does not expose this API, so this bridge keeps the plugin loadable on Spigot
 * and degrades to the vanilla number display instead of using version-specific
 * NMS classes.</p>
 */
public final class ScoreboardNumberPackets {

    private final Core plugin;
    private boolean detected;
    private boolean available;
    private boolean warningLogged;
    private Method numberFormat;
    private Method blank;

    public ScoreboardNumberPackets(Core plugin) {
        this.plugin = plugin;
    }

    public void apply(Objective objective, boolean hidden) {
        if (objective == null || !resolve(objective)) {
            return;
        }
        try {
            Object format = hidden ? blank.invoke(null) : null;
            numberFormat.invoke(objective, format);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            available = false;
            warn("Не удалось применить формат чисел scoreboard: " + cause(exception));
        }
    }

    public boolean available() {
        return available;
    }

    private boolean resolve(Objective objective) {
        if (detected) {
            return available;
        }
        detected = true;
        try {
            Class<?> type = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            numberFormat = objective.getClass().getMethod("numberFormat", type);
            blank = type.getMethod("blank");
            available = true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            available = false;
            warn("Скрытие чисел scoreboard требует Paper 1.20.3+; на Spigot числа остаются видимыми.");
        }
        return available;
    }

    private void warn(String message) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        plugin.getLogger().warning(message);
    }

    private String cause(Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException
                ? ((InvocationTargetException) exception).getTargetException()
                : exception;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
