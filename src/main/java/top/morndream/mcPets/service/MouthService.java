package top.morndream.mcPets.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
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
 * 叼物：狐狸用主手（零额外实体）；其余用一次配置好的 ItemDisplay 乘客。
 * 不参与 AI 每 tick 刷新，仅在设置/传送掉座时轻量校正，避免性能开销。
 */
public final class MouthService {

    private final McPets plugin;
    private final PluginConfig config;
    private final Map<UUID, UUID> mouthDisplays = new ConcurrentHashMap<>();
    private final Set<UUID> mouthDisplayIds = ConcurrentHashMap.newKeySet();
    /** 已套用的物品 id，避免重复 setItemStack */
    private final Map<UUID, String> appliedItemIds = new ConcurrentHashMap<>();

    public MouthService(McPets plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
    }

    public boolean isMouthDisplay(Entity entity) {
        return entity != null && mouthDisplayIds.contains(entity.getUniqueId());
    }

    public void apply(LivingEntity pet, PetData data) {
        if (!SchedulerUtil.owns(pet)) {
            SchedulerUtil.run(pet, plugin, () -> apply(pet, data));
            return;
        }
        if (CosmeticItems.isNone(data.getMouthItem())) {
            clearVisual(pet, data);
            return;
        }
        ItemStack stack = CosmeticItems.fromId(data.getMouthItem());
        if (stack == null || stack.getType().isAir()) {
            clearVisual(pet, data);
            return;
        }

        if (usesHandEquipment(pet)) {
            clearDisplayOnly(data.getPetId());
            EntityEquipment eq = pet.getEquipment();
            if (eq != null) {
                ItemStack current = eq.getItemInMainHand();
                if (current.getType() != stack.getType()) {
                    eq.setItemInMainHand(stack);
                }
            }
            appliedItemIds.put(data.getPetId(), data.getMouthItem());
            return;
        }

        ItemDisplay display = getOrCreateDisplay(pet, data);
        String prev = appliedItemIds.get(data.getPetId());
        if (prev == null || !prev.equalsIgnoreCase(data.getMouthItem())) {
            display.setItemStack(stack);
            appliedItemIds.put(data.getPetId(), data.getMouthItem());
        }
        configureDisplayOnce(pet, display);
        ensurePassenger(pet, display);
    }

    /**
     * 仅在传送等场景：若仍骑在宠物上则直接返回，否则重新上座。
     * 不重写 transformation，避免无谓数据包。
     */
    public void refreshPosition(LivingEntity pet, PetData data) {
        if (CosmeticItems.isNone(data.getMouthItem()) || usesHandEquipment(pet)) {
            return;
        }
        UUID displayId = mouthDisplays.get(data.getPetId());
        if (displayId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
            mouthDisplays.remove(data.getPetId());
            mouthDisplayIds.remove(displayId);
            appliedItemIds.remove(data.getPetId());
            return;
        }
        if (!SchedulerUtil.owns(pet)) {
            return;
        }
        ensurePassenger(pet, display);
    }

    private boolean usesHandEquipment(LivingEntity pet) {
        return pet instanceof Fox;
    }

    private void configureDisplayOnce(LivingEntity pet, ItemDisplay display) {
        float scale = config.getMouthDisplayScale();
        float forward = (float) config.getMouthForward();
        float down = (float) config.getMouthUp();
        float eyeFactor = (float) (pet.getEyeHeight() * config.getMouthEyeFactor());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTransformation(new Transformation(
                new Vector3f(0f, down - eyeFactor * 0.15f, forward + eyeFactor * 0.05f),
                new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setBillboard(ItemDisplay.Billboard.FIXED);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setViewRange(0.5f);
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
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
        LivingEntity pet = null;
        Entity maybe = Bukkit.getEntity(data.getEntityId());
        if (maybe instanceof LivingEntity living) {
            pet = living;
        }
        if (pet != null && !SchedulerUtil.owns(pet)) {
            LivingEntity finalPet = pet;
            SchedulerUtil.run(pet, plugin, () -> clearVisual(finalPet, data));
            return;
        }
        if (pet != null) {
            clearVisual(pet, data);
        } else {
            clearDisplayOnly(data.getPetId());
        }
    }

    private void clearVisual(LivingEntity pet, PetData data) {
        if (usesHandEquipment(pet)) {
            EntityEquipment eq = pet.getEquipment();
            if (eq != null) {
                String vanilla = data.getVanillaFoxMainHand();
                if (vanilla == null || vanilla.isBlank()) {
                    eq.setItemInMainHand(new ItemStack(Material.AIR));
                } else {
                    try {
                        eq.setItemInMainHand(new ItemStack(Material.valueOf(vanilla)));
                    } catch (IllegalArgumentException ex) {
                        eq.setItemInMainHand(new ItemStack(Material.AIR));
                    }
                }
            }
        }
        clearDisplayOnly(data.getPetId());
    }

    private void clearDisplayOnly(UUID petId) {
        appliedItemIds.remove(petId);
        UUID displayId = mouthDisplays.remove(petId);
        if (displayId == null) {
            return;
        }
        mouthDisplayIds.remove(displayId);
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

    private ItemDisplay getOrCreateDisplay(LivingEntity pet, PetData data) {
        UUID existing = mouthDisplays.get(data.getPetId());
        if (existing != null) {
            Entity entity = Bukkit.getEntity(existing);
            if (entity instanceof ItemDisplay display && display.isValid()) {
                return display;
            }
            mouthDisplays.remove(data.getPetId());
            mouthDisplayIds.remove(existing);
            appliedItemIds.remove(data.getPetId());
        }
        Location loc = pet.getLocation();
        ItemDisplay display = pet.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(0.5f);
        });
        mouthDisplays.put(data.getPetId(), display.getUniqueId());
        mouthDisplayIds.add(display.getUniqueId());
        return display;
    }

    public void shutdown() {
        for (UUID displayId : new HashSet<>(mouthDisplayIds)) {
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
        mouthDisplays.clear();
        mouthDisplayIds.clear();
        appliedItemIds.clear();
    }
}
