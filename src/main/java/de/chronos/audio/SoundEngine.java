package de.chronos.audio;

import de.chronos.core.event.GameEvent;
import de.chronos.core.event.GameEventListener;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Prozeduraler Audio-Synthesizer auf Basis von javax.sound.sampled.
 * Generiert sämtliche Soundeffekte zur Laufzeit mathematisch als PCM-Wellenformen
 * (Sinus, Rechteck, Dreieck, Rauschen) ohne externe Audiodateien.
 */
public final class SoundEngine implements GameEventListener {

    public enum SoundId {
        SLASH,
        MAGIC_BOLT,
        DAGGER,
        HIT,
        CRIT,
        DASH,
        SKILL,
        PICKUP,
        CHEST,
        LEVEL_UP,
        PLAYER_HURT,
        BOSS_PHASE,
        VICTORY,
        GAME_OVER,
        UI_CLICK
    }

    private static final float SAMPLE_RATE = 22050f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final Map<SoundId, byte[]> soundCache = new EnumMap<>(SoundId.class);
    private final Map<SoundId, Long> lastPlayedMs = new EnumMap<>(SoundId.class);
    private final ExecutorService audioExecutor;
    private volatile boolean muted;
    private volatile boolean audioAvailable = true;

    public SoundEngine(boolean muted) {
        this.muted = muted;
        // Begrenzte Warteschlange: Bei vielen gleichzeitigen Effekten werden überzählige Sounds verworfen,
        // statt sich aufzustauen und Sekunden später verspätet abgespielt zu werden.
        this.audioExecutor = new ThreadPoolExecutor(
                3, 3, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4),
                runnable -> {
                    Thread t = new Thread(runnable, "Chronos-Audio");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardPolicy());
        precomputeSounds();
    }

