package top.morndream.mcPets.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import top.morndream.mcPets.McPets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PluginConfig {

    private final McPets plugin;
    private FileConfiguration config;

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

    private Set<EntityType> blacklist = Set.of();

    private double attackDamage;
    private int maxHits;
    private int hateSeconds;
    private double autoRange;
    private double commandRange;
    private int attackInterval;
    private double attackSpeed;
    private boolean attackDefault;

    private List<Double> scaleTiers = List.of(0.5, 1.0, 2.0);
    private int defaultScaleTier;

    private double mouthForward;
    private double mouthUp;
    private float mouthDisplayScale;
    private int particleInterval;

    public PluginConfig(McPets plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

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
        followIntervalTicks = Math.max(1, config.getInt("settings.follow-interval-ticks", 8));
        missingCheckAttempts = Math.max(1, config.getInt("settings.missing-check-attempts", 3));
        missingCheckIntervalTicks = Math.max(20, config.getInt("settings.missing-check-interval-ticks", 100));
        unloadMode = UnloadMode.fromConfig(config.getString("settings.unload-mode", "park"));
        maxDisplayNameLength = Math.max(1, config.getInt("settings.max-display-name-length", 24));

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
        autoRange = config.getDouble("attack.auto-range", 12);
        commandRange = config.getDouble("attack.command-range", 5);
        attackInterval = config.getInt("attack.interval-ticks", 20);
        attackSpeed = config.getDouble("attack.speed", 1.35);

        List<Double> tiers = config.getDoubleList("scale.tiers");
        if (tiers.isEmpty()) {
            scaleTiers = List.of(0.5, 1.0, 2.0);
        } else {
            scaleTiers = List.copyOf(tiers);
        }
        defaultScaleTier = config.getInt("scale.default-tier", 1);

        mouthForward = config.getDouble("mouth-item.offset-forward", 0.35);
        mouthUp = config.getDouble("mouth-item.offset-up", 0.15);
        mouthDisplayScale = (float) config.getDouble("mouth-item.display-scale", 0.45);
        particleInterval = config.getInt("particles.interval-ticks", 10);
    }

    public String message(String path) {
        return config.getString("messages." + path, path);
    }

    public List<String> messageList(String path) {
        return config.getStringList("messages." + path);
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

    public double getCommandRange() {
        return commandRange;
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

    public int getParticleInterval() {
        return particleInterval;
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
