package top.morndream.mcPets.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.util.Text;

/**
 * 外观仅在插件运行时套用；卸载/删除时必须 {@link #revertVanilla} 还原，避免污染世界存档。
 * 无敌不写实体 Invulnerable，只靠伤害事件拦截。
 */
public final class AppearanceService {

    private final McPets plugin;
    private final PluginConfig config;
    private final Attribute scaleAttribute;

    public AppearanceService(McPets plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
        this.scaleAttribute = resolveScale();
    }

    /** 驯服/重生时记录实体当时的原版状态，供卸载还原。 */
    public void captureVanilla(LivingEntity entity, PetData data) {
        data.setVanillaScale(readScale(entity));
        data.setVanillaRemoveWhenFarAway(entity.getRemoveWhenFarAway());
        data.setVanillaCustomNameVisible(entity.isCustomNameVisible());
        Component name = entity.customName();
        if (name != null) {
            data.setVanillaCustomName(PlainTextComponentSerializer.plainText().serialize(name));
        } else {
            data.setVanillaCustomName(null);
        }
        if (entity instanceof Ageable ageable) {
            data.setVanillaBaby(!ageable.isAdult());
        } else {
            data.setVanillaBaby(false);
        }
        if (entity instanceof Fox fox) {
            ItemStack hand = fox.getEquipment().getItemInMainHand();
            if (hand.getType().isAir()) {
                data.setVanillaFoxMainHand(null);
            } else {
                data.setVanillaFoxMainHand(hand.getType().name());
            }
        } else {
            data.setVanillaFoxMainHand(null);
        }
    }

    public void applyRuntime(LivingEntity entity, PetData data) {
        entity.customName(Text.parse(data.effectiveDisplayRaw()));
        entity.setCustomNameVisible(true);
        applyScale(entity, config.scaleValue(data.getScaleTier()));
        applyBaby(entity, data.isBaby());
        // 不调用 setInvulnerable / setRemoveWhenFarAway / setTamed，避免写入存档
    }

    public void applyScale(LivingEntity entity, PetData data) {
        applyScale(entity, config.scaleValue(data.getScaleTier()));
    }

    public void applyScale(LivingEntity entity, double value) {
        if (scaleAttribute == null) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(scaleAttribute);
        if (instance == null) {
            return;
        }
        instance.setBaseValue(value);
    }

    public void applyBaby(LivingEntity entity, boolean baby) {
        if (entity instanceof Ageable ageable) {
            if (baby) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
    }

    /**
     * 将实体恢复为驯服前快照，确保区块存档中不残留插件造成的改动。
     */
    public void revertVanilla(LivingEntity entity, PetData data) {
        entity.customName(data.getVanillaCustomName() == null || data.getVanillaCustomName().isBlank()
                ? null
                : Text.parse(data.getVanillaCustomName()));
        entity.setCustomNameVisible(data.isVanillaCustomNameVisible());
        applyScale(entity, data.getVanillaScale());
        applyBaby(entity, data.isVanillaBaby());
        entity.setInvulnerable(false);
        entity.setRemoveWhenFarAway(data.isVanillaRemoveWhenFarAway());
        if (entity instanceof Fox fox) {
            String mat = data.getVanillaFoxMainHand();
            if (mat == null || mat.isBlank()) {
                fox.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                try {
                    fox.getEquipment().setItemInMainHand(new ItemStack(Material.valueOf(mat)));
                } catch (IllegalArgumentException ex) {
                    fox.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
                }
            }
        }
    }

    private double readScale(LivingEntity entity) {
        if (scaleAttribute == null) {
            return 1.0;
        }
        AttributeInstance instance = entity.getAttribute(scaleAttribute);
        if (instance == null) {
            return 1.0;
        }
        return instance.getBaseValue();
    }

    private Attribute resolveScale() {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
        if (attr == null) {
            plugin.getLogger().warning("当前服务端不支持 SCALE 属性，体型档位将不可用。");
        }
        return attr;
    }
}
