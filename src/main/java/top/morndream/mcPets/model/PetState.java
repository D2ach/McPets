package top.morndream.mcPets.model;

/**
 * 宠物行为状态（已移除 STAY，与 IDLE 合并语义）。
 */
public enum PetState {
    FOLLOW,
    IDLE,
    WANDER,
    ATTACK;

    public static PetState fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return FOLLOW;
        }
        try {
            return PetState.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FOLLOW;
        }
    }

    public String display() {
        return switch (this) {
            case FOLLOW -> "跟随";
            case IDLE -> "待命";
            case WANDER -> "漫步";
            case ATTACK -> "攻击";
        };
    }
}
