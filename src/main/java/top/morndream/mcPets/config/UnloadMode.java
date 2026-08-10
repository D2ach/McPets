package top.morndream.mcPets.config;

/**
 * 插件被 disable / 热卸载时，对世界中已有宠物实体的处理策略。
 */
public enum UnloadMode {
    /** 实体留在世界：关闭无敌/攻击/叼物/插件 AI，保留名字与标记，启用后恢复 */
    PARK,
    /** 移除实体并写档，再次启用时按坐标重新生成（不保留装备等原版 NBT） */
    DESPAWN;

    public static UnloadMode fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return PARK;
        }
        try {
            return UnloadMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PARK;
        }
    }
}
