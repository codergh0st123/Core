package ru.core.board;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import ru.core.packet.scoreboard.ScoreboardNumberPackets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerBoard {

    private static final int LIMIT = 15;
    private static final int PART = 64;

    private final Player player;
    private final Scoreboard scoreboard;
    private final ScoreboardNumberPackets numberPackets;
    private final List<String> lines = new ArrayList<>();
    private final Map<String, Integer> tracked = new HashMap<>();
    private Objective sidebar;
    private Objective below;
    private String title = "";
    private String display = "";
    private boolean health;
    private boolean numbersHidden;

    public PlayerBoard(Player player, ScoreboardNumberPackets numberPackets) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        this.player = player;
        this.scoreboard = manager.getNewScoreboard();
        this.numberPackets = numberPackets;
    }

    public Scoreboard scoreboard() {
        return scoreboard;
    }

    public void sidebar(String title, List<String> content, boolean hideNumbers) {
        if (sidebar == null) {
            sidebar = scoreboard.registerNewObjective("CORE_SIDEBAR", Criteria.DUMMY, cut(title, 128));
            this.title = title;
            updateNumberFormat(hideNumbers);
            sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (numbersHidden != hideNumbers) {
            updateNumberFormat(hideNumbers);
        }
        if (!this.title.equals(title)) {
            sidebar.setDisplayName(cut(title, 128));
            this.title = title;
        }
        int size = Math.min(content.size(), LIMIT);
        for (int index = lines.size() - 1; index >= size; index--) {
            Team team = scoreboard.getTeam(team(index));
            if (team != null) {
                team.unregister();
            }
            scoreboard.resetScores(entry(index));
            lines.remove(index);
        }
        for (int index = 0; index < size; index++) {
            String text = content.get(index);
            boolean fresh = index >= lines.size();
            Team team = scoreboard.getTeam(team(index));
            if (team == null) {
                team = scoreboard.registerNewTeam(team(index));
            }
            if (!team.hasEntry(entry(index))) {
                team.addEntry(entry(index));
            }
            Score score = sidebar.getScore(entry(index));
            if (fresh || score.getScore() != size - index) {
                score.setScore(size - index);
            }
            if (fresh) {
                lines.add(null);
            }
            if (!text.equals(lines.get(index))) {
                write(team, text);
                lines.set(index, text);
            }
        }
    }

    public void removeSidebar() {
        for (int index = 0; index < lines.size(); index++) {
            Team team = scoreboard.getTeam(team(index));
            if (team != null) {
                team.unregister();
            }
            scoreboard.resetScores(entry(index));
        }
        lines.clear();
        if (sidebar != null) {
            sidebar.unregister();
            sidebar = null;
        }
        title = "";
        numbersHidden = false;
    }

    public void below(String display, boolean health) {
        if (below != null && this.health != health) {
            removeBelow();
        }
        if (below == null) {
            below = scoreboard.registerNewObjective("CORE_BELOW", health ? Criteria.HEALTH : Criteria.DUMMY, cut(display, 128));
            below.setDisplaySlot(DisplaySlot.BELOW_NAME);
            this.health = health;
            this.display = display;
            return;
        }
        if (!this.display.equals(display)) {
            below.setDisplayName(cut(display, 128));
            this.display = display;
        }
    }

    public void value(String name, int value) {
        if (below == null || health) {
            return;
        }
        Integer previous = tracked.get(name);
        if (previous != null && previous == value) {
            return;
        }
        tracked.put(name, value);
        below.getScore(name).setScore(value);
    }

    public void forget(String name) {
        tracked.remove(name);
        if (below != null && !health) {
            scoreboard.resetScores(name);
        }
    }

    public void removeBelow() {
        tracked.clear();
        if (below != null) {
            below.unregister();
            below = null;
        }
        display = "";
    }

    public void destroy() {
        removeSidebar();
        removeBelow();
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            team.unregister();
        }
        for (Objective objective : new ArrayList<>(scoreboard.getObjectives())) {
            objective.unregister();
        }
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void updateNumberFormat(boolean hideNumbers) {
        numberPackets.apply(sidebar, hideNumbers);
        numbersHidden = hideNumbers;
    }

    private void write(Team team, String text) {
        String prefix = text;
        String suffix = "";
        if (text.length() > PART) {
            int split = PART;
            if (text.charAt(split - 1) == ChatColor.COLOR_CHAR) {
                split--;
            }
            prefix = text.substring(0, split);
            suffix = ChatColor.getLastColors(prefix) + text.substring(split);
            if (suffix.length() > PART) {
                suffix = suffix.substring(0, PART);
            }
        }
        if (!team.getPrefix().equals(prefix)) {
            team.setPrefix(prefix);
        }
        if (!team.getSuffix().equals(suffix)) {
            team.setSuffix(suffix);
        }
    }

    private String team(int index) {
        return "CORE_LINE_" + index;
    }

    private String entry(int index) {
        return ChatColor.COLOR_CHAR + Integer.toHexString(index) + ChatColor.RESET.toString();
    }

    private String cut(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) : text;
    }
}
