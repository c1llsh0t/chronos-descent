package de.chronos.ui.state;

import de.chronos.combat.Achievement;
import de.chronos.combat.FloatingText;
import de.chronos.combat.StatusEffect;
import de.chronos.combat.Upgrade;
import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.SaveManager;
import de.chronos.core.event.GameEvent;
import de.chronos.core.event.GameEventBus;
import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;
import de.chronos.entity.EnemyType;
import de.chronos.entity.HazardZone;
import de.chronos.entity.HeroClass;
import de.chronos.entity.InteractiveProp;
import de.chronos.entity.Pickup;
import de.chronos.entity.Player;
import de.chronos.entity.Projectile;
import de.chronos.graphics.Camera;
import de.chronos.graphics.ParticleSystem;
import de.chronos.math.SeededRandom;
import de.chronos.math.Vector2D;
import de.chronos.world.AStarPathfinder;
import de.chronos.world.DungeonGenerator;
import de.chronos.world.DungeonMap;
import de.chronos.world.Room;
import de.chronos.world.TileType;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Kapselt einen vollständigen Run inkl. aktueller Ebene, Entitäten, Physik,
 * Kollisionsauflösung, Raum-Fortschritt und Run-Statistiken.
 */
public final class GameSession implements CombatWorld {

    public static final class AchievementToast {
        public final Achievement achievement;
        public double timer = 3.6;

        public AchievementToast(Achievement achievement) {
            this.achievement = achievement;
        }
    }

    public final long seed;
    public final GameConfig.Difficulty difficulty;
    public final GameEventBus eventBus;
    public final SaveManager saveManager;

    private final DungeonGenerator generator = new DungeonGenerator();
    private final AStarPathfinder pathfinder = new AStarPathfinder();
    private final ParticleSystem particles = new ParticleSystem();
    private final Camera camera = new Camera();
    private SeededRandom random;

    private DungeonMap map;
    private Player player;
    private int currentFloor = 1;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Enemy> pendingEnemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Projectile> pendingProjectiles = new ArrayList<>();
    private final List<HazardZone> hazardZones = new ArrayList<>();
    private final List<HazardZone> pendingHazardZones = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<InteractiveProp> props = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final Deque<AchievementToast> toasts = new ArrayDeque<>();

    public double trapTimer = 0.0;
    public double runDurationSeconds = 0.0;
    public int totalKills = 0;
    public double totalDamageDealt = 0.0;
    public int chestsOpened = 0;
    public boolean portalUnlocked = false;
    public boolean runFinished = false;
    public boolean victory = false;
    public boolean pendingAltarUpgrade = false;

    public GameSession(HeroClass heroClass, GameConfig.Difficulty difficulty, long seed,
                       GameEventBus eventBus, SaveManager saveManager) {
        this.seed = seed;
        this.difficulty = difficulty;
        this.eventBus = eventBus;
        this.saveManager = saveManager;
        this.random = new SeededRandom(seed);
        this.player = new Player(0, 0, heroClass);
        loadFloor(1);
    }

    public void loadFloor(int floorNumber) {
        this.currentFloor = floorNumber;
        this.random = new SeededRandom(seed ^ (floorNumber * 0x517CC1B727220A95L));
        this.map = generator.generate(floorNumber, seed);

        enemies.clear();
        pendingEnemies.clear();
        projectiles.clear();
        pendingProjectiles.clear();
        hazardZones.clear();
        pendingHazardZones.clear();
        pickups.clear();
        props.clear();
        floatingTexts.clear();
        particles.clear();
        portalUnlocked = false;

        Vector2D spawn = map.getStartRoom().centerWorld();
        player.x = spawn.x;
        player.y = spawn.y;
        player.knockVx = 0;
        player.knockVy = 0;
        camera.snapTo(player.x, player.y);
        map.updateFogOfWar(player.x, player.y, GameConfig.VISION_RADIUS_TILES);

        populateFloorEntities();

        if (floorNumber >= 3) {
            unlockAchievement("DEEP_DELVER");
        }
        eventBus.publish(new GameEvent.FloorChanged(floorNumber));
    }

