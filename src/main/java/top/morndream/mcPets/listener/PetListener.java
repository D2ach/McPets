package top.morndream.mcPets.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.service.PetService;
import top.morndream.mcPets.util.SchedulerUtil;
import top.morndream.mcPets.util.Text;

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
        if (plugin.getTransferService() != null) {
            plugin.getTransferService().clearPlayer(id);
        }
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
        if (!player.hasPermission("mcpets.gui") && !player.hasPermission("mcpets.admin")) {
            plugin.getMessageService().send(player, "no-permission");
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

    /**
     * 配置 block-villager-profession 时，阻止宠物村民获取/更换工作职业。
     * 允许变为 none（失业）或保持 nitwit。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        if (!plugin.getPluginConfig().isBlockVillagerProfession()) {
            return;
        }
        PetData data = pets.storage().byEntityId(event.getEntity().getUniqueId());
        if (data == null) {
            return;
        }
        Villager.Profession next = event.getProfession();
        // 允许失业 / nitwit；其余职业变更一律拦截
        if (Villager.Profession.NONE.equals(next) || Villager.Profession.NITWIT.equals(next)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * 宠物击杀玩家：击杀者显示为「名字(生物id)」，落在翻译句式中间。
     * 例：xxx被 Fluffy(pig) 杀死了（而非句末再加括号）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerKilledByPet(PlayerDeathEvent event) {
        EntityDamageEvent cause = event.getEntity().getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent by)) {
            return;
        }
        Entity damager = by.getDamager();
        PetData data = pets.storage().byEntityId(damager.getUniqueId());
        if (data == null) {
            return;
        }
        String typeId = damager.getType().getKey().getKey();
        Component petName = damager.customName();
        if (petName == null) {
            petName = Text.parse(data.effectiveDisplayRaw());
        }
        // 用 empty() 作根，避免对可能为 null 的 Component 直接 append
        Component killerLabel = Component.empty()
                .append(petName)
                .append(Component.text("(" + typeId + ")", NamedTextColor.GRAY));

        // death.attack.mob = "%1$s was slain by %2$s" / "%1$s被%2$s杀死了"
        Component message = Component.translatable(
                "death.attack.mob",
                event.getEntity().displayName(),
                killerLabel
        );
        event.deathMessage(message);
        event.deathScreenMessageOverride(message);
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
        if (plugin.getGuiManager().isRenameSession(playerId)) {
            return false;
        }
        return !suppressChatBroadcast.contains(playerId);
    }

    private void captureRenameInput(Player player, String message) {
        UUID id = player.getUniqueId();
        if (plugin.getGuiManager().isRenameSession(id)) {
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
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getGuiManager().onClose(player.getUniqueId());
        }
    }
}
