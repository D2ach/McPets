package top.morndream.mcPets.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Paper / Folia 统一调度与传送辅助。
 * 全部走 Region / Entity / Global / Async Scheduler，避免 BukkitScheduler。
 */
public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, _ -> task.run(), Math.max(1L, delayTicks));
    }

    public static ScheduledTask runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, _ -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void runAsyncTimer(Plugin plugin, Runnable task, long delaySeconds, long periodSeconds) {
        Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, _ -> task.run(), Math.max(1L, delaySeconds), Math.max(1L, periodSeconds), TimeUnit.SECONDS);
    }

    public static void run(Entity entity, Plugin plugin, Runnable task) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.getScheduler().run(plugin, _ -> task.run(), null);
    }

    public static void runDelayed(Entity entity, Plugin plugin, Runnable task, long delayTicks) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.getScheduler().runDelayed(plugin, _ -> task.run(), null, Math.max(1L, delayTicks));
    }

    public static ScheduledTask runTimer(Entity entity, Plugin plugin, Consumer<ScheduledTask> task,
                                         long delayTicks, long periodTicks) {
        if (entity == null || !entity.isValid()) {
            return null;
        }
        return entity.getScheduler().runAtFixedRate(
                plugin, task, null, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void run(Player player, Plugin plugin, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getScheduler().run(plugin, _ -> task.run(), null);
    }

    /** 当前线程是否拥有该实体所在 region（Paper 上恒为 true）。 */
    public static boolean owns(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return Bukkit.isOwnedByCurrentRegion(entity);
        } catch (NoSuchMethodError | UnsupportedOperationException ex) {
            return true;
        }
    }

    public static void teleportAsync(Entity entity, Location to) {
        if (entity == null || to == null) {
            return;
        }
        entity.teleportAsync(to);
    }

    public static void teleportAsync(Entity entity, Location to, Consumer<Boolean> after) {
        if (entity == null || to == null) {
            if (after != null) {
                after.accept(false);
            }
            return;
        }
        entity.teleportAsync(to).thenAccept(success -> {
            if (after != null) {
                after.accept(Boolean.TRUE.equals(success));
            }
        });
    }
}
