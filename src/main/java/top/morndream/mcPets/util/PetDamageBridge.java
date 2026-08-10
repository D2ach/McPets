package top.morndream.mcPets.util;

/**
 * 标记由 McPets AI 主动结算的伤害，避免被伤害监听器误取消。
 */
public final class PetDamageBridge {

    private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

    private PetDamageBridge() {
    }

    public static void runAllowed(Runnable action) {
        ALLOWED.set(true);
        try {
            action.run();
        } finally {
            ALLOWED.set(false);
        }
    }

    public static boolean isAllowed() {
        return Boolean.TRUE.equals(ALLOWED.get());
    }
}
