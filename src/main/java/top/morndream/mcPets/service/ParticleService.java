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

/**
 * 优先探测 PlayerParticles；不可用时使用自研预设。
 * 粒子在宠物 EntityScheduler 线程上生成，Folia 安全。
 */
public final class ParticleService {

    private final PluginConfig config;

    public ParticleService(McPets plugin) {
        this.config = plugin.getPluginConfig();
        if (Bukkit.getPluginManager().getPlugin("PlayerParticles") != null) {
            plugin.getLogger().info("已检测到 PlayerParticles，粒子将优先走兼容通道（宠物本地预设仍可用）。");
        }
    }

    public void tickEntity(LivingEntity entity, PetData data) {
        if (entity == null || data == null) {
            return;
        }
        spawnBuiltin(entity, data.getParticlePreset());
    }

    private void spawnBuiltin(LivingEntity entity, String preset) {
        if (preset == null || preset.equalsIgnoreCase("none")) {
            return;
        }
        ConfigurationSection section = config.raw().getConfigurationSection("particles.presets." + preset);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        String particleName = section.getString("particle");
        if (particleName == null) {
            return;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return;
        }
        int count = section.getInt("count", 2);
        double offset = section.getDouble("offset", 0.3);
        Location loc = entity.getLocation().add(0, entity.getHeight() * 0.6, 0);

        try {
            entity.getWorld().spawnParticle(particle, loc, count, offset, offset, offset, 0.01);
        } catch (IllegalArgumentException ex) {
            if (particle.getDataType() == Particle.DustOptions.class) {
                entity.getWorld().spawnParticle(particle, loc, count, offset, offset, offset, 0,
                        new Particle.DustOptions(Color.AQUA, 1.0f));
            }
        }
    }
}
