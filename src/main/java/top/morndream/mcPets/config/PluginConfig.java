package top.morndream.mcPets.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import top.morndream.mcPets.McPets;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PluginConfig {

    private final McPets plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    private double tameRange;
    private double interactRange;
    private double followStart;
    private double followTeleport;
    private double followSpeed;
    private double wanderRadius;
    private int wanderInterval;
    private boolean notifyMissing;
    private int autoSaveSeconds;
    private int maxPetsPerPlayer;
    private int followIntervalTicks;
    private int missingCheckAttempts;
    private int missingCheckIntervalTicks;
    private UnloadMode unloadMode = UnloadMode.PARK;
    private int maxDisplayNameLength;
    private boolean defaultInvincible;
    private boolean blockVillagerProfession;

    private Set<EntityType> blacklist = Set.of();

    private double attackDamage;
    private int maxHits;
    private int hateSeconds;
    private double autoRange;
    private int attackInterval;
    private double attackSpeed;
    private boolean attackDefault;

    private List<Double> scaleTiers = List.of(0.5, 1.0, 2.0);
    private int defaultScaleTier;

    private double mouthForward;
    private double mouthUp;
    private float mouthDisplayScale;
    private double mouthEyeFactor;
    private double floatOffsetUp;
    private double floatOffsetForward;
    private float floatDisplayScale;
    private boolean floatBob;
    private double floatBobAmplitude;
    private int particleInterval;
    private double farDistanceSq;
    private int farIntervalTicks;
    private int offlineIntervalTicks;
    private int watchdogIntervalTicks;

    public PluginConfig(McPets plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        reloadMessages();

        tameRange = config.getDouble("settings.tame-range", 8);
        interactRange = config.getDouble("settings.interact-range", 5);
        followStart = config.getDouble("settings.follow-start-distance", 4);
        followTeleport = config.getDouble("settings.follow-teleport-distance", 16);
        followSpeed = config.getDouble("settings.follow-speed", 1.2);
        wanderRadius = config.getDouble("settings.wander-radius", 6);
        wanderInterval = config.getInt("settings.wander-interval-ticks", 60);
        notifyMissing = config.getBoolean("settings.notify-missing-pet", false);
        autoSaveSeconds = config.getInt("settings.auto-save-seconds", 120);
        maxPetsPerPlayer = Math.max(0, config.getInt("settings.max-pets-per-player", 5));
        followIntervalTicks = Math.max(1, config.getInt("settings.follow-interval-ticks", 14));
        missingCheckAttempts = Math.max(1, config.getInt("settings.missing-check-attempts", 3));
        missingCheckIntervalTicks = Math.max(20, config.getInt("settings.missing-check-interval-ticks", 100));
        unloadMode = UnloadMode.fromConfig(config.getString("settings.unload-mode", "park"));
        maxDisplayNameLength = Math.max(1, config.getInt("settings.max-display-name-length", 24));
        defaultInvincible = config.getBoolean("settings.default-invincible", true);
        blockVillagerProfession = config.getBoolean("settings.block-villager-profession", true);

        double farDistance = Math.max(8.0, config.getDouble("performance.far-distance", 48.0));
        farDistanceSq = farDistance * farDistance;
        farIntervalTicks = Math.max(1, config.getInt("performance.far-interval-ticks", 40));
        offlineIntervalTicks = Math.max(1, config.getInt("performance.offline-interval-ticks", 80));
        watchdogIntervalTicks = Math.max(40, config.getInt("performance.watchdog-interval-ticks", 400));

        Set<EntityType> types = new HashSet<>();
        for (String raw : config.getStringList("blacklist")) {
            try {
                types.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("未知黑名单实体: " + raw);
            }
        }
        blacklist = Collections.unmodifiableSet(types);

        attackDefault = config.getBoolean("attack.enabled-by-default", false);
        attackDamage = config.getDouble("attack.damage", 1.0);
        maxHits = config.getInt("attack.max-hits-per-target", 6);
        hateSeconds = config.getInt("attack.hate-duration-seconds", 60);
        autoRange = config.getDouble("attack.auto-range", 10);
        attackInterval = config.getInt("attack.interval-ticks", 20);
        attackSpeed = config.getDouble("attack.speed", 1.35);

        List<Double> tiers = config.getDoubleList("scale.tiers");
        if (tiers.isEmpty()) {
            scaleTiers = List.of(0.5, 1.0, 2.0);
        } else {
            scaleTiers = List.copyOf(tiers);
        }
        defaultScaleTier = config.getInt("scale.default-tier", 1);

        mouthForward = config.getDouble("mouth-item.offset-forward", 0.45);
        mouthUp = config.getDouble("mouth-item.offset-up", -0.55);
        mouthDisplayScale = (float) config.getDouble("mouth-item.display-scale", 0.35);
        mouthEyeFactor = config.getDouble("mouth-item.eye-factor", 1.0);
        floatOffsetUp = config.getDouble("float-item.offset-up", 1.15);
        floatOffsetForward = config.getDouble("float-item.offset-forward", 0.0);
        floatDisplayScale = (float) config.getDouble("float-item.display-scale", 0.5);
        floatBob = config.getBoolean("float-item.bob", true);
        floatBobAmplitude = config.getDouble("float-item.bob-amplitude", 0.08);
        particleInterval = Math.max(1, config.getInt("particles.interval-ticks", 16));
    }

    private void reloadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        // 兼容：若仍写在旧 config.yml 的 messages.* 且新文件缺键，可从 config 回退
    }

    public String message(String path) {
        if (messages != null && messages.contains(path)) {
            return messages.getString(path, path);
        }
        // 旧版 config.yml 中的 messages 回退
        if (config != null && config.contains("messages." + path)) {
            return config.getString("messages." + path, path);
        }
        return path;
    }

    public List<String> messageList(String path) {
        if (messages != null && messages.contains(path)) {
            return messages.getStringList(path);
        }
        if (config != null && config.contains("messages." + path)) {
            return config.getStringList("messages." + path);
        }
        return List.of();
    }

    public String prefix() {
        return message("prefix");
    }

    public boolean isBlacklisted(EntityType type) {
        return blacklist.contains(type);
    }

    public double getTameRange() {
        return tameRange;
    }

    public double getInteractRange() {
        return interactRange;
    }

    public double getFollowStart() {
        return followStart;
    }

    public double getFollowTeleport() {
        return followTeleport;
    }

    public double getFollowSpeed() {
        return followSpeed;
    }

    public double getWanderRadius() {
        return wanderRadius;
    }

    public int getWanderInterval() {
        return wanderInterval;
    }

    public boolean isNotifyMissing() {
        return notifyMissing;
    }

    public int getAutoSaveSeconds() {
        return autoSaveSeconds;
    }

    public int getMaxPetsPerPlayer() {
        return maxPetsPerPlayer;
    }

    public int getFollowIntervalTicks() {
        return followIntervalTicks;
    }

    public int getMissingCheckAttempts() {
        return missingCheckAttempts;
    }

    public int getMissingCheckIntervalTicks() {
        return missingCheckIntervalTicks;
    }

    public UnloadMode getUnloadMode() {
        return unloadMode;
    }

    public int getMaxDisplayNameLength() {
        return maxDisplayNameLength;
    }

    public boolean isDefaultInvincible() {
        return defaultInvincible;
    }

    /** true = 不允许宠物村民获取工作 */
    public boolean isBlockVillagerProfession() {
        return blockVillagerProfession;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public int getMaxHits() {
        return maxHits;
    }

    public int getHateSeconds() {
        return hateSeconds;
    }

    public double getAutoRange() {
        return autoRange;
    }

    public int getAttackInterval() {
        return attackInterval;
    }

    public double getAttackSpeed() {
        return attackSpeed;
    }

    public boolean isAttackDefault() {
        return attackDefault;
    }

    public List<Double> getScaleTiers() {
        return scaleTiers;
    }

    public int getDefaultScaleTier() {
        if (scaleTiers.isEmpty()) {
            return 0;
        }
        return Math.clamp(defaultScaleTier, 0, scaleTiers.size() - 1);
    }

    public double scaleValue(int tier) {
        if (scaleTiers.isEmpty()) {
            return 1.0;
        }
        int idx = Math.clamp(tier, 0, scaleTiers.size() - 1);
        return scaleTiers.get(idx);
    }

    public double getMouthForward() {
        return mouthForward;
    }

    public double getMouthUp() {
        return mouthUp;
    }

    public float getMouthDisplayScale() {
        return mouthDisplayScale;
    }

    public double getMouthEyeFactor() {
        return mouthEyeFactor;
    }

    public double getFloatOffsetUp() {
        return floatOffsetUp;
    }

    public double getFloatOffsetForward() {
        return floatOffsetForward;
    }

    public float getFloatDisplayScale() {
        return floatDisplayScale;
    }

    public boolean isFloatBob() {
        return floatBob;
    }

    public double getFloatBobAmplitude() {
        return floatBobAmplitude;
    }

    public int getParticleInterval() {
        return particleInterval;
    }

    public double getFarDistanceSq() {
        return farDistanceSq;
    }

    public int getFarIntervalTicks() {
        return farIntervalTicks;
    }

    public int getOfflineIntervalTicks() {
        return offlineIntervalTicks;
    }

    public int getWatchdogIntervalTicks() {
        return watchdogIntervalTicks;
    }

    public List<String> particlePresetIds() {
        var section = config.getConfigurationSection("particles.presets");
        if (section == null) {
            return List.of("none");
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public FileConfiguration raw() {
        return config;
    }
}
