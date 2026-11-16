/*
 * Spielobjekte: Heldenklassen, Gegnertypen, Upgrades, Erfolge, Statuseffekte,
 * Spieler, Gegner, Projektile, Beute, interaktive Objekte und Gegner-KI.
 */
'use strict';
(function () {
  const CH = window.CH;
  const { Vec, rgb, Config } = CH;

  // -------------------------------------------------------------------------
  // Statische Daten
  // -------------------------------------------------------------------------
  const HeroClass = {};
  function hero(id, baseHp, baseSpeed, baseDamage, attackCooldown, baseCritChance, baseMaxShield, skillQCooldown, skillECooldown, primary, accent) {
    HeroClass[id] = {
      id, baseHp, baseSpeed, baseDamage, attackCooldown, baseCritChance, baseMaxShield, skillQCooldown, skillECooldown,
      primaryColor: primary, accentColor: accent,
      displayName() { return CH.I18n.t('class.' + id + '.name'); },
      role() { return CH.I18n.t('class.' + id + '.role'); },
      description() { return CH.I18n.t('class.' + id + '.desc'); },
    };
  }
  hero('VANGUARD', 135, 210, 26, 0.34, 0.14, 30, 4.5, 11.0, rgb(72, 168, 255), rgb(190, 232, 255));
  hero('ARCANIST', 95, 222, 21, 0.30, 0.16, 10, 4.0, 12.0, rgb(174, 96, 255), rgb(102, 240, 255));
  hero('SHADOWBLADE', 105, 252, 16, 0.19, 0.28, 0, 3.5, 10.0, rgb(58, 228, 168), rgb(210, 255, 120));
  CH.HeroClass = HeroClass;
  CH.HERO_CLASSES = [HeroClass.VANGUARD, HeroClass.ARCANIST, HeroClass.SHADOWBLADE];

  const EnemyType = {};
  function etype(id, baseHp, baseSpeed, contactDamage, radius, xpReward, scoreReward, color, isBoss) {
    EnemyType[id] = { id, baseHp, baseSpeed, contactDamage, radius, xpReward, scoreReward, color, isBoss };
  }
  etype('SLIME', 36, 108, 12, 14, 14, 15, rgb(86, 224, 118), false);
  etype('ELDER_SLIME', 76, 86, 17, 20, 24, 35, rgb(46, 185, 140), false);
  etype('SKELETON_ARCHER', 44, 105, 14, 14, 22, 28, rgb(224, 218, 195), false);
  etype('BERSERKER', 88, 96, 22, 18, 30, 42, rgb(235, 88, 68), false);
  etype('NECROMANCER', 68, 84, 16, 16, 36, 55, rgb(178, 84, 255), false);
  etype('BOSS_MALAKOR', 520, 102, 24, 28, 150, 500, rgb(255, 58, 98), true);
  CH.EnemyType = EnemyType;

  const Rarity = {
    COMMON: { id: 'COMMON', color: rgb(176, 196, 222), weight: 55 },
    RARE: { id: 'RARE', color: rgb(86, 180, 255), weight: 30 },
    EPIC: { id: 'EPIC', color: rgb(188, 108, 255), weight: 12 },
    LEGENDARY: { id: 'LEGENDARY', color: rgb(255, 196, 62), weight: 5 },
  };
  CH.Rarity = Rarity;

  function upg(id, rarity, nameDe, nameEn, descDe, descEn) {
    return {
      id, rarity,
      name() { return CH.I18n.isDe() ? nameDe : nameEn; },
      description() { return CH.I18n.isDe() ? descDe : descEn; },
    };
  }
  const ALL_UPGRADES = [
    upg('POWER_STRIKE', Rarity.COMMON, 'Klingenstärke', 'Sharpened Edge', '+20% Grundschaden auf alle Angriffe.', '+20% base damage on all attacks.'),
    upg('SWIFT_STEP', Rarity.COMMON, 'Windstiefel', 'Zephyr Boots', '+12% Bewegungstempo & -15% Dash-Abklingzeit.', '+12% move speed & -15% dash cooldown.'),
    upg('VITAL_HEART', Rarity.COMMON, 'Titanenherz', 'Titanic Heart', '+30 Maximale HP und sofort +30 HP Heilung.', '+30 Max HP and heal +30 HP immediately.'),
    upg('RAPID_HANDS', Rarity.COMMON, 'Flinke Reflexe', 'Rapid Reflexes', '+18% Angriffsgeschwindigkeit.', '+18% attack speed.'),
    upg('AEGIS_WARD', Rarity.COMMON, 'Ägis-Barriere', 'Aegis Barrier', '+25 Maximaler Schild & schnellere Regeneration.', '+25 Max Shield & faster shield recharge.'),
    upg('CRIT_FOCUS', Rarity.RARE, 'Scharfer Fokus', 'Keen Precision', '+12% Kritische Trefferchance & +25% Krit-Schaden.', '+12% Crit Chance & +25% Crit Damage.'),
    upg('VAMPIRIC', Rarity.RARE, 'Blutpakt', 'Vampiric Pact', 'Heilt +4 HP bei jedem besiegten Gegner.', 'Heal +4 HP on every enemy kill.'),
    upg('CHRONOS_HASTE', Rarity.RARE, 'Chronos-Echo', 'Chronos Echo', '-22% Abklingzeit für [Q] und [E] Fähigkeiten.', '-22% cooldown for [Q] and [E] skills.'),
    upg('MULTISHOT', Rarity.EPIC, 'Doppelschlag', 'Twin Echo', '+1 zusätzliches Projektil bzw. +25% Nahkampf-Reichweite.', '+1 extra projectile or +25% melee reach.'),
    upg('CHAIN_LIGHTNING', Rarity.EPIC, 'Kettenblitz', 'Chain Lightning', 'Treffer springen mit Blitzschaden auf nahe Gegner über.', 'Hits arc lightning damage to nearby foes.'),
    upg('FROST_AURA', Rarity.RARE, 'Frostkranz', 'Frostbite Aura', 'Verlangsamt und verletzt Gegner in unmittelbarer Nähe.', 'Slows and damages enemies close to you.'),
    upg('RICOCHET', Rarity.RARE, 'Querschläger', 'Ricochet Rune', 'Projektile prallen von Wänden ab und durchschlagen +1 Ziel.', 'Projectiles bounce off walls and pierce +1 foe.'),
    upg('EXPLOSIVE_IMPACT', Rarity.EPIC, 'Arkane Detonation', 'Arcane Detonation', 'Kritische Treffer erzeugen eine Explosionswelle.', 'Critical hits trigger an AoE blast wave.'),
    upg('EXECUTIONER', Rarity.RARE, 'Henkersmal', "Executioner's Mark", '+40% Schaden gegen Gegner unter 40% HP.', '+40% damage against enemies below 40% HP.'),
    upg('CHRONO_OVERDRIVE', Rarity.LEGENDARY, 'Zeitüberladung', 'Chrono Overdrive', '+30% Schaden, +20% Tempo und +15% Krit-Chance!', '+30% damage, +20% speed and +15% crit chance!'),
    upg('PHOENIX_SOUL', Rarity.LEGENDARY, 'Phönixfunke', 'Phoenix Spark', 'Entzündet alle Angriffe, +40 Max HP & volle Heilung.', 'Ignites all attacks, +40 Max HP & full heal.'),
  ];
  CH.UpgradePool = {
    all: ALL_UPGRADES,
    find(id) { return ALL_UPGRADES.find(u => u.id === id) || null; },
    rollChoices(rng, count) {
      const pool = ALL_UPGRADES.slice();
      const picked = [];
      while (picked.length < count && pool.length) {
        let total = 0;
        for (const u of pool) total += u.rarity.weight;
        const roll = rng.nextInt(total);
        let acc = 0, idx = 0;
        for (let i = 0; i < pool.length; i++) {
          acc += pool[i].rarity.weight;
          if (roll < acc) { idx = i; break; }
        }
        picked.push(pool.splice(idx, 1)[0]);
      }
      return picked;
    },
  };

  function ach(id, tDe, tEn, dDe, dEn) {
    return { id, title() { return CH.I18n.isDe() ? tDe : tEn; }, description() { return CH.I18n.isDe() ? dDe : dEn; } };
  }
  CH.ACHIEVEMENTS = [
    ach('FIRST_BLOOD', 'Erstes Blut', 'First Blood', 'Besiege deinen ersten Gegner im Labyrinth.', 'Defeat your first enemy in the labyrinth.'),
    ach('COMBO_10', 'Kampfrausch', 'Battle Flow', 'Erreiche eine 10er-Kombo.', 'Reach a 10x kill combo.'),
    ach('LEVEL_5', 'Meisteranwärter', 'Rising Master', 'Erreiche Heldenstufe 5 in einem Run.', 'Reach hero level 5 in a single run.'),
    ach('TREASURE_HUNTER', 'Schatzsucher', 'Treasure Hunter', 'Öffne 3 Schatztruhen in einem Run.', 'Open 3 treasure chests in one run.'),
    ach('DEEP_DELVER', 'Tiefengänger', 'Deep Delver', 'Erreiche Ebene 3 des Chronos-Gewölbes.', 'Reach Floor 3 of the Chronos Vault.'),
    ach('BOSS_SLAYER', 'Chronos-Bezwinger', 'Warden Slayer', 'Besiege Malakor, den Chronos-Wächter.', 'Defeat Malakor, Warden of Chronos.'),
    ach('SURVIVOR_HARD', 'Eiserner Wille', 'Iron Will', 'Gewinne einen Run auf Schwer oder Alptraum.', 'Win a run on Hard or Nightmare difficulty.'),
    ach('SPEEDRUNNER', 'Blitzlauf', 'Chrono Blitz', 'Gewinne einen Run in unter 6 Minuten.', 'Win a run in under 6 minutes.'),
  ];
  CH.findAchievement = (id) => CH.ACHIEVEMENTS.find(a => a.id === id) || null;

  // -------------------------------------------------------------------------
  // Kleine Objekte
  // -------------------------------------------------------------------------
  class FloatingText {
    constructor(x, y, text, color, fontSize, maxLife) {
      this.x = x; this.y = y; this.vy = -38; this.text = text; this.color = color;
      this.fontSize = fontSize; this.life = maxLife; this.maxLife = maxLife;
    }
    update(dt) { this.y += this.vy * dt; this.vy *= Math.pow(0.25, dt); this.life -= dt; return this.life > 0; }
    alpha() { return CH.clamp(this.life / this.maxLife, 0, 1); }
  }
  CH.FloatingText = FloatingText;

  class HazardZone {
    constructor(x, y, radius, delay, damage, hurtsPlayer, color) {
      this.x = x; this.y = y; this.radius = radius; this.totalDelay = delay; this.remainingDelay = delay;
      this.damage = damage; this.hurtsPlayer = hurtsPlayer; this.color = color; this.detonated = false;
    }
    progress() { return 1 - CH.clamp(this.remainingDelay / this.totalDelay, 0, 1); }
  }
  CH.HazardZone = HazardZone;

  class Pickup {
    constructor(x, y, kind, value, vx, vy) {
      this.x = x; this.y = y; this.kind = kind; this.value = value; this.vx = vx; this.vy = vy;
      this.animTime = 0; this.active = true;
    }
  }
  CH.Pickup = Pickup;

  class InteractiveProp {
    constructor(x, y, type) { this.x = x; this.y = y; this.type = type; this.used = false; this.pulseTime = 0; }
  }
  CH.InteractiveProp = InteractiveProp;

  class StatusEffect {
    constructor(type, duration, magnitude) {
      this.type = type; this.remainingSeconds = duration; this.magnitude = magnitude; this.tickAccumulator = 0;
    }
    refresh(duration, magnitude) {
      this.remainingSeconds = Math.max(this.remainingSeconds, duration);
      this.magnitude = Math.max(this.magnitude, magnitude);
    }
  }
  const STATUS_ORDER = ['BURN', 'POISON', 'SLOW', 'STUN', 'FRENZY']; // Reihenfolge wie Javas EnumMap

  // -------------------------------------------------------------------------
  // Basisklasse
  // -------------------------------------------------------------------------
  class Entity {
    constructor(x, y, radius, maxHp) {
      this.x = x; this.y = y; this.vx = 0; this.vy = 0; this.knockVx = 0; this.knockVy = 0;
      this.radius = radius; this.hp = maxHp; this.maxHp = maxHp; this.active = true;
      this.facingAngle = 0; this.hitFlashTimer = 0; this.animTime = 0;
      this.statusEffects = new Map();
    }
    pos() { return new Vec(this.x, this.y); }
    applyKnockback(kx, ky) { this.knockVx += kx; this.knockVy += ky; }
    applyStatus(type, duration, magnitude) {
      const ex = this.statusEffects.get(type);
      if (ex) ex.refresh(duration, magnitude);
      else this.statusEffects.set(type, new StatusEffect(type, duration, magnitude));
    }
    hasStatus(type) { return this.statusEffects.has(type); }
    getSpeedMultiplierFromStatus() {
      if (this.statusEffects.has('STUN')) return 0;
      let m = 1;
      const slow = this.statusEffects.get('SLOW');
      if (slow) m *= Math.max(0.35, 1 - slow.magnitude);
      const fr = this.statusEffects.get('FRENZY');
      if (fr) m *= (1 + fr.magnitude);
      return m;
    }
    updateBaseTimers(dt, world) {
      this.animTime += dt;
      if (this.hitFlashTimer > 0) this.hitFlashTimer = Math.max(0, this.hitFlashTimer - dt);
      const decay = Math.exp(-9 * dt);
      this.knockVx *= decay; this.knockVy *= decay;
      for (const type of STATUS_ORDER) {
        const eff = this.statusEffects.get(type);
        if (!eff) continue;
        eff.remainingSeconds -= dt;
        if (type === 'BURN' || type === 'POISON') {
          eff.tickAccumulator += dt;
          if (eff.tickAccumulator >= 0.5) {
            eff.tickAccumulator -= 0.5;
            this.onStatusDotTick(type, eff.magnitude, world);
          }
        }
        if (eff.remainingSeconds <= 0) this.statusEffects.delete(type);
      }
    }
    onStatusDotTick() {}
  }
  CH.Entity = Entity;

  // -------------------------------------------------------------------------
  // Spieler
  // -------------------------------------------------------------------------

  class Player extends Entity {
    constructor(x, y, heroClass) {
      super(x, y, 15, heroClass.baseHp);
      this.heroClass = heroClass;
      this.maxShield = heroClass.baseMaxShield;
      this.shield = this.maxShield;
      this.level = 1; this.xp = 0; this.xpToNext = 55; this.gold = 0; this.score = 0;
      this.combo = 0; this.maxCombo = 0; this.comboTimer = 0; this.pendingLevelUps = 0;
      this.attackTimer = 0; this.dashTimer = 0; this.dashCooldownTimer = 0;
      this.skillQTimer = 0; this.skillETimer = 0; this.invulnTimer = 0; this.shieldRegenDelay = 0;
      this.frostAuraTick = 0; this.dashDirX = 0; this.dashDirY = 0; this.afterimageTick = 0;
      this.upgradeStacks = new Map(); // Einfügereihenfolge wie LinkedHashMap
    }

    getUpgradeCount(id) { return this.upgradeStacks.get(id) || 0; }

    applyUpgrade(u) {
      this.upgradeStacks.set(u.id, this.getUpgradeCount(u.id) + 1);
      if (u.id === 'VITAL_HEART') { this.maxHp += 30; this.heal(30); }
      else if (u.id === 'AEGIS_WARD') { this.maxShield += 25; this.shield = Math.min(this.maxShield, this.shield + 25); }
      else if (u.id === 'PHOENIX_SOUL') { this.maxHp += 40; this.hp = this.maxHp; }
    }

    heal(a) { this.hp = Math.min(this.maxHp, this.hp + a); }

    addXp(amount) {
      this.xp += amount;
      let leveled = false;
      while (this.xp >= this.xpToNext) {
        this.xp -= this.xpToNext;
        this.level++;
        this.xpToNext = Math.round(this.xpToNext * 1.32 + 25);
        this.maxHp += 8; this.heal(18);
        this.pendingLevelUps++;
        leveled = true;
      }
      return leveled;
    }

    getEffectiveSpeed() {
      const mult = 1 + 0.12 * this.getUpgradeCount('SWIFT_STEP') + 0.20 * this.getUpgradeCount('CHRONO_OVERDRIVE');
      // FRENZY-Tempobonus steckt bereits in getSpeedMultiplierFromStatus()
      return this.heroClass.baseSpeed * mult * this.getSpeedMultiplierFromStatus();
    }
    getEffectiveDamage() {
      return this.heroClass.baseDamage * (1 + 0.20 * this.getUpgradeCount('POWER_STRIKE') + 0.30 * this.getUpgradeCount('CHRONO_OVERDRIVE'));
    }
    getEffectiveAttackCooldown() {
      let bonus = 1 + 0.18 * this.getUpgradeCount('RAPID_HANDS');
      if (this.hasStatus('FRENZY')) bonus += 0.45;
      return Math.max(0.08, this.heroClass.attackCooldown / bonus);
    }
    getEffectiveCritChance() {
      return Math.min(0.85, this.heroClass.baseCritChance + 0.12 * this.getUpgradeCount('CRIT_FOCUS') + 0.15 * this.getUpgradeCount('CHRONO_OVERDRIVE'));
    }
    getCritMultiplier() { return 1.75 + 0.25 * this.getUpgradeCount('CRIT_FOCUS'); }
    getCooldownScale() { return Math.pow(0.78, this.getUpgradeCount('CHRONOS_HASTE')); }
    getDashCooldownMax() { return Config.DASH_COOLDOWN * Math.pow(0.85, this.getUpgradeCount('SWIFT_STEP')); }
    isDashing() { return this.dashTimer > 0; }
    isInvulnerable() { return this.invulnTimer > 0 || this.isDashing(); }

    update(dt, moveX, moveY, aimAngle, world) {
      this.updateBaseTimers(dt, world);
      this.facingAngle = aimAngle;
      if (this.attackTimer > 0) this.attackTimer = Math.max(0, this.attackTimer - dt);
      if (this.dashCooldownTimer > 0) this.dashCooldownTimer = Math.max(0, this.dashCooldownTimer - dt);
      if (this.skillQTimer > 0) this.skillQTimer = Math.max(0, this.skillQTimer - dt);
      if (this.skillETimer > 0) this.skillETimer = Math.max(0, this.skillETimer - dt);
      if (this.invulnTimer > 0) this.invulnTimer = Math.max(0, this.invulnTimer - dt);

      if (this.combo > 0) {
        this.comboTimer -= dt;
        if (this.comboTimer <= 0) this.combo = 0;
      }

      if (this.shieldRegenDelay > 0) this.shieldRegenDelay -= dt;
      else if (this.shield < this.maxShield) {
        const rate = 4.5 + 3.0 * this.getUpgradeCount('AEGIS_WARD');
        this.shield = Math.min(this.maxShield, this.shield + rate * dt);
      }

      const frost = this.getUpgradeCount('FROST_AURA');
      if (frost > 0) {
        this.frostAuraTick += dt;
        if (this.frostAuraTick >= 0.5) {
          this.frostAuraTick = 0;
          for (const e of world.getEnemies().slice()) {
            if (!e.active) continue;
            if (this.pos().distanceTo(e.pos()) <= 115) {
              e.applyStatus('SLOW', 1.2, 0.35);
              world.damageEnemy(e, 4 * frost, false, 0, 0);
            }
          }
        }
      }

      let stepX, stepY;
      if (this.dashTimer > 0) {
        this.dashTimer -= dt;
        const ds = this.getEffectiveSpeed() * Config.DASH_SPEED_MULTIPLIER;
        stepX = this.dashDirX * ds * dt;
        stepY = this.dashDirY * ds * dt;
        this.afterimageTick -= dt;
        if (this.afterimageTick <= 0) {
          this.afterimageTick = 0.032;
          world.getParticles().spawnAfterimage(this.x, this.y, this.radius, this.heroClass.primaryColor);
        }
      } else {
        let mv = new Vec(moveX, moveY);
        if (mv.lengthSquared() > 1e-6) mv = mv.normalize();
        const sp = this.getEffectiveSpeed();
        this.vx = mv.x * sp; this.vy = mv.y * sp;
        stepX = (this.vx + this.knockVx) * dt;
        stepY = (this.vy + this.knockVy) * dt;
      }
      const np = world.getMap().moveWithCollision(this.x, this.y, stepX, stepY, this.radius);
      this.x = np.x; this.y = np.y;
    }

    tryDash(moveX, moveY, world) {
      if (this.dashCooldownTimer > 0 || this.dashTimer > 0) return false;
      const dir = (Math.abs(moveX) > 0.01 || Math.abs(moveY) > 0.01) ? new Vec(moveX, moveY).normalize() : Vec.fromAngle(this.facingAngle, 1);
      this.dashDirX = dir.x; this.dashDirY = dir.y;
      this.dashTimer = Config.DASH_DURATION;
      this.dashCooldownTimer = this.getDashCooldownMax();
      this.afterimageTick = 0;
      if (this.heroClass.id === 'SHADOWBLADE') {
        world.spawnHazardZone(new HazardZone(this.x, this.y, 68, 0.28, this.getEffectiveDamage() * 1.4, false, this.heroClass.primaryColor));
      } else if (this.heroClass.id === 'ARCANIST') {
        world.getParticles().spawnShockwave(this.x, this.y, 52, 0.22, this.heroClass.accentColor);
      }
      world.getParticles().spawnBurst(this.x, this.y, 10, 40, 140, 4, 0.25, this.heroClass.accentColor, true);
      world.publishEvent('DashPerformed', { x: this.x, y: this.y });
      return true;
    }

    tryPrimaryAttack(world) {
      if (this.attackTimer > 0) return false;
      this.attackTimer = this.getEffectiveAttackCooldown();
      const crit = world.getRandom().nextDouble() < this.getEffectiveCritChance();
      const dmg = this.getEffectiveDamage() * (crit ? this.getCritMultiplier() : 1);
      const extra = this.getUpgradeCount('MULTISHOT');
      if (this.heroClass.id === 'VANGUARD') this.vanguardSlash(dmg, crit, extra, world);
      else if (this.heroClass.id === 'ARCANIST') this.arcanistBolts(dmg, crit, extra, world);
      else this.shadowDaggers(dmg, crit, extra, world);
      world.publishEvent('AttackPerformed', { classId: this.heroClass.id, critical: crit });
      return true;
    }

    vanguardSlash(dmg, crit, extra, world) {
      const reach = 82 * (1 + 0.22 * extra);
      const halfArc = 68 * Math.PI / 180;
      world.getParticles().spawnSlash(this.x, this.y, this.facingAngle, 136, reach * 0.88, crit ? rgb(255, 224, 96) : this.heroClass.accentColor);
      const facing = Vec.fromAngle(this.facingAngle, 1);
      for (const e of world.getEnemies().slice()) {
        if (!e.active) continue;
        const to = new Vec(e.x - this.x, e.y - this.y);
        const dist = to.length();
        if (dist <= reach + e.radius) {
          const dir = to.normalize();
          const angleDiff = Math.acos(CH.clamp(facing.dot(dir), -1, 1));
          if (dist < this.radius + e.radius + 12 || angleDiff <= halfArc) {
            if (world.getMap().hasLineOfSight(this.x, this.y, e.x, e.y)) {
              const fd = this.applyExecutioner(dmg, e);
              world.damageEnemy(e, fd, crit, dir.x * 260, dir.y * 260);
              this.applyWeaponOnHitEffects(e, fd, crit, world);
            }
          }
        }
      }
    }

    arcanistBolts(dmg, crit, extra, world) {
      const count = 1 + extra;
      const spread = 11 * Math.PI / 180;
      const start = this.facingAngle - spread * (count - 1) / 2;
      for (let i = 0; i < count; i++) {
        const a = start + i * spread;
        const v = Vec.fromAngle(a, 540);
        const p = new Projectile(this.x + Math.cos(a) * 18, this.y + Math.sin(a) * 18, v.x, v.y, 7, dmg, true, 1.5, this.heroClass.accentColor);
        p.crit = crit;
        p.homing = true;
        p.bouncesLeft = this.getUpgradeCount('RICOCHET');
        p.pierceLeft = this.getUpgradeCount('RICOCHET');
        p.explosive = crit && this.getUpgradeCount('EXPLOSIVE_IMPACT') > 0;
        if (this.getUpgradeCount('PHOENIX_SOUL') > 0) { p.applyStatus = 'BURN'; p.statusDuration = 3; p.statusMagnitude = 7; }
        world.spawnProjectile(p);
      }
    }

    shadowDaggers(dmg, crit, extra, world) {
      const count = 1 + extra;
      const spread = 8 * Math.PI / 180;
      const start = this.facingAngle - spread * (count - 1) / 2;
      for (let i = 0; i < count; i++) {
        const a = start + i * spread;
        const v = Vec.fromAngle(a, 690);
        const p = new Projectile(this.x + Math.cos(a) * 16, this.y + Math.sin(a) * 16, v.x, v.y, 5.5, dmg, true, 1.05, this.heroClass.accentColor);
        p.crit = crit;
        p.bouncesLeft = this.getUpgradeCount('RICOCHET');
        p.pierceLeft = 1 + this.getUpgradeCount('RICOCHET');
        p.explosive = crit && this.getUpgradeCount('EXPLOSIVE_IMPACT') > 0;
        p.applyStatus = 'POISON'; p.statusDuration = 2.5; p.statusMagnitude = 4.5;
        world.spawnProjectile(p);
      }
    }

    trySkillQ(world) {
      if (this.skillQTimer > 0) return false;
      this.skillQTimer = this.heroClass.skillQCooldown * this.getCooldownScale();
      const base = this.getEffectiveDamage();
      const parts = world.getParticles();
      if (this.heroClass.id === 'VANGUARD') {
        const radius = 128;
        parts.spawnShockwave(this.x, this.y, radius, 0.28, this.heroClass.primaryColor);
        parts.spawnSlash(this.x, this.y, 0, 355, radius * 0.85, this.heroClass.accentColor);
        world.getCamera().addShake(0.32);
        for (const e of world.getEnemies().slice()) {
          if (!e.active) continue;
          if (this.pos().distanceTo(e.pos()) <= radius + e.radius) {
            const dir = new Vec(e.x - this.x, e.y - this.y).normalize();
            world.damageEnemy(e, base * 1.9, true, dir.x * 360, dir.y * 360);
            e.applyStatus('STUN', 0.85, 1);
          }
        }
      } else if (this.heroClass.id === 'ARCANIST') {
        const radius = 155;
        parts.spawnShockwave(this.x, this.y, radius, 0.35, rgb(110, 235, 255));
        parts.spawnBurst(this.x, this.y, 26, 70, 240, 5, 0.45, rgb(150, 245, 255), true);
        world.getCamera().addShake(0.28);
        for (const e of world.getEnemies().slice()) {
          if (!e.active) continue;
          if (this.pos().distanceTo(e.pos()) <= radius + e.radius) {
            const dir = new Vec(e.x - this.x, e.y - this.y).normalize();
            world.damageEnemy(e, base * 1.65, false, dir.x * 220, dir.y * 220);
            e.applyStatus('SLOW', 3.2, 0.55);
          }
        }
      } else {
        const blades = 9, cone = 135 * Math.PI / 180;
        const start = this.facingAngle - cone / 2;
        for (let i = 0; i < blades; i++) {
          const a = start + cone * i / (blades - 1);
          const v = Vec.fromAngle(a, 660);
          const p = new Projectile(this.x, this.y, v.x, v.y, 5.5, base * 1.25, true, 0.9, this.heroClass.accentColor);
          p.pierceLeft = 2;
          p.applyStatus = 'POISON'; p.statusDuration = 3; p.statusMagnitude = 5;
          world.spawnProjectile(p);
        }
        parts.spawnDirectionalSpark(this.x, this.y, this.facingAngle, cone * 0.5, 14, 220, this.heroClass.accentColor);
      }
      world.publishEvent('SkillCast', { slot: 1 });
      return true;
    }

    trySkillE(aimWorld, world) {
      if (this.skillETimer > 0) return false;
      this.skillETimer = this.heroClass.skillECooldown * this.getCooldownScale();
      const base = this.getEffectiveDamage();
      const parts = world.getParticles();
      if (this.heroClass.id === 'VANGUARD') {
        this.invulnTimer = Math.max(this.invulnTimer, 1.6);
        this.shield = Math.min(this.maxShield + 40, this.shield + 40);
        parts.spawnShockwave(this.x, this.y, 145, 0.4, rgb(255, 220, 100));
        world.addFloatingText(new FloatingText(this.x, this.y - 28, 'AEGIS!', rgb(255, 225, 110), 16, 0.9));
        for (const e of world.getEnemies().slice()) {
          if (e.active && this.pos().distanceTo(e.pos()) <= 145) {
            const dir = new Vec(e.x - this.x, e.y - this.y).normalize();
            world.damageEnemy(e, base * 2.1, true, dir.x * 420, dir.y * 420);
          }
        }
      } else if (this.heroClass.id === 'ARCANIST') {
        for (let i = 0; i < 5; i++) {
          const ox = i === 0 ? 0 : world.getRandom().nextDouble(-78, 78);
          const oy = i === 0 ? 0 : world.getRandom().nextDouble(-78, 78);
          world.spawnHazardZone(new HazardZone(aimWorld.x + ox, aimWorld.y + oy, 68, 0.32 + i * 0.10, base * 2.2, false, rgb(255, 118, 66)));
        }
        world.getCamera().addShake(0.35);
      } else {
        this.applyStatus('FRENZY', 5, 0.45);
        this.dashCooldownTimer = 0;
        this.skillQTimer = 0;
        parts.spawnShockwave(this.x, this.y, 90, 0.25, this.heroClass.primaryColor);
        world.addFloatingText(new FloatingText(this.x, this.y - 28, 'FRENZY!', this.heroClass.accentColor, 16, 0.9));
      }
      world.publishEvent('SkillCast', { slot: 2 });
      return true;
    }

    applyWeaponOnHitEffects(hit, dealt, crit, world) {
      if (this.getUpgradeCount('PHOENIX_SOUL') > 0) hit.applyStatus('BURN', 3, 7);
      const chain = this.getUpgradeCount('CHAIN_LIGHTNING');
      if (chain > 0) {
        let closest = null, best = 185 * 185;
        for (const o of world.getEnemies()) {
          if (!o.active || o === hit) continue;
          const d2 = hit.pos().distanceSquaredTo(o.pos());
          if (d2 < best) { best = d2; closest = o; }
        }
        if (closest) {
          world.getParticles().spawnLightning(hit.x, hit.y, closest.x, closest.y, rgb(115, 225, 255));
          world.damageEnemy(closest, dealt * (0.45 + 0.15 * chain), false, 0, 0);
        }
      }
      if (crit && this.getUpgradeCount('EXPLOSIVE_IMPACT') > 0) this.triggerExplosion(hit.x, hit.y, dealt * 0.55, world);
    }

    triggerExplosion(ex, ey, dmg, world) {
      const radius = 78;
      world.getParticles().spawnShockwave(ex, ey, radius, 0.24, rgb(255, 145, 70));
      world.getParticles().spawnBurst(ex, ey, 14, 50, 180, 4.5, 0.35, rgb(255, 180, 75), true);
      for (const e of world.getEnemies().slice()) {
        if (!e.active) continue;
        if (Math.hypot(e.x - ex, e.y - ey) <= radius + e.radius) world.damageEnemy(e, dmg, false, 0, 0);
      }
    }

    applyExecutioner(dmg, e) {
      const s = this.getUpgradeCount('EXECUTIONER');
      return (s > 0 && e.hp < e.maxHp * 0.40) ? dmg * (1 + 0.40 * s) : dmg;
    }

    onStatusDotTick(type, dmg, world) {
      if (!this.isInvulnerable()) world.damagePlayer(dmg, 0, 0);
    }
  }
  CH.Player = Player;

  // -------------------------------------------------------------------------
  // Projektil
  // -------------------------------------------------------------------------
  class Projectile {
    constructor(x, y, vx, vy, radius, damage, fromPlayer, life, color) {
      this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.radius = radius; this.damage = damage;
      this.fromPlayer = fromPlayer; this.life = life; this.color = color;
      this.crit = false; this.pierceLeft = 0; this.bouncesLeft = 0; this.homing = false; this.explosive = false;
      this.applyStatus = null; this.statusDuration = 0; this.statusMagnitude = 0;
      this.active = true; this.hitEnemies = new Set();
    }

    update(dt, world) {
      if (!this.active) return;
      this.life -= dt;
      if (this.life <= 0) { this.active = false; return; }
      if (this.homing && this.fromPlayer) {
        let nearest = null, best = 280 * 280;
        for (const e of world.getEnemies()) {
          if (!e.active) continue;
          const d2 = (e.x - this.x) ** 2 + (e.y - this.y) ** 2;
          if (d2 < best) { best = d2; nearest = e; }
        }
        if (nearest) {
          const speed = Math.hypot(this.vx, this.vy);
          const des = new Vec(nearest.x - this.x, nearest.y - this.y).normalize().scale(speed);
          const turn = Math.min(1, dt * 4.5);
          this.vx += (des.x - this.vx) * turn;
          this.vy += (des.y - this.vy) * turn;
          const ns = Math.hypot(this.vx, this.vy);
          if (ns > 1e-4) { this.vx = this.vx / ns * speed; this.vy = this.vy / ns * speed; }
        }
      }
      const map = world.getMap();
      const nx = this.x + this.vx * dt, ny = this.y + this.vy * dt;
      const hitX = map.collidesCircleWithWalls(nx, this.y, this.radius);
      const hitY = map.collidesCircleWithWalls(this.x, ny, this.radius);
      if (hitX || hitY) {
        if (this.bouncesLeft > 0) {
          this.bouncesLeft--;
          if (hitX) this.vx = -this.vx;
          if (hitY) this.vy = -this.vy;
          this.x += this.vx * dt * 0.5;
          this.y += this.vy * dt * 0.5;
        } else {
          this.active = false;
          world.getParticles().spawnBurst(this.x, this.y, 5, 30, 90, 3.5, 0.22, this.color, true);
        }
      } else {
        this.x = nx; this.y = ny;
      }
    }
  }
  CH.Projectile = Projectile;

  // -------------------------------------------------------------------------
  // Gegner-KI (Strategy Pattern)
  // -------------------------------------------------------------------------
  function moveTargetAlongPath(enemy, goal, world) {
    const map = world.getMap();
    if (map.hasLineOfSight(enemy.x, enemy.y, goal.x, goal.y)) return goal;
    if (enemy.pathRecalcTimer <= 0) {
      enemy.pathRecalcTimer = 0.40;
      enemy.currentPath = world.getPathfinder().findPath(map, enemy.pos(), goal).slice();
    }
    while (enemy.currentPath.length) {
      const wp = enemy.currentPath[0];
      if (enemy.pos().distanceTo(wp) < 16 && enemy.currentPath.length > 1) enemy.currentPath.shift();
      else return wp;
    }
    return goal;
  }

  function moveEnemy(enemy, world, dirX, dirY, speed, knockFactor, dt) {
    const np = world.getMap().moveWithCollision(enemy.x, enemy.y,
      (dirX * speed + enemy.knockVx * knockFactor) * dt,
      (dirY * speed + enemy.knockVy * knockFactor) * dt, enemy.radius);
    enemy.x = np.x; enemy.y = np.y;
  }

  class ChaseBehavior {
    constructor(speedScale) { this.speedScale = speedScale; }
    update(enemy, dt, world) {
      const player = world.getPlayer();
      const speed = enemy.type.baseSpeed * this.speedScale * enemy.getSpeedMultiplierFromStatus();
      const target = moveTargetAlongPath(enemy, player.pos(), world);
      const dir = target.sub(enemy.pos()).normalize();
      enemy.facingAngle = dir.angle();
      moveEnemy(enemy, world, dir.x, dir.y, speed, 1, dt);
    }
  }

  class RangedKiteBehavior {
    constructor(speedScale) { this.speedScale = speedScale; this.aimTimer = 0; this.lockedAngle = 0; }
    update(enemy, dt, world) {
      const player = world.getPlayer();
      const dist = enemy.pos().distanceTo(player.pos());
      const hasLos = world.getMap().hasLineOfSight(enemy.x, enemy.y, player.x, player.y);
      let speed = enemy.type.baseSpeed * this.speedScale * enemy.getSpeedMultiplierFromStatus();

      if (this.aimTimer > 0) {
        this.aimTimer -= dt;
        enemy.telegraphing = true;
        enemy.telegraphX1 = enemy.x; enemy.telegraphY1 = enemy.y;
        enemy.telegraphX2 = enemy.x + Math.cos(this.lockedAngle) * 320;
        enemy.telegraphY2 = enemy.y + Math.sin(this.lockedAngle) * 320;
        enemy.telegraphColor = rgb(255, 78, 78);
        if (this.aimTimer <= 0) {
          enemy.telegraphing = false;
          const v = Vec.fromAngle(this.lockedAngle, 345);
          world.spawnProjectile(new Projectile(enemy.x + Math.cos(this.lockedAngle) * 14, enemy.y + Math.sin(this.lockedAngle) * 14,
            v.x, v.y, 5.5, 14, false, 2.0, rgb(255, 95, 75)));
          enemy.attackCooldownTimer = 1.65;
        } else {
          moveEnemy(enemy, world, 0, 0, 0, 1, dt);
          return;
        }
      }

      enemy.telegraphing = false;
      let md;
      if (!hasLos || dist > 330) {
        md = moveTargetAlongPath(enemy, player.pos(), world).sub(enemy.pos()).normalize();
      } else if (dist < 170) {
        md = enemy.pos().sub(player.pos()).normalize();
      } else {
        const toP = player.pos().sub(enemy.pos()).normalize();
        md = new Vec(-toP.y, toP.x);
        speed *= 0.55;
      }
      enemy.facingAngle = player.pos().sub(enemy.pos()).angle();
      moveEnemy(enemy, world, md.x, md.y, speed, 1, dt);

      if (hasLos && dist < 390 && enemy.attackCooldownTimer <= 0) {
        this.aimTimer = 0.45;
        this.lockedAngle = player.pos().sub(enemy.pos()).angle();
      }
    }
  }

  class ChargeBehavior {
    constructor(speedScale) { this.speedScale = speedScale; this.phase = 'CHASE'; this.phaseTimer = 0; this.cdx = 0; this.cdy = 0; }
    update(enemy, dt, world) {
      const player = world.getPlayer();
      const dist = enemy.pos().distanceTo(player.pos());
      const hasLos = world.getMap().hasLineOfSight(enemy.x, enemy.y, player.x, player.y);
      if (this.phase === 'CHASE') {
        enemy.telegraphing = false;
        const speed = enemy.type.baseSpeed * this.speedScale * enemy.getSpeedMultiplierFromStatus();
        const dir = moveTargetAlongPath(enemy, player.pos(), world).sub(enemy.pos()).normalize();
        enemy.facingAngle = dir.angle();
        moveEnemy(enemy, world, dir.x, dir.y, speed, 1, dt);
        if (hasLos && dist < 245 && enemy.attackCooldownTimer <= 0) {
          this.phase = 'WINDUP';
          this.phaseTimer = 0.55;
          const c = player.pos().sub(enemy.pos()).normalize();
          this.cdx = c.x; this.cdy = c.y;
          enemy.facingAngle = c.angle();
        }
      } else if (this.phase === 'WINDUP') {
        this.phaseTimer -= dt;
        enemy.telegraphing = true;
        enemy.telegraphX1 = enemy.x; enemy.telegraphY1 = enemy.y;
        enemy.telegraphX2 = enemy.x + this.cdx * 260; enemy.telegraphY2 = enemy.y + this.cdy * 260;
        enemy.telegraphColor = rgb(255, 60, 40);
        if (this.phaseTimer <= 0) {
          enemy.telegraphing = false;
          this.phase = 'CHARGING';
          this.phaseTimer = 0.52;
        }
      } else {
        this.phaseTimer -= dt;
        const cs = 440 * this.speedScale;
        const dx = this.cdx * cs * dt, dy = this.cdy * cs * dt;
        if (world.getMap().collidesCircleWithWalls(enemy.x + dx, enemy.y + dy, enemy.radius)) {
          this.phase = 'CHASE';
          enemy.attackCooldownTimer = 2.1;
          enemy.applyStatus('STUN', 1.15, 1);
          world.getCamera().addShake(0.22);
          world.getParticles().spawnBurst(enemy.x, enemy.y, 12, 40, 160, 4.5, 0.3, rgb(255, 200, 90), true);
          world.addFloatingText(new FloatingText(enemy.x, enemy.y - 22, 'STUN!', rgb(255, 220, 80), 14, 0.75));
        } else {
          enemy.x += dx; enemy.y += dy;
          world.getParticles().spawnAfterimage(enemy.x, enemy.y, enemy.radius * 0.85, rgb(235, 88, 68));
          if (this.phaseTimer <= 0) { this.phase = 'CHASE'; enemy.attackCooldownTimer = 2.0; }
        }
      }
    }
  }

  class NecromancerBehavior {
    constructor(speedScale) { this.speedScale = speedScale; this.summonCooldown = 3.2; }
    update(enemy, dt, world) {
      const player = world.getPlayer();
      const map = world.getMap();
      const dist = enemy.pos().distanceTo(player.pos());
      const speed = enemy.type.baseSpeed * this.speedScale * enemy.getSpeedMultiplierFromStatus();
      this.summonCooldown = Math.max(0, this.summonCooldown - dt);
      let dir = Vec.ZERO;
      if (dist < 200) dir = enemy.pos().sub(player.pos()).normalize();
      else if (dist > 360 || !map.hasLineOfSight(enemy.x, enemy.y, player.x, player.y)) {
        dir = moveTargetAlongPath(enemy, player.pos(), world).sub(enemy.pos()).normalize();
      }
      enemy.facingAngle = player.pos().sub(enemy.pos()).angle();
      moveEnemy(enemy, world, dir.x, dir.y, speed, 1, dt);

      if (this.summonCooldown <= 0 && world.getEnemies().length < 26) {
        this.summonCooldown = 5.5;
        const a = world.getRandom().nextDouble(0, Math.PI * 2);
        const sx = enemy.x + Math.cos(a) * 44, sy = enemy.y + Math.sin(a) * 44;
        if (!map.collidesCircleWithWalls(sx, sy, 14)) {
          const m = new Enemy(sx, sy, EnemyType.SLIME, enemy.homeRoomId, 0.75, this.speedScale);
          m.alerted = true;
          world.spawnEnemy(m);
          world.getParticles().spawnBurst(sx, sy, 12, 30, 120, 4, 0.35, rgb(185, 85, 255), true);
          world.addFloatingText(new FloatingText(enemy.x, enemy.y - 24, 'SUMMON!', rgb(205, 130, 255), 13, 0.7));
        }
      }
      if (enemy.attackCooldownTimer <= 0 && map.hasLineOfSight(enemy.x, enemy.y, player.x, player.y)) {
        enemy.attackCooldownTimer = 2.2;
        for (let i = -1; i <= 1; i++) {
          const a = enemy.facingAngle + i * 0.22;
          const v = Vec.fromAngle(a, 270);
          world.spawnProjectile(new Projectile(enemy.x, enemy.y, v.x, v.y, 6, 12, false, 2.3, rgb(196, 92, 255)));
        }
      }
    }
  }

  class BossBehavior {
    constructor(speedScale) { this.speedScale = speedScale; this.spiralAngle = 0; this.spiralTimer = 1.0; this.slamTimer = 3.2; }
    update(enemy, dt, world) {
      const player = world.getPlayer();
      if (enemy.bossPhase === 1 && enemy.hp <= enemy.maxHp * 0.5) {
        enemy.bossPhase = 2;
        world.getCamera().addShake(0.6);
        world.getParticles().spawnShockwave(enemy.x, enemy.y, 200, 0.45, rgb(255, 65, 110));
        world.publishEvent('BossPhaseChanged', { phase: 2 });
        // Zwei Skelett-Schützen zur Unterstützung rufen
        for (let i = 0; i < 2; i++) {
          const a = i * Math.PI;
          const sx = enemy.x + Math.cos(a) * 56, sy = enemy.y + Math.sin(a) * 56;
          if (!world.getMap().collidesCircleWithWalls(sx, sy, 14)) {
            const m = new Enemy(sx, sy, EnemyType.SKELETON_ARCHER, enemy.homeRoomId, 0.85, this.speedScale);
            m.alerted = true;
            world.spawnEnemy(m);
          }
        }
      }
      const phaseMult = enemy.bossPhase === 2 ? 1.24 : 1.0;
      const speed = enemy.type.baseSpeed * this.speedScale * phaseMult * enemy.getSpeedMultiplierFromStatus();
      const dir = moveTargetAlongPath(enemy, player.pos(), world).sub(enemy.pos()).normalize();
      enemy.facingAngle = player.pos().sub(enemy.pos()).angle();
      moveEnemy(enemy, world, dir.x, dir.y, speed, 0.35, dt);

      this.spiralTimer -= dt;
      if (this.spiralTimer <= 0) {
        this.spiralTimer = enemy.bossPhase === 2 ? 0.24 : 0.38;
        const arms = enemy.bossPhase === 2 ? 6 : 4;
        this.spiralAngle += 0.38;
        for (let i = 0; i < arms; i++) {
          const a = this.spiralAngle + 2 * Math.PI * i / arms;
          const v = Vec.fromAngle(a, 245);
          world.spawnProjectile(new Projectile(enemy.x, enemy.y, v.x, v.y, 7, 15, false, 2.8, rgb(255, 72, 118)));
        }
      }
      this.slamTimer -= dt;
      if (this.slamTimer <= 0) {
        this.slamTimer = enemy.bossPhase === 2 ? 2.6 : 3.8;
        const zones = enemy.bossPhase === 2 ? 3 : 1;
        for (let i = 0; i < zones; i++) {
          const ox = i === 0 ? 0 : world.getRandom().nextDouble(-95, 95);
          const oy = i === 0 ? 0 : world.getRandom().nextDouble(-95, 95);
          world.spawnHazardZone(new HazardZone(player.x + ox, player.y + oy, 66, 0.85, 24, true, rgb(255, 52, 88)));
        }
      }
    }
  }

  // -------------------------------------------------------------------------
  // Gegner
  // -------------------------------------------------------------------------
  class Enemy extends Entity {
    constructor(x, y, type, homeRoomId, hpScale, speedScale) {
      super(x, y, type.radius, type.baseHp * hpScale);
      this.type = type;
      this.homeRoomId = homeRoomId;
      this.alerted = false;
      this.attackCooldownTimer = 0.6;
      this.contactCooldown = 0;
      this.bossPhase = 1;
      this.currentPath = [];
      this.pathRecalcTimer = 0;
      this.telegraphing = false;
      this.telegraphX1 = 0; this.telegraphY1 = 0; this.telegraphX2 = 0; this.telegraphY2 = 0;
      this.telegraphColor = rgb(255, 70, 70);
      switch (type.id) {
        case 'SLIME': case 'ELDER_SLIME': this.behavior = new ChaseBehavior(speedScale); break;
        case 'SKELETON_ARCHER': this.behavior = new RangedKiteBehavior(speedScale); break;
        case 'BERSERKER': this.behavior = new ChargeBehavior(speedScale); break;
        case 'NECROMANCER': this.behavior = new NecromancerBehavior(speedScale); break;
        default: this.behavior = new BossBehavior(speedScale);
      }
    }

    update(dt, world) {
      if (!this.active) return;
      this.updateBaseTimers(dt, world);
      if (this.attackCooldownTimer > 0) this.attackCooldownTimer = Math.max(0, this.attackCooldownTimer - dt);
      if (this.contactCooldown > 0) this.contactCooldown = Math.max(0, this.contactCooldown - dt);
      if (this.pathRecalcTimer > 0) this.pathRecalcTimer = Math.max(0, this.pathRecalcTimer - dt);

      const player = world.getPlayer();
      if (!this.alerted) {
        const dist = this.pos().distanceTo(player.pos());
        const room = world.getMap().getRoomAt(player.x, player.y);
        const sameRoom = room !== null && room.id === this.homeRoomId;
        if (sameRoom || (dist < 380 && world.getMap().hasLineOfSight(this.x, this.y, player.x, player.y))) this.alerted = true;
        else return;
      }
      if (this.hasStatus('STUN')) {
        this.telegraphing = false;
        moveEnemy(this, world, 0, 0, 0, 1, dt);
        return;
      }
      this.behavior.update(this, dt, world);
    }

    onStatusDotTick(type, dmg, world) {
      const col = type === 'BURN' ? rgb(255, 140, 50) : rgb(110, 235, 95);
      world.damageEnemy(this, dmg, false, 0, 0, col);
    }
  }
  CH.Enemy = Enemy;
})();
