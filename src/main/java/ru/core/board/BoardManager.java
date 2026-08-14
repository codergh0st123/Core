package ru.core.board;

import org.bukkit.entity.Player;
import ru.core.packet.scoreboard.ScoreboardNumberPackets;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BoardManager {

    private final Map<UUID, PlayerBoard> boards = new HashMap<>();
    private final ScoreboardNumberPackets numberPackets;

    public BoardManager(ScoreboardNumberPackets numberPackets) {
        this.numberPackets = numberPackets;
    }

    public PlayerBoard create(Player player) {
        remove(player);
        PlayerBoard board = new PlayerBoard(player, numberPackets);
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board.scoreboard());
        return board;
    }

    public PlayerBoard get(Player player) {
        return boards.get(player.getUniqueId());
    }

    public void remove(Player player) {
        PlayerBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.destroy();
        }
    }

    public void forget(String name) {
        for (PlayerBoard board : boards.values()) {
            board.forget(name);
        }
    }

    public Collection<PlayerBoard> all() {
        return boards.values();
    }

    public void clear() {
        for (PlayerBoard board : boards.values()) {
            board.destroy();
        }
        boards.clear();
    }
}