    private void populateFloorEntities() {
        double hpScale = difficulty.enemyHealthMultiplier * (1.0 + (currentFloor - 1) * GameConfig.FLOOR_HP_SCALING);
        double spdScale = difficulty.enemySpeedMultiplier * (1.0 + (currentFloor - 1) * 0.06);

        for (Room room : map.getRooms()) {
            switch (room.type) {
                case START -> {
                    // Auf Ebene 1 eine kleine Starttruhe als Belohnung für Entdecker in der Ecke? Nein, sauberer Fokus.
                }
                case TREASURE -> {
                    Vector2D c = room.centerWorld();
                    props.add(new InteractiveProp(c.x, c.y, InteractiveProp.PropType.CHEST));
                    props.add(new InteractiveProp(c.x - 54, c.y, InteractiveProp.PropType.CHEST));
                }
                case SHRINE -> {
                    Vector2D c = room.centerWorld();
                    props.add(new InteractiveProp(c.x - 42, c.y, InteractiveProp.PropType.HEALING_SHRINE));
                    props.add(new InteractiveProp(c.x + 42, c.y, InteractiveProp.PropType.RELIC_ALTAR));
                }
                case COMBAT -> {
                    int count = random.nextInt(3 + currentFloor, 5 + currentFloor) + difficulty.extraEnemiesPerRoom;
                    for (int i = 0; i < count; i++) {
                        Vector2D p = findFreeSpotInRoom(room, 18.0);
                        if (p != null) {
                            EnemyType type = pickEnemyForFloor(currentFloor);
                            enemies.add(new Enemy(p.x, p.y, type, room.id, hpScale, spdScale));
                        }
                    }
                    if (random.chance(0.45)) {
                        Vector2D cp = findFreeSpotInRoom(room, 18.0);
                        if (cp != null) {
                            props.add(new InteractiveProp(cp.x, cp.y, InteractiveProp.PropType.CHEST));
                        }
                    }
                }
                case BOSS -> {
                    Vector2D c = room.centerWorld();
                    props.add(new InteractiveProp(c.x, c.y - 46, InteractiveProp.PropType.EXIT_PORTAL));
                    if (currentFloor < GameConfig.MAX_FLOORS) {
                        // Mini-Boss-Wächter-Welle auf Ebene 1 & 2
                        int count = 3 + currentFloor + difficulty.extraEnemiesPerRoom;
                        for (int i = 0; i < count; i++) {
                            Vector2D p = findFreeSpotInRoom(room, 18.0);
                            if (p != null) {
                                EnemyType t = (i == 0) ? EnemyType.NECROMANCER :
                                        (i % 2 == 0 ? EnemyType.BERSERKER : EnemyType.SKELETON_ARCHER);
                                enemies.add(new Enemy(p.x, p.y, t, room.id, hpScale * 1.15, spdScale));
                            }
                        }
                    } else {
                        // Finaler Boss auf Ebene 3
                        enemies.add(new Enemy(c.x, c.y + 30, EnemyType.BOSS_MALAKOR, room.id,
                                difficulty.enemyHealthMultiplier, difficulty.enemySpeedMultiplier));
                    }
                }
            }
        }
    }

    private EnemyType pickEnemyForFloor(int floor) {
        double r = random.nextDouble();
        if (floor == 1) {
            if (r < 0.42) return EnemyType.SLIME;
            if (r < 0.72) return EnemyType.SKELETON_ARCHER;
            if (r < 0.90) return EnemyType.ELDER_SLIME;
            return EnemyType.BERSERKER;
        } else if (floor == 2) {
            if (r < 0.22) return EnemyType.SLIME;
            if (r < 0.44) return EnemyType.ELDER_SLIME;
            if (r < 0.68) return EnemyType.SKELETON_ARCHER;
            if (r < 0.88) return EnemyType.BERSERKER;
            return EnemyType.NECROMANCER;
        } else {
            if (r < 0.22) return EnemyType.ELDER_SLIME;
            if (r < 0.48) return EnemyType.SKELETON_ARCHER;
            if (r < 0.76) return EnemyType.BERSERKER;
            return EnemyType.NECROMANCER;
        }
    }

