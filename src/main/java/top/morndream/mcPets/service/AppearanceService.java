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
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
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
    private final VariantService variants;
    private final Attribute scaleAttribute;

    public AppearanceService(McPets plugin, VariantService variants) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
        this.variants = variants;
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
        // 群系变种：只快照原版，不把玩家后来的选择写回 vanilla
        data.setVanillaVariant(variants.read(entity));
        if (entity instanceof Villager villager) {
            data.setVanillaProfession(villager.getProfession().getKey().toString());
            data.setVanillaVillagerXp(villager.getVillagerExperience());
        } else {
            data.setVanillaProfession(null);
            data.setVanillaVillagerXp(0);
        }
    }

    /**
     * @return 是否因旧档迁移补写了变种字段（调用方应 markDirty）
     */
    public boolean applyRuntime(LivingEntity entity, PetData data) {
        entity.customName(Text.parse(data.effectiveDisplayRaw()));
        entity.setCustomNameVisible(true);
        applyScale(entity, config.scaleValue(data.getScaleTier()));
        applyBaby(entity, data.isBaby());
        boolean migrated = applyVariant(entity, data);
        stripVillagerProfessionIfBlocked(entity);
        // 不调用 setInvulnerable / setRemoveWhenFarAway / setTamed，避免写入存档
        return migrated;
    }

    /** 配置开启时，清除宠物村民的工作职业（保留 nitwit / none）。 */
    public void stripVillagerProfessionIfBlocked(LivingEntity entity) {
        if (!config.isBlockVillagerProfession()) {
            return;
        }
        if (!(entity instanceof Villager villager)) {
            return;
        }
        Villager.Profession profession = villager.getProfession();
        if (Villager.Profession.NONE.equals(profession)
                || Villager.Profession.NITWIT.equals(profession)) {
            return;
        }
        villager.setProfession(Villager.Profession.NONE);
        villager.setVillagerExperience(0);
    }

    /**
     * 旧档迁移：若尚未有 vanilla 变种快照，以当前实体为准补齐；
     * 有选中变种则运行时套用。
     *
     * @return 是否补写了存档字段（调用方应 markDirty）
     */
    public boolean applyVariant(LivingEntity entity, PetData data) {
        if (!variants.supports(entity)) {
            return false;
        }
        boolean migrated = false;
        if (data.getVanillaVariant() == null) {
            data.setVanillaVariant(variants.read(entity));
            migrated = true;
        }
        if (data.getVariant() == null) {
            data.setVariant(data.getVanillaVariant());
            migrated = true;
        }
        if (data.getVariant() != null && !variants.apply(entity, data.getVariant())) {
            plugin.getLogger().warning("无法套用变种 " + data.getVariant()
                    + " → 宠物 " + data.getInternalName() + " (" + data.getEntityType() + ")");
        }
        return migrated;
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
        // 必须还原驯服前变种，避免玩家换皮后的外观写进区块
        if (variants.supports(entity)) {
            String vanilla = data.getVanillaVariant();
            if (vanilla != null && !vanilla.isBlank()) {
                if (!variants.apply(entity, vanilla)) {
                    plugin.getLogger().warning("卸载还原变种失败 " + vanilla
                            + " → 宠物 " + data.getInternalName());
                }
            }
        }
        if (entity instanceof Villager villager) {
            restoreVillagerProfession(villager, data);
        }
    }

    private void restoreVillagerProfession(Villager villager, PetData data) {
        String raw = data.getVanillaProfession();
        if (raw == null || raw.isBlank()) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        if (key == null) {
            return;
        }
        Villager.Profession profession = Registry.VILLAGER_PROFESSION.get(key);
        if (profession == null) {
            plugin.getLogger().warning("无法还原村民职业 " + raw + " → " + data.getInternalName());
            return;
        }
        villager.setProfession(profession);
        villager.setVillagerExperience(data.getVanillaVillagerXp());
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
