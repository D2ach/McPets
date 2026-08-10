package top.morndream.mcPets.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.config.UnloadMode;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.model.PetState;
import top.morndream.mcPets.storage.PetStorage;
import top.morndream.mcPets.util.LegacyPdcCleaner;
import top.morndream.mcPets.util.SchedulerUtil;
import top.morndream.mcPets.util.Text;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PetService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\w\\u4e00-\\u9fa5-]{1,16}$");

    private final McPets plugin;
    private final PetStorage storage;
    private final PluginConfig config;
    private final MessageService messages;
    private final MouthService mouthService;
    private final FloatItemService floatItemService;
    private final AppearanceService appearanceService;
    private final Set<UUID> pendingMissingChecks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> unloadWaitCounts = new ConcurrentHashMap<>();

    public PetService(McPets plugin, PetStorage storage, MessageService messages,
                      MouthService mouthService, FloatItemService floatItemService,
                      AppearanceService appearanceService) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = plugin.getPluginConfig();
        this.messages = messages;
        this.mouthService = mouthService;
        this.floatItemService = floatItemService;
        this.appearanceService = appearanceService;
    }

    public boolean isValidInternalName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public PetData tame(Player owner, LivingEntity target, String internalName) {
        UUID petId = UUID.randomUUID();
        PetData data = new PetData(petId);
        data.setOwnerId(owner.getUniqueId());
        data.setEntityId(target.getUniqueId());
        data.setInternalName(internalName);
        data.setDisplayName(null);
        data.setEntityType(target.getType());
        data.setState(PetState.FOLLOW);
        data.setAttackEnabled(config.isAttackDefault());
        data.setInvincible(false);
        data.setAiEnabled(true);
        data.setScaleTier(config.getDefaultScaleTier());
        if (target instanceof Ageable ageable) {
            data.setBaby(!ageable.isAdult());
        }
        data.setParticlePreset("none");
        data.setMouthItem("none");
        data.setFloatItem("none");
        snapshotLocation(data, target);

        // 快照原版状态 → 只写 pets.yml；运行时套用外观，不改 Tameable / Invulnerable / PDC
        appearanceService.captureVanilla(target, data);
        LegacyPdcCleaner.strip(plugin, target);
        if (target instanceof Mob mob) {
            mob.setAware(true);
            mob.setTarget(null);
        }
        appearanceService.applyRuntime(target, data);
        storage.add(data);
        storage.flush();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().ensureStarted(data, target);
        }
        return data;
    }

    public void delete(PetData data, boolean resetVisual) {
        pendingMissingChecks.remove(data.getPetId());
        unloadWaitCounts.remove(data.getPetId());
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().stop(data.getPetId());
        }
        LivingEntity entity = findEntity(data);
        if (entity != null) {
            SchedulerUtil.run(entity, plugin, () -> {
                mouthService.clear(data);
                floatItemService.clear(data);
                LegacyPdcCleaner.strip(plugin, entity);
                if (resetVisual) {
                    appearanceService.revertVanilla(entity, data);
                }
            });
        } else {
            mouthService.clear(data);
            floatItemService.clear(data);
        }
        storage.remove(data);
        storage.flush();
    }

    public void handleDeath(PetData data) {
        pendingMissingChecks.remove(data.getPetId());
        unloadWaitCounts.remove(data.getPetId());
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().stop(data.getPetId());
        }
        mouthService.clear(data);
        floatItemService.clear(data);
        Player owner = Bukkit.getPlayer(data.getOwnerId());
        if (owner != null) {
            SchedulerUtil.run(owner, plugin,
                    () -> messages.send(owner, "pet-died", Map.of("name", data.getInternalName())));
        }
        storage.remove(data);
        storage.flush();
    }

    public LivingEntity findEntity(PetData data) {
        Entity entity = Bukkit.getEntity(data.getEntityId());
        if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
            return living;
        }
        return null;
    }

    public void snapshotLocation(PetData data, Entity entity) {
        Location loc = entity.getLocation();
        if (loc.getWorld() != null) {
            data.setLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch());
        }
    }

    public void applyOwnerVisual(LivingEntity entity, PetData data) {
        entity.customName(Text.parse(data.effectiveDisplayRaw()));
        entity.setCustomNameVisible(true);
    }

    public void setState(PetData data, PetState state) {
        data.setState(state);
        if (state != PetState.ATTACK) {
            data.setAttackTargetId(null);
        }
        LivingEntity entity = findEntity(data);
        if (entity instanceof Mob mob) {
            SchedulerUtil.run(entity, plugin, () -> mob.setTarget(null));
        }
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
    }

    public void setAttackEnabled(PetData data, boolean enabled) {
        data.setAttackEnabled(enabled);
        if (!enabled) {
            if (data.getState() == PetState.ATTACK) {
                data.setState(PetState.FOLLOW);
            }
            data.clearCombat();
            LivingEntity entity = findEntity(data);
            if (entity instanceof Mob mob) {
                SchedulerUtil.run(entity, plugin, () -> mob.setTarget(null));
            }
        }
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
    }

    /**
     * 点击攻击：锁定宠物 5 格内最近的一名非主人玩家；没有目标则不攻击。
     * 不会全图扫描 / 同时打多人。
     */
    public ClickAttackResult clickAttack(PetData data) {
        if (data.isInvincible()) {
            return ClickAttackResult.INVINCIBLE;
        }
        LivingEntity entity = findEntity(data);
        if (entity == null || !entity.isValid()) {
            return ClickAttackResult.ENTITY_MISSING;
        }
        Player target = findNearestAttackTarget(data, entity, config.getAutoRange());
        if (target == null) {
            setAttackEnabled(data, false);
            return ClickAttackResult.NO_TARGET;
        }
        data.clearCombat();
        data.setAttackTargetId(target.getUniqueId());
        data.setAttackEnabled(true);
        data.setState(PetState.ATTACK);
        data.setHateUntil(System.currentTimeMillis() + config.getHateSeconds() * 1000L);
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
        return ClickAttackResult.STARTED;
    }

    /** 仅在当前世界、给定半径内找最近的一名可攻击玩家。 */
    public Player findNearestAttackTarget(PetData data, LivingEntity entity, double range) {
        if (entity == null || range <= 0) {
            return null;
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        double rangeSq = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getUniqueId().equals(data.getOwnerId())) {
                continue;
            }
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            double d = entity.getLocation().distanceSquared(player.getLocation());
            if (d <= rangeSq && d < best) {
                best = d;
                nearest = player;
            }
        }
        return nearest;
    }

    public enum ClickAttackResult {
        STARTED,
        NO_TARGET,
        INVINCIBLE,
        ENTITY_MISSING
    }

    public void setInvincible(PetData data, boolean invincible) {
        data.setInvincible(invincible);
        LivingEntity entity = findEntity(data);
        if (invincible) {
            if (data.getState() == PetState.ATTACK || data.isAttackEnabled()) {
                data.setAttackEnabled(false);
                data.setState(PetState.FOLLOW);
                data.clearCombat();
                if (entity instanceof Mob mob) {
                    SchedulerUtil.run(entity, plugin, () -> mob.setTarget(null));
                }
                Player owner = Bukkit.getPlayer(data.getOwnerId());
                if (owner != null) {
                    SchedulerUtil.run(owner, plugin, () -> messages.send(owner, "attack-exit-invincible",
                            Map.of("name", data.getInternalName())));
                }
            }
        }
        // 无敌只写 pets.yml + 伤害监听，不改实体 Invulnerable
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
    }

    public void setAiEnabled(PetData data, boolean enabled) {
        data.setAiEnabled(enabled);
        LivingEntity entity = findEntity(data);
        if (entity instanceof Mob mob) {
            SchedulerUtil.run(entity, plugin, () -> {
                if (!enabled) {
                    mob.getPathfinder().stopPathfinding();
                    mob.setTarget(null);
                }
                mob.setAware(enabled);
            });
        }
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
    }

    public void cycleScale(PetData data) {
        int next = data.getScaleTier() + 1;
        if (next >= config.getScaleTiers().size()) {
            next = 0;
        }
        data.setScaleTier(next);
        LivingEntity entity = findEntity(data);
        if (entity != null) {
            SchedulerUtil.run(entity, plugin, () -> appearanceService.applyScale(entity, data));
        }
        storage.markDirty();
    }

    public boolean toggleBaby(PetData data) {
        LivingEntity entity = findEntity(data);
        if (!(entity instanceof Ageable ageable)) {
            return false;
        }
        data.setBaby(!data.isBaby());
        SchedulerUtil.run(entity, plugin, () -> {
            if (data.isBaby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        });
        storage.markDirty();
        return true;
    }

    public void cycleParticle(PetData data) {
        List<String> presets = config.particlePresetIds();
        if (presets.isEmpty()) {
            return;
        }
        int idx = presets.indexOf(data.getParticlePreset());
        int next = (idx + 1) % presets.size();
        data.setParticlePreset(presets.get(next));
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
    }

    public void setMouth(PetData data, String mouthId) {
        data.setMouthItem(mouthId);
        LivingEntity entity = findEntity(data);
        if (entity != null) {
            SchedulerUtil.run(entity, plugin, () -> mouthService.apply(entity, data));
        }
        storage.markDirty();
    }

    public void setFloatItem(PetData data, String floatId) {
        data.setFloatItem(floatId);
        LivingEntity entity = findEntity(data);
        if (entity != null) {
            SchedulerUtil.run(entity, plugin, () -> floatItemService.apply(entity, data));
        }
        storage.markDirty();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
    }

    public boolean isValidDisplayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return Text.plainLength(raw) <= config.getMaxDisplayNameLength();
    }

    /**
     * @return false 若可见字数超限（不含颜色符号）
     */
    public boolean setDisplayName(PetData data, String raw) {
        if (!isValidDisplayName(raw)) {
            return false;
        }
        if (raw == null || Text.plainLength(raw) == 0) {
            data.setDisplayName(null);
        } else {
            data.setDisplayName(raw);
        }
        LivingEntity entity = findEntity(data);
        if (entity != null) {
            SchedulerUtil.run(entity, plugin, () -> applyOwnerVisual(entity, data));
        }
        storage.markDirty();
        return true;
    }

    public boolean teleportPlayerToPet(Player player, PetData data) {
        LivingEntity entity = findEntity(data);
        if (entity == null) {
            return false;
        }
        SchedulerUtil.run(entity, plugin, () -> {
            if (!entity.isValid()) {
                return;
            }
            Location loc = entity.getLocation().clone();
            SchedulerUtil.teleportAsync(player, loc);
        });
        return true;
    }

    public boolean teleportPetToPlayer(Player player, PetData data) {
        LivingEntity entity = findEntity(data);
        if (entity == null) {
            return false;
        }
        Location dest = player.getLocation().clone();
        SchedulerUtil.teleportAsync(entity, dest, success -> {
            if (!Boolean.TRUE.equals(success)) {
                return;
            }
            SchedulerUtil.run(entity, plugin, () -> {
                if (!entity.isValid()) {
                    return;
                }
                snapshotLocation(data, entity);
                mouthService.refreshPosition(entity, data);
                floatItemService.refreshPosition(entity, data);
                storage.markDirty();
            });
        });
        return true;
    }

    public void validateOnJoin(Player player) {
        for (PetData data : storage.byOwner(player.getUniqueId())) {
            LivingEntity entity = findEntity(data);
            if (entity != null) {
                pendingMissingChecks.remove(data.getPetId());
                SchedulerUtil.run(entity, plugin, () -> {
                    appearanceService.applyRuntime(entity, data);
                    mouthService.apply(entity, data);
                    floatItemService.apply(entity, data);
                    if (plugin.getPetAIManager() != null) {
                        plugin.getPetAIManager().ensureStarted(data, entity);
                    }
                });
                continue;
            }
            // 找不到实体：不立刻删档，进入延迟确认（未加载区块 / 稍后加载）
            scheduleMissingCheck(data, 1);
        }
    }

    private void scheduleMissingCheck(PetData data, int attempt) {
        UUID petId = data.getPetId();
        if (attempt == 1 && !pendingMissingChecks.add(petId)) {
            return;
        }
        pendingMissingChecks.add(petId);
        long delay = config.getMissingCheckIntervalTicks();
        SchedulerUtil.runGlobalDelayed(plugin, () -> runMissingCheck(petId, attempt), delay);
    }

    private void runMissingCheck(UUID petId, int attempt) {
        PetData data = storage.byPetId(petId);
        if (data == null) {
            pendingMissingChecks.remove(petId);
            unloadWaitCounts.remove(petId);
            return;
        }
        LivingEntity entity = findEntity(data);
        if (entity != null) {
            recoverFoundEntity(data, entity);
            return;
        }

        World world = data.getWorldName() == null ? null : Bukkit.getWorld(data.getWorldName());
        if (world == null) {
            // 世界不存在：直接按丢失处理
            if (attempt < config.getMissingCheckAttempts()) {
                scheduleMissingCheck(data, attempt + 1);
                return;
            }
            removeMissingPet(data);
            return;
        }

        int cx = Location.locToBlock(data.getX()) >> 4;
        int cz = Location.locToBlock(data.getZ()) >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            // 未加载：可能仍在别的区块，不能当丢失；限制空等次数避免无限调度
            int waits = unloadWaitCounts.merge(petId, 1, Integer::sum);
            if (waits > 60) {
                pendingMissingChecks.remove(petId);
                unloadWaitCounts.remove(petId);
                plugin.getLogger().info("宠物 " + data.getInternalName() + " 所在区块长期未加载，暂停丢失检测（下次上线再查）。");
                return;
            }
            scheduleMissingCheck(data, attempt);
            return;
        }

        // 强制触达存档坐标区块后再查一次，减少误判
        try {
            world.getChunkAt(cx, cz);
        } catch (Exception ignored) {
        }
        LivingEntity again = findEntity(data);
        if (again != null) {
            recoverFoundEntity(data, again);
            return;
        }

        unloadWaitCounts.remove(petId);
        if (attempt < config.getMissingCheckAttempts()) {
            scheduleMissingCheck(data, attempt + 1);
            return;
        }

        removeMissingPet(data);
    }

    private void recoverFoundEntity(PetData data, LivingEntity entity) {
        pendingMissingChecks.remove(data.getPetId());
        unloadWaitCounts.remove(data.getPetId());
        SchedulerUtil.run(entity, plugin, () -> {
            appearanceService.applyRuntime(entity, data);
            mouthService.apply(entity, data);
            floatItemService.apply(entity, data);
            if (plugin.getPetAIManager() != null) {
                plugin.getPetAIManager().ensureStarted(data, entity);
            }
        });
    }

    private void removeMissingPet(PetData data) {
        UUID petId = data.getPetId();
        pendingMissingChecks.remove(petId);
        unloadWaitCounts.remove(petId);
        Player owner = Bukkit.getPlayer(data.getOwnerId());
        if (owner != null && config.isNotifyMissing()) {
            SchedulerUtil.run(owner, plugin, () ->
                    messages.send(owner, "pet-missing-notify", Map.of("name", data.getInternalName())));
        }
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().stop(petId);
        }
        mouthService.clear(data);
        floatItemService.clear(data);
        storage.remove(data);
        storage.flush();
    }

    public void applyToExistingEntities() {
        restoreAfterEnable();
    }

    /**
     * 插件启用后：恢复停泊宠物，或按 despawn 模式补生成。
     */
    public void restoreAfterEnable() {
        int restored = 0;
        int respawned = 0;
        for (PetData data : storage.all()) {
            LivingEntity entity = findEntity(data);
            if (entity != null) {
                restoreEntity(entity, data);
                restored++;
                continue;
            }
            if (config.getUnloadMode() == UnloadMode.DESPAWN) {
                if (tryRespawn(data)) {
                    respawned++;
                }
            }
        }
        plugin.getLogger().info("宠物恢复完成：在世 " + restored + "，重新生成 " + respawned
                + "，模式 " + config.getUnloadMode());
    }

    private void restoreEntity(LivingEntity entity, PetData data) {
        Runnable work = () -> {
            LegacyPdcCleaner.strip(plugin, entity);
            appearanceService.applyRuntime(entity, data);
            mouthService.apply(entity, data);
            floatItemService.apply(entity, data);
            if (entity instanceof Mob mob) {
                mob.setAware(data.isAiEnabled());
            }
            if (plugin.getPetAIManager() != null) {
                plugin.getPetAIManager().ensureStarted(data, entity);
            }
        };
        if (SchedulerUtil.owns(entity)) {
            work.run();
        } else {
            SchedulerUtil.run(entity, plugin, work);
        }
    }

    /**
     * 插件 disable / 热卸载：按配置停泊或移除实体，并强制写档。
     * 在 onDisable 中同步执行（调度器可能已不可用）。
     */
    public void handlePluginUnload() {
        UnloadMode mode = config.getUnloadMode();
        int handled = 0;
        for (PetData data : storage.all()) {
            LivingEntity entity = findEntity(data);
            if (entity == null) {
                continue;
            }
            try {
                snapshotLocation(data, entity);
                mouthService.clear(data);
                floatItemService.clear(data);
                if (mode == UnloadMode.DESPAWN) {
                    entity.remove();
                } else {
                    parkEntity(entity, data);
                }
                handled++;
            } catch (Exception ex) {
                plugin.getLogger().warning("卸载处理宠物失败 " + data.getInternalName() + ": " + ex.getMessage());
            }
        }
        storage.markDirty();
        storage.save(true);
        plugin.getLogger().info("插件卸载处理完成（" + mode + "）：处理实体 " + handled + " 只，已写档。");
    }

    private void parkEntity(LivingEntity entity, PetData data) {
        LegacyPdcCleaner.strip(plugin, entity);
        // 完整还原驯服前快照，避免自定义名/体型/幼体/狐狸手物/NoAI 等写入区块存档
        appearanceService.revertVanilla(entity, data);
        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().stopPathfinding();
            } catch (Exception ignored) {
            }
            mob.setTarget(null);
            mob.setAware(true);
        }
    }

    private boolean tryRespawn(PetData data) {
        if (data.getEntityType() == null || data.getWorldName() == null) {
            return false;
        }
        World world = Bukkit.getWorld(data.getWorldName());
        if (world == null) {
            return false;
        }
        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());
        try {
            world.getChunkAt(loc);
            Entity spawned = world.spawnEntity(loc, data.getEntityType());
            if (!(spawned instanceof LivingEntity living)) {
                spawned.remove();
                return false;
            }
            storage.rebindEntity(data, living.getUniqueId());
            // 新实体的「原版」状态即刚生成时的状态，供日后 park/delete 还原
            appearanceService.captureVanilla(living, data);
            restoreEntity(living, data);
            storage.flush();
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("无法重新生成宠物 " + data.getInternalName() + ": " + ex.getMessage());
            return false;
        }
    }

    public void bindLoadedEntity(LivingEntity entity) {
        // 身份只认 pets.yml 中的实体 UUID，不读区块内任何插件标记
        PetData data = storage.byEntityId(entity.getUniqueId());
        if (data == null) {
            return;
        }
        pendingMissingChecks.remove(data.getPetId());
        unloadWaitCounts.remove(data.getPetId());
        restoreEntity(entity, data);
    }

    public PetStorage storage() {
        return storage;
    }
}
