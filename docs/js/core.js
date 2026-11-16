/*
 * CHRONOS: Aethelgard's Descent – Web-Version
 * Kern: Konfiguration, Java-kompatibler Zufallsgenerator, Vektoren, Farben,
 * Lokalisierung, Event-Bus, Eingabe und Speicherstand.
 */
'use strict';

const CH = window.CH = window.CH || {};

// ---------------------------------------------------------------------------
// Konfiguration (entspricht de.chronos.core.GameConfig)
// ---------------------------------------------------------------------------
CH.Config = Object.freeze({
  GAME_TITLE: "CHRONOS: Aethelgard's Descent",
  VERSION: '1.2.0-web',
  LOGICAL_WIDTH: 1280,
  LOGICAL_HEIGHT: 720,
  TARGET_FPS: 60,
  FIXED_DT: 1 / 60,
  MAX_FRAME_TIME: 0.1,
  TILE_SIZE: 44,
  MAX_FLOORS: 3,
  BASE_MAP_COLS: 72,
  BASE_MAP_ROWS: 54,
  VISION_RADIUS_TILES: 11,
  DASH_DURATION: 0.18,
  DASH_COOLDOWN: 1.35,
  DASH_SPEED_MULTIPLIER: 3.25,
  PICKUP_MAGNET_RADIUS: 135.0,
  INTERACT_RADIUS: 66.0,
  COMBO_DECAY_SECONDS: 4.5,
});

// Gegner-Leben, Tempo, Punkte-Faktor, Gegner-Schaden, Angriffstempo, Zusatzgegner pro Raum
function difficulty(name, hp, speed, score, damage, attackRate, extraEnemies) {
  return Object.freeze({
    name, enemyHealthMultiplier: hp, enemySpeedMultiplier: speed, scoreMultiplier: score,
    enemyDamageMultiplier: damage, enemyAttackRate: attackRate, extraEnemiesPerRoom: extraEnemies,
  });
}
CH.Difficulty = Object.freeze({
  NORMAL: difficulty('NORMAL', 1.5, 1.12, 1.0, 1.8, 1.35, 2),
  HARD: difficulty('HARD', 2.0, 1.25, 1.6, 2.4, 1.6, 3),
  NIGHTMARE: difficulty('NIGHTMARE', 2.7, 1.4, 2.5, 3.2, 1.9, 4),
});
CH.Balance = Object.freeze({
  FLOOR_HP_SCALING: 0.55,       // +55% Gegner-Leben pro Ebene
  FLOOR_DAMAGE_SCALING: 0.30,   // +30% Gegner-Schaden pro Ebene
  PLAYER_HIT_INVULN: 0.30,
  LEVEL_UP_HEAL: 5,
  SHIELD_REGEN: 2.0,
  SPIKE_DAMAGE: 20,
  POTION_DROP_CHANCE: 0.07,
});
CH.DIFFICULTIES = [CH.Difficulty.NORMAL, CH.Difficulty.HARD, CH.Difficulty.NIGHTMARE];

// ---------------------------------------------------------------------------
// 64-Bit-Hilfen (Java long) über BigInt
// ---------------------------------------------------------------------------
CH.Long = {
  wrap(v) { return BigInt.asIntN(64, v); },
  mul(a, b) { return BigInt.asIntN(64, BigInt(a) * BigInt(b)); },
  xor(a, b) { return BigInt.asIntN(64, BigInt(a) ^ BigInt(b)); },
};

// ---------------------------------------------------------------------------
// Exakter Nachbau von java.util.Random (48-Bit-LCG), damit gleiche Seeds
// in der Java- und der Web-Version identische Dungeons erzeugen.
// Der Zustand wird in zwei 24-Bit-Hälften gehalten (schnell, ohne BigInt).
// ---------------------------------------------------------------------------
const TWO24 = 16777216;
const MUL_HI = 0x5DE;       // 0x5DEECE66D >> 24
const MUL_LO = 0xECE66D;    // 0x5DEECE66D & 0xFFFFFF

class JavaRandom {
  constructor(seed) {
    this.setSeed(seed);
  }

