package top.morndream.mcPets.ai;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.model.PetState;
import top.morndream.mcPets.service.PetService;
import top.morndream.mcPets.util.PetDamageBridge;
import top.morndream.mcPets.util.SchedulerUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Folia 安全 AI：
 * - FOLLOW / WANDER：较长间隔
 * - ATTACK：较短间隔
 * - IDLE / AI 关闭：停掉运动 timer（有粒子时仅保留慢速粒子任务）
 */
public final class PetAIManager {

    private final McPets plugin;
    private final PetService pets;
    private final PluginConfig config;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ticks = new ConcurrentHashMap<>();
    private ScheduledTask watchdog;

    public PetAIManager(McPets plugin) {
        this.plugin = plugin;
        this.pets = plugin.getPetService();
        this.config = plugin.getPluginConfig();
    }

    public void start() {
        attachAllLoaded();
        watchdog = SchedulerUtil.runGlobalTimer(plugin, this::attachAllLoaded, 100L, 100L);
    }

    public void shutdown() {
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        for (ScheduledTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        ticks.clear();
    }

    public void attachAllLoaded() {
        for (PetData data : pets.storage().all()) {
            if (tasks.containsKey(data.getPetId())) {
                continue;
            }
            LivingEntity entity = pets.findEntity(data);
            if (entity != null) {
                resync(data, entity);
            }
        }
    }

    public void ensureStarted(PetData data, LivingEntity entity) {
        resync(data, entity);
    }

    /**
     * 按当前状态重建调度：IDLE / AI 关则停运动；有粒子则慢 tick；否则按 follow/attack 间隔。
     */
    public void resync(PetData data) {
        if (data == null) {
            return;
        }
        LivingEntity entity = pets.findEntity(data);
        if (entity != null) {
            resync(data, entity);
        } else {
            stop(data.getPetId());
        }
    }

    public void resync(PetData data, LivingEntity entity) {
        if (data == null || entity == null || !entity.isValid()) {
            return;
        }
        if (!SchedulerUtil.owns(entity)) {
            SchedulerUtil.run(entity, plugin, () -> resync(data, entity));
            return;
        }

        stop(data.getPetId());

        boolean motion = needsMotion(data);
        boolean light = needsLightTick(data);

        if (!motion) {
            handleIdle(entity);
            if (!light) {
                return;
            }
            long period = Math.max(1L, config.getParticleInterval());
            ScheduledTask task = SchedulerUtil.runTimer(entity, plugin, _ -> tickLight(data.getPetId()), period, period);
            if (task != null) {
                tasks.put(data.getPetId(), task);
                ticks.put(data.getPetId(), 0L);
            }
            return;
        }

        long period = resolvePeriod(data);
        ScheduledTask task = SchedulerUtil.runTimer(entity, plugin, _ -> tickPet(data.getPetId()), period, period);
        if (task != null) {
            tasks.put(data.getPetId(), task);
            ticks.put(data.getPetId(), 0L);
        }
    }

    private boolean needsMotion(PetData data) {
        // 无敌模式站桩：不跟随/漫步/寻路，等同暂停运动 AI
        if (!data.isAiEnabled() || data.isInvincible()) {
            return false;
        }
        return data.isAttackEnabled() || data.getState() != PetState.IDLE;
    }

    private boolean hasParticles(PetData data) {
        String p = data.getParticlePreset();
        return p != null && !p.equalsIgnoreCase("none");
    }

    private boolean needsLightTick(PetData data) {
        if (hasParticles(data)) {
            return true;
        }
        // 悬浮物仅在开启浮动时需要慢速 tick；关闭浮动则乘客自然跟随，不占调度
        return config.isFloatBob() && !top.morndream.mcPets.util.CosmeticItems.isNone(data.getFloatItem());
    }

    private long resolvePeriod(PetData data) {
        if (data.isAttackEnabled() && !data.isInvincible()) {
            return Math.max(1L, config.getAttackInterval());
        }
        if (data.getState() == PetState.WANDER) {
            return Math.max(1L, config.getWanderInterval());
        }
        return Math.max(1L, config.getFollowIntervalTicks());
    }

    public void stop(UUID petId) {
        ScheduledTask task = tasks.remove(petId);
        if (task != null) {
            task.cancel();
        }
        ticks.remove(petId);
    }

    private void tickLight(UUID petId) {
        PetData data = pets.storage().byPetId(petId);
        if (data == null) {
            stop(petId);
            return;
        }
        if (needsMotion(data)) {
            LivingEntity entity = pets.findEntity(data);
            if (entity != null) {
                resync(data, entity);
            }
            return;
        }
        LivingEntity entity = pets.findEntity(data);
        if (entity == null || entity.isDead() || !entity.isValid()) {
            stop(petId);
            return;
        }
        ticks.merge(petId, 1L, Long::sum);
        if (hasParticles(data)) {
            plugin.getParticleService().tickEntity(entity, data);
        }
        plugin.getFloatItemService().tick(entity, data, ticks.getOrDefault(petId, 0L));
        if (!needsLightTick(data)) {
            stop(petId);
        }
    }

    private void tickPet(UUID petId) {
        PetData data = pets.storage().byPetId(petId);
        if (data == null) {
            stop(petId);
            return;
        }
        LivingEntity entity = pets.findEntity(data);
        if (entity == null || entity.isDead() || !entity.isValid()) {
            stop(petId);
            return;
        }

        // 状态已变到无需运动 → 重建调度
        if (!needsMotion(data)) {
            resync(data, entity);
            return;
        }

        ticks.merge(petId, 1L, Long::sum);
        pets.snapshotLocation(data, entity);
        if (ticks.getOrDefault(petId, 0L) % 25 == 0) {
            pets.storage().markDirty();
        }

        if (hasParticles(data)) {
            plugin.getParticleService().tickEntity(entity, data);
        }
        // 叼物不在此刷新；悬浮物：浮动时更新，否则偶尔检查是否掉座
        if (config.isFloatBob() || ticks.getOrDefault(petId, 0L) % 20 == 0) {
            plugin.getFloatItemService().tick(entity, data, ticks.getOrDefault(petId, 0L));
        }

        if (data.isInvincible() && data.getState() == PetState.ATTACK) {
            data.setState(PetState.FOLLOW);
            data.setAttackEnabled(false);
            data.clearCombat();
            resync(data, entity);
            return;
        }

        if (data.isAttackEnabled() && !data.isInvincible()) {
            if (data.getState() != PetState.ATTACK) {
                data.setState(PetState.ATTACK);
            }
            if (data.getHateUntil() > 0 && System.currentTimeMillis() > data.getHateUntil()) {
                endClickAttack(data, entity);
                return;
            }
            handleAttack(data, entity);
            return;
        }

        switch (data.getState()) {
            case FOLLOW -> handleFollow(data, entity);
            case IDLE -> {
                handleIdle(entity);
                resync(data, entity);
            }
            case WANDER -> handleWander(entity);
            case ATTACK -> {
                data.setState(PetState.FOLLOW);
                resync(data, entity);
            }
        }
    }

    private void handleIdle(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.setTarget(null);
        }
    }