    private Vector2D findFreeSpotInRoom(Room room, double radius) {
        for (int attempt = 0; attempt < 25; attempt++) {
            int tx = random.nextInt(room.tileX + 1, room.tileX + room.width - 2);
            int ty = random.nextInt(room.tileY + 1, room.tileY + room.height - 2);
            if (map.getTile(tx, ty) == TileType.FLOOR) {
                Vector2D pos = map.tileCenterWorld(tx, ty);
                if (!map.collidesCircleWithWalls(pos.x, pos.y, radius)) {
                    return pos;
                }
            }
        }
        return room.centerWorld();
    }

    public void update(double dt, double moveX, double moveY, double mouseLogicalX, double mouseLogicalY,
                       boolean attacking, boolean dashPressed, boolean qPressed, boolean ePressed, boolean interactPressed) {
        if (runFinished) {
            return;
        }

        runDurationSeconds += dt;
        trapTimer = (trapTimer + dt) % 2.8;

        Vector2D aimWorld = camera.screenToWorld(mouseLogicalX, mouseLogicalY);
        double aimAngle = Math.atan2(aimWorld.y - player.y, aimWorld.x - player.x);

        if (dashPressed) {
            player.tryDash(moveX, moveY, this);
        }
        if (attacking) {
            player.tryPrimaryAttack(this);
        }
        if (qPressed) {
            player.trySkillQ(this);
        }
        if (ePressed) {
            player.trySkillE(aimWorld, this);
        }

        player.update(dt, moveX, moveY, aimAngle, this);
        camera.update(player.x, player.y, mouseLogicalX, mouseLogicalY, dt);
        map.updateFogOfWar(player.x, player.y, GameConfig.VISION_RADIUS_TILES);

        // Stachelfallen prüfen
        if (trapTimer >= 2.1 && !player.isInvulnerable()) {
            int ptx = map.worldToTile(player.x);
            int pty = map.worldToTile(player.y);
            if (map.getTile(ptx, pty) == TileType.SPIKE_TRAP) {
                damagePlayer(GameConfig.SPIKE_DAMAGE, 0, 0);
            }
        }

        // Interaktive Objekte prüfen
        for (InteractiveProp prop : props) {
            prop.pulseTime += dt;
        }
        if (interactPressed) {
            tryInteractWithNearestProp();
        }

        // Gegner aktualisieren
        for (Enemy enemy : enemies) {
            enemy.update(dt, this);
            if (enemy.active && enemy.contactCooldown <= 0.0) {
                double dist = player.pos().distanceTo(enemy.pos());
                if (dist < player.radius + enemy.radius) {
                    Vector2D push = player.pos().sub(enemy.pos()).normalize().scale(220.0);
                    damagePlayer(enemy.type.contactDamage, push.x, push.y);
                    enemy.contactCooldown = 0.65;
                }
            }
        }

        // Projektile aktualisieren + Kollisionen prüfen
        for (Projectile p : projectiles) {
            p.update(dt, this);
            if (!p.active) continue;

            if (p.fromPlayer) {
                for (Enemy enemy : enemies) {
                    if (!enemy.active || p.hitEnemies.contains(enemy)) continue;
                    double rSum = p.radius + enemy.radius;
                    if (Math.hypot(p.x - enemy.x, p.y - enemy.y) <= rSum) {
                        p.hitEnemies.add(enemy);
                        double finalDmg = player.applyExecutioner(p.damage, enemy);
                        Vector2D knock = new Vector2D(p.vx, p.vy).normalize().scale(140.0);
                        damageEnemy(enemy, finalDmg, p.crit, knock.x, knock.y);
                        if (p.applyStatus != null) {
                            enemy.applyStatus(p.applyStatus, p.statusDuration, p.statusMagnitude);
                        }
                        // Enthält auch die Explosion bei kritischen Treffern (Arkane Detonation)
                        player.applyWeaponOnHitEffects(enemy, finalDmg, p.crit, this);
                        if (p.pierceLeft > 0) {
                            p.pierceLeft--;
                        } else {
                            p.active = false;
                            break;
                        }
                    }
                }
            } else {
                // Vanguard Schild-Dash fängt gegnerische Projektile ab
                double rSum = p.radius + player.radius + (player.isDashing() && player.heroClass == HeroClass.VANGUARD ? 12.0 : 0.0);
                if (Math.hypot(p.x - player.x, p.y - player.y) <= rSum) {
                    if (player.isDashing() && player.heroClass == HeroClass.VANGUARD) {
                        p.active = false;
                        particles.spawnBurst(p.x, p.y, 8, 40, 140, 3.5, 0.25, new Color(120, 210, 255), true);
                        addFloatingText(new FloatingText(player.x, player.y - 24, "BLOCK!", new Color(130, 220, 255), 13f, 0.6));
                    } else if (!player.isInvulnerable()) {
                        p.active = false;
                        Vector2D knock = new Vector2D(p.vx, p.vy).normalize().scale(160.0);
                        damagePlayer(p.damage, knock.x, knock.y);
                    }
                }
            }
        }

        // Gefahrenzonen aktualisieren
        for (HazardZone hz : hazardZones) {
            hz.remainingDelay -= dt;
            if (hz.remainingDelay <= 0.0 && !hz.detonated) {
                hz.detonated = true;
                particles.spawnShockwave(hz.x, hz.y, hz.radius, 0.28, hz.color);
                particles.spawnBurst(hz.x, hz.y, 18, 60, 220, 5.0, 0.4, hz.color, true);
                camera.addShake(0.24);
                if (hz.hurtsPlayer) {
                    if (!player.isInvulnerable() && Math.hypot(player.x - hz.x, player.y - hz.y) <= hz.radius + player.radius * 0.7) {
                        damagePlayer(hz.damage, 0, 0);
                    }
                } else {
                    for (Enemy enemy : enemies) {
                        if (enemy.active && Math.hypot(enemy.x - hz.x, enemy.y - hz.y) <= hz.radius + enemy.radius) {
                            damageEnemy(enemy, hz.damage, false, 0, 0);
                        }
                    }
                }
            }
        }

        // Beute (Pickups) aktualisieren + Magnetismus
        for (Pickup pk : pickups) {
            if (!pk.active) continue;
            pk.animTime += dt;
            pk.vx *= Math.exp(-6.0 * dt);
            pk.vy *= Math.exp(-6.0 * dt);

            double dist = Math.hypot(player.x - pk.x, player.y - pk.y);
            if (dist < GameConfig.PICKUP_MAGNET_RADIUS) {
                double pull = 360.0 * (1.0 - dist / GameConfig.PICKUP_MAGNET_RADIUS) + 140.0;
                Vector2D dir = new Vector2D(player.x - pk.x, player.y - pk.y).normalize();
                pk.vx = dir.x * pull;
                pk.vy = dir.y * pull;
            }
            Vector2D nextPos = map.moveWithCollision(pk.x, pk.y, pk.vx * dt, pk.vy * dt, 6.0);
            pk.x = nextPos.x;
            pk.y = nextPos.y;

            if (dist <= player.radius + 12.0) {
                collectPickup(pk);
            }
        }

        // Verschobene Spawns einfügen & inaktive entfernen (kein ConcurrentModificationException!)
        if (!pendingEnemies.isEmpty()) {
            enemies.addAll(pendingEnemies);
            pendingEnemies.clear();
        }
        if (!pendingProjectiles.isEmpty()) {
            projectiles.addAll(pendingProjectiles);
            pendingProjectiles.clear();
        }
        if (!pendingHazardZones.isEmpty()) {
            hazardZones.addAll(pendingHazardZones);
            pendingHazardZones.clear();
        }

        enemies.removeIf(e -> !e.active);
        projectiles.removeIf(p -> !p.active);
        hazardZones.removeIf(h -> h.detonated);
        pickups.removeIf(pk -> !pk.active);
        floatingTexts.removeIf(ft -> !ft.update(dt));
        particles.update(dt);

        // Portal im Bossraum freischalten, sobald alle Gegner dort besiegt sind
        checkRoomAndPortalStatus();

        // Toasts herunterzählen
        if (!toasts.isEmpty()) {
            AchievementToast head = toasts.peekFirst();
            head.timer -= dt;
            if (head.timer <= 0.0) {
                toasts.pollFirst();
            }
        }
    }