    private void precomputeSounds() {
        soundCache.put(SoundId.SLASH, synthSweep(320, 110, 0.11, 0.35, Wave.NOISE_MIX));
        soundCache.put(SoundId.MAGIC_BOLT, synthSweep(640, 280, 0.13, 0.28, Wave.SINE));
        soundCache.put(SoundId.DAGGER, synthSweep(980, 420, 0.07, 0.24, Wave.TRIANGLE));
        soundCache.put(SoundId.HIT, synthSweep(190, 75, 0.08, 0.32, Wave.SQUARE));
        soundCache.put(SoundId.CRIT, synthArpeggio(new double[]{520, 780, 1040}, 0.045, 0.34, Wave.SQUARE));
        soundCache.put(SoundId.DASH, synthSweep(260, 620, 0.12, 0.28, Wave.NOISE_MIX));
        soundCache.put(SoundId.SKILL, synthArpeggio(new double[]{330, 440, 660, 880}, 0.055, 0.35, Wave.TRIANGLE));
        soundCache.put(SoundId.PICKUP, synthArpeggio(new double[]{660, 990}, 0.045, 0.22, Wave.SINE));
        soundCache.put(SoundId.CHEST, synthArpeggio(new double[]{440, 554, 659, 880}, 0.07, 0.32, Wave.TRIANGLE));
        soundCache.put(SoundId.LEVEL_UP, synthArpeggio(new double[]{440, 554, 659, 880, 1108}, 0.08, 0.38, Wave.TRIANGLE));
        soundCache.put(SoundId.PLAYER_HURT, synthSweep(150, 55, 0.18, 0.42, Wave.SQUARE));
        soundCache.put(SoundId.BOSS_PHASE, synthArpeggio(new double[]{180, 160, 140, 220}, 0.12, 0.44, Wave.SQUARE));
        soundCache.put(SoundId.VICTORY, synthArpeggio(new double[]{523.25, 659.25, 783.99, 1046.5}, 0.11, 0.38, Wave.TRIANGLE));
        soundCache.put(SoundId.GAME_OVER, synthArpeggio(new double[]{392, 349, 311, 261}, 0.14, 0.36, Wave.SINE));
        soundCache.put(SoundId.UI_CLICK, synthSweep(720, 520, 0.04, 0.18, Wave.SINE));
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public void toggleMute() {
        this.muted = !this.muted;
    }

    public void play(SoundId id) {
        if (muted || !audioAvailable || id == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastPlayedMs.get(id);
        if (prev != null && now - prev < 40) {
            return;
        }
        lastPlayedMs.put(id, now);
        byte[] pcm = soundCache.get(id);
        if (pcm == null) {
            return;
        }
        audioExecutor.submit(() -> playPcm(pcm));
    }

    private void playPcm(byte[] pcm) {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            audioAvailable = false;
            return;
        }
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(FORMAT);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (LineUnavailableException ex) {
            // Kurzzeitig alle Audiokanäle belegt (häufig unter Windows): diesen Sound einfach auslassen,
            // Audio aber NICHT dauerhaft abschalten.
        } catch (Exception ex) {
            // Headless- oder deaktivierte Audio-Umgebungen stören niemals die Spiellogik
            audioAvailable = false;
        }
    }

    @Override
    public void onEvent(GameEvent event) {
        switch (event) {
            case GameEvent.AttackPerformed e -> {
                if ("VANGUARD".equals(e.classId())) {
                    play(SoundId.SLASH);
                } else if ("ARCANIST".equals(e.classId())) {
                    play(SoundId.MAGIC_BOLT);
                } else {
                    play(SoundId.DAGGER);
                }
                if (e.critical()) {
                    play(SoundId.CRIT);
                }
            }
            case GameEvent.EnemyKilled ignored -> play(SoundId.HIT);
            case GameEvent.PlayerHurt ignored -> play(SoundId.PLAYER_HURT);
            case GameEvent.DashPerformed ignored -> play(SoundId.DASH);
            case GameEvent.SkillCast ignored -> play(SoundId.SKILL);
            case GameEvent.PickupCollected ignored -> play(SoundId.PICKUP);
            case GameEvent.LevelUp ignored -> play(SoundId.LEVEL_UP);
            case GameEvent.ChestOpened ignored -> play(SoundId.CHEST);
            case GameEvent.FloorChanged ignored -> play(SoundId.SKILL);
            case GameEvent.BossPhaseChanged ignored -> play(SoundId.BOSS_PHASE);
            case GameEvent.RunEnded e -> play(e.victory() ? SoundId.VICTORY : SoundId.GAME_OVER);
            case GameEvent.UiClick ignored -> play(SoundId.UI_CLICK);
        }
    }

    public void shutdown() {
        audioExecutor.shutdownNow();
    }

    private enum Wave {
        SINE, SQUARE, TRIANGLE, NOISE_MIX
    }

    private static byte[] synthSweep(double startFreq, double endFreq, double durationSec, double volume, Wave wave) {
        int samples = (int) (SAMPLE_RATE * durationSec);
        byte[] buffer = new byte[samples * 2];
        double phase = 0.0;
        long noiseState = 123456789L;

        for (int i = 0; i < samples; i++) {
            double progress = (double) i / samples;
            double freq = startFreq + (endFreq - startFreq) * progress;
            phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;

            double env = (1.0 - progress);
            if (progress < 0.08) {
                env *= (progress / 0.08);
            }

            double sampleVal;
            switch (wave) {
                case SQUARE -> sampleVal = Math.sin(phase) >= 0 ? 0.7 : -0.7;
                case TRIANGLE -> sampleVal = (2.0 / Math.PI) * Math.asin(Math.sin(phase));
                case NOISE_MIX -> {
                    noiseState = (noiseState * 1103515245L + 12345L) & 0x7fffffffL;
                    double noise = ((noiseState / (double) 0x7fffffffL) * 2.0 - 1.0);
                    sampleVal = 0.55 * Math.sin(phase) + 0.45 * noise;
                }
                default -> sampleVal = Math.sin(phase);
            }

            short pcmVal = (short) Math.max(-32767, Math.min(32767, sampleVal * env * volume * 32767.0));
            buffer[i * 2] = (byte) (pcmVal & 0xff);
            buffer[i * 2 + 1] = (byte) ((pcmVal >> 8) & 0xff);
        }
        return buffer;
    }

    private static byte[] synthArpeggio(double[] freqs, double noteDurationSec, double volume, Wave wave) {
        int noteSamples = (int) (SAMPLE_RATE * noteDurationSec);
        int totalSamples = noteSamples * freqs.length;
        byte[] buffer = new byte[totalSamples * 2];

        for (int n = 0; n < freqs.length; n++) {
            double freq = freqs[n];
            double phase = 0.0;
            for (int i = 0; i < noteSamples; i++) {
                double progress = (double) i / noteSamples;
                phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                double env = (1.0 - progress * 0.75);
                if (progress < 0.1) {
                    env *= (progress / 0.1);
                }
                double val = switch (wave) {
                    case SQUARE -> Math.sin(phase) >= 0 ? 0.65 : -0.65;
                    case TRIANGLE -> (2.0 / Math.PI) * Math.asin(Math.sin(phase));
                    default -> Math.sin(phase);
                };
                int idx = (n * noteSamples + i) * 2;
                short pcmVal = (short) Math.max(-32767, Math.min(32767, val * env * volume * 32767.0));
                buffer[idx] = (byte) (pcmVal & 0xff);
                buffer[idx + 1] = (byte) ((pcmVal >> 8) & 0xff);
            }
        }
        return buffer;
    }
}
