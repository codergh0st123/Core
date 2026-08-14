package ru.core.packet.scoreboard;

import org.bukkit.scoreboard.Objective;
import ru.core.Core;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Applies the number format before a sidebar objective is displayed to a player.
 * Paper exposes this through a public API. Spigot-compatible servers use the
 * same vanilla scoreboard packet, but do not expose its number format in Bukkit.
 */
public final class ScoreboardNumberPackets {

    private final Core plugin;
    private boolean paperResolved;
    private boolean paperAvailable;
    private boolean nmsResolved;
    private boolean nmsAvailable;
    private boolean warningLogged;
    private Method paperNumberFormat;
    private Method paperBlank;
    private Method objectiveHandle;
    private Method nmsNumberFormat;
    private Field nmsBlank;

    public ScoreboardNumberPackets(Core plugin) {
        this.plugin = plugin;
    }

    public boolean apply(Objective objective, boolean hidden) {
        if (objective == null) {
            return false;
        }
        if (applyPaper(objective, hidden)) {
            return true;
        }
        return applyNms(objective, hidden);
    }

    private boolean applyPaper(Objective objective, boolean hidden) {
        if (!resolvePaper(objective)) {
            return false;
        }
        try {
            Object format = hidden ? paperBlank.invoke(null) : null;
            paperNumberFormat.invoke(objective, format);
            return true;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            paperAvailable = false;
            return false;
        }
    }

    private boolean applyNms(Objective objective, boolean hidden) {
        if (!resolveNms(objective)) {
            warn("Скрытие чисел scoreboard недоступно на этом ядре сервера.");
            return false;
        }
        try {
            Object handle = objectiveHandle.invoke(objective);
            Object format = hidden ? nmsBlank.get(null) : null;
            nmsNumberFormat.invoke(handle, format);
            return true;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            nmsAvailable = false;
            warn("Не удалось применить NMS-формат чисел scoreboard: " + cause(exception));
            return false;
        }
    }

    private boolean resolvePaper(Objective objective) {
        if (paperResolved) {
            return paperAvailable;
        }
        paperResolved = true;
        try {
            Class<?> type = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            paperNumberFormat = objective.getClass().getMethod("numberFormat", type);
            paperBlank = type.getMethod("blank");
            paperAvailable = true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            paperAvailable = false;
        }
        return paperAvailable;
    }

    private boolean resolveNms(Objective objective) {
        if (nmsResolved) {
            return nmsAvailable;
        }
        nmsResolved = true;
        try {
            objectiveHandle = objective.getClass().getDeclaredMethod("getHandle");
            objectiveHandle.trySetAccessible();
            Object handle = objectiveHandle.invoke(objective);
            Class<?> formatType = Class.forName("net.minecraft.network.chat.numbers.NumberFormat");
            Class<?> blankType = Class.forName("net.minecraft.network.chat.numbers.BlankFormat");
            nmsNumberFormat = handle.getClass().getMethod("setNumberFormat", formatType);
            nmsBlank = blankType.getField("INSTANCE");
            nmsAvailable = true;
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException
                 | IllegalAccessException | InvocationTargetException exception) {
            nmsAvailable = false;
        }
        return nmsAvailable;
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