    private void checkRoomAndPortalStatus() {
        Room bossRoom = map.getBossRoom();
        if (bossRoom != null && !portalUnlocked) {
            boolean anyBossRoomEnemyAlive = false;
            for (Enemy e : enemies) {
                if (e.active && e.homeRoomId == bossRoom.id) {
                    anyBossRoomEnemyAlive = true;
                    break;
                }
            }
            if (!anyBossRoomEnemyAlive) {
                portalUnlocked = true;
                bossRoom.cleared = true;
                Vector2D c = bossRoom.centerWorld();
                particles.spawnShockwave(c.x, c.y - 46, 120.0, 0.5, new Color(85, 240, 255));
                addFloatingText(new FloatingText(player.x, player.y - 36, "PORTAL OPEN!", new Color(85, 240, 255), 16f, 1.4));
            }
        }
    }

    private void collectPickup(Pickup pk) {
        pk.active = false;
        switch (pk.kind) {
            case XP_GEM -> {
                boolean lv = player.addXp(pk.value);
                if (lv) {
                    particles.spawnShockwave(player.x, player.y, 130.0, 0.45, new Color(255, 225, 95));
                    addFloatingText(new FloatingText(player.x, player.y - 30, "LEVEL UP!", new Color(255, 230, 100), 18f, 1.2));
                    eventBus.publish(new GameEvent.LevelUp(player.level));
                    if (player.level >= 5) {
                        unlockAchievement("LEVEL_5");
                    }
                }
            }
            case GOLD_COIN -> {
                player.gold += pk.value;
                player.score += pk.value * 2;
                addFloatingText(new FloatingText(pk.x, pk.y - 10, "+" + pk.value + "g", new Color(255, 215, 65), 13f, 0.7));
            }
            case HEALTH_POTION -> {
                player.heal(pk.value);
                addFloatingText(new FloatingText(player.x, player.y - 22, "+" + pk.value + " HP", new Color(95, 240, 130), 14f, 0.8));
            }
            case SHIELD_ORB -> {
                player.shield = Math.min(player.maxShield + 20, player.shield + pk.value);
                addFloatingText(new FloatingText(player.x, player.y - 22, "+" + pk.value + " SHD", new Color(100, 210, 255), 14f, 0.8));
            }
        }
        eventBus.publish(new GameEvent.PickupCollected(pk.kind.name()));
    }