  setSeed(seed) {
    const s = (BigInt.asIntN(64, BigInt(seed)) ^ 0x5DEECE66Dn) & 0xFFFFFFFFFFFFn;
    this.hi = Number(s >> 24n);
    this.lo = Number(s & 0xFFFFFFn);
  }

  next(bits) {
    const pLo = this.lo * MUL_LO + 0xB;
    const newLo = pLo % TWO24;
    const carry = Math.floor(pLo / TWO24);
    const pHi = this.hi * MUL_LO + this.lo * MUL_HI + carry;
    const newHi = pHi % TWO24;
    this.hi = newHi;
    this.lo = newLo;
    const value = newHi * TWO24 + newLo; // 48 Bit, exakt darstellbar
    const r = Math.floor(value / Math.pow(2, 48 - bits));
    return r | 0; // als vorzeichenbehafteter 32-Bit-int
  }

  nextInt(bound) {
    if (bound === undefined) return this.next(32);
    if (bound <= 0) throw new Error('bound must be positive');
    if ((bound & -bound) === bound) {
      // Zweierpotenz: (int)((bound * (long)next(31)) >> 31)
      return Math.floor((bound * this.next(31)) / 2147483648);
    }
    let bits, val;
    do {
      bits = this.next(31);
      val = bits % bound;
    } while (((bits - val + (bound - 1)) | 0) < 0);
    return val;
  }

  nextDouble() {
    return (this.next(26) * 134217728 + this.next(27)) / 9007199254740992;
  }

  nextBoolean() {
    return this.next(1) !== 0;
  }
}
CH.JavaRandom = JavaRandom;

class SeededRandom {
  constructor(seed) {
    this.seed = BigInt.asIntN(64, BigInt(seed));
    this.random = new JavaRandom(this.seed);
  }

  static parseSeed(raw) {
    if (raw == null || raw.trim() === '') {
      // Zufälliger 64-Bit-Seed
      const a = BigInt(Math.floor(Math.random() * 0x100000000));
      const b = BigInt(Math.floor(Math.random() * 0x100000000));
      const t = BigInt(Date.now());
      return BigInt.asIntN(64, ((a << 32n) | b) ^ (t * 0x5DEECE66Dn));
    }
    const trimmed = raw.trim();
    if (/^[+-]?\d+$/.test(trimmed)) {
      const v = BigInt(trimmed);
      if (v >= -(2n ** 63n) && v < 2n ** 63n) return v; // wie Long.parseLong
    }
    let hash = 1125899906842597n;
    for (let i = 0; i < trimmed.length; i++) {
      hash = BigInt.asIntN(64, 31n * hash + BigInt(trimmed.charCodeAt(i)));
    }
    return hash;
  }

  nextInt(a, b) {
    if (b === undefined) return this.random.nextInt(a);
    if (b <= a) return a;
    return a + this.random.nextInt(b - a + 1);
  }

  nextDouble(min, max) {
    if (min === undefined) return this.random.nextDouble();
    return min + (max - min) * this.random.nextDouble();
  }

  nextBoolean() { return this.random.nextBoolean(); }

  chance(p) { return this.random.nextDouble() < p; }

  pick(items) {
    if (!items || items.length === 0) return null;
    return items[this.random.nextInt(items.length)];
  }
}
CH.SeededRandom = SeededRandom;

// ---------------------------------------------------------------------------
// Vektor
// ---------------------------------------------------------------------------
class Vec {
  constructor(x, y) { this.x = x; this.y = y; }
  static fromAngle(rad, len) { return new Vec(Math.cos(rad) * len, Math.sin(rad) * len); }
  add(o) { return new Vec(this.x + o.x, this.y + o.y); }
  sub(o) { return new Vec(this.x - o.x, this.y - o.y); }
  scale(f) { return new Vec(this.x * f, this.y * f); }
  lengthSquared() { return this.x * this.x + this.y * this.y; }
  length() { return Math.sqrt(this.x * this.x + this.y * this.y); }
  normalize() {
    const len = this.length();
    if (len < 1e-9) return Vec.ZERO;
    return new Vec(this.x / len, this.y / len);
  }
  distanceTo(o) { const dx = this.x - o.x, dy = this.y - o.y; return Math.sqrt(dx * dx + dy * dy); }
  distanceSquaredTo(o) { const dx = this.x - o.x, dy = this.y - o.y; return dx * dx + dy * dy; }
  dot(o) { return this.x * o.x + this.y * o.y; }
  angle() { return Math.atan2(this.y, this.x); }
}
Vec.ZERO = new Vec(0, 0);
CH.Vec = Vec;

