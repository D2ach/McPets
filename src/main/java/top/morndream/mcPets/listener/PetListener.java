package top.morndream.mcPets.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.service.PetService;
import top.morndream.mcPets.util.SchedulerUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PetListener implements Listener {

    private final McPets plugin;
    private final PetService pets;
    /** 改名吞聊：兼容 PlayerChat 等会在 HIGHEST 再加回接收者的插件 */
    private final Set<UUID> suppressChatBroadcast = ConcurrentHashMap.newKeySet();
    /** 同一条消息可能同时触发 legacy + Paper 事件，只处理一次 */
    private final Map<UUID, Long> renameCaptureAt = new ConcurrentHashMap<>();

    public PetListener(McPets plugin) {
        this.plugin = plugin;
        this.pets = plugin.getPetService();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SchedulerUtil.runDelayed(player, plugin, () -> pets.validateOnJoin(player), 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        pets.bindLoadedEntity(living);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.getGuiManager().onClose(id);
        suppressChatBroadcast.remove(id);
        renameCaptureAt.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity)) {
            return;
        }
        PetData data = pets.storage().byEntityId(clicked.getUniqueId());
        if (data == null) {
            return;
        }
        if (!data.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("mcpets.admin")) {
            return;
        }
        if (!player.hasPermission("mcpets.gui")) {
            return;
        }
        double range = plugin.getPluginConfig().getInteractRange();
        if (player.getLocation().distanceSquared(clicked.getLocation()) > range * range) {
            return;
        }
        event.setCancelled(true);
        plugin.getGuiManager().openManage(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        PetData data = pets.storage().byEntityId(event.getEntity().getUniqueId());
        if (data != null) {
            pets.handleDeath(data);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        PetData data = pets.storage().byEntityId(entity.getUniqueId());
        if (data != null && data.isInvincible()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        PetData data = pets.storage().byEntityId(damager.getUniqueId());
        if (data != null && !top.morndream.mcPets.util.PetDamageBridge.isAllowed()) {
            event.setCancelled(true);
        }
        if (plugin.getMouthService().isMouthDisplay(event.getEntity())
                || plugin.getFloatItemService().isFloatDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * PlayerChat 等插件常用 legacy AsyncPlayerChatEvent；
     * 必须在此取消并清空 recipients，否则会先于 Paper 事件发出去。
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChatLowest(AsyncPlayerChatEvent event) {
        if (allowNormalChat(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
        captureRenameInput(event.getPlayer(), event.getMessage());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLegacyChatHighest(AsyncPlayerChatEvent event) {
        if (allowNormalChat(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    /** Paper Adventure 聊天事件：同样 cancel + 清空 viewers。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChatLowest(AsyncChatEvent event) {
        if (allowNormalChat(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        captureRenameInput(event.getPlayer(), message);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPaperChatHighest(AsyncChatEvent event) {
        if (allowNormalChat(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
    }

    /** 非改名会话且未处于吞聊窗口时，放行普通聊天。 */
    private boolean allowNormalChat(UUID playerId) {
        return !plugin.getGuiManager().isRenameSession(playerId)
                && !suppressChatBroadcast.contains(playerId);
    }

    private void captureRenameInput(Player player, String message) {
        UUID id = player.getUniqueId();
        if (!plugin.getGuiManager().isRenameSession(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = renameCaptureAt.put(id, now);
        if (prev != null && now - prev < 250L) {
            return; // 同一条输入的双事件，跳过第二次
        }
        suppressChatBroadcast.add(id);
        SchedulerUtil.run(player, plugin, () -> {
            try {
                plugin.getGuiManager().handleRenameChat(player, message);
            } finally {
                SchedulerUtil.runGlobalDelayed(plugin, () -> {
                    suppressChatBroadcast.remove(id);
                    renameCaptureAt.remove(id);
                }, 5L);
            }
        });
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getGuiManager().onClose(player.getUniqueId());
        }
    }
}