    private void tryInteractWithNearestProp() {
        InteractiveProp nearest = getNearestInteractableProp();
        if (nearest == null) {
            return;
        }
        switch (nearest.type) {
            case CHEST -> {
                if (!nearest.used) {
                    nearest.used = true;
                    chestsOpened++;
                    particles.spawnBurst(nearest.x, nearest.y, 18, 50, 180, 4.5, 0.4, new Color(255, 215, 80), true);
                    for (int i = 0; i < 5; i++) {
                        double a = random.nextDouble(0, Math.PI * 2);
                        pickups.add(new Pickup(nearest.x, nearest.y, Pickup.Kind.GOLD_COIN, 8, Math.cos(a) * 110, Math.sin(a) * 110));
                    }
                    pickups.add(new Pickup(nearest.x, nearest.y, Pickup.Kind.XP_GEM, 28, 0, -90));
                    if (random.chance(0.55)) {
                        pickups.add(new Pickup(nearest.x, nearest.y, Pickup.Kind.HEALTH_POTION, 25, 75, 45));
                    }
                    eventBus.publish(new GameEvent.ChestOpened(nearest.x, nearest.y));
                    if (chestsOpened >= 3) {
                        unlockAchievement("TREASURE_HUNTER");
                    }
                }
            }
            case HEALING_SHRINE -> {
                if (!nearest.used && player.gold < 25) {
                    showNotEnoughGold(nearest, 25);
                } else if (!nearest.used) {
                    nearest.used = true;
                    player.gold -= 25;
                    player.heal(45.0);
                    particles.spawnShockwave(nearest.x, nearest.y, 90.0, 0.4, new Color(90, 255, 150));
                    addFloatingText(new FloatingText(player.x, player.y - 26, "+45 HP", new Color(90, 255, 150), 16f, 1.0));
                    eventBus.publish(new GameEvent.UiClick());
                }
            }
            case RELIC_ALTAR -> {
                if (!nearest.used && player.gold < 40) {
                    showNotEnoughGold(nearest, 40);
                } else if (!nearest.used) {
                    nearest.used = true;
                    player.gold -= 40;
                    pendingAltarUpgrade = true;
                    eventBus.publish(new GameEvent.UiClick());
                }
            }
            case EXIT_PORTAL -> {
                if (portalUnlocked) {
                    if (currentFloor < GameConfig.MAX_FLOORS) {
                        loadFloor(currentFloor + 1);
                    } else {
                        endRun(true);
                    }
                } else {
                    addFloatingText(new FloatingText(nearest.x, nearest.y - 26, I18n.t("hud.portal_locked"), new Color(255, 110, 95), 14f, 1.2));
                }
            }
        }
    }

