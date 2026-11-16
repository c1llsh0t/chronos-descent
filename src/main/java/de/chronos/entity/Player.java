package de.chronos.entity;

import de.chronos.combat.FloatingText;
import de.chronos.combat.StatusEffect;
import de.chronos.combat.Upgrade;
import de.chronos.core.GameConfig;
import de.chronos.core.event.GameEvent;
import de.chronos.math.Vector2D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spieler-Entität mit Klassenfähigkeiten, Dash-Mechanik, XP/Levelsystem
 * und stapelbaren Roguelite-Upgrades.
 */
public final class Player extends Entity {

    public final HeroClass heroClass;

    // Ressourcen & Progression
    public double shield;
    public double maxShield;
    public int level = 1;
    public int xp = 0;
    public int xpToNext = 55;
    public int gold = 0;
    public int score = 0;
    public int combo = 0;
    public int maxCombo = 0;
    public double comboTimer = 0.0;
    public int pendingLevelUps = 0;

    // Cooldowns & Zustände
    public double attackTimer = 0.0;
    public double dashTimer = 0.0;
    public double dashCooldownTimer = 0.0;
    public double skillQTimer = 0.0;
    public double skillETimer = 0.0;
    public double invulnTimer = 0.0;
    public double shieldRegenDelay = 0.0;
    private double frostAuraTick = 0.0;
    private double dashDirX = 0.0;
    private double dashDirY = 0.0;
    private double afterimageTick = 0.0;

    private final Map<String, Integer> upgradeStacks = new LinkedHashMap<>();

    public Player(double x, double y, HeroClass heroClass) {
        super(x, y, 15.0, heroClass.baseHp);
        this.heroClass = heroClass;
        this.maxShield = heroClass.baseMaxShield;
        this.shield = this.maxShield;
    }

    public int getUpgradeCount(String id) {
        return upgradeStacks.getOrDefault(id, 0);
    }

    public Map<String, Integer> getUpgradeStacks() {
        return Collections.unmodifiableMap(upgradeStacks);
    }

    public void applyUpgrade(Upgrade upgrade) {
        upgradeStacks.merge(upgrade.id, 1, Integer::sum);
        switch (upgrade.id) {
            case "VITAL_HEART" -> {
                maxHp += 30.0;
                heal(30.0);
            }
            case "AEGIS_WARD" -> {
                maxShield += 25.0;
                shield = Math.min(maxShield, shield + 25.0);
            }
            case "PHOENIX_SOUL" -> {
                maxHp += 40.0;
                hp = maxHp;
            }
            default -> {
                // Andere Upgrades wirken dynamisch über Getter/Effekte
            }
        }
    }

