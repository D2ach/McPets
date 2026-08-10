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

public final class PetListener implements Listener {

    private final McPets plugin;
    private final PetService pets;

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
        plugin.getGuiManager().onClose(event.getPlayer().getUniqueId());
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

    /** 重命名会话：吞掉聊天，不向任何玩家（含自己）广播。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGuiManager().isRenameSession(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        SchedulerUtil.run(player, plugin, () -> plugin.getGuiManager().handleRenameChat(player, message));
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getGuiManager().onClose(player.getUniqueId());
        }
    }
}