    private void showNotEnoughGold(InteractiveProp prop, int cost) {
        String msg = String.format(I18n.t("hud.not_enough_gold"), cost, player.gold);
        addFloatingText(new FloatingText(prop.x, prop.y - 26, msg, new Color(255, 110, 95), 14f, 1.2));
    }

    public InteractiveProp getNearestInteractableProp() {
        InteractiveProp best = null;
        double bestDist = GameConfig.INTERACT_RADIUS;
        for (InteractiveProp prop : props) {
            if (prop.used && prop.type != InteractiveProp.PropType.EXIT_PORTAL) {
                continue;
            }
            double d = Math.hypot(player.x - prop.x, player.y - prop.y);
            if (d <= bestDist) {
                bestDist = d;
                best = prop;
            }
        }
        return best;
    }

    public void endRun(boolean isVictory) {
        if (runFinished) {
            return;
        }
        runFinished = true;
        victory = isVictory;

        if (isVictory) {
            // Bonus wird unten zusammen mit dem Rest einmal mit dem Schwierigkeitsfaktor multipliziert
            player.score += 600;
            if (difficulty != GameConfig.Difficulty.NORMAL) {
                unlockAchievement("SURVIVOR_HARD");
            }
            if (runDurationSeconds < 360.0) {
                unlockAchievement("SPEEDRUNNER");
            }
        }

        int finalScore = (int) Math.round(player.score * difficulty.scoreMultiplier);
        player.score = finalScore;

        saveManager.recordRun(
                finalScore,
                player.heroClass.name(),
                difficulty.name(),
                currentFloor,
                totalKills,
                runDurationSeconds,
                isVictory,
                seed
        );
        eventBus.publish(new GameEvent.RunEnded(isVictory, finalScore, totalKills, runDurationSeconds));
    }