    public void heal(double amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public boolean addXp(int amount) {
        xp += amount;
        boolean leveled = false;
        while (xp >= xpToNext) {
            xp -= xpToNext;
            level++;
            xpToNext = (int) Math.round(xpToNext * 1.32 + 25);
            maxHp += 8.0;
            heal(18.0);
            pendingLevelUps++;
            leveled = true;
        }
        return leveled;
    }

    public double getEffectiveSpeed() {
        double mult = 1.0 + 0.12 * getUpgradeCount("SWIFT_STEP") + 0.20 * getUpgradeCount("CHRONO_OVERDRIVE");
        // FRENZY-Tempobonus steckt bereits in getSpeedMultiplierFromStatus()
        return heroClass.baseSpeed * mult * getSpeedMultiplierFromStatus();
    }

    public double getEffectiveDamage() {
        double mult = 1.0 + 0.20 * getUpgradeCount("POWER_STRIKE") + 0.30 * getUpgradeCount("CHRONO_OVERDRIVE");
        return heroClass.baseDamage * mult;
    }

    public double getEffectiveAttackCooldown() {
        double speedBonus = 1.0 + 0.18 * getUpgradeCount("RAPID_HANDS");
        if (hasStatus(StatusEffect.Type.FRENZY)) {
            speedBonus += 0.45;
        }
        return Math.max(0.08, heroClass.attackCooldown / speedBonus);
    }

    public double getEffectiveCritChance() {
        return Math.min(0.85, heroClass.baseCritChance
                + 0.12 * getUpgradeCount("CRIT_FOCUS")
                + 0.15 * getUpgradeCount("CHRONO_OVERDRIVE"));
    }

    public double getCritMultiplier() {
        return 1.75 + 0.25 * getUpgradeCount("CRIT_FOCUS");
    }

    public double getCooldownScale() {
        return Math.pow(0.78, getUpgradeCount("CHRONOS_HASTE"));
    }

    public double getDashCooldownMax() {
        return GameConfig.DASH_COOLDOWN * Math.pow(0.85, getUpgradeCount("SWIFT_STEP"));
    }

    public boolean isDashing() {
        return dashTimer > 0.0;
    }

    public boolean isInvulnerable() {
        return invulnTimer > 0.0 || isDashing();
    }

    public void update(double dt, double moveX, double moveY, double aimAngle, CombatWorld world) {
        updateBaseTimers(dt, world);
        this.facingAngle = aimAngle;

        if (attackTimer > 0.0) attackTimer = Math.max(0.0, attackTimer - dt);
        if (dashCooldownTimer > 0.0) dashCooldownTimer = Math.max(0.0, dashCooldownTimer - dt);
        if (skillQTimer > 0.0) skillQTimer = Math.max(0.0, skillQTimer - dt);
        if (skillETimer > 0.0) skillETimer = Math.max(0.0, skillETimer - dt);
        if (invulnTimer > 0.0) invulnTimer = Math.max(0.0, invulnTimer - dt);

        if (combo > 0) {
            comboTimer -= dt;
            if (comboTimer <= 0.0) {
                combo = 0;
            }
        }

        // Schild-Regeneration nach kurzer Schadenspause
        if (shieldRegenDelay > 0.0) {
            shieldRegenDelay -= dt;
        } else if (shield < maxShield) {
            double regenRate = 4.5 + 3.0 * getUpgradeCount("AEGIS_WARD");
            shield = Math.min(maxShield, shield + regenRate * dt);
        }

        // Frostkranz-Upgrade: verlangsamt und verletzt nahe Gegner periodisch
        int frostStacks = getUpgradeCount("FROST_AURA");
        if (frostStacks > 0) {
            frostAuraTick += dt;
            if (frostAuraTick >= 0.5) {
                frostAuraTick = 0.0;
                double auraRadius = 115.0;
                for (Enemy e : new ArrayList<>(world.getEnemies())) {
                    if (!e.active) continue;
                    double d = pos().distanceTo(e.pos());
                    if (d <= auraRadius) {
                        e.applyStatus(StatusEffect.Type.SLOW, 1.2, 0.35);
                        world.damageEnemy(e, 4.0 * frostStacks, false, 0, 0);
                    }
                }
            }
        }

        // Bewegung (Dash überschreibt normale Steuerung)
        double stepX;
        double stepY;
        if (dashTimer > 0.0) {
            dashTimer -= dt;
            double dashSpeed = getEffectiveSpeed() * GameConfig.DASH_SPEED_MULTIPLIER;
            stepX = dashDirX * dashSpeed * dt;
            stepY = dashDirY * dashSpeed * dt;

            afterimageTick -= dt;
            if (afterimageTick <= 0.0) {
                afterimageTick = 0.032;
                world.getParticles().spawnAfterimage(x, y, radius, heroClass.primaryColor);
            }
        } else {
            Vector2D moveVec = new Vector2D(moveX, moveY);
            if (moveVec.lengthSquared() > 1e-6) {
                moveVec = moveVec.normalize();
            }
            double speed = getEffectiveSpeed();
            vx = moveVec.x * speed;
            vy = moveVec.y * speed;
            stepX = (vx + knockVx) * dt;
            stepY = (vy + knockVy) * dt;
        }

        Vector2D nextPos = world.getMap().moveWithCollision(x, y, stepX, stepY, radius);
        x = nextPos.x;
        y = nextPos.y;
    }

    public boolean tryDash(double moveX, double moveY, CombatWorld world) {
        if (dashCooldownTimer > 0.0 || dashTimer > 0.0) {
            return false;
        }
        Vector2D dir = (Math.abs(moveX) > 0.01 || Math.abs(moveY) > 0.01)
                ? new Vector2D(moveX, moveY).normalize()
                : Vector2D.fromAngle(facingAngle, 1.0);

        dashDirX = dir.x;
        dashDirY = dir.y;
        dashTimer = GameConfig.DASH_DURATION;
        dashCooldownTimer = getDashCooldownMax();
        afterimageTick = 0.0;

        // Klassenspezifische Dash-Effekte
        if (heroClass == HeroClass.SHADOWBLADE) {
            world.spawnHazardZone(new HazardZone(
                    x, y, 68.0, 0.28, getEffectiveDamage() * 1.4, false, heroClass.primaryColor));
        } else if (heroClass == HeroClass.ARCANIST) {
            world.getParticles().spawnShockwave(x, y, 52.0, 0.22, heroClass.accentColor);
        }

        world.getParticles().spawnBurst(x, y, 10, 40, 140, 4.0, 0.25, heroClass.accentColor, true);
        world.publishEvent(new GameEvent.DashPerformed(x, y));
        return true;
    }

    public boolean tryPrimaryAttack(CombatWorld world) {
        if (attackTimer > 0.0) {
            return false;
        }
        attackTimer = getEffectiveAttackCooldown();

        boolean crit = world.getRandom().nextDouble() < getEffectiveCritChance();
        double dmg = getEffectiveDamage() * (crit ? getCritMultiplier() : 1.0);
        int extra = getUpgradeCount("MULTISHOT");

        switch (heroClass) {
            case VANGUARD -> performVanguardSlash(dmg, crit, extra, world);
            case ARCANIST -> performArcanistBolts(dmg, crit, extra, world);
            case SHADOWBLADE -> performShadowDaggers(dmg, crit, extra, world);
        }

        world.publishEvent(new GameEvent.AttackPerformed(heroClass.name(), crit));
        return true;
    }

    private void performVanguardSlash(double dmg, boolean crit, int extra, CombatWorld world) {
        double reach = 82.0 * (1.0 + 0.22 * extra);
        double halfArc = Math.toRadians(68.0);
        world.getParticles().spawnSlash(x, y, facingAngle, 136.0, reach * 0.88,
                crit ? new Color(255, 224, 96) : heroClass.accentColor);

        Vector2D facing = Vector2D.fromAngle(facingAngle, 1.0);
        for (Enemy e : new ArrayList<>(world.getEnemies())) {
            if (!e.active) continue;
            Vector2D toEnemy = new Vector2D(e.x - x, e.y - y);
            double dist = toEnemy.length();
            if (dist <= reach + e.radius) {
                Vector2D dir = toEnemy.normalize();
                double dot = facing.dot(dir);
                double angleDiff = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
                if (dist < radius + e.radius + 12.0 || angleDiff <= halfArc) {
                    if (world.getMap().hasLineOfSight(x, y, e.x, e.y)) {
                        double finalDmg = applyExecutioner(dmg, e);
                        world.damageEnemy(e, finalDmg, crit, dir.x * 260.0, dir.y * 260.0);
                        applyWeaponOnHitEffects(e, finalDmg, crit, world);
                    }
                }
            }
        }
    }

    private void performArcanistBolts(double dmg, boolean crit, int extra, CombatWorld world) {
        int count = 1 + extra;
        double spread = Math.toRadians(11.0);
        double startAngle = facingAngle - spread * (count - 1) / 2.0;

        for (int i = 0; i < count; i++) {
            double a = startAngle + i * spread;
            Vector2D vel = Vector2D.fromAngle(a, 540.0);
            Projectile p = new Projectile(
                    x + Math.cos(a) * 18.0,
                    y + Math.sin(a) * 18.0,
                    vel.x, vel.y,
                    7.0, dmg, true, 1.5, heroClass.accentColor
            );
            p.crit = crit;
            p.homing = true;
            p.bouncesLeft = getUpgradeCount("RICOCHET");
            p.pierceLeft = getUpgradeCount("RICOCHET");
            p.explosive = crit && getUpgradeCount("EXPLOSIVE_IMPACT") > 0;
            if (getUpgradeCount("PHOENIX_SOUL") > 0) {
                p.applyStatus = StatusEffect.Type.BURN;
                p.statusDuration = 3.0;
                p.statusMagnitude = 7.0;
            }
            world.spawnProjectile(p);
        }
    }

    private void performShadowDaggers(double dmg, boolean crit, int extra, CombatWorld world) {
        int count = 1 + extra;
        double spread = Math.toRadians(8.0);
        double startAngle = facingAngle - spread * (count - 1) / 2.0;

        for (int i = 0; i < count; i++) {
            double a = startAngle + i * spread;
            Vector2D vel = Vector2D.fromAngle(a, 690.0);
            Projectile p = new Projectile(
                    x + Math.cos(a) * 16.0,
                    y + Math.sin(a) * 16.0,
                    vel.x, vel.y,
                    5.5, dmg, true, 1.05, heroClass.accentColor
            );
            p.crit = crit;
            p.bouncesLeft = getUpgradeCount("RICOCHET");
            p.pierceLeft = 1 + getUpgradeCount("RICOCHET");
            p.explosive = crit && getUpgradeCount("EXPLOSIVE_IMPACT") > 0;
            p.applyStatus = StatusEffect.Type.POISON;
            p.statusDuration = 2.5;
            p.statusMagnitude = 4.5;
            world.spawnProjectile(p);
        }
    }

    public boolean trySkillQ(CombatWorld world) {
        if (skillQTimer > 0.0) {
            return false;
        }
        skillQTimer = heroClass.skillQCooldown * getCooldownScale();
        double baseDmg = getEffectiveDamage();

        switch (heroClass) {
            case VANGUARD -> {
                double radius = 128.0;
                world.getParticles().spawnShockwave(x, y, radius, 0.28, heroClass.primaryColor);
                world.getParticles().spawnSlash(x, y, 0, 355, radius * 0.85, heroClass.accentColor);
                world.getCamera().addShake(0.32);
                for (Enemy e : new ArrayList<>(world.getEnemies())) {
                    if (!e.active) continue;
                    double d = pos().distanceTo(e.pos());
                    if (d <= radius + e.radius) {
                        Vector2D dir = new Vector2D(e.x - x, e.y - y).normalize();
                        world.damageEnemy(e, baseDmg * 1.9, true, dir.x * 360.0, dir.y * 360.0);
                        e.applyStatus(StatusEffect.Type.STUN, 0.85, 1.0);
                    }
                }
            }
            case ARCANIST -> {
                double radius = 155.0;
                world.getParticles().spawnShockwave(x, y, radius, 0.35, new Color(110, 235, 255));
                world.getParticles().spawnBurst(x, y, 26, 70, 240, 5.0, 0.45, new Color(150, 245, 255), true);
                world.getCamera().addShake(0.28);
                for (Enemy e : new ArrayList<>(world.getEnemies())) {
                    if (!e.active) continue;
                    double d = pos().distanceTo(e.pos());
                    if (d <= radius + e.radius) {
                        Vector2D dir = new Vector2D(e.x - x, e.y - y).normalize();
                        world.damageEnemy(e, baseDmg * 1.65, false, dir.x * 220.0, dir.y * 220.0);
                        e.applyStatus(StatusEffect.Type.SLOW, 3.2, 0.55);
                    }
                }
            }
            case SHADOWBLADE -> {
                int blades = 9;
                double cone = Math.toRadians(135.0);
                double start = facingAngle - cone / 2.0;
                for (int i = 0; i < blades; i++) {
                    double a = start + (cone * i / (blades - 1));
                    Vector2D vel = Vector2D.fromAngle(a, 660.0);
                    Projectile p = new Projectile(x, y, vel.x, vel.y, 5.5, baseDmg * 1.25, true, 0.9, heroClass.accentColor);
                    p.pierceLeft = 2;
                    p.applyStatus = StatusEffect.Type.POISON;
                    p.statusDuration = 3.0;
                    p.statusMagnitude = 5.0;
                    world.spawnProjectile(p);
                }
                world.getParticles().spawnDirectionalSpark(x, y, facingAngle, cone * 0.5, 14, 220, heroClass.accentColor);
            }
        }

        world.publishEvent(new GameEvent.SkillCast(1, x, y));
        return true;
    }

    public boolean trySkillE(Vector2D aimWorld, CombatWorld world) {
        if (skillETimer > 0.0) {
            return false;
        }
        skillETimer = heroClass.skillECooldown * getCooldownScale();
        double baseDmg = getEffectiveDamage();

        switch (heroClass) {
            case VANGUARD -> {
                invulnTimer = Math.max(invulnTimer, 1.6);
                shield = Math.min(maxShield + 40.0, shield + 40.0);
                world.getParticles().spawnShockwave(x, y, 145.0, 0.4, new Color(255, 220, 100));
                world.addFloatingText(new FloatingText(x, y - 28, "AEGIS!", new Color(255, 225, 110), 16f, 0.9));
                for (Enemy e : new ArrayList<>(world.getEnemies())) {
                    if (e.active && pos().distanceTo(e.pos()) <= 145.0) {
                        Vector2D dir = new Vector2D(e.x - x, e.y - y).normalize();
                        world.damageEnemy(e, baseDmg * 2.1, true, dir.x * 420.0, dir.y * 420.0);
                    }
                }
            }
            case ARCANIST -> {
                // 5 Arkane Meteore im Zielbereich
                for (int i = 0; i < 5; i++) {
                    double ox = (i == 0) ? 0.0 : world.getRandom().nextDouble(-78.0, 78.0);
                    double oy = (i == 0) ? 0.0 : world.getRandom().nextDouble(-78.0, 78.0);
                    world.spawnHazardZone(new HazardZone(
                            aimWorld.x + ox, aimWorld.y + oy,
                            68.0, 0.32 + i * 0.10, baseDmg * 2.2, false, new Color(255, 118, 66)
                    ));
                }
                world.getCamera().addShake(0.35);
            }
            case SHADOWBLADE -> {
                applyStatus(StatusEffect.Type.FRENZY, 5.0, 0.45);
                dashCooldownTimer = 0.0;
                skillQTimer = 0.0;
                world.getParticles().spawnShockwave(x, y, 90.0, 0.25, heroClass.primaryColor);
                world.addFloatingText(new FloatingText(x, y - 28, "FRENZY!", heroClass.accentColor, 16f, 0.9));
            }
        }

        world.publishEvent(new GameEvent.SkillCast(2, x, y));
        return true;
    }

    public void applyWeaponOnHitEffects(Enemy hitEnemy, double dealtDamage, boolean crit, CombatWorld world) {
        if (getUpgradeCount("PHOENIX_SOUL") > 0) {
            hitEnemy.applyStatus(StatusEffect.Type.BURN, 3.0, 7.0);
        }
        int chain = getUpgradeCount("CHAIN_LIGHTNING");
        if (chain > 0) {
            Enemy closest = null;
            double bestDistSq = 185.0 * 185.0;
            for (Enemy other : world.getEnemies()) {
                if (!other.active || other == hitEnemy) continue;
                double d2 = hitEnemy.pos().distanceSquaredTo(other.pos());
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    closest = other;
                }
            }
            if (closest != null) {
                world.getParticles().spawnLightning(hitEnemy.x, hitEnemy.y, closest.x, closest.y, new Color(115, 225, 255));
                world.damageEnemy(closest, dealtDamage * (0.45 + 0.15 * chain), false, 0, 0);
            }
        }
        if (crit && getUpgradeCount("EXPLOSIVE_IMPACT") > 0) {
            triggerExplosion(hitEnemy.x, hitEnemy.y, dealtDamage * 0.55, world);
        }
    }

    public void triggerExplosion(double ex, double ey, double aoEDamage, CombatWorld world) {
        double radius = 78.0;
        world.getParticles().spawnShockwave(ex, ey, radius, 0.24, new Color(255, 145, 70));
        world.getParticles().spawnBurst(ex, ey, 14, 50, 180, 4.5, 0.35, new Color(255, 180, 75), true);
        for (Enemy e : new ArrayList<>(world.getEnemies())) {
            if (!e.active) continue;
            if (Math.hypot(e.x - ex, e.y - ey) <= radius + e.radius) {
                world.damageEnemy(e, aoEDamage, false, 0, 0);
            }
        }
    }

    public double applyExecutioner(double dmg, Enemy e) {
        int stacks = getUpgradeCount("EXECUTIONER");
        if (stacks > 0 && e.hp < e.maxHp * 0.40) {
            return dmg * (1.0 + 0.40 * stacks);
        }
        return dmg;
    }

    @Override
    protected void onStatusDotTick(StatusEffect.Type type, double dmg, CombatWorld world) {
        if (!isInvulnerable()) {
            world.damagePlayer(dmg, 0, 0);
        }
    }
}