    private void handleFollow(PetData data, LivingEntity entity) {
        Player owner = Bukkit.getPlayer(data.getOwnerId());
        if (owner == null || !owner.isOnline()) {
            handleIdle(entity);
            return;
        }

        if (!SchedulerUtil.owns(owner)) {
            SchedulerUtil.run(owner, plugin, () -> {
                if (!owner.isOnline()) {
                    return;
                }
                Location dest = owner.getLocation().clone();
                SchedulerUtil.teleportAsync(entity, dest);
            });
            return;
        }

        if (!owner.getWorld().equals(entity.getWorld())) {
            SchedulerUtil.teleportAsync(entity, owner.getLocation().clone());
            return;
        }

        double dist = entity.getLocation().distance(owner.getLocation());
        if (dist >= config.getFollowTeleport()) {
            SchedulerUtil.teleportAsync(entity, owner.getLocation().clone());
            return;
        }
        if (dist >= config.getFollowStart() && entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(owner, config.getFollowSpeed());
        } else if (entity instanceof Mob mob && dist < 2.0) {
            mob.getPathfinder().stopPathfinding();
        }
    }

    private void handleWander(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        Location base = entity.getLocation();
        double r = config.getWanderRadius();
        double ox = ThreadLocalRandom.current().nextDouble(-r, r);
        double oz = ThreadLocalRandom.current().nextDouble(-r, r);
        mob.getPathfinder().moveTo(base.clone().add(ox, 0, oz), 0.9);
    }