    @Override
    public void damageEnemy(Enemy enemy, double amount, boolean crit, double knockX, double knockY) {
        damageEnemy(enemy, amount, crit, knockX, knockY, null);
    }

    @Override
    public void damageEnemy(Enemy enemy, double amount, boolean crit, double knockX, double knockY, Color textColor) {
        if (!enemy.active || amount <= 0.0) {
            return;
        }
        enemy.hp -= amount;
        enemy.hitFlashTimer = 0.09;
        enemy.alerted = true;
        enemy.applyKnockback(knockX, knockY);
        totalDamageDealt += amount;

        Color txtColor = textColor != null ? textColor : (crit ? new Color(255, 220, 75) : Color.WHITE);
        float txtSize = crit ? 16f : (textColor != null ? 12f : 13f);
        String label = (crit ? "!" : "") + (int) Math.max(1, Math.round(amount));
        floatingTexts.add(new FloatingText(enemy.x, enemy.y - enemy.radius - 4, label, txtColor, txtSize, 0.65));
        particles.spawnBurst(enemy.x, enemy.y, crit ? 9 : 4, 40, 150, 3.6, 0.25, enemy.type.color, crit);

        if (crit) {
            camera.addShake(0.14);
        }

        if (enemy.hp <= 0.0) {
            killEnemy(enemy);
        }
    }

    private void killEnemy(Enemy enemy) {
        enemy.active = false;
        totalKills++;
        player.combo++;
        player.maxCombo = Math.max(player.maxCombo, player.combo);
        player.comboTimer = GameConfig.COMBO_DECAY_SECONDS;

        double comboBonus = 1.0 + Math.min(2.0, player.combo * 0.05);
        player.score += (int) Math.round(enemy.type.scoreReward * comboBonus);

        int vamp = player.getUpgradeCount("VAMPIRIC");
        if (vamp > 0) {
            player.heal(4.0 * vamp);
        }

        particles.spawnBurst(enemy.x, enemy.y, enemy.type.isBoss ? 48 : 16, 60, 240, 5.5, 0.45, enemy.type.color, true);
        camera.addShake(enemy.type.isBoss ? 0.65 : 0.16);

        // Beute droppen
        pickups.add(new Pickup(enemy.x, enemy.y, Pickup.Kind.XP_GEM, enemy.type.xpReward,
                random.nextDouble(-60, 60), random.nextDouble(-60, 60)));
        if (random.chance(0.65) || enemy.type.isBoss) {
            int coins = enemy.type.isBoss ? 8 : random.nextInt(1, 3);
            for (int i = 0; i < coins; i++) {
                pickups.add(new Pickup(enemy.x, enemy.y, Pickup.Kind.GOLD_COIN, 5,
                        random.nextDouble(-90, 90), random.nextDouble(-90, 90)));
            }
        }
        if (random.chance(GameConfig.POTION_DROP_CHANCE)) {
            pickups.add(new Pickup(enemy.x, enemy.y,
                    random.chance(0.65) ? Pickup.Kind.HEALTH_POTION : Pickup.Kind.SHIELD_ORB,
                    18, random.nextDouble(-50, 50), random.nextDouble(-50, 50)));
        }

        // Älterer Schleim spaltet sich in 2 kleine Schleime
        if (enemy.type == EnemyType.ELDER_SLIME) {
            for (int i = 0; i < 2; i++) {
                double ox = (i == 0 ? -18.0 : 18.0);
                if (!map.collidesCircleWithWalls(enemy.x + ox, enemy.y, 13.0)) {
                    Enemy child = new Enemy(enemy.x + ox, enemy.y, EnemyType.SLIME, enemy.homeRoomId, 0.7, difficulty.enemySpeedMultiplier);
                    child.alerted = true;
                    spawnEnemy(child);
                }
            }
        }

        if (enemy.type.isBoss) {
            unlockAchievement("BOSS_SLAYER");
        }
        if (totalKills == 1) {
            unlockAchievement("FIRST_BLOOD");
        }
        if (player.combo >= 10) {
            unlockAchievement("COMBO_10");
        }

        eventBus.publish(new GameEvent.EnemyKilled(enemy.type.name(), enemy.type.isBoss, enemy.x, enemy.y, player.combo));
    }

