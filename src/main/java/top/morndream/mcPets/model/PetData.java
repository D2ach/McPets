package top.morndream.mcPets.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一只宠物的持久化数据（轻量：必要字段 + 外观/开关）。
 */
public final class PetData {

    private final UUID petId;
    private UUID ownerId;
    private UUID entityId;
    private String internalName;
    private String displayName;
    private EntityType entityType;
    private PetState state = PetState.FOLLOW;
    private boolean attackEnabled;
    private boolean invincible;
    private boolean aiEnabled = true;
    private int scaleTier = 1;
    private boolean baby;
    private String particlePreset = "none";
    private String mouthItem = "none";
    private String floatItem = "none";
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    /** 驯服前原版快照：卸载时完整还原，避免污染世界存档 */
    private double vanillaScale = 1.0;
    private boolean vanillaBaby;
    private boolean vanillaRemoveWhenFarAway = true;
    private String vanillaCustomName;
    private boolean vanillaCustomNameVisible;
    /** 狐狸主手物品材质名；null 表示空或不适用 */
    private String vanillaFoxMainHand;

    /** 运行时：每个目标的命中次数（不落盘） */
    private final Map<UUID, Integer> hitCounts = new HashMap<>();
    /** 运行时：仇恨截止时间 epoch ms */
    private long hateUntil;
    private UUID attackTargetId;

    public PetData(UUID petId) {
        this.petId = petId;
    }

