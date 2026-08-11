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
import top.morndream.mcPets.util.CosmeticItems;
import top.morndream.mcPets.util.PetDamageBridge;
import top.morndream.mcPets.util.SchedulerUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Folia 安全 AI：
 * - FOLLOW / WANDER：较长间隔；攻击用短间隔
 * - IDLE / 无敌且无粒子/悬浮：不挂 timer
 * - 主人离线或过远：降频，只做保活校验
 */
public final class PetAIManager {

    private final McPets plugin;
    private final PetService pets;
    private final PluginConfig config;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ticks = new ConcurrentHashMap<>();
    /** 上一 tick 是否处于降频态，用于切换时 rebuild period */
    private final Map<UUID, Boolean> dormantFlags = new ConcurrentHashMap<>();
    private ScheduledTask watchdog;

    public PetAIManager(McPets plugin) {
        this.plugin = plugin;
        this.pets = plugin.getPetService();
        this.config = plugin.getPluginConfig();
    }

    public void start() {
        attachAllLoaded();
        long period = Math.max(40L, config.getWatchdogIntervalTicks());
        watchdog = SchedulerUtil.runGlobalTimer(plugin, this::attachOnlineMissing, period, period);
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
        dormantFlags.clear();
    }

    /** 启用时全量补挂一次（含主人离线的在世宠物）。 */
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

    /**
     * 定时补挂：只扫「尚无 AI 任务」且「主人在线」的宠物，避免全表 getEntity。
     */
    public void attachOnlineMissing() {
        for (PetData data : pets.storage().all()) {
            if (tasks.containsKey(data.getPetId())) {
                continue;
            }
            Player owner = Bukkit.getPlayer(data.getOwnerId());
            if (owner == null || !owner.isOnline()) {
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
                // 无敌/待命 + none 粒子 + 无悬浮浮动：完全不挂 timer
                return;
            }
            boolean dormant = isDormant(data, entity);
            dormantFlags.put(data.getPetId(), dormant);
            long period = resolveLightPeriod(data, dormant);
            ScheduledTask task = SchedulerUtil.runTimer(entity, plugin, _ -> tickLight(data.getPetId()), period, period);
            if (task != null) {
                tasks.put(data.getPetId(), task);
                ticks.put(data.getPetId(), 0L);
            }
            return;
        }

        boolean dormant = isDormant(data, entity);
        dormantFlags.put(data.getPetId(), dormant);
        long period = resolvePeriod(data, dormant);
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
        return plugin.getParticleService().isActivePreset(data.getParticlePreset());
    }

    private boolean needsLightTick(PetData data) {
        if (hasParticles(data)) {
            return true;
        }
        // 悬浮物仅在开启浮动时需要慢速 tick；none / 关闭浮动不占调度
        return config.isFloatBob() && !CosmeticItems.isNone(data.getFloatItem());
    }

    private boolean isOwnerOnline(PetData data) {
        Player owner = Bukkit.getPlayer(data.getOwnerId());
        return owner != null && owner.isOnline();
    }

    private boolean isFarFromOwner(PetData data, LivingEntity entity) {
        Player owner = Bukkit.getPlayer(data.getOwnerId());
        if (owner == null || !owner.isOnline()) {
            return false;
        }
        if (!owner.getWorld().equals(entity.getWorld())) {
            return true;
        }
        return entity.getLocation().distanceSquared(owner.getLocation()) > config.getFarDistanceSq();
    }

    /** 主人离线或过远：降频态（攻击中不降频）。 */
    private boolean isDormant(PetData data, LivingEntity entity) {
        if (data.isAttackEnabled() && !data.isInvincible()) {
            return false;
        }
        if (!isOwnerOnline(data)) {
            return true;
        }
        return isFarFromOwner(data, entity);
    }

    private long resolvePeriod(PetData data, boolean dormant) {
        if (data.isAttackEnabled() && !data.isInvincible()) {
            return Math.max(1L, config.getAttackInterval());
        }
        if (dormant) {
            return Math.max(1L, isOwnerOnline(data)
                    ? config.getFarIntervalTicks()
                    : config.getOfflineIntervalTicks());
        }
        if (data.getState() == PetState.WANDER) {
            return Math.max(1L, config.getWanderInterval());
        }
        return Math.max(1L, config.getFollowIntervalTicks());
    }

    private long resolveLightPeriod(PetData data, boolean dormant) {
        if (dormant) {
            return Math.max(1L, isOwnerOnline(data)
                    ? config.getFarIntervalTicks()
                    : config.getOfflineIntervalTicks());
        }
        return Math.max(1L, config.getParticleInterval());
    }

    public void stop(UUID petId) {
        ScheduledTask task = tasks.remove(petId);
        if (task != null) {
            task.cancel();
        }
        ticks.remove(petId);
        dormantFlags.remove(petId);
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
        if (!needsLightTick(data)) {
            stop(petId);
            return;
        }

        boolean dormant = isDormant(data, entity);
        Boolean prev = dormantFlags.put(petId, dormant);
        if (prev != null && prev != dormant) {
            resync(data, entity);
            return;
        }

        ticks.merge(petId, 1L, Long::sum);
        if (dormant) {
            // 降频：不刷粒子、不更新悬浮浮动
            return;
        }
        if (hasParticles(data)) {
            plugin.getParticleService().tickEntity(entity, data);
        }
        plugin.getFloatItemService().tick(entity, data, ticks.getOrDefault(petId, 0L));
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

        boolean dormant = isDormant(data, entity);
        Boolean prev = dormantFlags.put(petId, dormant);
        if (prev != null && prev != dormant) {
            resync(data, entity);
            return;
        }

        ticks.merge(petId, 1L, Long::sum);

        if (dormant) {
            // 降频保活：偶尔写坐标；不跟随、不粒子、不悬浮
            if (ticks.getOrDefault(petId, 0L) % 5 == 0) {
                pets.snapshotLocation(data, entity);
            }
            handleIdle(entity);
            return;
        }

        pets.snapshotLocation(data, entity);
        if (ticks.getOrDefault(petId, 0L) % 40 == 0) {
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
