package de.chronos.core.event;

/**
 * Typisierte Ereignisse zur Entkopplung von Spiellogik, Audio, Partikeln und Achievements.
 */
public sealed interface GameEvent permits
        GameEvent.EnemyKilled,
        GameEvent.PlayerHurt,
        GameEvent.AttackPerformed,
        GameEvent.DashPerformed,
        GameEvent.SkillCast,
        GameEvent.PickupCollected,
        GameEvent.LevelUp,
        GameEvent.ChestOpened,
        GameEvent.FloorChanged,
        GameEvent.BossPhaseChanged,
        GameEvent.RunEnded,
        GameEvent.UiClick {

    record EnemyKilled(String enemyTypeId, boolean wasBoss, double x, double y, int combo) implements GameEvent {}
    record PlayerHurt(double damage, double remainingHp) implements GameEvent {}
    record AttackPerformed(String classId, boolean critical) implements GameEvent {}
    record DashPerformed(double x, double y) implements GameEvent {}
    record SkillCast(int skillSlot, double x, double y) implements GameEvent {}
    record PickupCollected(String kind) implements GameEvent {}
    record LevelUp(int newLevel) implements GameEvent {}
    record ChestOpened(double x, double y) implements GameEvent {}
    record FloorChanged(int newFloor) implements GameEvent {}
    record BossPhaseChanged(int newPhase) implements GameEvent {}
    record RunEnded(boolean victory, int score, int kills, double durationSeconds) implements GameEvent {}
    record UiClick() implements GameEvent {}
}
