package de.chronos.math;

import java.util.List;
import java.util.Random;

/**
 * Deterministischer Zufallsgenerator mit expliziter Seed-Verwaltung.
 * Ermöglicht reproduzierbare Dungeon-Layouts anhand eines teilbaren Seed-Strings.
 */
public final class SeededRandom {
    private final long seed;
    private final Random random;

    public SeededRandom(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public static long parseSeed(String rawSeed) {
        if (rawSeed == null || rawSeed.trim().isEmpty()) {
            return System.nanoTime() ^ 0x5DEECE66DL;
        }
        String trimmed = rawSeed.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            long hash = 1125899906842597L;
            for (int i = 0; i < trimmed.length(); i++) {
                hash = 31L * hash + trimmed.charAt(i);
            }
            return hash;
        }
    }

    public long getSeed() {
        return seed;
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public int nextInt(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public double nextDouble(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    public <T> T pick(List<T> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(random.nextInt(items.size()));
    }

    public SeededRandom derive(long salt) {
        return new SeededRandom(seed * 6364136223846793005L + salt);
    }
}
