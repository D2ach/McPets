package top.morndream.mcPets.service;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 优先探测 PlayerParticles；不可用时使用自研预设。
 * 预设在 reload 时缓存，AI tick 不再读 YAML / valueOf。
 */
public final class ParticleService {

    public record Preset(String id, boolean enabled, Particle particle, int count, double offset, boolean dust) {
    }

    private final McPets plugin;
    private final PluginConfig config;
    private volatile Map<String, Preset> presets = Map.of();

    public ParticleService(McPets plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
        if (Bukkit.getPluginManager().getPlugin("PlayerParticles") != null) {
            plugin.getLogger().info("已检测到 PlayerParticles，粒子将优先走兼容通道（宠物本地预设仍可用）。");
        }
        reloadCache();
    }

    /** 配置重载后重建缓存。 */
    public void reloadCache() {
        Map<String, Preset> map = new LinkedHashMap<>();
        ConfigurationSection root = config.raw().getConfigurationSection("particles.presets");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                boolean enabled = section.getBoolean("enabled", true);
                if ("none".equalsIgnoreCase(id)) {
                    enabled = false;
                }
                String particleName = section.getString("particle");
                Particle particle = null;
                boolean dust = false;
                if (particleName != null && enabled) {
                    try {
                        particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
                        dust = particle.getDataType() == Particle.DustOptions.class;
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("未知粒子类型: " + particleName + " (预设 " + id + ")");
                        enabled = false;
                    }
                }
                int count = Math.max(1, section.getInt("count", 2));
                double offset = section.getDouble("offset", 0.3);
                map.put(id.toLowerCase(Locale.ROOT), new Preset(id, enabled, particle, count, offset, dust));
            }
        }
        if (!map.containsKey("none")) {
            map.put("none", new Preset("none", false, null, 0, 0, false));
        }
        presets = Collections.unmodifiableMap(map);
    }

    /** 是否为会真正刷粒子的预设（none / 禁用 / 无效 = false）。 */
    public boolean isActivePreset(String presetId) {
        if (presetId == null || presetId.isBlank() || "none".equalsIgnoreCase(presetId)) {
            return false;
        }
        Preset preset = presets.get(presetId.toLowerCase(Locale.ROOT));
        return preset != null && preset.enabled() && preset.particle() != null;
    }

    public void tickEntity(LivingEntity entity, PetData data) {
        if (entity == null || data == null) {
            return;
        }
        spawnBuiltin(entity, data.getParticlePreset());
    }

    private void spawnBuiltin(LivingEntity entity, String presetId) {
        if (presetId == null || "none".equalsIgnoreCase(presetId)) {
            return;
        }
        Preset preset = presets.get(presetId.toLowerCase(Locale.ROOT));
        if (preset == null || !preset.enabled() || preset.particle() == null) {
            return;
        }
        Location loc = entity.getLocation().add(0, entity.getHeight() * 0.6, 0);
        try {
            if (preset.dust()) {
                entity.getWorld().spawnParticle(preset.particle(), loc, preset.count(),
                        preset.offset(), preset.offset(), preset.offset(), 0,
                        new Particle.DustOptions(Color.AQUA, 1.0f));
            } else {
                entity.getWorld().spawnParticle(preset.particle(), loc, preset.count(),
                        preset.offset(), preset.offset(), preset.offset(), 0.01);
            }
        } catch (IllegalArgumentException ignored) {
            // 个别服务端粒子数据要求不同，忽略单次失败
        }
    }
}