    private void handleAttack(PetData data, LivingEntity entity) {
        // 仅追击点击时锁定的那一名目标，绝不重新全图搜敌
        Player target = resolveLockedTarget(data, entity);
        if (target == null) {
            endClickAttack(data, entity);
            return;
        }

        if (!SchedulerUtil.owns(target)) {
            return;
        }

        if (entity instanceof Mob mob) {
            mob.setTarget(target);
            mob.getPathfinder().moveTo(target, config.getAttackSpeed());
        }

        double reach = 2.5;
        if (entity.getLocation().distanceSquared(target.getLocation()) > reach * reach) {
            return;
        }
        int hits = data.getHitCounts().getOrDefault(target.getUniqueId(), 0);
        if (hits >= config.getMaxHits()) {
            endClickAttack(data, entity);
            return;
        }

        double damage = config.getAttackDamage();
        UUID targetId = target.getUniqueId();
        data.getHitCounts().put(targetId, hits + 1);
        SchedulerUtil.run(target, plugin, () -> dealDamage(data, targetId, damage));
        if (hits + 1 >= config.getMaxHits()) {
            endClickAttack(data, entity);
        }
    }

    private void dealDamage(PetData data, UUID targetId, double damage) {
        PetDamageBridge.runAllowed(() -> {
            Player p = Bukkit.getPlayer(targetId);
            if (p == null || !p.isOnline() || p.isDead()) {
                return;
            }
            LivingEntity pet = pets.findEntity(data);
            if (pet == null || !pet.isValid()) {
                return;
            }
            p.damage(damage, pet);
            Vector kb = p.getLocation().toVector().subtract(pet.getLocation().toVector());
            if (kb.lengthSquared() > 1.0E-4) {
                kb.normalize().multiply(0.15).setY(0.05);
                try {
                    p.setVelocity(p.getVelocity().add(kb));
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void endClickAttack(PetData data, LivingEntity entity) {
        data.setAttackEnabled(false);
        data.setState(PetState.FOLLOW);
        data.clearCombat();
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            try {
                mob.getPathfinder().stopPathfinding();
            } catch (Exception ignored) {
            }
        }
        pets.storage().markDirty();
        resync(data, entity);
    }

    /** 只认点击锁定的目标；离开攻击半径或失效则返回 null。 */
    private Player resolveLockedTarget(PetData data, LivingEntity entity) {
        UUID locked = data.getAttackTargetId();
        if (locked == null) {
            return null;
        }
        Player p = Bukkit.getPlayer(locked);
        if (!isValidTarget(data, entity, p)) {
            return null;
        }
        if (data.getHitCounts().getOrDefault(locked, 0) >= config.getMaxHits()) {
            return null;
        }
        double range = config.getAutoRange();
        double rangeSq = range * range;
        if (entity.getLocation().distanceSquared(p.getLocation()) > rangeSq) {
            return null;
        }
        return p;
    }

    private boolean isValidTarget(PetData data, LivingEntity entity, Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getUniqueId().equals(data.getOwnerId())) {
            return false;
        }
        return player.getWorld().equals(entity.getWorld());
    }
}