// ---------------------------------------------------------------------------
// Farbe (wie java.awt.Color, mit gecachtem CSS-String)
// ---------------------------------------------------------------------------
class Color {
  constructor(r, g, b, a = 255) {
    this.r = r; this.g = g; this.b = b; this.a = a;
    this.css = a >= 255 ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${(a / 255).toFixed(3)})`;
  }
  withAlpha(alpha) {
    const a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
    return new Color(this.r, this.g, this.b, a);
  }
}
Color.WHITE = new Color(255, 255, 255);
Color.BLACK = new Color(0, 0, 0);
CH.Color = Color;
CH.rgb = (r, g, b, a) => new Color(r, g, b, a === undefined ? 255 : a);

CH.clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
CH.pad2 = (n) => String(Math.trunc(n)).padStart(2, '0');
CH.formatTime = (sec) => `${CH.pad2(sec / 60)}:${CH.pad2(sec % 60)}`;

// ---------------------------------------------------------------------------
// Lokalisierung DE / EN
// ---------------------------------------------------------------------------
const I18N_DE = {}, I18N_EN = {};
function reg(k, de, en) { I18N_DE[k] = de; I18N_EN[k] = en; }

reg('subtitle', 'Prozeduraler Action-Roguelite Dungeon Crawler', 'Procedural Action-Roguelite Dungeon Crawler');
reg('menu.start', 'Abenteuer starten', 'Start Descent');
reg('menu.highscores', 'Bestenliste & Erfolge', 'Highscores & Achievements');
reg('menu.help', 'Steuerung & Technik', 'Controls & Tech Info');
reg('menu.fullscreen', 'Vollbild', 'Fullscreen');
reg('menu.select_class', 'Heldenklasse wählen', 'Choose Hero Class');
reg('menu.difficulty', 'Schwierigkeit', 'Difficulty');
reg('menu.seed', 'Dungeon-Seed (leer = zufällig)', 'Dungeon Seed (blank = random)');
reg('menu.seed_hint', 'Tippen für eigenen Seed...', 'Type custom seed...');
reg('menu.language', 'Sprache [L]: DE', 'Language [L]: EN');
reg('menu.hint', 'ENTER / Klick: Starten   |   1-3: Klasse   |   L: Sprache   |   M: Ton',
  'ENTER / Click: Start   |   1-3: Class   |   L: Language   |   M: Audio');

reg('diff.NORMAL', 'Normal', 'Normal');
reg('diff.HARD', 'Schwer', 'Hard');
reg('diff.NIGHTMARE', 'Alptraum', 'Nightmare');

reg('class.VANGUARD.name', 'Klingenwächter', 'Vanguard');
reg('class.VANGUARD.role', 'Nahkampf-Tank & Schildbrecher', 'Melee Bruiser & Shieldbreaker');
reg('class.VANGUARD.desc', 'Breiter Schwertbogen, Schild-Dash blockt Geschosse, Wirbelnova [Q], Unbezwingbare Ägis [E].',
  'Wide blade cleave, shield dash blocks projectiles, Whirlwind Nova [Q], Immortal Aegis [E].');
reg('class.ARCANIST.name', 'Arkanmagier', 'Arcanist');
reg('class.ARCANIST.role', 'Elementarmagie & Flächenschaden', 'Arcane Burst & Area Control');
reg('class.ARCANIST.desc', 'Zielsuchende Arkanbolzen, Arkan-Dash, Frostnova [Q], Meteorsturm [E].',
  'Homing arcane bolts, arcane dash, Frost Nova [Q], Meteor Storm [E].');
reg('class.SHADOWBLADE.name', 'Schattenjäger', 'Shadowblade');
reg('class.SHADOWBLADE.role', 'Hohes Tempo & Kritische Treffer', 'High Mobility & Critical Strikes');
reg('class.SHADOWBLADE.desc', 'Giftige Wurfdolche, explosiver Schatten-Dash, Klingenfächer [Q], Schattenrausch [E].',
  'Poisoned throwing daggers, exploding shadow dash, Fan of Knives [Q], Shadow Frenzy [E].');

reg('hud.floor', 'Ebene', 'Floor');
reg('hud.level', 'Stufe', 'Lv.');
reg('hud.gold', 'Gold', 'Gold');
reg('hud.score', 'Punkte', 'Score');
reg('hud.combo', 'Kombo', 'Combo');
reg('hud.boss_name', 'Malakor, der Chronos-Wächter', 'Malakor, Warden of Chronos');
reg('hud.boss_phase2', 'PHASE II: CHRONOS-ÜBERLADUNG', 'PHASE II: CHRONOS OVERLOAD');
reg('hud.interact_chest', '[F] Schatztruhe öffnen', '[F] Open Treasure Chest');
reg('hud.interact_shrine', '[F] Heilschrein nutzen (25 Gold -> +45 HP)', '[F] Use Healing Shrine (25 Gold -> +45 HP)');
reg('hud.interact_altar', '[F] Relikt-Altar aktivieren (40 Gold -> Upgrade)', '[F] Activate Relic Altar (40 Gold -> Upgrade)');
reg('hud.interact_portal', '[F] Portal zur nächsten Ebene betreten', '[F] Enter Portal to Next Floor');
reg('hud.not_enough_gold', 'Nicht genug Gold (%d benötigt, %d vorhanden)', 'Not enough gold (%d needed, you have %d)');
reg('hud.portal_locked', 'Besiege den Wächter/alle Gegner im Zielraum!', 'Defeat the Guardian / Room enemies first!');
reg('hud.muted', 'TON AUS [M]', 'MUTED [M]');
reg('hud.seed', 'Seed', 'Seed');
reg('hud.upgrades', 'Relikte', 'Relics');
reg('hud.achievement', 'ERFOLG FREIGESCHALTET', 'ACHIEVEMENT UNLOCKED');
reg('hud.map_legend', 'Blau: Start   |   Gold: Schatz   |   Grün: Schrein   |   Rot: Boss / Portal',
  'Blue: Start   |   Gold: Treasure   |   Green: Shrine   |   Red: Boss / Portal');

reg('intro.developed_by', 'Entwickelt durch', 'Developed by');
reg('intro.published_by', 'Veröffentlicht durch', 'Published by');
reg('intro.skip', 'Beliebige Taste oder Klick zum Überspringen', 'Press any key or click to skip');

reg('draft.title', 'STUFENAUFSTIEG! WÄHLE EINE VERBESSERUNG', 'LEVEL UP! CHOOSE AN UPGRADE');
reg('draft.altar_title', 'URALTER ALTAR: WÄHLE EIN RELIKT', 'ANCIENT ALTAR: CHOOSE A RELIC');
reg('draft.hint', 'Klicke auf eine Karte oder drücke [1], [2], [3]', 'Click a card or press [1], [2], [3]');

reg('pause.title', 'SPIEL PAUSIERT', 'GAME PAUSED');
reg('pause.resume', 'Fortsetzen [ESC]', 'Resume [ESC]');
reg('pause.restart', 'Neuer Run (gleiche Klasse)', 'New Run (Same Class)');
reg('pause.menu', 'Zurück zum Hauptmenü', 'Return to Main Menu');

reg('end.victory', 'SIEG! DER CHRONOS-WÄCHTER IST GEFALLEN!', 'VICTORY! THE WARDEN OF CHRONOS FELL!');
reg('end.gameover', 'NIEDERLAGE IM LABYRINTH', 'FALLEN IN THE LABYRINTH');
reg('end.play_again', 'Erneut spielen', 'Play Again');
reg('end.menu', 'Hauptmenü', 'Main Menu');
reg('end.duration', 'Zeit', 'Duration');
reg('end.kills', 'Besiegte Gegner', 'Enemies Slain');
reg('end.damage', 'Verursachter Schaden', 'Damage Dealt');
reg('end.max_combo', 'Höchste Kombo', 'Peak Combo');
reg('end.floor_reached', 'Erreichte Ebene', 'Floor Reached');

reg('hs.title', 'BESTENLISTE & ERFOLGE', 'HALL OF FAME & ACHIEVEMENTS');
reg('hs.empty', 'Noch keine abgeschlossenen Runs gespeichert.', 'No completed runs recorded yet.');
reg('hs.achievements', 'Erfolge', 'Achievements');
reg('hs.back', 'Zurück [ESC]', 'Back [ESC]');

reg('help.title', 'STEUERUNG, KAMPFSYSTEM & INFORMATIK-ARCHITEKTUR', 'CONTROLS, COMBAT & CS ARCHITECTURE');
reg('help.back', 'Zurück [ESC]', 'Back [ESC]');

reg('rarity.COMMON', 'Gewöhnlich', 'Common');
reg('rarity.RARE', 'Selten', 'Rare');
reg('rarity.EPIC', 'Episch', 'Epic');
reg('rarity.LEGENDARY', 'Legendär', 'Legendary');

CH.I18n = {
  lang: 'DE',
  t(key) {
    const map = this.lang === 'DE' ? I18N_DE : I18N_EN;
    return Object.prototype.hasOwnProperty.call(map, key) ? map[key] : key;
  },
  fmt(key, ...args) {
    let i = 0;
    return this.t(key).replace(/%d/g, () => String(args[i++]));
  },
  toggle() { this.lang = this.lang === 'DE' ? 'EN' : 'DE'; },
  isDe() { return this.lang === 'DE'; },
};

// ---------------------------------------------------------------------------
// Event-Bus (Observer)
// ---------------------------------------------------------------------------
class EventBus {
  constructor() { this.listeners = []; }
  subscribe(fn) { this.listeners.push(fn); }
  publish(type, data) {
    const ev = Object.assign({ type }, data || {});
    for (const l of this.listeners) l(ev);
  }
}
CH.EventBus = EventBus;

// ---------------------------------------------------------------------------
// Eingabe: Tastatur & Maus (Taste = KeyboardEvent.code)
// ---------------------------------------------------------------------------
class Input {
  constructor() {
    this.keysDown = new Set();
    this.keysPressed = new Set();
    this.keysPressedBuffer = new Set();
    this.mouseDown = new Set();
    this.mousePressed = new Set();
    this.mousePressedBuffer = new Set();
    this.typedChars = [];
    this.mouseX = CH.Config.LOGICAL_WIDTH / 2;
    this.mouseY = CH.Config.LOGICAL_HEIGHT / 2;
    this.offsetX = 0; this.offsetY = 0; this.scale = 1;
  }

  static normalize(code) {
    if (code === 'ShiftLeft' || code === 'ShiftRight') return 'Shift';
    if (code === 'Numpad1') return 'Digit1';
    if (code === 'Numpad2') return 'Digit2';
    if (code === 'Numpad3') return 'Digit3';
    if (code === 'NumpadEnter') return 'Enter';
    return code;
  }

  beginFrame() {
    this.keysPressed = this.keysPressedBuffer;
    this.keysPressedBuffer = new Set();
    this.mousePressed = this.mousePressedBuffer;
    this.mousePressedBuffer = new Set();
  }

  isKeyDown(c) { return this.keysDown.has(c); }
  wasKeyPressed(c) { return this.keysPressed.has(c); }
  isMouseDown(b) { return this.mouseDown.has(b); }
  wasMousePressed(b) { return this.mousePressed.has(b); }
  wasAnyInputPressed() { return this.keysPressed.size > 0 || this.mousePressed.size > 0; }
  pollTypedChar() { return this.typedChars.length ? this.typedChars.shift() : null; }
  clearTypedChars() { this.typedChars.length = 0; }

  clearAllInputs() {
    this.keysDown.clear(); this.keysPressed.clear(); this.keysPressedBuffer.clear();
    this.mouseDown.clear(); this.mousePressed.clear(); this.mousePressedBuffer.clear();
    this.typedChars.length = 0;
  }

  setRenderTransform(ox, oy, s) { this.offsetX = ox; this.offsetY = oy; this.scale = Math.max(0.01, s); }

  updateMouse(clientX, clientY, rect) {
    const lx = (clientX - rect.left - this.offsetX) / this.scale;
    const ly = (clientY - rect.top - this.offsetY) / this.scale;
    this.mouseX = CH.clamp(lx, 0, CH.Config.LOGICAL_WIDTH);
    this.mouseY = CH.clamp(ly, 0, CH.Config.LOGICAL_HEIGHT);
  }

  onKeyDown(e) {
    const code = Input.normalize(e.code);
    if (!this.keysDown.has(code)) this.keysPressedBuffer.add(code);
    this.keysDown.add(code);
    let ch = null;
    if (e.key === 'Backspace') ch = '\b';
    else if (e.key === 'Enter') ch = '\n';
    else if (e.key && e.key.length === 1 && !e.ctrlKey && !e.metaKey) ch = e.key;
    if (ch !== null && this.typedChars.length < 32) this.typedChars.push(ch);
  }

  onKeyUp(e) { this.keysDown.delete(Input.normalize(e.code)); }

  onMouseDown(btn) {
    if (!this.mouseDown.has(btn)) this.mousePressedBuffer.add(btn);
    this.mouseDown.add(btn);
  }

  onMouseUp(btn) { this.mouseDown.delete(btn); }
}
Input.BUTTON1 = 1;
Input.BUTTON3 = 3;
CH.Input = Input;

// ---------------------------------------------------------------------------
// Speicherstand im Browser (localStorage)
// ---------------------------------------------------------------------------
class SaveManager {
  constructor(storageKey = 'chronos-descent-save-v1') {
    this.key = storageKey;
    this.highscores = [];
    this.achievements = new Set();
    this.language = 'DE';
    this.muted = false;
    this.load();
  }

  load() {
    try {
      const raw = window.localStorage.getItem(this.key);
      if (!raw) {
        // Standardsprache aus dem Browser ableiten
        const nav = (navigator.language || 'de').toLowerCase();
        this.language = nav.startsWith('de') ? 'DE' : 'EN';
        return;
      }
      const d = JSON.parse(raw);
      this.language = d.language === 'EN' ? 'EN' : 'DE';
      this.muted = !!d.muted;
      this.achievements = new Set(Array.isArray(d.achievements) ? d.achievements : []);
      this.highscores = Array.isArray(d.highscores) ? d.highscores.filter(h => h && typeof h.score === 'number') : [];
      this.sortAndTrim();
    } catch (e) {
      // Beschädigter Speicher oder privater Modus – Spiel läuft trotzdem
    }
  }

  save() {
    try {
      window.localStorage.setItem(this.key, JSON.stringify({
        language: this.language,
        muted: this.muted,
        achievements: [...this.achievements],
        highscores: this.highscores,
      }));
    } catch (e) { /* ignorieren */ }
  }

  recordRun(score, heroClass, difficulty, floorReached, kills, durationSeconds, victory, seed) {
    const d = new Date();
    const ts = `${d.getFullYear()}-${CH.pad2(d.getMonth() + 1)}-${CH.pad2(d.getDate())} ${CH.pad2(d.getHours())}:${CH.pad2(d.getMinutes())}`;
    this.highscores.push({ score, heroClass, difficulty, floorReached, kills, durationSeconds, victory, seed: String(seed), timestamp: ts });
    this.sortAndTrim();
    this.save();
  }

  unlockAchievement(id) {
    if (!id || this.achievements.has(id)) return false;
    this.achievements.add(id);
    this.save();
    return true;
  }

  isAchievementUnlocked(id) { return this.achievements.has(id); }
  setLanguage(l) { this.language = l; this.save(); }
  setMuted(m) { this.muted = m; this.save(); }

  sortAndTrim() {
    this.highscores.sort((a, b) => b.score - a.score);
    if (this.highscores.length > 10) this.highscores.length = 10;
  }
}
CH.SaveManager = SaveManager;