    public UUID getPetId() {
        return petId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String effectiveDisplayRaw() {
        return displayName == null || displayName.isBlank() ? internalName : displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public PetState getState() {
        return state;
    }

    public void setState(PetState state) {
        this.state = state == null ? PetState.FOLLOW : state;
    }

    public boolean isAttackEnabled() {
        return attackEnabled;
    }

    public void setAttackEnabled(boolean attackEnabled) {
        this.attackEnabled = attackEnabled;
    }

    public boolean isInvincible() {
        return invincible;
    }

    public void setInvincible(boolean invincible) {
        this.invincible = invincible;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public int getScaleTier() {
        return scaleTier;
    }

    public void setScaleTier(int scaleTier) {
        this.scaleTier = scaleTier;
    }

    public boolean isBaby() {
        return baby;
    }

    public void setBaby(boolean baby) {
        this.baby = baby;
    }

    public String getParticlePreset() {
        return particlePreset;
    }

    public void setParticlePreset(String particlePreset) {
        this.particlePreset = particlePreset == null ? "none" : particlePreset;
    }

    public String getMouthItem() {
        return mouthItem;
    }

    public void setMouthItem(String mouthItem) {
        this.mouthItem = mouthItem == null ? "none" : mouthItem;
    }

    public String getFloatItem() {
        return floatItem;
    }

    public void setFloatItem(String floatItem) {
        this.floatItem = floatItem == null ? "none" : floatItem;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this.worldName = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getVanillaScale() {
        return vanillaScale;
    }

    public void setVanillaScale(double vanillaScale) {
        this.vanillaScale = vanillaScale;
    }

    public boolean isVanillaBaby() {
        return vanillaBaby;
    }

    public void setVanillaBaby(boolean vanillaBaby) {
        this.vanillaBaby = vanillaBaby;
    }

    public boolean isVanillaRemoveWhenFarAway() {
        return vanillaRemoveWhenFarAway;
    }

    public void setVanillaRemoveWhenFarAway(boolean vanillaRemoveWhenFarAway) {
        this.vanillaRemoveWhenFarAway = vanillaRemoveWhenFarAway;
    }

    public String getVanillaCustomName() {
        return vanillaCustomName;
    }

    public void setVanillaCustomName(String vanillaCustomName) {
        this.vanillaCustomName = vanillaCustomName;
    }

    public boolean isVanillaCustomNameVisible() {
        return vanillaCustomNameVisible;
    }

    public void setVanillaCustomNameVisible(boolean vanillaCustomNameVisible) {
        this.vanillaCustomNameVisible = vanillaCustomNameVisible;
    }

    public String getVanillaFoxMainHand() {
        return vanillaFoxMainHand;
    }

    public void setVanillaFoxMainHand(String vanillaFoxMainHand) {
        this.vanillaFoxMainHand = vanillaFoxMainHand;
    }

    public Map<UUID, Integer> getHitCounts() {
        return hitCounts;
    }

    public long getHateUntil() {
        return hateUntil;
    }

    public void setHateUntil(long hateUntil) {
        this.hateUntil = hateUntil;
    }

    public UUID getAttackTargetId() {
        return attackTargetId;
    }

    public void setAttackTargetId(UUID attackTargetId) {
        this.attackTargetId = attackTargetId;
    }

    public void clearCombat() {
        hitCounts.clear();
        hateUntil = 0L;
        attackTargetId = null;
    }

    public void write(ConfigurationSection section) {
        section.set("pet-id", petId.toString());
        section.set("owner", ownerId.toString());
        section.set("entity", entityId.toString());
        section.set("internal-name", internalName);
        section.set("display-name", displayName);
        section.set("type", entityType == null ? null : entityType.name());
        section.set("state", state.name());
        section.set("attack-enabled", attackEnabled);
        section.set("invincible", invincible);
        section.set("ai-enabled", aiEnabled);
        section.set("scale-tier", scaleTier);
        section.set("baby", baby);
        section.set("particle", particlePreset);
        section.set("mouth", mouthItem);
        section.set("float", floatItem);
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
        section.set("vanilla.scale", vanillaScale);
        section.set("vanilla.baby", vanillaBaby);
        section.set("vanilla.remove-when-far-away", vanillaRemoveWhenFarAway);
        section.set("vanilla.custom-name", vanillaCustomName);
        section.set("vanilla.custom-name-visible", vanillaCustomNameVisible);
        section.set("vanilla.fox-main-hand", vanillaFoxMainHand);
    }

    public static PetData read(ConfigurationSection section) {
        UUID petId = UUID.fromString(section.getString("pet-id", UUID.randomUUID().toString()));
        PetData data = new PetData(petId);
        String ownerRaw = section.getString("owner");
        String entityRaw = section.getString("entity");
        if (ownerRaw == null || entityRaw == null) {
            throw new IllegalArgumentException("宠物条目缺少 owner/entity: " + petId);
        }
        data.ownerId = UUID.fromString(ownerRaw);
        data.entityId = UUID.fromString(entityRaw);
        data.internalName = section.getString("internal-name", "pet");
        data.displayName = section.getString("display-name");
        String type = section.getString("type");
        if (type != null) {
            try {
                data.entityType = EntityType.valueOf(type);
            } catch (IllegalArgumentException ignored) {
                data.entityType = EntityType.PIG;
            }
        }
        data.state = PetState.fromString(section.getString("state"));
        data.attackEnabled = section.getBoolean("attack-enabled", false);
        data.invincible = section.getBoolean("invincible", false);
        data.aiEnabled = section.getBoolean("ai-enabled", true);
        data.scaleTier = section.getInt("scale-tier", 1);
        data.baby = section.getBoolean("baby", false);
        data.particlePreset = section.getString("particle", "none");
        data.mouthItem = section.getString("mouth", "none");
        // 旧版「叼物」实际是头顶展示：无 float 字段时迁到悬浮物，叼物置空
        if (section.contains("float")) {
            data.floatItem = section.getString("float", "none");
        } else if (section.contains("mouth") && !"none".equalsIgnoreCase(data.mouthItem)) {
            data.floatItem = data.mouthItem;
            data.mouthItem = "none";
        } else {
            data.floatItem = "none";
        }
        data.worldName = section.getString("world");
        data.x = section.getDouble("x");
        data.y = section.getDouble("y");
        data.z = section.getDouble("z");
        data.yaw = (float) section.getDouble("yaw");
        data.pitch = (float) section.getDouble("pitch");
        data.vanillaScale = section.getDouble("vanilla.scale", 1.0);
        data.vanillaBaby = section.getBoolean("vanilla.baby", data.baby);
        data.vanillaRemoveWhenFarAway = section.getBoolean("vanilla.remove-when-far-away", true);
        data.vanillaCustomName = section.getString("vanilla.custom-name");
        data.vanillaCustomNameVisible = section.getBoolean("vanilla.custom-name-visible", false);
        data.vanillaFoxMainHand = section.getString("vanilla.fox-main-hand");
        return data;
    }
}
