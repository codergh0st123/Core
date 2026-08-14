package ru.core.performance;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Distributes player updates across scheduler runs while preserving a stable
 * round-robin order. The caller remains on the Bukkit main thread.
 */
public final class PlayerBatch {

    private int cursor;

    public List<Player> next(Collection<? extends Player> source, int configuredLimit) {
        int size = source.size();
        if (size == 0) {
            cursor = 0;
            return Collections.emptyList();
        }
        int limit = Math.max(1, Math.min(configuredLimit, size));
        if (cursor >= size) {
            cursor = 0;
        }
        List<Player> result = new ArrayList<>(limit);
        append(source, result, cursor, limit);
        if (result.size() < limit) {
            append(source, result, 0, limit);
        }
        cursor = (cursor + limit) % size;
        return result;
    }

    public void reset() {
        cursor = 0;
    }

    private void append(Collection<? extends Player> source, List<Player> target, int skip, int limit) {
        int index = 0;
        for (Player player : source) {
            if (index++ < skip) {
                continue;
            }
            target.add(player);
            if (target.size() == limit) {
                return;
            }
        }
    }
}
