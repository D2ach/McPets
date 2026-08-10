package top.morndream.mcPets.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.util.SchedulerUtil;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 叼物：Fox 用手部物品；其余用内存登记的 ItemDisplay passenger。
 * 展示实体 setPersistent(false)，且不写任何插件 PDC。
 */
public final class MouthService {

    private final McPets plugin;
    private final PluginConfig config;
    /** petId -> display entity UUID（仅内存） */
    private final Map<UUID, UUID> mouthDisplays = new ConcurrentHashMap<>();
    /** 快速判断是否为叼物展示实体（仅内存） */
    private final Set<UUID> mouthDisplayIds = ConcurrentHashMap.newKeySet();

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
        String id = data.getMouthItem();
        if (id == null || id.equalsIgnoreCase("none")) {
            clear(data);
            return;
        }
        ItemStack stack = toItem(id);
        if (stack == null || stack.getType().isAir()) {
            clear(data);
            return;
        }

        if (pet instanceof Fox fox) {
            clearDisplayOnly(data.getPetId());
            fox.getEquipment().setItemInMainHand(stack);
            return;
        }

        ItemDisplay display = getOrCreateDisplay(pet, data);
        display.setItemStack(stack);
        applyPassengerTransform(display);
        ensurePassenger(pet, display);
    }

    public void refreshPosition(LivingEntity pet, PetData data) {
        UUID displayId = mouthDisplays.get(data.getPetId());
        if (displayId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
            mouthDisplays.remove(data.getPetId());
            mouthDisplayIds.remove(displayId);
            return;
        }
        if (!SchedulerUtil.owns(pet)) {
            return;
        }
        ensurePassenger(pet, display);
        applyPassengerTransform(display);
    }

    private void ensurePassenger(LivingEntity pet, ItemDisplay display) {
        if (display.getVehicle() != null && display.getVehicle().equals(pet)) {
            return;
        }
        if (display.getVehicle() != null) {
            display.leaveVehicle();
        }
        if (pet.getPassengers().contains(display)) {
            return;
        }
        pet.addPassenger(display);
    }

    private void applyPassengerTransform(ItemDisplay display) {
        float scale = config.getMouthDisplayScale();
        float fx = (float) config.getMouthForward();
        float uy = (float) config.getMouthUp();
        display.setTransformation(new Transformation(
                new Vector3f(0f, uy + 0.2f, fx),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setBillboard(ItemDisplay.Billboard.FIXED);
    }

    public void clear(PetData data) {
        LivingEntity pet = null;
        Entity maybe = Bukkit.getEntity(data.getEntityId());
        if (maybe instanceof LivingEntity living) {
            pet = living;
        }
        if (pet != null && !SchedulerUtil.owns(pet)) {
            LivingEntity finalPet = pet;
            SchedulerUtil.run(pet, plugin, () -> {
                if (finalPet instanceof Fox fox) {
                    fox.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
                }
                clearDisplayOnly(data.getPetId());
            });
            return;
        }
        if (pet instanceof Fox fox) {
            fox.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
        }
        clearDisplayOnly(data.getPetId());
    }

    private void clearDisplayOnly(UUID petId) {
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
        }
        Location loc = pet.getLocation();
        ItemDisplay display = pet.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(0.6f);
        });
        mouthDisplays.put(data.getPetId(), display.getUniqueId());
        mouthDisplayIds.add(display.getUniqueId());
        return display;
    }

    public ItemStack toItem(String id) {
        if (id == null) {
            return null;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "none" -> new ItemStack(Material.AIR);
            case "bone" -> new ItemStack(Material.BONE);
            case "stick" -> new ItemStack(Material.STICK);
            case "rose" -> new ItemStack(Material.POPPY);
            case "porkchop" -> new ItemStack(Material.PORKCHOP);
            case "diamond" -> new ItemStack(Material.DIAMOND);
            case "sword" -> new ItemStack(Material.GOLDEN_SWORD);
            default -> {
                try {
                    yield new ItemStack(Material.valueOf(id.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
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
    }
}