    @Override
    public void damagePlayer(double amount, double knockX, double knockY) {
        if (player.isInvulnerable() || runFinished) {
            return;
        }
        // Jeder Schaden am Spieler stammt von Gegnern oder Fallen: hier zentral skalieren
        amount *= difficulty.enemyDamageMultiplier * (1.0 + (currentFloor - 1) * GameConfig.FLOOR_DAMAGE_SCALING);
        player.invulnTimer = GameConfig.PLAYER_HIT_INVULN;
        player.hitFlashTimer = 0.16;
        player.shieldRegenDelay = 4.0;
        player.applyKnockback(knockX, knockY);
        camera.addShake(0.32);

        double remaining = amount;
        if (player.shield > 0.0) {
            double absorbed = Math.min(player.shield, remaining);
            player.shield -= absorbed;
            remaining -= absorbed;
        }
        if (remaining > 0.0) {
            player.hp = Math.max(0.0, player.hp - remaining);
        }

        floatingTexts.add(new FloatingText(player.x, player.y - 22, "-" + (int) Math.ceil(amount), new Color(255, 85, 85), 15f, 0.75));
        particles.spawnBurst(player.x, player.y, 10, 50, 180, 4.0, 0.3, new Color(255, 80, 80), true);
        eventBus.publish(new GameEvent.PlayerHurt(amount, player.hp));

        if (player.hp <= 0.0) {
            endRun(false);
        }
    }

    private void unlockAchievement(String id) {
        if (saveManager.unlockAchievement(id)) {
            Achievement ach = Achievement.findById(id);
            if (ach != null) {
                toasts.addLast(new AchievementToast(ach));
            }
        }
    }

    public AchievementToast getCurrentToast() {
        return toasts.peekFirst();
    }

    public Enemy getActiveBoss() {
        for (Enemy e : enemies) {
            if (e.active && e.type.isBoss && e.alerted) {
                return e;
            }
        }
        return null;
    }

    public void applyChosenUpgrade(Upgrade u) {
        player.applyUpgrade(u);
        particles.spawnShockwave(player.x, player.y, 110.0, 0.38, u.rarity.color);
        addFloatingText(new FloatingText(player.x, player.y - 28, u.getName(), u.rarity.color, 15f, 1.1));
    }

    // --- CombatWorld & Getter ---
    @Override public DungeonMap getMap() { return map; }
    @Override public Player getPlayer() { return player; }
    @Override public List<Enemy> getEnemies() { return enemies; }
    @Override public AStarPathfinder getPathfinder() { return pathfinder; }
    @Override public ParticleSystem getParticles() { return particles; }
    @Override public Camera getCamera() { return camera; }
    @Override public SeededRandom getRandom() { return random; }
    @Override public GameConfig.Difficulty getDifficulty() { return difficulty; }
    @Override public void spawnProjectile(Projectile p) { pendingProjectiles.add(p); }
    @Override public void spawnEnemy(Enemy e) { pendingEnemies.add(e); }
    @Override public void spawnHazardZone(HazardZone zone) { pendingHazardZones.add(zone); }
    @Override public void addFloatingText(FloatingText text) { floatingTexts.add(text); }
    @Override public void publishEvent(GameEvent event) { eventBus.publish(event); }

    public int getCurrentFloor() { return currentFloor; }
    public List<Projectile> getProjectiles() { return projectiles; }
    public List<HazardZone> getHazardZones() { return hazardZones; }
    public List<Pickup> getPickups() { return pickups; }
    public List<InteractiveProp> getProps() { return props; }
    public List<FloatingText> getFloatingTexts() { return floatingTexts; }
}
