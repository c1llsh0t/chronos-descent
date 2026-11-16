/*
 * Prozedurale Soundeffekte über die Web Audio API – dieselben Wellenform-Formeln
 * wie im Java-Original, ganz ohne Audiodateien.
 */
'use strict';
(function () {
  const CH = window.CH;
  const RATE = 22050;
  const MAX_VOICES = 8;

  function sweep(startF, endF, dur, vol, wave) {
    const n = Math.trunc(RATE * dur);
    const out = new Float32Array(n);
    let phase = 0;
    let noise = 123456789;
    for (let i = 0; i < n; i++) {
      const prog = i / n;
      phase += 2 * Math.PI * (startF + (endF - startF) * prog) / RATE;
      let env = 1 - prog;
      if (prog < 0.08) env *= prog / 0.08;
      let v;
      if (wave === 'SQUARE') v = Math.sin(phase) >= 0 ? 0.7 : -0.7;
      else if (wave === 'TRIANGLE') v = (2 / Math.PI) * Math.asin(Math.sin(phase));
      else if (wave === 'NOISE') {
        noise = (Math.imul(noise, 1103515245) + 12345) & 0x7fffffff;
        v = 0.55 * Math.sin(phase) + 0.45 * ((noise / 2147483647) * 2 - 1);
      } else v = Math.sin(phase);
      out[i] = CH.clamp(v * env * vol, -1, 1);
    }
    return out;
  }

  function arpeggio(freqs, noteDur, vol, wave) {
    const ns = Math.trunc(RATE * noteDur);
    const out = new Float32Array(ns * freqs.length);
    freqs.forEach((f, n) => {
      let phase = 0;
      for (let i = 0; i < ns; i++) {
        const prog = i / ns;
        phase += 2 * Math.PI * f / RATE;
        let env = 1 - prog * 0.75;
        if (prog < 0.1) env *= prog / 0.1;
        let v;
        if (wave === 'SQUARE') v = Math.sin(phase) >= 0 ? 0.65 : -0.65;
        else if (wave === 'TRIANGLE') v = (2 / Math.PI) * Math.asin(Math.sin(phase));
        else v = Math.sin(phase);
        out[n * ns + i] = CH.clamp(v * env * vol, -1, 1);
      }
    });
    return out;
  }

  const DEFS = {
    SLASH: () => sweep(320, 110, 0.11, 0.35, 'NOISE'),
    MAGIC_BOLT: () => sweep(640, 280, 0.13, 0.28, 'SINE'),
    DAGGER: () => sweep(980, 420, 0.07, 0.24, 'TRIANGLE'),
    HIT: () => sweep(190, 75, 0.08, 0.32, 'SQUARE'),
    CRIT: () => arpeggio([520, 780, 1040], 0.045, 0.34, 'SQUARE'),
    DASH: () => sweep(260, 620, 0.12, 0.28, 'NOISE'),
    SKILL: () => arpeggio([330, 440, 660, 880], 0.055, 0.35, 'TRIANGLE'),
    PICKUP: () => arpeggio([660, 990], 0.045, 0.22, 'SINE'),
    CHEST: () => arpeggio([440, 554, 659, 880], 0.07, 0.32, 'TRIANGLE'),
    LEVEL_UP: () => arpeggio([440, 554, 659, 880, 1108], 0.08, 0.38, 'TRIANGLE'),
    PLAYER_HURT: () => sweep(150, 55, 0.18, 0.42, 'SQUARE'),
    BOSS_PHASE: () => arpeggio([180, 160, 140, 220], 0.12, 0.44, 'SQUARE'),
    VICTORY: () => arpeggio([523.25, 659.25, 783.99, 1046.5], 0.11, 0.38, 'TRIANGLE'),
    GAME_OVER: () => arpeggio([392, 349, 311, 261], 0.14, 0.36, 'SINE'),
    UI_CLICK: () => sweep(720, 520, 0.04, 0.18, 'SINE'),
  };

  class SoundEngine {
    constructor(muted) {
      this.muted = muted;
      this.ctx = null;
      this.buffers = {};
      this.lastPlayed = {};
      this.voices = 0;
      this.master = null;
    }

    /** Muss nach einer Nutzerinteraktion aufgerufen werden (Browser-Autoplay-Regeln). */
    unlock() {
      try {
        if (!this.ctx) {
          const AC = window.AudioContext || window.webkitAudioContext;
          if (!AC) return;
          this.ctx = new AC();
          this.master = this.ctx.createGain();
          this.master.gain.value = 0.9;
          this.master.connect(this.ctx.destination);
          for (const [id, fn] of Object.entries(DEFS)) {
            const data = fn();
            const buf = this.ctx.createBuffer(1, data.length, RATE);
            buf.getChannelData(0).set(data);
            this.buffers[id] = buf;
          }
        }
        if (this.ctx.state === 'suspended') this.ctx.resume();
      } catch (e) {
        this.ctx = null; // Kein Audio verfügbar – Spiel läuft weiter
      }
    }

    toggleMute() { this.muted = !this.muted; }
    isMuted() { return this.muted; }

    play(id) {
      if (this.muted || !this.ctx || this.ctx.state !== 'running') return;
      const now = performance.now();
      if (this.lastPlayed[id] && now - this.lastPlayed[id] < 40) return;
      if (this.voices >= MAX_VOICES) return;
      const buf = this.buffers[id];
      if (!buf) return;
      this.lastPlayed[id] = now;
      try {
        const src = this.ctx.createBufferSource();
        src.buffer = buf;
        src.connect(this.master);
        this.voices++;
        src.onended = () => { this.voices--; };
        src.start();
      } catch (e) { /* ignorieren */ }
    }

    onEvent(ev) {
      switch (ev.type) {
        case 'AttackPerformed':
          this.play(ev.classId === 'VANGUARD' ? 'SLASH' : ev.classId === 'ARCANIST' ? 'MAGIC_BOLT' : 'DAGGER');
          if (ev.critical) this.play('CRIT');
          break;
        case 'EnemyKilled': this.play('HIT'); break;
        case 'PlayerHurt': this.play('PLAYER_HURT'); break;
        case 'DashPerformed': this.play('DASH'); break;
        case 'SkillCast': this.play('SKILL'); break;
        case 'PickupCollected': this.play('PICKUP'); break;
        case 'LevelUp': this.play('LEVEL_UP'); break;
        case 'ChestOpened': this.play('CHEST'); break;
        case 'FloorChanged': this.play('SKILL'); break;
        case 'BossPhaseChanged': this.play('BOSS_PHASE'); break;
        case 'RunEnded': this.play(ev.victory ? 'VICTORY' : 'GAME_OVER'); break;
        case 'UiClick': this.play('UI_CLICK'); break;
      }
    }
  }
  CH.SoundEngine = SoundEngine;
})();
