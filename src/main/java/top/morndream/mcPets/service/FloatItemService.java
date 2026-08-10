package top.morndream.mcPets.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.util.CosmeticItems;
import top.morndream.mcPets.util.SchedulerUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 悬浮物品：头顶 ItemDisplay。浮动时降频更新 transform；关闭浮动则仅 apply 一次。
 */
public final class FloatItemService {

    private static final long BOB_EVERY_TICKS = 2L;

    private final McPets plugin;
    private final PluginConfig config;
    private final Map<UUID, UUID> displays = new ConcurrentHashMap<>();
    private final Set<UUID> displayIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> appliedItemIds = new ConcurrentHashMap<>();

    public FloatItemService(McPets plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
    }

    public boolean isFloatDisplay(Entity entity) {
        return entity != null && displayIds.contains(entity.getUniqueId());
    }

    public void apply(LivingEntity pet, PetData data) {
        if (!SchedulerUtil.owns(pet)) {
            SchedulerUtil.run(pet, plugin, () -> apply(pet, data));
            return;
        }
        if (CosmeticItems.isNone(data.getFloatItem())) {
            clear(data);
            return;
        }
        ItemStack stack = CosmeticItems.fromId(data.getFloatItem());
        if (stack == null || stack.getType().isAir()) {
            clear(data);
            return;
        }
        ItemDisplay display = getOrCreate(pet, data);
        String prev = appliedItemIds.get(data.getPetId());
        if (prev == null || !prev.equalsIgnoreCase(data.getFloatItem())) {
            display.setItemStack(stack);
            appliedItemIds.put(data.getPetId(), data.getFloatItem());
        }
        setupStaticProps(display);
        applyTransform(display, 0L);
        ensurePassenger(pet, display);
    }

    public void tick(LivingEntity pet, PetData data, long tick) {
        if (CosmeticItems.isNone(data.getFloatItem())) {
            return;
        }
        UUID displayId = displays.get(data.getPetId());
        if (displayId == null) {
            apply(pet, data);
            return;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
            displays.remove(data.getPetId());
            displayIds.remove(displayId);
            appliedItemIds.remove(data.getPetId());
            apply(pet, data);
            return;
        }
        if (!SchedulerUtil.owns(pet)) {
            return;
        }
        // 掉座才重挂；正常跟随乘客不发额外包
        ensurePassenger(pet, display);
        if (!config.isFloatBob()) {
            return;
        }
        if (tick % BOB_EVERY_TICKS != 0) {
            return;
        }
        applyTransform(display, tick);
    }

    public void refreshPosition(LivingEntity pet, PetData data) {
        if (CosmeticItems.isNone(data.getFloatItem())) {
            return;
        }
        UUID displayId = displays.get(data.getPetId());
        if (displayId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
            return;
        }
        if (!SchedulerUtil.owns(pet)) {
            return;
        }
        ensurePassenger(pet, display);
    }

    private void setupStaticProps(ItemDisplay display) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(config.isFloatBob() ? 2 : 0);
        display.setBillboard(ItemDisplay.Billboard.CENTER);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        display.setViewRange(0.7f);
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
    }

    private void applyTransform(ItemDisplay display, long tick) {
        float scale = config.getFloatDisplayScale();
        float baseY = (float) config.getFloatOffsetUp();
        float fx = (float) config.getFloatOffsetForward();
        float bob = 0f;
        if (config.isFloatBob()) {
            bob = (float) (Math.sin(tick * 0.15) * config.getFloatBobAmplitude());
        }
        display.setTransformation(new Transformation(
                new Vector3f(0f, baseY + bob, fx),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));
    }

    private void ensurePassenger(LivingEntity pet, ItemDisplay display) {
        Entity vehicle = display.getVehicle();
        if (vehicle != null && vehicle.equals(pet)) {
            return;
        }
        if (vehicle != null) {
            display.leaveVehicle();
        }
        if (!pet.getPassengers().contains(display)) {
            pet.addPassenger(display);
        }
    }

    public void clear(PetData data) {
        clearDisplay(data.getPetId());
    }

    private void clearDisplay(UUID petId) {
        appliedItemIds.remove(petId);
        UUID displayId = displays.remove(petId);
        if (displayId == null) {
            return;
        }
        displayIds.remove(displayId);
        Entity entity = Bukkit.getEntity(displayId);
        if (entity == null) {
            return;
        }
        Runnable remove = () -> {
            if (entity.getVehicle() != null) {
                entity.leaveVehicle();
            }
            entity.remove();
        };
        if (SchedulerUtil.owns(entity)) {
            remove.run();
        } else {
            SchedulerUtil.run(entity, plugin, remove);
        }
    }

    private ItemDisplay getOrCreate(LivingEntity pet, PetData data) {
        UUID existing = displays.get(data.getPetId());
        if (existing != null) {
            Entity entity = Bukkit.getEntity(existing);
            if (entity instanceof ItemDisplay display && display.isValid()) {
                return display;
            }
            displays.remove(data.getPetId());
            displayIds.remove(existing);
            appliedItemIds.remove(data.getPetId());
        }
        Location loc = pet.getLocation();
        ItemDisplay display = pet.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(0.7f);
        });
        displays.put(data.getPetId(), display.getUniqueId());
        displayIds.add(display.getUniqueId());
        return display;
    }

    public void shutdown() {
        for (UUID displayId : new HashSet<>(displayIds)) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) {
                try {
                    if (entity.getVehicle() != null) {
                        entity.leaveVehicle();
                    }
                    entity.remove();
                } catch (Exception ignored) {
                }
            }
        }
        displays.clear();
        displayIds.clear();
        appliedItemIds.clear();
    }
}
