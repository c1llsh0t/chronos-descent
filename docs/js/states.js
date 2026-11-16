/*
 * Zustandsmaschine (State Pattern) mit allen Bildschirmen:
 * Intro, Hauptmenü, Spiel, Pause, Upgrade-Auswahl, Run-Ende, Bestenliste, Hilfe.
 */
'use strict';
(function () {
  const CH = window.CH;
  const { rgb, Color, Config, UiHelper: U, rect } = CH;
  const W = Config.LOGICAL_WIDTH, H = Config.LOGICAL_HEIGHT;
  const I = () => CH.I18n;
  const B1 = CH.Input.BUTTON1, B3 = CH.Input.BUTTON3;

  class StateManager {
    constructor() { this.stack = []; }
    push(s) { this.stack.push(s); if (s.onEnter) s.onEnter(); }
    pop() { const s = this.stack.pop(); if (s && s.onExit) s.onExit(); }
    replaceAll(s) { while (this.stack.length) this.pop(); this.push(s); }
    peek() { return this.stack[this.stack.length - 1] || null; }
    belowTop() { return this.stack.length >= 2 ? this.stack[this.stack.length - 2] : null; }
    update(dt, input) { const t = this.peek(); if (t) t.update(dt, input); }
    render(g, input) { const t = this.peek(); if (t) t.render(g, input); }
  }
  CH.StateManager = StateManager;

  function bgGradient(g, c0, c1) {
    g.setPaint(g.linearGradient(0, 0, c0, 0, H, c1));
    g.fillRect(0, 0, W, H);
  }

  // -------------------------------------------------------------------------
  // Intro mit Credits
  // -------------------------------------------------------------------------
  class IntroState {
    constructor(sm) { this.sm = sm; this.time = 0; this.finished = false; }
    static get DEVELOPER() { return 'K Mujkanovic'; }
    static get PUBLISHER() { return 'c1llsh0t'; }
    update(dt, input) {
      if (this.finished) return;
      this.time += dt;
      const FADE_IN = 0.8, HOLD = 2.6, FADE_OUT = 0.7;
      if (this.time > 0.25 && input.wasAnyInputPressed() && this.time < FADE_IN + HOLD) this.time = FADE_IN + HOLD;
      if (this.time >= FADE_IN + HOLD + FADE_OUT) {
        this.finished = true;
        input.clearAllInputs();
        this.sm.pop();
      }
    }
    alpha() {
      const FADE_IN = 0.8, HOLD = 2.6, FADE_OUT = 0.7;
      const sm = (t) => { t = CH.clamp(t, 0, 1); return t * t * (3 - 2 * t); };
      if (this.time < FADE_IN) return sm(this.time / FADE_IN);
      if (this.time < FADE_IN + HOLD) return 1;
      return sm(1 - (this.time - FADE_IN - HOLD) / FADE_OUT);
    }
    render(g) {
      const cx = W / 2;
      bgGradient(g, rgb(6, 7, 14), rgb(14, 9, 24));
      g.setAlpha(this.alpha());
      const pulse = 0.5 + 0.5 * Math.sin(this.time * 2.4), rr = 64;
      g.setColor(rgb(85, 200, 255, 40 + Math.trunc(40 * pulse)));
      g.fillOval(cx - rr - 18, 190 - rr - 18, (rr + 18) * 2, (rr + 18) * 2);
      g.setStroke(3); g.setColor(rgb(120, 220, 255));
      g.drawOval(cx - rr, 190 - rr, rr * 2, rr * 2);
      const hand = this.time * 1.6 - Math.PI / 2;
      g.drawLine(cx, 190, cx + Math.cos(hand) * rr * 0.75, 190 + Math.sin(hand) * rr * 0.75);
      g.fillOval(cx - 5, 185, 10, 10);
      U.drawCentered(g, 'CHRONOS', cx, 330, 56, true, rgb(235, 242, 255));
      U.drawCentered(g, "AETHELGARD'S DESCENT", cx, 366, 18, true, rgb(126, 204, 255));
      g.setColor(rgb(90, 110, 150)); g.setStroke(1); g.drawLine(cx - 160, 410, cx + 160, 410);
      U.drawCentered(g, I().t('intro.developed_by'), cx, 455, 16, false, rgb(160, 175, 205));
      U.drawCentered(g, IntroState.DEVELOPER, cx, 487, 26, true, Color.WHITE);
      U.drawCentered(g, I().t('intro.published_by'), cx, 540, 16, false, rgb(160, 175, 205));
      U.drawCentered(g, IntroState.PUBLISHER, cx, 572, 26, true, rgb(255, 214, 92));
      U.drawCentered(g, I().t('intro.skip'), cx, 670, 12, false, rgb(110, 122, 150));
      g.setAlpha(1);
    }
  }
  CH.IntroState = IntroState;

  // -------------------------------------------------------------------------
  // Hauptmenü
  // -------------------------------------------------------------------------
  class MenuState {
    constructor(sm, ctx) {
      this.sm = sm; this.ctx = ctx;
      this.selectedClass = CH.HeroClass.VANGUARD;
      this.selectedDifficulty = CH.Difficulty.NORMAL;
      this.seedText = '';
      this.seedFocused = false;
      this.animTime = 0;
      this.classCards = [rect(120, 210, 320, 175), rect(480, 210, 320, 175), rect(840, 210, 320, 175)];
      this.diffButtons = [rect(120, 425, 160, 40), rect(292, 425, 160, 40), rect(464, 425, 160, 40)];
      this.seedBox = rect(690, 425, 470, 40);
      this.startBtn = rect(460, 505, 360, 50);
      this.highscoreBtn = rect(130, 575, 240, 42);
      this.statsBtn = rect(382, 575, 170, 42);
      this.helpBtn = rect(564, 575, 230, 42);
      this.langBtn = rect(806, 575, 170, 42);
      this.fullscreenBtn = rect(988, 575, 162, 42);
    }
    click() { this.ctx.eventBus.publish('UiClick'); }

    update(dt, input) {
      this.animTime += dt;
      if (input.wasMousePressed(B1)) this.seedFocused = U.isHovered(this.seedBox, input);
      if (this.seedFocused) {
        let ch;
        while ((ch = input.pollTypedChar()) !== null) {
          if (ch === '\b') this.seedText = this.seedText.slice(0, -1);
          else if (ch === '\n' || ch === '\r') this.seedFocused = false;
          else if (/^[\p{L}\p{N}_-]$/u.test(ch) && this.seedText.length < 20) this.seedText += ch;
        }
        if (input.wasKeyPressed('Escape')) this.seedFocused = false;
        return;
      }
      input.clearTypedChars();

      if (input.wasKeyPressed('Digit1')) { this.selectedClass = CH.HeroClass.VANGUARD; this.click(); }
      if (input.wasKeyPressed('Digit2')) { this.selectedClass = CH.HeroClass.ARCANIST; this.click(); }
      if (input.wasKeyPressed('Digit3')) { this.selectedClass = CH.HeroClass.SHADOWBLADE; this.click(); }
      if (input.wasKeyPressed('KeyL')) this.toggleLanguage();
      if (input.wasKeyPressed('KeyM')) this.toggleMute();
      if (input.wasKeyPressed('F11')) this.ctx.toggleFullscreen();

      CH.HERO_CLASSES.forEach((hc, i) => { if (U.isClicked(this.classCards[i], input)) { this.selectedClass = hc; this.click(); } });
      CH.DIFFICULTIES.forEach((d, i) => { if (U.isClicked(this.diffButtons[i], input)) { this.selectedDifficulty = d; this.click(); } });

      if (U.isClicked(this.startBtn, input) || input.wasKeyPressed('Enter')) this.startNewRun();
      else if (U.isClicked(this.highscoreBtn, input)) { this.click(); this.sm.push(new HighscoreState(this.sm, this.ctx)); }
      else if (U.isClicked(this.statsBtn, input)) { this.click(); this.sm.push(new StatsState(this.sm, this.ctx)); }
      else if (U.isClicked(this.helpBtn, input)) { this.click(); this.sm.push(new HelpState(this.sm, this.ctx)); }
      else if (U.isClicked(this.langBtn, input)) this.toggleLanguage();
      else if (U.isClicked(this.fullscreenBtn, input)) { this.click(); this.ctx.toggleFullscreen(); }
    }

    toggleLanguage() { I().toggle(); this.ctx.saveManager.setLanguage(I().lang); this.click(); }
    toggleMute() { this.ctx.sound.toggleMute(); this.ctx.saveManager.setMuted(this.ctx.sound.isMuted()); }

    startNewRun() {
      const seed = CH.SeededRandom.parseSeed(this.seedText);
      this.click();
      const s = new CH.GameSession(this.selectedClass, this.selectedDifficulty, seed, this.ctx.eventBus, this.ctx.saveManager);
      this.sm.push(new PlayingState(this.sm, this.ctx, s));
    }

    render(g, input) {
      bgGradient(g, rgb(11, 13, 24), rgb(20, 14, 34));
      for (let i = 0; i < 36; i++) {
        const px = ((i * 197.3 + this.animTime * (12 + (i % 5) * 6)) % (W + 40)) - 20;
        const py = (((i * 113.7 - this.animTime * (16 + (i % 4) * 5)) % H) + H) % H;
        g.setColor(rgb(120, 175, 255, 35 + (i % 4) * 20));
        g.fillOval(px, py, 4, 4);
      }
      U.drawCentered(g, 'CHRONOS', W / 2, 78, 54, true, rgb(235, 242, 255));
      U.drawCentered(g, "AETHELGARD'S DESCENT  •  " + I().t('subtitle'), W / 2, 112, 16, true, rgb(126, 204, 255));

      g.setFont(15, true); g.setColor(rgb(195, 208, 232)); g.drawString(I().t('menu.select_class'), 120, 192);
      CH.HERO_CLASSES.forEach((hc, i) => this.classCard(g, this.classCards[i], hc, i + 1, hc === this.selectedClass, U.isHovered(this.classCards[i], input)));

      g.setFont(14, true); g.setColor(rgb(195, 208, 232)); g.drawString(I().t('menu.difficulty'), 120, 412);
      CH.DIFFICULTIES.forEach((d, i) => {
        const r = this.diffButtons[i], active = d === this.selectedDifficulty, hov = U.isHovered(r, input);
        g.setColor(active ? rgb(48, 72, 118) : (hov ? rgb(32, 38, 60) : rgb(20, 24, 38)));
        g.fillRoundRect(r.x, r.y, r.w, r.h, 8);
        g.setColor(active ? rgb(110, 215, 255) : rgb(75, 92, 128)); g.setStroke(active ? 2.2 : 1.3);
        g.drawRoundRect(r.x, r.y, r.w, r.h, 8);
        U.drawCentered(g, I().t('diff.' + d.name), r.x + r.w / 2, r.y + 25, 14, true, active ? Color.WHITE : rgb(188, 200, 224));
      });

      const sb = this.seedBox;
      g.setFont(14, true); g.setColor(rgb(195, 208, 232)); g.drawString(I().t('menu.seed'), sb.x, 412);
      g.setColor(this.seedFocused ? rgb(32, 42, 68) : rgb(20, 24, 38)); g.fillRoundRect(sb.x, sb.y, sb.w, sb.h, 8);
      g.setColor(this.seedFocused ? rgb(110, 225, 255) : rgb(75, 92, 128)); g.setStroke(this.seedFocused ? 2.2 : 1.3);
      g.drawRoundRect(sb.x, sb.y, sb.w, sb.h, 8);
      g.setFont(15, true, true);
      const cursor = (this.seedFocused && Math.trunc(this.animTime * 2) % 2 === 0) ? '_' : '';
      if (!this.seedText) {
        g.setColor(rgb(118, 130, 158)); g.drawString(this.seedFocused ? cursor : I().t('menu.seed_hint'), sb.x + 14, sb.y + 25);
      } else {
        g.setColor(Color.WHITE); g.drawString(this.seedText + cursor, sb.x + 14, sb.y + 25);
      }

      U.drawButton(g, this.startBtn, I().t('menu.start'), U.isHovered(this.startBtn, input), this.selectedClass.accentColor);
      U.drawButton(g, this.highscoreBtn, I().t('menu.highscores'), U.isHovered(this.highscoreBtn, input), rgb(255, 206, 78));
      U.drawButton(g, this.statsBtn, I().t('menu.stats'), U.isHovered(this.statsBtn, input), rgb(255, 150, 90));
      U.drawButton(g, this.helpBtn, I().t('menu.help'), U.isHovered(this.helpBtn, input), rgb(120, 210, 255));
      U.drawButton(g, this.langBtn, I().t('menu.language'), U.isHovered(this.langBtn, input), rgb(145, 235, 180));
      U.drawButton(g, this.fullscreenBtn, I().t('menu.fullscreen'), U.isHovered(this.fullscreenBtn, input), rgb(255, 105, 115));
      U.drawCentered(g, I().t('menu.hint'), W / 2, 665, 13, false, rgb(148, 162, 192));
      U.drawCentered(g, `v${Config.VERSION}  •  K Mujkanovic  •  c1llsh0t`, W / 2, 700, 11, false, rgb(90, 100, 128));
    }

    classCard(g, r, hc, num, selected, hovered) {
      g.setColor(selected ? rgb(28, 36, 62) : (hovered ? rgb(24, 28, 48) : rgb(16, 20, 34)));
      g.fillRoundRect(r.x, r.y, r.w, r.h, 12);
      g.setColor(selected ? hc.accentColor : rgb(68, 84, 120)); g.setStroke(selected ? 2.6 : 1.4);
      g.drawRoundRect(r.x, r.y, r.w, r.h, 12);
      g.setColor(hc.primaryColor); g.fillOval(r.x + 16, r.y + 18, 28, 28);
      g.setColor(hc.accentColor); g.setStroke(1.4); g.drawOval(r.x + 16, r.y + 18, 28, 28);
      g.setFont(17, true); g.setColor(Color.WHITE); g.drawString(`[${num}] ${hc.displayName()}`, r.x + 54, r.y + 34);
      g.setFont(12, true); g.setColor(hc.accentColor); g.drawString(hc.role(), r.x + 16, r.y + 62);
      g.setFont(12, false); g.setColor(rgb(195, 206, 228));
      let ly = r.y + 84;
      for (const line of U.wrapText(g, hc.description(), r.w - 32)) { g.drawString(line, r.x + 16, ly); ly += 17; }
      g.setFont(12, true, true); g.setColor(rgb(165, 220, 255));
      g.drawString(`HP:${Math.trunc(hc.baseHp)}  ATK:${Math.trunc(hc.baseDamage)}  SPD:${Math.trunc(hc.baseSpeed)}  CRIT:${Math.round(hc.baseCritChance * 100)}%`, r.x + 16, r.y + r.h - 14);
    }
  }
  CH.MenuState = MenuState;

  // -------------------------------------------------------------------------
  // Spielzustand
  // -------------------------------------------------------------------------
  class PlayingState {
    constructor(sm, ctx, session) {
      this.sm = sm; this.ctx = ctx; this.session = session;
      this.world = new CH.WorldRenderer();
      this.hud = new CH.HudRenderer();
      this.time = 0;
      this.showFullMap = false;
    }
    update(dt, input) {
      this.time += dt;
      if (input.wasKeyPressed('Escape') || input.wasKeyPressed('KeyP')) { this.sm.push(new PauseState(this.sm, this.ctx, this.session)); return; }
      if (input.wasKeyPressed('KeyM')) { this.ctx.sound.toggleMute(); this.ctx.saveManager.setMuted(this.ctx.sound.isMuted()); }
      if (input.wasKeyPressed('F11')) this.ctx.toggleFullscreen();
      if (input.wasKeyPressed('Tab')) this.showFullMap = !this.showFullMap;

      let mx = 0, my = 0;
      if (input.isKeyDown('KeyW') || input.isKeyDown('ArrowUp')) my -= 1;
      if (input.isKeyDown('KeyS') || input.isKeyDown('ArrowDown')) my += 1;
      if (input.isKeyDown('KeyA') || input.isKeyDown('ArrowLeft')) mx -= 1;
      if (input.isKeyDown('KeyD') || input.isKeyDown('ArrowRight')) mx += 1;

      const s = this.session;
      s.update(dt, mx, my, input.mouseX, input.mouseY,
        input.isMouseDown(B1) || input.isKeyDown('KeyJ'),
        input.wasKeyPressed('Space') || input.wasKeyPressed('Shift'),
        input.wasKeyPressed('KeyQ') || input.wasMousePressed(B3),
        input.wasKeyPressed('KeyE'),
        input.wasKeyPressed('KeyF'));

      if (s.runFinished) { this.sm.replaceAll(new RunEndState(this.sm, this.ctx, s)); return; }
      if (s.player.pendingLevelUps > 0) {
        s.player.pendingLevelUps--;
        this.sm.push(new UpgradeDraftState(this.sm, this.ctx, s, false));
      } else if (s.pendingAltarUpgrade) {
        s.pendingAltarUpgrade = false;
        this.sm.push(new UpgradeDraftState(this.sm, this.ctx, s, true));
      }
    }
    render(g, input) {
      this.world.render(g, this.session, input.mouseX, input.mouseY, this.time);
      this.hud.render(g, this.session, this.showFullMap, this.ctx.sound.isMuted());
    }
  }
  CH.PlayingState = PlayingState;

  function freshRun(sm, ctx, old) {
    old.commitStats(old.runFinished ? (old.victory ? 'WIN' : 'DEATH') : 'ABANDON');
    const s = new CH.GameSession(old.player.heroClass, old.difficulty, CH.SeededRandom.parseSeed(''), ctx.eventBus, ctx.saveManager);
    sm.replaceAll(new MenuState(sm, ctx));
    sm.push(new PlayingState(sm, ctx, s));
  }

  // -------------------------------------------------------------------------
  class PauseState {
    constructor(sm, ctx, session) {
      this.sm = sm; this.ctx = ctx; this.session = session;
      this.resumeBtn = rect(470, 320, 340, 46);
      this.restartBtn = rect(470, 382, 340, 46);
      this.menuBtn = rect(470, 444, 340, 46);
    }
    update(dt, input) {
      if (input.wasKeyPressed('Escape') || input.wasKeyPressed('KeyP') || U.isClicked(this.resumeBtn, input)) {
        this.ctx.eventBus.publish('UiClick'); this.sm.pop();
      } else if (U.isClicked(this.restartBtn, input)) {
        this.ctx.eventBus.publish('UiClick'); freshRun(this.sm, this.ctx, this.session);
      } else if (U.isClicked(this.menuBtn, input)) {
        this.ctx.eventBus.publish('UiClick');
        this.session.commitStats('ABANDON');
        this.sm.replaceAll(new MenuState(this.sm, this.ctx));
      }
    }
    render(g, input) {
      const below = this.sm.belowTop();
      if (below) below.render(g, input);
      g.setColor(rgb(8, 10, 18, 195)); g.fillRect(0, 0, W, H);
      g.setColor(rgb(18, 22, 36, 245)); g.fillRoundRect(390, 140, 500, 390, 16);
      g.setColor(rgb(95, 130, 195)); g.setStroke(1); g.drawRoundRect(390, 140, 500, 390, 16);
      U.drawCentered(g, I().t('pause.title'), W / 2, 195, 30, true, Color.WHITE);
      const s = this.session, p = s.player;
      U.drawCentered(g, `${I().t('hud.floor')} ${s.currentFloor}  |  ${I().t('hud.level')} ${p.level}  |  Kills: ${s.totalKills}  |  DMG: ${Math.round(s.totalDamageDealt)}`,
        W / 2, 245, 13, false, rgb(175, 200, 235));
      U.drawCentered(g, `Seed: ${s.seed}`, W / 2, 268, 13, false, rgb(135, 215, 255));
      U.drawButton(g, this.resumeBtn, I().t('pause.resume'), U.isHovered(this.resumeBtn, input), rgb(100, 225, 255));
      U.drawButton(g, this.restartBtn, I().t('pause.restart'), U.isHovered(this.restartBtn, input), rgb(255, 205, 80));
      U.drawButton(g, this.menuBtn, I().t('pause.menu'), U.isHovered(this.menuBtn, input), rgb(255, 105, 115));
    }
  }

  // -------------------------------------------------------------------------
  class UpgradeDraftState {
    constructor(sm, ctx, session, fromAltar) {
      this.sm = sm; this.ctx = ctx; this.session = session; this.fromAltar = fromAltar;
      this.choices = CH.UpgradePool.rollChoices(session.getRandom(), 3);
      this.cards = [rect(170, 210, 280, 320), rect(500, 210, 280, 320), rect(830, 210, 280, 320)];
      this.age = 0;
    }
    update(dt, input) {
      this.age += dt;
      if (this.age < 0.2) return; // verhindert versehentliche Wahl durch gehaltene Angriffs-Klicks
      if (input.wasKeyPressed('Digit1')) return this.choose(0);
      if (input.wasKeyPressed('Digit2')) return this.choose(1);
      if (input.wasKeyPressed('Digit3')) return this.choose(2);
      for (let i = 0; i < this.choices.length; i++) if (U.isClicked(this.cards[i], input)) return this.choose(i);
    }
    choose(i) {
      if (i < 0 || i >= this.choices.length) return;
      this.session.applyChosenUpgrade(this.choices[i]);
      this.ctx.eventBus.publish('UiClick');
      this.sm.pop();
    }
    render(g, input) {
      const below = this.sm.belowTop();
      if (below) below.render(g, input);
      g.setColor(rgb(6, 8, 16, 185)); g.fillRect(0, 0, W, H);
      U.drawCentered(g, this.fromAltar ? I().t('draft.altar_title') : I().t('draft.title'), W / 2, 130, 28, true, rgb(255, 224, 110));
      U.drawCentered(g, I().t('draft.hint'), W / 2, 165, 15, false, rgb(195, 210, 235));
      this.choices.forEach((u, i) => {
        const r = this.cards[i], hov = U.isHovered(r, input), col = u.rarity.color;
        g.setColor(hov ? rgb(34, 42, 68) : rgb(20, 24, 40)); g.fillRoundRect(r.x, r.y, r.w, r.h, 16);
        g.setColor(col); g.setStroke(hov ? 3.2 : 2); g.drawRoundRect(r.x, r.y, r.w, r.h, 16);
        g.setColor(rgb(col.r, col.g, col.b, 40)); g.fillRoundRect(r.x + 16, r.y + 18, r.w - 32, 28, 8);
        U.drawCentered(g, I().t('rarity.' + u.rarity.id).toUpperCase(), r.x + r.w / 2, r.y + 37, 12, true, col);
        U.drawCentered(g, u.name(), r.x + r.w / 2, r.y + 92, 20, true, Color.WHITE);
        g.setFont(14, false);
        let ly = r.y + 145;
        for (const line of U.wrapText(g, u.description(), r.w - 40)) {
          U.drawCentered(g, line, r.x + r.w / 2, ly, 14, false, rgb(205, 216, 238));
          ly += 22;
        }
        const cur = this.session.player.getUpgradeCount(u.id);
        if (cur > 0) U.drawCentered(g, `Stack: ${cur} -> ${cur + 1}`, r.x + r.w / 2, r.y + r.h - 52, 13, true, rgb(120, 230, 175));
        U.drawCentered(g, `[${i + 1}]`, r.x + r.w / 2, r.y + r.h - 20, 16, true, col);
      });
    }
  }

  // -------------------------------------------------------------------------
  class RunEndState {
    constructor(sm, ctx, session) {
      this.sm = sm; this.ctx = ctx; this.session = session;
      this.retryBtn = rect(380, 540, 240, 48);
      this.menuBtn = rect(660, 540, 240, 48);
      this.age = 0;
    }
    update(dt, input) {
      this.age += dt;
      if (this.age < 0.4) return;
      if (U.isClicked(this.retryBtn, input) || input.wasKeyPressed('Enter')) {
        this.ctx.eventBus.publish('UiClick'); freshRun(this.sm, this.ctx, this.session);
      } else if (U.isClicked(this.menuBtn, input) || input.wasKeyPressed('Escape')) {
        this.ctx.eventBus.publish('UiClick'); this.sm.replaceAll(new MenuState(this.sm, this.ctx));
      }
    }
    render(g, input) {
      const s = this.session, v = s.victory;
      bgGradient(g, v ? rgb(14, 28, 42) : rgb(32, 12, 20), rgb(10, 11, 18));
      U.drawCentered(g, v ? I().t('end.victory') : I().t('end.gameover'), W / 2, 120, 34, true, v ? rgb(255, 218, 88) : rgb(255, 92, 102));
      const bx = 340, by = 175, bw = 600, bh = 325;
      g.setColor(rgb(18, 22, 36, 235)); g.fillRoundRect(bx, by, bw, bh, 16);
      g.setColor(v ? rgb(255, 208, 82) : rgb(220, 75, 90)); g.setStroke(1); g.drawRoundRect(bx, by, bw, bh, 16);
      const rows = [
        [I().t('hud.score'), String(s.player.score), rgb(255, 220, 95)],
        [I().t('end.floor_reached'), `${s.currentFloor} / ${Config.MAX_FLOORS}`, Color.WHITE],
        [I().t('end.kills'), String(s.totalKills), Color.WHITE],
        [I().t('end.damage'), String(Math.round(s.totalDamageDealt)), Color.WHITE],
        [I().t('end.max_combo'), s.player.maxCombo + 'x', rgb(255, 155, 80)],
        [I().t('end.duration'), CH.formatTime(s.runDurationSeconds), Color.WHITE],
        [I().t('hud.seed'), String(s.seed), rgb(135, 215, 255)],
      ];
      rows.forEach(([label, value, col], i) => {
        const x = bx + 42, y = by + 50 + i * 40, w = bw - 84;
        g.setFont(16, false); g.setColor(rgb(180, 195, 220)); g.drawString(label, x, y);
        g.setFont(17, true); g.setColor(col); g.drawString(value, x + w - g.stringWidth(value), y);
      });
      U.drawButton(g, this.retryBtn, I().t('end.play_again'), U.isHovered(this.retryBtn, input), rgb(100, 225, 255));
      U.drawButton(g, this.menuBtn, I().t('end.menu'), U.isHovered(this.menuBtn, input), rgb(200, 212, 235));
    }
  }

  // -------------------------------------------------------------------------
  class HighscoreState {
    constructor(sm, ctx) { this.sm = sm; this.ctx = ctx; this.backBtn = rect(510, 642, 260, 44); }
    update(dt, input) {
      if (input.wasKeyPressed('Escape') || U.isClicked(this.backBtn, input)) { this.ctx.eventBus.publish('UiClick'); this.sm.pop(); }
    }
    render(g, input) {
      bgGradient(g, rgb(11, 13, 24), rgb(18, 16, 32));
      U.drawCentered(g, I().t('hs.title'), W / 2, 62, 30, true, rgb(255, 214, 92));
      const lx = 48, ly = 96, lw = 720, lh = 515;
      g.setColor(rgb(18, 22, 36, 230)); g.fillRoundRect(lx, ly, lw, lh, 14);
      g.setColor(rgb(80, 102, 148)); g.setStroke(1); g.drawRoundRect(lx, ly, lw, lh, 14);
      const pad = (v, n) => String(v).padEnd(n, ' ');
      g.setFont(13, true, true); g.setColor(rgb(145, 200, 255));
      g.drawString(`${pad('#', 3)} ${pad('SCORE', 7)} ${pad('CLASS', 12)} ${pad('DIFF', 10)} ${pad('FLR', 5)} ${pad('KILLS', 6)} ${pad('TIME', 6)} ${pad('RES', 6)}`, lx + 20, ly + 34);
      const scores = this.ctx.saveManager.highscores;
      if (!scores.length) {
        U.drawCentered(g, I().t('hs.empty'), lx + lw / 2, ly + 250, 16, false, rgb(155, 168, 195));
      } else {
        g.setFont(13, false, true);
        scores.forEach((e, i) => {
          g.setColor(i === 0 ? rgb(255, 220, 105) : Color.WHITE);
          g.drawString(`${pad(i + 1, 3)} ${pad(e.score, 7)} ${pad(e.heroClass, 12)} ${pad(e.difficulty, 10)} ${pad(e.floorReached, 5)} ${pad(e.kills, 6)} ${pad(CH.formatTime(e.durationSeconds), 6)} ${pad(e.victory ? 'WIN' : 'RIP', 6)}`,
            lx + 20, ly + 68 + i * 42);
        });
      }
      const rx = 796, ry = 96, rw = 436, rh = 515;
      g.setColor(rgb(18, 22, 36, 230)); g.fillRoundRect(rx, ry, rw, rh, 14);
      g.setColor(rgb(80, 102, 148)); g.drawRoundRect(rx, ry, rw, rh, 14);
      g.setFont(16, true); g.setColor(rgb(255, 214, 92));
      g.drawString(`${I().t('hs.achievements')} (${this.ctx.saveManager.achievements.size}/${CH.ACHIEVEMENTS.length})`, rx + 20, ry + 32);
      let ay = ry + 52;
      for (const a of CH.ACHIEVEMENTS) {
        const un = this.ctx.saveManager.isAchievementUnlocked(a.id);
        g.setColor(un ? rgb(36, 46, 70) : rgb(18, 20, 30)); g.fillRoundRect(rx + 16, ay, rw - 32, 48, 8);
        g.setColor(un ? rgb(255, 205, 72) : rgb(56, 64, 88)); g.drawRoundRect(rx + 16, ay, rw - 32, 48, 8);
        g.setFont(13, true); g.setColor(un ? rgb(255, 225, 115) : rgb(120, 130, 155));
        g.drawString((un ? '[✓] ' : '[ ] ') + a.title(), rx + 28, ay + 20);
        g.setFont(11, false); g.setColor(un ? rgb(205, 216, 236) : rgb(95, 104, 126));
        g.drawString(a.description(), rx + 28, ay + 38);
        ay += 56;
      }
      U.drawButton(g, this.backBtn, I().t('hs.back'), U.isHovered(this.backBtn, input), rgb(115, 215, 255));
    }
  }

  // -------------------------------------------------------------------------
  // Gesamtstatistik: alles, was man über alle Runs gemacht und geschafft hat
  // -------------------------------------------------------------------------
  class StatsState {
    constructor(sm, ctx) { this.sm = sm; this.ctx = ctx; this.backBtn = rect(510, 652, 260, 44); }
    update(dt, input) {
      if (input.wasKeyPressed('Escape') || U.isClicked(this.backBtn, input)) { this.ctx.eventBus.publish('UiClick'); this.sm.pop(); }
    }
    num(v) { return Math.round(v).toLocaleString(I().isDe() ? 'de-CH' : 'en-US'); }
    duration(sec) {
      sec = Math.round(sec);
      const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
      return h > 0 ? `${h} h ${CH.pad2(m)} min` : `${CH.pad2(m)}:${CH.pad2(s)}`;
    }
    panelFrame(g, x, y, w, h, title) {
      g.setColor(rgb(18, 22, 36, 230)); g.fillRoundRect(x, y, w, h, 12);
      g.setColor(rgb(80, 102, 148)); g.setStroke(1); g.drawRoundRect(x, y, w, h, 12);
      g.setFont(15, true); g.setColor(rgb(255, 214, 92)); g.drawString(title, x + 16, y + 25);
    }
    /** Zweispaltige Tabelle: Bezeichnung | Wert */
    keyValues(g, x, y, w, title, rows) {
      const h = 40 + rows.length * 22;
      this.panelFrame(g, x, y, w, h, title);
      rows.forEach(([label, value, col], i) => {
        const ry = y + 50 + i * 22;
        if (i % 2 === 0) { g.setColor(rgb(255, 255, 255, 8)); g.fillRect(x + 8, ry - 15, w - 16, 22); }
        g.setFont(13, false); g.setColor(rgb(180, 195, 220)); g.drawString(label, x + 16, ry);
        g.setFont(13, true); g.setColor(col || Color.WHITE); g.drawString(value, x + w - 16 - g.stringWidth(value), ry);
      });
      return y + h;
    }
    /** Tabelle mit Kopfzeile und rechtsbündigen Zahlenspalten */
    table(g, x, y, w, title, headers, colRight, rows) {
      const h = 40 + 22 + rows.length * 22;
      this.panelFrame(g, x, y, w, h, title);
      const col = (j) => (j === 0 ? x + 16 : x + colRight[j - 1]);
      g.setFont(12, true); g.setColor(rgb(145, 200, 255));
      headers.forEach((t, j) => g.drawString(t, j === 0 ? col(0) : col(j) - g.stringWidth(t), y + 50));
      g.setColor(rgb(80, 102, 148)); g.drawLine(x + 12, y + 56, x + w - 12, y + 56);
      rows.forEach((r, i) => {
        const ry = y + 74 + i * 22;
        if (i % 2 === 0) { g.setColor(rgb(255, 255, 255, 8)); g.fillRect(x + 8, ry - 15, w - 16, 22); }
        r.cells.forEach((t, j) => {
          g.setFont(13, j === 0); g.setColor(j === 0 ? (r.color || Color.WHITE) : rgb(220, 228, 245));
          g.drawString(t, j === 0 ? col(0) : col(j) - g.stringWidth(t), ry);
        });
      });
      return y + h;
    }
    render(g, input) {
      bgGradient(g, rgb(11, 13, 24), rgb(18, 16, 32));
      U.drawCentered(g, I().t('stats.title'), W / 2, 48, 28, true, rgb(255, 150, 90));
      const st = this.ctx.saveManager.stats, save = this.ctx.saveManager;
      const pct = (a, b) => (b > 0 ? Math.round(a / b * 100) + ' %' : '–');
      const dash = (v, f) => (v > 0 ? f(v) : '–');
      if (st.runs === 0) U.drawCentered(g, I().t('stats.empty'), W / 2, 74, 13, false, rgb(155, 168, 195));

      const top = 84, colW = 390;
      const x1 = 40, x2 = 445, x3 = 850;

      // Spalte 1: Überblick & Rekorde
      let y = this.keyValues(g, x1, top, colW, I().t('stats.overview'), [
        [I().t('stats.runs'), this.num(st.runs)],
        [I().t('stats.wins'), this.num(st.wins), rgb(255, 218, 88)],
        [I().t('stats.deaths'), this.num(st.deaths), rgb(255, 110, 120)],
        [I().t('stats.abandoned'), this.num(st.abandoned)],
        [I().t('stats.winrate'), pct(st.wins, st.wins + st.deaths)],
        [I().t('stats.playtime'), this.duration(st.playTime)],
        [I().t('stats.achievements'), `${save.achievements.size} / ${CH.ACHIEVEMENTS.length}`, rgb(255, 205, 72)],
      ]);
      this.keyValues(g, x1, y + 14, colW, I().t('stats.records'), [
        [I().t('stats.best_score'), dash(st.bestScore, v => this.num(v)), rgb(255, 220, 95)],
        [I().t('stats.best_level'), dash(st.highestLevel, String)],
        [I().t('stats.best_floor'), dash(st.highestFloor, v => `${v} / ${Config.MAX_FLOORS}`)],
        [I().t('stats.best_combo'), dash(st.maxCombo, v => v + 'x'), rgb(255, 155, 80)],
        [I().t('stats.fastest'), dash(st.fastestWin, v => CH.formatTime(v)), rgb(135, 215, 255)],
      ]);

      // Spalte 2: Kampf & Beute
      y = this.keyValues(g, x2, top, colW, I().t('stats.combat'), [
        [I().t('stats.kills'), this.num(st.kills)],
        [I().t('stats.bosskills'), this.num(st.bossKills), rgb(255, 90, 120)],
        [I().t('stats.dmg_dealt'), this.num(st.damageDealt)],
        [I().t('stats.dmg_taken'), this.num(st.damageTaken), rgb(255, 120, 120)],
        [I().t('stats.hits_taken'), this.num(st.hitsTaken)],
        [I().t('stats.attacks'), this.num(st.attacks)],
        [I().t('stats.crits'), `${this.num(st.crits)} (${pct(st.crits, st.attacks)})`, rgb(255, 224, 96)],
        [I().t('stats.dashes'), this.num(st.dashes)],
        [I().t('stats.skills'), `${this.num(st.skillsQ)} / ${this.num(st.skillsE)}`],
      ]);
      this.keyValues(g, x2, y + 14, colW, I().t('stats.loot'), [
        [I().t('stats.gold'), this.num(st.goldCollected), rgb(255, 212, 70)],
        [I().t('stats.chests'), this.num(st.chestsOpened)],
        [I().t('stats.shrines'), `${this.num(st.shrinesUsed)} / ${this.num(st.altarsUsed)}`],
        [I().t('stats.levelups'), this.num(st.levelUps)],
        [I().t('stats.upgrades'), this.num(st.upgradesChosen)],
        [I().t('stats.potions'), `${this.num(st.potions)} / ${this.num(st.shieldOrbs)}`],
        [I().t('stats.portals'), this.num(st.portalsUsed)],
      ]);

      // Spalte 3: Gegner, Klassen, Schwierigkeit
      const enemyIds = ['SLIME', 'ELDER_SLIME', 'SKELETON_ARCHER', 'BERSERKER', 'NECROMANCER', 'BOSS_MALAKOR'];
      y = this.table(g, x3, top, colW, I().t('stats.enemies'), [I().t('stats.col_enemy'), I().t('stats.col_kills')], [colW - 16],
        enemyIds.map(id => ({ cells: [I().t('enemy.' + id), this.num(st.killsByType[id] || 0)], color: CH.EnemyType[id].color })));
      y = this.table(g, x3, y + 14, colW, I().t('stats.classes'),
        [I().t('stats.col_class'), I().t('stats.col_runs'), I().t('stats.col_wins'), I().t('stats.col_best'), I().t('stats.col_floor')], [190, 245, 320, colW - 16],
        CH.HERO_CLASSES.map(hc => {
          const c = st.perClass[hc.id] || { runs: 0, wins: 0, bestScore: 0, bestFloor: 0 };
          return { cells: [hc.displayName(), this.num(c.runs), this.num(c.wins), dash(c.bestScore, v => this.num(v)), dash(c.bestFloor, String)], color: hc.accentColor };
        }));
      this.table(g, x3, y + 14, colW, I().t('stats.difficulties'),
        [I().t('stats.col_diff'), I().t('stats.col_runs'), I().t('stats.col_wins'), I().t('stats.col_best')], [190, 260, colW - 16],
        CH.DIFFICULTIES.map(d => {
          const c = st.perDifficulty[d.name] || { runs: 0, wins: 0, bestScore: 0 };
          return { cells: [I().t('diff.' + d.name), this.num(c.runs), this.num(c.wins), dash(c.bestScore, v => this.num(v))] };
        }));

      U.drawButton(g, this.backBtn, I().t('hs.back'), U.isHovered(this.backBtn, input), rgb(255, 150, 90));
    }
  }
  CH.StatsState = StatsState;

  // -------------------------------------------------------------------------
  const HELP = {
    DE: {
      left: ['Steuerung & Spielablauf', [
        'W / A / S / D oder Pfeiltasten : Bewegung',
        'Maus bewegen                    : Zielen',
        'Linksklick oder [J]             : Primärangriff (gedrückt halten)',
        'Leertaste oder Shift            : Dash (Unverwundbarkeit & Trail)',
        '[Q] oder Rechtsklick            : Klassen-Fähigkeit 1',
        '[E]                             : Klassen-Ultimate',
        '[F]                             : Interagieren (Truhe, Schrein, Portal)',
        '[TAB]                           : Große Dungeon-Karte umschalten',
        '[ESC] / [P]                     : Pause-Menü',
        '[M]                             : Audio stummschalten / aktivieren',
        '[L]                             : Sprache Deutsch / Englisch (Menü)',
        '[F11]                           : Vollbild umschalten',
        '',
        'Ziel: Kämpfe dich durch 3 prozedurale Ebenen,',
        'sammle XP für Roguelite-Upgrades und besiege',
        'auf Ebene 3 den Chronos-Wächter Malakor!']],
      right: ['Informatik-Konzepte & Architektur', [
        '1. BSP-Tree & Prims MST:',
        '   Rekursive Raumteilung + Minimaler Spannbaum + Rundwege.',
        '2. Doppel-BFS Graphdurchmesser:',
        '   Start- und Bossraum stets am maximalen Abstand.',
        '3. A*-Pfadfindung (Binär-Heap + Oktil-Heuristik):',
        '   Intelligente Gegnernavigation ohne Corner-Cutting.',
        '4. Recursive Shadowcasting & Bresenham LoS:',
        '   Echtzeit-Fog-of-War über 8 Oktanten & Sichtprüfung.',
        '5. Entwurfsmuster (Design Patterns):',
        '   • State Pattern (Stack-basierte Zustandsmaschine)',
        '   • Strategy Pattern (Klassen- & Gegner-KI-Verhalten)',
        '   • Observer Pattern (GameEventBus)',
        '6. Prozeduraler Audio-Synthesizer (Web Audio API):',
        '   Alle Effekte werden berechnet, keine Audiodateien.',
        '7. Java-kompatibler Zufall: gleicher Seed = gleicher',
        '   Dungeon wie in der Java-Desktop-Version.']],
    },
    EN: {
      left: ['Controls & Gameplay Loop', [
        'W / A / S / D or Arrow Keys     : Move',
        'Mouse Move                      : Aim',
        'Left Click or [J]               : Primary Attack (hold to repeat)',
        'Space or Shift                  : Dash (Invulnerability frames)',
        '[Q] or Right Click              : Class Skill 1',
        '[E]                             : Class Ultimate',
        '[F]                             : Interact (Chest, Shrine, Portal)',
        '[TAB]                           : Toggle Full Map Overlay',
        '[ESC] / [P]                     : Pause Menu',
        '[M]                             : Mute / Unmute Audio',
        '[L]                             : Toggle German / English (menu)',
        '[F11]                           : Toggle Fullscreen',
        '',
        'Goal: Descend through 3 procedural floors,',
        'draft Roguelite upgrades on level-up, and defeat',
        'Malakor, Warden of Chronos on Floor 3!']],
      right: ['CS Algorithms & Architecture', [
        "1. BSP-Tree & Prim's MST:",
        '   Recursive space partitioning + MST + tactical loops.',
        '2. Double-BFS Graph Diameter:',
        '   Places Start and Boss rooms at maximum graph distance.',
        '3. A* Pathfinding (Binary Heap + Octile Heuristic):',
        '   Smart obstacle navigation with corner-cut prevention.',
        '4. Recursive Shadowcasting & Bresenham LoS:',
        '   Real-time 8-octant Fog-of-War & line-of-sight.',
        '5. Software Design Patterns:',
        '   • State Pattern (Stack-based game state machine)',
        '   • Strategy Pattern (Modular AI & hero classes)',
        '   • Observer Pattern (GameEventBus)',
        '6. Procedural Audio Synthesizer (Web Audio API):',
        '   Every effect is computed, zero audio files.',
        '7. Java-compatible RNG: same seed = same dungeon',
        '   as the Java desktop edition.']],
    },
  };

  class HelpState {
    constructor(sm, ctx) { this.sm = sm; this.ctx = ctx; this.backBtn = rect(510, 642, 260, 44); }
    update(dt, input) {
      if (input.wasKeyPressed('Escape') || U.isClicked(this.backBtn, input)) { this.ctx.eventBus.publish('UiClick'); this.sm.pop(); }
    }
    panel(g, x, y, w, h, title, lines) {
      g.setColor(rgb(18, 22, 36, 230)); g.fillRoundRect(x, y, w, h, 14);
      g.setColor(rgb(80, 102, 148)); g.setStroke(1); g.drawRoundRect(x, y, w, h, 14);
      g.setFont(17, true); g.setColor(rgb(255, 214, 92)); g.drawString(title, x + 22, y + 34);
      g.setFont(12, false, true); g.setColor(rgb(208, 218, 240));
      lines.forEach((l, i) => g.drawString(l, x + 22, y + 66 + i * 26));
    }
    render(g, input) {
      bgGradient(g, rgb(11, 13, 24), rgb(18, 16, 32));
      U.drawCentered(g, I().t('help.title'), W / 2, 58, 26, true, rgb(120, 218, 255));
      const t = HELP[I().lang];
      this.panel(g, 60, 95, 560, 520, t.left[0], t.left[1]);
      this.panel(g, 660, 95, 560, 520, t.right[0], t.right[1]);
      U.drawButton(g, this.backBtn, I().t('help.back'), U.isHovered(this.backBtn, input), rgb(115, 215, 255));
    }
  }
})();
