package de.chronos.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verwaltet persistente Spielstände: Bestenliste, freigeschaltete Erfolge und Benutzereinstellungen.
 */
public final class SaveManager {

    public record HighscoreEntry(
            int score,
            String heroClass,
            String difficulty,
            int floorReached,
            int kills,
            double durationSeconds,
            boolean victory,
            long seed,
            String timestamp
    ) {}

    private static final int MAX_HIGHSCORES = 10;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Path saveFile;
    private final List<HighscoreEntry> highscores = new ArrayList<>();
    private final Set<String> unlockedAchievements = new HashSet<>();
    private I18n.Language language = I18n.Language.DE;
    private boolean muted = false;

    public SaveManager() {
        this(Paths.get(System.getProperty("user.home"), ".chronos-descent", "save.dat"));
    }

    public SaveManager(Path saveFile) {
        this.saveFile = saveFile;
        load();
    }

    public synchronized void load() {
        highscores.clear();
        unlockedAchievements.clear();
        if (!Files.exists(saveFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(saveFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("LANG=")) {
                    String val = line.substring("LANG=".length()).trim();
                    if ("EN".equalsIgnoreCase(val)) {
                        language = I18n.Language.EN;
                    } else {
                        language = I18n.Language.DE;
                    }
                } else if (line.startsWith("MUTED=")) {
                    muted = Boolean.parseBoolean(line.substring("MUTED=".length()).trim());
                } else if (line.startsWith("ACH=")) {
                    String id = line.substring("ACH=".length()).trim();
                    if (!id.isEmpty()) {
                        unlockedAchievements.add(id);
                    }
                } else if (line.startsWith("SCORE=")) {
                    HighscoreEntry entry = parseScoreLine(line.substring("SCORE=".length()));
                    if (entry != null) {
                        highscores.add(entry);
                    }
                }
            }
            sortAndTrimScores();
        } catch (IOException ignored) {
            // Beschädigte Datei bricht Start nicht ab
        }
    }

    public synchronized void save() {
        try {
            Path parent = saveFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            lines.add("# CHRONOS: Aethelgard's Descent Save Data");
            lines.add("LANG=" + language.name());
            lines.add("MUTED=" + muted);
            for (String ach : unlockedAchievements) {
                lines.add("ACH=" + ach);
            }
            for (HighscoreEntry entry : highscores) {
                lines.add(String.format(java.util.Locale.ROOT,
                        "SCORE=%d|%s|%s|%d|%d|%.2f|%b|%d|%s",
                        entry.score(),
                        sanitize(entry.heroClass()),
                        sanitize(entry.difficulty()),
                        entry.floorReached(),
                        entry.kills(),
                        entry.durationSeconds(),
                        entry.victory(),
                        entry.seed(),
                        sanitize(entry.timestamp())
                ));
            }
            Files.write(saveFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Ignorieren, falls schreibgeschützt
        }
    }

    public synchronized void recordRun(
            int score,
            String heroClass,
            String difficulty,
            int floorReached,
            int kills,
            double durationSeconds,
            boolean victory,
            long seed
    ) {
        String ts = LocalDateTime.now().format(TS_FMT);
        highscores.add(new HighscoreEntry(score, heroClass, difficulty, floorReached, kills, durationSeconds, victory, seed, ts));
        sortAndTrimScores();
        save();
    }

    public synchronized boolean unlockAchievement(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean added = unlockedAchievements.add(id);
        if (added) {
            save();
        }
        return added;
    }

    public synchronized boolean isAchievementUnlocked(String id) {
        return unlockedAchievements.contains(id);
    }

    public synchronized Set<String> getUnlockedAchievements() {
        return Collections.unmodifiableSet(new HashSet<>(unlockedAchievements));
    }

    public synchronized List<HighscoreEntry> getHighscores() {
        return Collections.unmodifiableList(new ArrayList<>(highscores));
    }

    public I18n.Language getLanguage() {
        return language;
    }

    public void setLanguage(I18n.Language language) {
        this.language = language;
        save();
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        save();
    }

    private void sortAndTrimScores() {
        highscores.sort(Comparator.comparingInt(HighscoreEntry::score).reversed());
        while (highscores.size() > MAX_HIGHSCORES) {
            highscores.remove(highscores.size() - 1);
        }
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replace("|", "_").replace("\n", "").replace("\r", "");
    }

    private static HighscoreEntry parseScoreLine(String raw) {
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 9) {
            return null;
        }
        try {
            return new HighscoreEntry(
                    Integer.parseInt(parts[0]),
                    parts[1],
                    parts[2],
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Double.parseDouble(parts[5]),
                    Boolean.parseBoolean(parts[6]),
                    Long.parseLong(parts[7]),
                    parts[8]
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
