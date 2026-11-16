/*
 * Kamera, Partikelsystem und GameSession (ein kompletter Run inkl. Physik,
 * Kollisionen, Beute, Raumfortschritt, Erfolgen und Statistiken).
 */
'use strict';
(function () {
  const CH = window.CH;
  const { Vec, rgb, Config, FloatingText, Pickup, InteractiveProp, Enemy, EnemyType } = CH;
  const W = Config.LOGICAL_WIDTH, H = Config.LOGICAL_HEIGHT;

  class Camera {
    constructor() { this.x = 0; this.y = 0; this.trauma = 0; this.sx = 0; this.sy = 0; this.rng = new CH.JavaRandom(42n); }
    snapTo(tx, ty) { this.x = tx - W / 2; this.y = ty - H / 2; }
    addShake(t) { this.trauma = Math.min(1, this.trauma + t); }
    update(tx, ty, mx, my, dt) {
      const dx = tx + (mx - W / 2) * 0.14 - W / 2;
      const dy = ty + (my - H / 2) * 0.14 - H / 2;
      const s = 1 - Math.exp(-9.5 * dt);
      this.x += (dx - this.x) * s;
      this.y += (dy - this.y) * s;
      if (this.trauma > 0.001) {
        const p = this.trauma * this.trauma * 14;
        this.sx = (this.rng.nextDouble() * 2 - 1) * p;
        this.sy = (this.rng.nextDouble() * 2 - 1) * p;
        this.trauma = Math.max(0, this.trauma - 2.6 * dt);
      } else {
        this.trauma = 0; this.sx = 0; this.sy = 0;
      }
    }
    getX() { return this.x + this.sx; }
    getY() { return this.y + this.sy; }
    screenToWorld(sx, sy) { return new Vec(sx + this.getX(), sy + this.getY()); }
  }
  CH.Camera = Camera;

  // -------------------------------------------------------------------------
  const MAX_PARTICLES = 700;

  class ParticleSystem {
    constructor() {
      this.particles = []; this.shockwaves = []; this.slashes = []; this.bolts = []; this.afterimages = [];
      this.rng = new CH.JavaRandom(1337n);
    }
    clear() {
      this.particles.length = 0; this.shockwaves.length = 0; this.slashes.length = 0;
      this.bolts.length = 0; this.afterimages.length = 0;
    }
    addParticle(p) {
      if (this.particles.length >= MAX_PARTICLES) this.particles.shift();
      this.particles.push(p);
    }
    spawnBurst(x, y, count, minSpeed, maxSpeed, size, life, color, glow) {
      const r = this.rng;
      for (let i = 0; i < count; i++) {
        const a = r.nextDouble() * Math.PI * 2;
        const sp = minSpeed + r.nextDouble() * (maxSpeed - minSpeed);
        const sz = size * (0.7 + r.nextDouble() * 0.6);
        const lf = life * (0.75 + r.nextDouble() * 0.5);
        this.addParticle({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, size: sz, life: lf, maxLife: lf, drag: 3.5, color, glow });
      }
    }
    spawnDirectionalSpark(x, y, dir, spread, count, speed, color) {
      const r = this.rng;
      for (let i = 0; i < count; i++) {
        const a = dir + (r.nextDouble() * 2 - 1) * spread;
        const s = speed * (0.6 + r.nextDouble() * 0.7);
        this.addParticle({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, size: 3.8, life: 0.28, maxLife: 0.28, drag: 4.2, color, glow: true });
      }
    }
    spawnShockwave(x, y, maxRadius, duration, color) { this.shockwaves.push({ x, y, maxRadius, color, life: duration, maxLife: duration }); }
    spawnSlash(x, y, angleRad, arcDeg, radius, color) { this.slashes.push({ x, y, angleRad, arcDeg, radius, color, life: 0.16, maxLife: 0.16 }); }
    spawnLightning(x1, y1, x2, y2, color) {
      const pts = [new Vec(x1, y1)];
      for (let i = 1; i < 6; i++) {
        const t = i / 6;
        pts.push(new Vec(x1 + (x2 - x1) * t + (this.rng.nextDouble() - 0.5) * 18, y1 + (y2 - y1) * t + (this.rng.nextDouble() - 0.5) * 18));
      }
      pts.push(new Vec(x2, y2));
      this.bolts.push({ points: pts, color, life: 0.16, maxLife: 0.16 });
    }
    spawnAfterimage(x, y, radius, color) { this.afterimages.push({ x, y, radius, color, life: 0.24, maxLife: 0.24 }); }

    update(dt) {
      const ps = this.particles;
      let w = 0;
      for (let i = 0; i < ps.length; i++) {
        const p = ps[i];
        p.x += p.vx * dt; p.y += p.vy * dt;
        const d = Math.exp(-p.drag * dt);
        p.vx *= d; p.vy *= d;
        p.life -= dt;
        if (p.life > 0) ps[w++] = p;
      }
      ps.length = w;
      for (const list of [this.shockwaves, this.slashes, this.bolts, this.afterimages]) {
        let k = 0;
        for (let i = 0; i < list.length; i++) {
          list[i].life -= dt;
          if (list[i].life > 0) list[k++] = list[i];
        }
        list.length = k;
      }
    }
  }
  CH.ParticleSystem = ParticleSystem;

  // -------------------------------------------------------------------------
  class GameSession {
    constructor(heroClass, difficulty, seed, eventBus, saveManager) {
      this.seed = BigInt.asIntN(64, BigInt(seed));
      this.difficulty = difficulty;
      this.eventBus = eventBus;
      this.saveManager = saveManager;
      this.generator = new CH.DungeonGenerator();
      this.pathfinder = new CH.AStarPathfinder();
      this.particles = new ParticleSystem();
      this.camera = new Camera();
      this.random = new CH.SeededRandom(this.seed);
      this.map = null;
      this.player = new CH.Player(0, 0, heroClass);
      this.currentFloor = 1;

      this.enemies = []; this.pendingEnemies = [];
      this.projectiles = []; this.pendingProjectiles = [];
      this.hazardZones = []; this.pendingHazardZones = [];
      this.pickups = []; this.props = []; this.floatingTexts = []; this.toasts = [];

      this.trapTimer = 0; this.runDurationSeconds = 0; this.totalKills = 0; this.totalDamageDealt = 0;
      this.chestsOpened = 0; this.portalUnlocked = false; this.runFinished = false; this.victory = false;
      this.pendingAltarUpgrade = false;
      // Zähler für die Gesamtstatistik (wird am Run-Ende bzw. beim Abbruch gespeichert)
      this.stats = {
        killsByType: {}, bossKills: 0, damageTaken: 0, goldCollected: 0, shrinesUsed: 0, altarsUsed: 0,
        levelUps: 0, potions: 0, shieldOrbs: 0, upgradesChosen: 0, attacks: 0, crits: 0, dashes: 0,
        skillsQ: 0, skillsE: 0, portalsUsed: 0, hitsTaken: 0,
      };
      this.statsCommitted = false;
      this.loadFloor(1);
    }

    loadFloor(n) {
      this.currentFloor = n;
      this.random = new CH.SeededRandom(CH.Long.xor(this.seed, CH.Long.mul(n, 0x517CC1B727220A95n)));
      this.map = this.generator.generate(n, this.seed);
      for (const l of [this.enemies, this.pendingEnemies, this.projectiles, this.pendingProjectiles,
        this.hazardZones, this.pendingHazardZones, this.pickups, this.props, this.floatingTexts]) l.length = 0;
      this.particles.clear();
      this.portalUnlocked = false;

      const spawn = this.map.startRoom.centerWorld();
      const p = this.player;
      p.x = spawn.x; p.y = spawn.y; p.knockVx = 0; p.knockVy = 0;
      this.camera.snapTo(p.x, p.y);
      this.map.updateFogOfWar(p.x, p.y, Config.VISION_RADIUS_TILES);
      this.populateFloorEntities();
      if (n >= 3) this.unlockAchievement('DEEP_DELVER');
      this.eventBus.publish('FloorChanged', { floor: n });
    }

    populateFloorEntities() {
      const d = this.difficulty, f = this.currentFloor;
      const hpScale = d.enemyHealthMultiplier * (1 + (f - 1) * CH.Balance.FLOOR_HP_SCALING);
      const spdScale = d.enemySpeedMultiplier * (1 + (f - 1) * 0.06);
      for (const room of this.map.rooms) {
        const c = room.centerWorld();
        switch (room.type) {
          case 'TREASURE':
            this.props.push(new InteractiveProp(c.x, c.y, 'CHEST'));
            this.props.push(new InteractiveProp(c.x - 54, c.y, 'CHEST'));
            break;
          case 'SHRINE':
            this.props.push(new InteractiveProp(c.x - 42, c.y, 'HEALING_SHRINE'));
            this.props.push(new InteractiveProp(c.x + 42, c.y, 'RELIC_ALTAR'));
            break;
          case 'COMBAT': {
            const count = this.random.nextInt(3 + f, 5 + f) + d.extraEnemiesPerRoom;
            for (let i = 0; i < count; i++) {
              const p = this.findFreeSpot(room, 18);
              if (p) this.enemies.push(new Enemy(p.x, p.y, this.pickEnemyForFloor(f), room.id, hpScale, spdScale));
            }
            if (this.random.chance(0.45)) {
              const cp = this.findFreeSpot(room, 18);
              if (cp) this.props.push(new InteractiveProp(cp.x, cp.y, 'CHEST'));
            }
            break;
          }
          case 'BOSS':
            this.props.push(new InteractiveProp(c.x, c.y - 46, 'EXIT_PORTAL'));
            if (f < Config.MAX_FLOORS) {
              const count = 3 + f + d.extraEnemiesPerRoom;
              for (let i = 0; i < count; i++) {
                const p = this.findFreeSpot(room, 18);
                if (p) {
                  const t = i === 0 ? EnemyType.NECROMANCER : (i % 2 === 0 ? EnemyType.BERSERKER : EnemyType.SKELETON_ARCHER);
                  this.enemies.push(new Enemy(p.x, p.y, t, room.id, hpScale * 1.15, spdScale));
                }
              }
            } else {
              this.enemies.push(new Enemy(c.x, c.y + 30, EnemyType.BOSS_MALAKOR, room.id, d.enemyHealthMultiplier, d.enemySpeedMultiplier));
            }
            break;
          default: break;
        }
      }
    }

    pickEnemyForFloor(f) {
      const r = this.random.nextDouble();
      const T = EnemyType;
      if (f === 1) {
        if (r < 0.42) return T.SLIME; if (r < 0.72) return T.SKELETON_ARCHER; if (r < 0.90) return T.ELDER_SLIME; return T.BERSERKER;
      } else if (f === 2) {
        if (r < 0.22) return T.SLIME; if (r < 0.44) return T.ELDER_SLIME; if (r < 0.68) return T.SKELETON_ARCHER; if (r < 0.88) return T.BERSERKER; return T.NECROMANCER;
      }
      if (r < 0.22) return T.ELDER_SLIME; if (r < 0.48) return T.SKELETON_ARCHER; if (r < 0.76) return T.BERSERKER; return T.NECROMANCER;
    }

    findFreeSpot(room, radius) {
      for (let i = 0; i < 25; i++) {
        const tx = this.random.nextInt(room.tileX + 1, room.tileX + room.width - 2);
        const ty = this.random.nextInt(room.tileY + 1, room.tileY + room.height - 2);
        if (this.map.getTile(tx, ty) === CH.Tile.FLOOR) {
          const pos = this.map.tileCenterWorld(tx, ty);
          if (!this.map.collidesCircleWithWalls(pos.x, pos.y, radius)) return pos;
        }
      }
      return room.centerWorld();
    }

    update(dt, moveX, moveY, mouseX, mouseY, attacking, dashPressed, qPressed, ePressed, interactPressed) {
      if (this.runFinished) return;
      const player = this.player, map = this.map;
      this.runDurationSeconds += dt;
      this.trapTimer = (this.trapTimer + dt) % 2.8;

      const aimWorld = this.camera.screenToWorld(mouseX, mouseY);
      const aimAngle = Math.atan2(aimWorld.y - player.y, aimWorld.x - player.x);
      if (dashPressed) player.tryDash(moveX, moveY, this);
      if (attacking) player.tryPrimaryAttack(this);
      if (qPressed) player.trySkillQ(this);
      if (ePressed) player.trySkillE(aimWorld, this);

      player.update(dt, moveX, moveY, aimAngle, this);
      this.camera.update(player.x, player.y, mouseX, mouseY, dt);
      this.map.updateFogOfWar(player.x, player.y, Config.VISION_RADIUS_TILES);

      if (this.trapTimer >= 2.1 && !player.isInvulnerable()) {
        if (map.getTile(map.worldToTile(player.x), map.worldToTile(player.y)) === CH.Tile.SPIKE_TRAP) this.damagePlayer(CH.Balance.SPIKE_DAMAGE, 0, 0);
      }

      for (const prop of this.props) prop.pulseTime += dt;
      if (interactPressed) this.tryInteract();

      for (const e of this.enemies) {
        e.update(dt, this);
        if (e.active && e.contactCooldown <= 0) {
          const dist = player.pos().distanceTo(e.pos());
          if (dist < player.radius + e.radius) {
            const push = player.pos().sub(e.pos()).normalize().scale(220);
            this.damagePlayer(e.type.contactDamage, push.x, push.y);
            e.contactCooldown = 0.65;
          }
        }
      }

      for (const p of this.projectiles) {
        p.update(dt, this);
        if (!p.active) continue;
        if (p.fromPlayer) {
          for (const e of this.enemies) {
            if (!e.active || p.hitEnemies.has(e)) continue;
            if (Math.hypot(p.x - e.x, p.y - e.y) <= p.radius + e.radius) {
              p.hitEnemies.add(e);
              const fd = player.applyExecutioner(p.damage, e);
              const k = new Vec(p.vx, p.vy).normalize().scale(140);
              this.damageEnemy(e, fd, p.crit, k.x, k.y);
              if (p.applyStatus) e.applyStatus(p.applyStatus, p.statusDuration, p.statusMagnitude);
              // Enthält auch die Explosion bei kritischen Treffern (Arkane Detonation)
              player.applyWeaponOnHitEffects(e, fd, p.crit, this);
              if (p.pierceLeft > 0) p.pierceLeft--;
              else { p.active = false; break; }
            }
          }
        } else {
          const vanguardDash = player.isDashing() && player.heroClass.id === 'VANGUARD';
          const rSum = p.radius + player.radius + (vanguardDash ? 12 : 0);
          if (Math.hypot(p.x - player.x, p.y - player.y) <= rSum) {
            if (vanguardDash) {
              p.active = false;
              this.particles.spawnBurst(p.x, p.y, 8, 40, 140, 3.5, 0.25, rgb(120, 210, 255), true);
              this.addFloatingText(new FloatingText(player.x, player.y - 24, 'BLOCK!', rgb(130, 220, 255), 13, 0.6));
            } else if (!player.isInvulnerable()) {
              p.active = false;
              const k = new Vec(p.vx, p.vy).normalize().scale(160);
              this.damagePlayer(p.damage, k.x, k.y);
            }
          }
        }
      }

      for (const hz of this.hazardZones) {
        hz.remainingDelay -= dt;
        if (hz.remainingDelay <= 0 && !hz.detonated) {
          hz.detonated = true;
          this.particles.spawnShockwave(hz.x, hz.y, hz.radius, 0.28, hz.color);
          this.particles.spawnBurst(hz.x, hz.y, 18, 60, 220, 5, 0.4, hz.color, true);
          this.camera.addShake(0.24);
          if (hz.hurtsPlayer) {
            if (!player.isInvulnerable() && Math.hypot(player.x - hz.x, player.y - hz.y) <= hz.radius + player.radius * 0.7) this.damagePlayer(hz.damage, 0, 0);
          } else {
            for (const e of this.enemies) {
              if (e.active && Math.hypot(e.x - hz.x, e.y - hz.y) <= hz.radius + e.radius) this.damageEnemy(e, hz.damage, false, 0, 0);
            }
          }
        }
      }

      for (const pk of this.pickups) {
        if (!pk.active) continue;
        pk.animTime += dt;
        const damp = Math.exp(-6 * dt);
        pk.vx *= damp; pk.vy *= damp;
        const dist = Math.hypot(player.x - pk.x, player.y - pk.y);
        if (dist < Config.PICKUP_MAGNET_RADIUS) {
          const pull = 360 * (1 - dist / Config.PICKUP_MAGNET_RADIUS) + 140;
          const dir = new Vec(player.x - pk.x, player.y - pk.y).normalize();
          pk.vx = dir.x * pull; pk.vy = dir.y * pull;
        }
        const np = map.moveWithCollision(pk.x, pk.y, pk.vx * dt, pk.vy * dt, 6);
        pk.x = np.x; pk.y = np.y;
        if (dist <= player.radius + 12) this.collectPickup(pk);
      }

      if (this.pendingEnemies.length) { this.enemies.push(...this.pendingEnemies); this.pendingEnemies.length = 0; }
      if (this.pendingProjectiles.length) { this.projectiles.push(...this.pendingProjectiles); this.pendingProjectiles.length = 0; }
      if (this.pendingHazardZones.length) { this.hazardZones.push(...this.pendingHazardZones); this.pendingHazardZones.length = 0; }

      this.enemies = this.enemies.filter(e => e.active);
      this.projectiles = this.projectiles.filter(p => p.active);
      this.hazardZones = this.hazardZones.filter(h => !h.detonated);
      this.pickups = this.pickups.filter(p => p.active);
      this.floatingTexts = this.floatingTexts.filter(ft => ft.update(dt));
      this.particles.update(dt);

      this.checkPortal();

      if (this.toasts.length) {
        this.toasts[0].timer -= dt;
        if (this.toasts[0].timer <= 0) this.toasts.shift();
      }
    }

    checkPortal() {
      const boss = this.map.bossRoom;
      if (boss && !this.portalUnlocked) {
        const alive = this.enemies.some(e => e.active && e.homeRoomId === boss.id);
        if (!alive) {
          this.portalUnlocked = true;
          boss.cleared = true;
          const c = boss.centerWorld();
          this.particles.spawnShockwave(c.x, c.y - 46, 120, 0.5, rgb(85, 240, 255));
          this.addFloatingText(new FloatingText(this.player.x, this.player.y - 36, 'PORTAL OPEN!', rgb(85, 240, 255), 16, 1.4));
        }
      }
    }

    collectPickup(pk) {
      pk.active = false;
      const p = this.player;
      switch (pk.kind) {
        case 'XP_GEM':
          if (p.addXp(pk.value)) {
            this.stats.levelUps++;
            this.particles.spawnShockwave(p.x, p.y, 130, 0.45, rgb(255, 225, 95));
            this.addFloatingText(new FloatingText(p.x, p.y - 30, 'LEVEL UP!', rgb(255, 230, 100), 18, 1.2));
            this.eventBus.publish('LevelUp', { level: p.level });
            if (p.level >= 5) this.unlockAchievement('LEVEL_5');
          }
          break;
        case 'GOLD_COIN':
          this.stats.goldCollected += pk.value;
          p.gold += pk.value;
          p.score += pk.value * 2;
          this.addFloatingText(new FloatingText(pk.x, pk.y - 10, '+' + pk.value + 'g', rgb(255, 215, 65), 13, 0.7));
          break;
        case 'HEALTH_POTION':
          this.stats.potions++;
          p.heal(pk.value);
          this.addFloatingText(new FloatingText(p.x, p.y - 22, '+' + pk.value + ' HP', rgb(95, 240, 130), 14, 0.8));
          break;
        case 'SHIELD_ORB':
          this.stats.shieldOrbs++;
          p.shield = Math.min(p.maxShield + 20, p.shield + pk.value);
          this.addFloatingText(new FloatingText(p.x, p.y - 22, '+' + pk.value + ' SHD', rgb(100, 210, 255), 14, 0.8));
          break;
      }
      this.eventBus.publish('PickupCollected', { kind: pk.kind });
    }

    tryInteract() {
      const n = this.getNearestInteractableProp();
      if (!n) return;
      const p = this.player;
      switch (n.type) {
        case 'CHEST':
          if (!n.used) {
            n.used = true;
            this.chestsOpened++;
            this.particles.spawnBurst(n.x, n.y, 18, 50, 180, 4.5, 0.4, rgb(255, 215, 80), true);
            for (let i = 0; i < 5; i++) {
              const a = this.random.nextDouble(0, Math.PI * 2);
              this.pickups.push(new Pickup(n.x, n.y, 'GOLD_COIN', 8, Math.cos(a) * 110, Math.sin(a) * 110));
            }
            this.pickups.push(new Pickup(n.x, n.y, 'XP_GEM', 28, 0, -90));
            if (this.random.chance(0.55)) this.pickups.push(new Pickup(n.x, n.y, 'HEALTH_POTION', 25, 75, 45));
            this.eventBus.publish('ChestOpened', {});
            if (this.chestsOpened >= 3) this.unlockAchievement('TREASURE_HUNTER');
          }
          break;
        case 'HEALING_SHRINE':
          if (!n.used && p.gold < 25) this.showNotEnoughGold(n, 25);
          else if (!n.used) {
            n.used = true; p.gold -= 25; p.heal(45); this.stats.shrinesUsed++;
            this.particles.spawnShockwave(n.x, n.y, 90, 0.4, rgb(90, 255, 150));
            this.addFloatingText(new FloatingText(p.x, p.y - 26, '+45 HP', rgb(90, 255, 150), 16, 1.0));
            this.eventBus.publish('UiClick', {});
          }
          break;
        case 'RELIC_ALTAR':
          if (!n.used && p.gold < 40) this.showNotEnoughGold(n, 40);
          else if (!n.used) {
            n.used = true; p.gold -= 40; this.pendingAltarUpgrade = true; this.stats.altarsUsed++;
            this.eventBus.publish('UiClick', {});
          }
          break;
        case 'EXIT_PORTAL':
          if (this.portalUnlocked) {
            this.stats.portalsUsed++;
            if (this.currentFloor < Config.MAX_FLOORS) this.loadFloor(this.currentFloor + 1);
            else this.endRun(true);
          } else {
            this.addFloatingText(new FloatingText(n.x, n.y - 26, CH.I18n.t('hud.portal_locked'), rgb(255, 110, 95), 14, 1.2));
          }
          break;
      }
    }

    showNotEnoughGold(prop, cost) {
      this.addFloatingText(new FloatingText(prop.x, prop.y - 26, CH.I18n.fmt('hud.not_enough_gold', cost, this.player.gold), rgb(255, 110, 95), 14, 1.2));
    }

    getNearestInteractableProp() {
      let best = null, bestD = Config.INTERACT_RADIUS;
      for (const pr of this.props) {
        if (pr.used && pr.type !== 'EXIT_PORTAL') continue;
        const d = Math.hypot(this.player.x - pr.x, this.player.y - pr.y);
        if (d <= bestD) { bestD = d; best = pr; }
      }
      return best;
    }

    endRun(isVictory) {
      if (this.runFinished) return;
      this.runFinished = true;
      this.victory = isVictory;
      const p = this.player;
      if (isVictory) {
        // Bonus wird unten zusammen mit dem Rest einmal mit dem Schwierigkeitsfaktor multipliziert
        p.score += 600;
        if (this.difficulty !== CH.Difficulty.NORMAL) this.unlockAchievement('SURVIVOR_HARD');
        if (this.runDurationSeconds < 360) this.unlockAchievement('SPEEDRUNNER');
      }
      const finalScore = Math.round(p.score * this.difficulty.scoreMultiplier);
      p.score = finalScore;
      this.commitStats(isVictory ? 'WIN' : 'DEATH');
      this.saveManager.recordRun(finalScore, p.heroClass.id, this.difficulty.name, this.currentFloor, this.totalKills,
        this.runDurationSeconds, isVictory, this.seed);
      this.eventBus.publish('RunEnded', { victory: isVictory, score: finalScore });
    }

    damageEnemy(e, amount, crit, kx, ky, textColor) {
      if (!e.active || amount <= 0) return;
      e.hp -= amount;
      e.hitFlashTimer = 0.09;
      e.alerted = true;
      e.applyKnockback(kx, ky);
      this.totalDamageDealt += amount;
      const col = textColor || (crit ? rgb(255, 220, 75) : CH.Color.WHITE);
      const size = crit ? 16 : (textColor ? 12 : 13);
      const label = (crit ? '!' : '') + Math.max(1, Math.round(amount));
      this.floatingTexts.push(new FloatingText(e.x, e.y - e.radius - 4, label, col, size, 0.65));
      this.particles.spawnBurst(e.x, e.y, crit ? 9 : 4, 40, 150, 3.6, 0.25, e.type.color, crit);
      if (crit) this.camera.addShake(0.14);
      if (e.hp <= 0) this.killEnemy(e);
    }

    killEnemy(e) {
      const p = this.player;
      e.active = false;
      this.totalKills++;
      this.stats.killsByType[e.type.id] = (this.stats.killsByType[e.type.id] || 0) + 1;
      if (e.type.isBoss) this.stats.bossKills++;
      p.combo++;
      p.maxCombo = Math.max(p.maxCombo, p.combo);
      p.comboTimer = Config.COMBO_DECAY_SECONDS;
      const bonus = 1 + Math.min(2, p.combo * 0.05);
      p.score += Math.round(e.type.scoreReward * bonus);
      const vamp = p.getUpgradeCount('VAMPIRIC');
      if (vamp > 0) p.heal(4 * vamp);

      this.particles.spawnBurst(e.x, e.y, e.type.isBoss ? 48 : 16, 60, 240, 5.5, 0.45, e.type.color, true);
      this.camera.addShake(e.type.isBoss ? 0.65 : 0.16);

      const r = this.random;
      this.pickups.push(new Pickup(e.x, e.y, 'XP_GEM', e.type.xpReward, r.nextDouble(-60, 60), r.nextDouble(-60, 60)));
      if (r.chance(0.65) || e.type.isBoss) {
        const coins = e.type.isBoss ? 8 : r.nextInt(1, 3);
        for (let i = 0; i < coins; i++) this.pickups.push(new Pickup(e.x, e.y, 'GOLD_COIN', 5, r.nextDouble(-90, 90), r.nextDouble(-90, 90)));
      }
      if (r.chance(CH.Balance.POTION_DROP_CHANCE)) {
        this.pickups.push(new Pickup(e.x, e.y, r.chance(0.65) ? 'HEALTH_POTION' : 'SHIELD_ORB', 18, r.nextDouble(-50, 50), r.nextDouble(-50, 50)));
      }
      if (e.type === EnemyType.ELDER_SLIME) {
        for (let i = 0; i < 2; i++) {
          const ox = i === 0 ? -18 : 18;
          if (!this.map.collidesCircleWithWalls(e.x + ox, e.y, 13)) {
            const child = new Enemy(e.x + ox, e.y, EnemyType.SLIME, e.homeRoomId, 0.7, this.difficulty.enemySpeedMultiplier);
            child.alerted = true;
            this.spawnEnemy(child);
          }
        }
      }
      if (e.type.isBoss) this.unlockAchievement('BOSS_SLAYER');
      if (this.totalKills === 1) this.unlockAchievement('FIRST_BLOOD');
      if (p.combo >= 10) this.unlockAchievement('COMBO_10');
      this.eventBus.publish('EnemyKilled', { boss: e.type.isBoss });
    }

    damagePlayer(amount, kx, ky) {
      const p = this.player;
      if (p.isInvulnerable() || this.runFinished) return;
      // Jeder Schaden am Spieler stammt von Gegnern oder Fallen: hier zentral skalieren
      amount *= this.difficulty.enemyDamageMultiplier * (1 + (this.currentFloor - 1) * CH.Balance.FLOOR_DAMAGE_SCALING);
      p.invulnTimer = CH.Balance.PLAYER_HIT_INVULN;
      p.hitFlashTimer = 0.16;
      p.shieldRegenDelay = 4.0;
      p.applyKnockback(kx, ky);
      this.camera.addShake(0.32);
      let rem = amount;
      if (p.shield > 0) {
        const abs = Math.min(p.shield, rem);
        p.shield -= abs; rem -= abs;
      }
      this.stats.damageTaken += amount;
      this.stats.hitsTaken++;
      if (rem > 0) p.hp = Math.max(0, p.hp - rem);
      this.floatingTexts.push(new FloatingText(p.x, p.y - 22, '-' + Math.ceil(amount), rgb(255, 85, 85), 15, 0.75));
      this.particles.spawnBurst(p.x, p.y, 10, 50, 180, 4, 0.3, rgb(255, 80, 80), true);
      this.eventBus.publish('PlayerHurt', {});
      if (p.hp <= 0) this.endRun(false);
    }

    unlockAchievement(id) {
      if (this.saveManager.unlockAchievement(id)) {
        const a = CH.findAchievement(id);
        if (a) this.toasts.push({ achievement: a, timer: 3.6 });
      }
    }

    getCurrentToast() { return this.toasts[0] || null; }

    getActiveBoss() {
      for (const e of this.enemies) if (e.active && e.type.isBoss && e.alerted) return e;
      return null;
    }

    applyChosenUpgrade(u) {
      this.stats.upgradesChosen++;
      this.player.applyUpgrade(u);
      this.particles.spawnShockwave(this.player.x, this.player.y, 110, 0.38, u.rarity.color);
      this.addFloatingText(new FloatingText(this.player.x, this.player.y - 28, u.name(), u.rarity.color, 15, 1.1));
    }

    // CombatWorld-Schnittstelle
    getMap() { return this.map; }
    getPlayer() { return this.player; }
    getEnemies() { return this.enemies; }
    getPathfinder() { return this.pathfinder; }
    getParticles() { return this.particles; }
    getCamera() { return this.camera; }
    getRandom() { return this.random; }
    getDifficulty() { return this.difficulty; }
    spawnProjectile(p) { this.pendingProjectiles.push(p); }
    spawnEnemy(e) { this.pendingEnemies.push(e); }
    spawnHazardZone(z) { this.pendingHazardZones.push(z); }
    addFloatingText(t) { this.floatingTexts.push(t); }
    publishEvent(type, data) {
      if (type === 'DashPerformed') this.stats.dashes++;
      else if (type === 'AttackPerformed') { this.stats.attacks++; if (data && data.critical) this.stats.crits++; }
      else if (type === 'SkillCast') { if (data && data.slot === 1) this.stats.skillsQ++; else this.stats.skillsE++; }
      this.eventBus.publish(type, data);
    }

    /** Überträgt diesen Run einmalig in die Gesamtstatistik (Sieg, Niederlage oder Abbruch). */
    commitStats(outcome) {
      if (this.statsCommitted) return;
      this.statsCommitted = true;
      this.saveManager.addRunStats(this, outcome);
    }
  }
  CH.GameSession = GameSession;
})();
