package top.morndream.mcPets.service;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Salmon;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieNautilus;
import org.bukkit.entity.ZombieVillager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 同种生物外观/群系变种：只改 Variant，不换实体、不重生。
 * <p>
 * 数量对照（Paper 26.2，运行时以 Registry 为准）：
 * <ul>
 *   <li>狼 9 · 猫 11 · 村民/僵尸村民 7 · 兔 7 · 马颜色 7 + 花纹 5</li>
 *   <li>牛/猪/鸡/蛙 各 3（cold/temperate/warm）</li>
 *   <li>僵尸鹦鹉螺仅 2（temperate/warm，无 cold）</li>
 *   <li>美西螈/鹦鹉 5 · 羊驼 4 · 鲑鱼体型 3 · 狐/蘑菇牛 2 · 熊猫基因 7</li>
 * </ul>
 */
public final class VariantService {

    /**
     * @param id         存档用 id
     * @param display    GUI 显示名
     * @param biomeGroup 分组标签
     * @param icon       图标
     */
    public record Option(String id, String display, String biomeGroup, Material icon) {
    }

    public boolean supports(@Nullable EntityType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case WOLF, FROG, COW, PIG, CHICKEN, CAT, FOX, RABBIT,
                 AXOLOTL, PARROT, MOOSHROOM, VILLAGER, ZOMBIE_VILLAGER,
                 ZOMBIE_NAUTILUS, HORSE, LLAMA, TRADER_LLAMA,
                 SALMON, PANDA -> true;
            default -> false;
        };
    }

    public boolean supports(LivingEntity entity) {
        return supports(entity.getType());
    }

    public List<Option> list(@Nullable EntityType type) {
        if (!supports(type)) {
            return List.of();
        }
        return switch (type) {
            case WOLF -> keyedOptions(RegistryKey.WOLF_VARIANT, this::wolfDisplay, this::wolfGroup, this::wolfIcon);
            case FROG -> sortClimate(keyedOptions(RegistryKey.FROG_VARIANT, this::climateDisplay, this::climateGroup, this::frogIcon));
            case COW -> sortClimate(keyedOptions(RegistryKey.COW_VARIANT, this::climateDisplay, this::climateGroup, this::climateIcon));
            case PIG -> sortClimate(keyedOptions(RegistryKey.PIG_VARIANT, this::climateDisplay, this::climateGroup, this::climateIcon));
            case CHICKEN -> sortClimate(keyedOptions(RegistryKey.CHICKEN_VARIANT, this::climateDisplay, this::climateGroup, this::climateIcon));
            case ZOMBIE_NAUTILUS -> sortClimate(keyedOptions(RegistryKey.ZOMBIE_NAUTILUS_VARIANT,
                    this::climateDisplay, this::climateGroup, this::climateIcon));
            case CAT -> keyedOptions(RegistryKey.CAT_VARIANT, this::catDisplay, _ -> "外观变种", this::catIcon);
            case VILLAGER, ZOMBIE_VILLAGER -> keyedOptions(RegistryKey.VILLAGER_TYPE,
                    this::villagerDisplay, this::villagerGroup, this::villagerIcon);
            case FOX -> List.of(
                    new Option(Fox.Type.RED.name(), "红狐 · 温带森林", "温带群系", Material.ORANGE_DYE),
                    new Option(Fox.Type.SNOW.name(), "雪狐 · 雪原", "寒冷群系", Material.SNOWBALL)
            );
            case RABBIT -> enumOptions(Rabbit.Type.class, this::rabbitDisplay, this::rabbitGroup, this::rabbitIcon);
            case AXOLOTL -> enumOptions(Axolotl.Variant.class, this::axolotlDisplay, _ -> "外观变种", this::axolotlIcon);
            case PARROT -> enumOptions(Parrot.Variant.class, this::parrotDisplay, _ -> "外观变种", this::parrotIcon);
            case MOOSHROOM -> List.of(
                    new Option(MushroomCow.Variant.RED.name(), "红色蘑菇牛", "蘑菇岛", Material.RED_MUSHROOM),
                    new Option(MushroomCow.Variant.BROWN.name(), "棕色蘑菇牛", "蘑菇岛", Material.BROWN_MUSHROOM)
            );
            case HORSE -> horseOptions();
            case LLAMA, TRADER_LLAMA -> enumOptions(Llama.Color.class, this::llamaDisplay, _ -> "外观变种", this::llamaIcon);
            case SALMON -> enumOptions(Salmon.Variant.class, this::salmonDisplay, _ -> "体型变种", this::salmonIcon);
            case PANDA -> enumOptions(Panda.Gene.class, this::pandaDisplay, _ -> "基因外观", this::pandaIcon);
            default -> List.of();
        };
    }

    /** @return namespaced / 组合 id；不支持则 null */
    public @Nullable String read(LivingEntity entity) {
        if (entity instanceof Wolf wolf) {
            return keyId(wolf.getVariant());
        }
        if (entity instanceof Frog frog) {
            return keyId(frog.getVariant());
        }
        // 蘑菇牛与牛分离；必须先于 Cow 判断（防御性）
        if (entity instanceof MushroomCow mooshroom) {
            return mooshroom.getVariant().name();
        }
        if (entity instanceof Cow cow) {
            return keyId(cow.getVariant());
        }
        if (entity instanceof Pig pig) {
            return keyId(pig.getVariant());
        }
        if (entity instanceof Chicken chicken) {
            return keyId(chicken.getVariant());
        }
        if (entity instanceof ZombieNautilus nautilus) {
            return keyId(nautilus.getVariant());
        }
        if (entity instanceof Cat cat) {
            return keyId(cat.getCatType());
        }
        if (entity instanceof Villager villager) {
            return keyId(villager.getVillagerType());
        }
        if (entity instanceof ZombieVillager zombieVillager) {
            return keyId(zombieVillager.getVillagerType());
        }
        if (entity instanceof Fox fox) {
            return fox.getFoxType().name();
        }
        if (entity instanceof Rabbit rabbit) {
            return rabbit.getRabbitType().name();
        }
        if (entity instanceof Axolotl axolotl) {
            return axolotl.getVariant().name();
        }
        if (entity instanceof Parrot parrot) {
            return parrot.getVariant().name();
        }
        if (entity instanceof Horse horse) {
            return horse.getColor().name() + "|" + horse.getStyle().name();
        }
        if (entity instanceof Llama llama) {
            return llama.getColor().name();
        }
        if (entity instanceof Salmon salmon) {
            return salmon.getVariant().name();
        }
        if (entity instanceof Panda panda) {
            return panda.getMainGene().name() + "|" + panda.getHiddenGene().name();
        }
        return null;
    }

    /** @return 是否成功应用 */
    public boolean apply(LivingEntity entity, @Nullable String variantId) {
        if (variantId == null || variantId.isBlank()) {
            return false;
        }
        String id = variantId.trim();
        try {
            if (entity instanceof Wolf wolf) {
                Wolf.Variant v = resolveKeyed(RegistryKey.WOLF_VARIANT, id);
                if (v == null) {
                    return false;
                }
                wolf.setVariant(v);
                return true;
            }
            if (entity instanceof Frog frog) {
                Frog.Variant v = resolveKeyed(RegistryKey.FROG_VARIANT, id);
                if (v == null) {
                    return false;
                }
                frog.setVariant(v);
                return true;
            }
            if (entity instanceof MushroomCow mooshroom) {
                mooshroom.setVariant(MushroomCow.Variant.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Cow cow) {
                Cow.Variant v = resolveKeyed(RegistryKey.COW_VARIANT, id);
                if (v == null) {
                    return false;
                }
                cow.setVariant(v);
                return true;
            }
            if (entity instanceof Pig pig) {
                Pig.Variant v = resolveKeyed(RegistryKey.PIG_VARIANT, id);
                if (v == null) {
                    return false;
                }
                pig.setVariant(v);
                return true;
            }
            if (entity instanceof Chicken chicken) {
                Chicken.Variant v = resolveKeyed(RegistryKey.CHICKEN_VARIANT, id);
                if (v == null) {
                    return false;
                }
                chicken.setVariant(v);
                return true;
            }
            if (entity instanceof ZombieNautilus nautilus) {
                ZombieNautilus.Variant v = resolveKeyed(RegistryKey.ZOMBIE_NAUTILUS_VARIANT, id);
                if (v == null) {
                    return false;
                }
                nautilus.setVariant(v);
                return true;
            }
            if (entity instanceof Cat cat) {
                Cat.Type v = resolveKeyed(RegistryKey.CAT_VARIANT, id);
                if (v == null) {
                    return false;
                }
                cat.setCatType(v);
                return true;
            }
            if (entity instanceof Villager villager) {
                Villager.Type v = resolveKeyed(RegistryKey.VILLAGER_TYPE, id);
                if (v == null) {
                    return false;
                }
                villager.setVillagerType(v);
                return true;
            }
            if (entity instanceof ZombieVillager zombieVillager) {
                Villager.Type v = resolveKeyed(RegistryKey.VILLAGER_TYPE, id);
                if (v == null) {
                    return false;
                }
                zombieVillager.setVillagerType(v);
                return true;
            }
            if (entity instanceof Fox fox) {
                fox.setFoxType(Fox.Type.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Rabbit rabbit) {
                rabbit.setRabbitType(Rabbit.Type.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Axolotl axolotl) {
                axolotl.setVariant(Axolotl.Variant.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Parrot parrot) {
                parrot.setVariant(Parrot.Variant.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Horse horse) {
                applyHorse(horse, id);
                return true;
            }
            if (entity instanceof Llama llama) {
                llama.setColor(Llama.Color.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Salmon salmon) {
                salmon.setVariant(Salmon.Variant.valueOf(normalizeEnum(id)));
                return true;
            }
            if (entity instanceof Panda panda) {
                applyPanda(panda, id);
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    private void applyHorse(Horse horse, String id) {
        if (id.startsWith("color:")) {
            horse.setColor(Horse.Color.valueOf(normalizeEnum(id.substring("color:".length()))));
            return;
        }
        if (id.startsWith("style:")) {
            horse.setStyle(Horse.Style.valueOf(normalizeEnum(id.substring("style:".length()))));
            return;
        }
        String[] parts = id.split("\\|", 2);
        if (parts.length == 2) {
            horse.setColor(Horse.Color.valueOf(normalizeEnum(parts[0])));
            horse.setStyle(Horse.Style.valueOf(normalizeEnum(parts[1])));
            return;
        }
        horse.setColor(Horse.Color.valueOf(normalizeEnum(id)));
    }

    private void applyPanda(Panda panda, String id) {
        String[] parts = id.split("\\|", 2);
        if (parts.length == 2) {
            panda.setMainGene(Panda.Gene.valueOf(normalizeEnum(parts[0])));
            panda.setHiddenGene(Panda.Gene.valueOf(normalizeEnum(parts[1])));
            return;
        }
        Panda.Gene gene = Panda.Gene.valueOf(normalizeEnum(id));
        // 选单一基因时主/隐都设为该基因，外观稳定可预期
        panda.setMainGene(gene);
        panda.setHiddenGene(gene);
    }

    private List<Option> horseOptions() {
        List<Option> list = new ArrayList<>();
        for (Horse.Color color : Horse.Color.values()) {
            list.add(new Option("color:" + color.name(), "毛色 · " + horseColorDisplay(color),
                    "马匹毛色", horseColorIcon(color)));
        }
        for (Horse.Style style : Horse.Style.values()) {
            list.add(new Option("style:" + style.name(), "花纹 · " + horseStyleDisplay(style),
                    "马匹花纹", Material.WHITE_DYE));
        }
        return list;
    }

    private <T extends Keyed> List<Option> keyedOptions(
            RegistryKey<T> registryKey,
            java.util.function.Function<String, String> displayFn,
            java.util.function.Function<String, String> groupFn,
            java.util.function.Function<String, Material> iconFn) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        List<Option> list = new ArrayList<>();
        for (T value : registry) {
            String id = keyId(value);
            String path = value.getKey().getKey();
            list.add(new Option(id, displayFn.apply(path), groupFn.apply(path), iconFn.apply(path)));
        }
        list.sort(Comparator.comparing(Option::display, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private <E extends Enum<E>> List<Option> enumOptions(
            Class<E> type,
            java.util.function.Function<E, String> displayFn,
            java.util.function.Function<E, String> groupFn,
            java.util.function.Function<E, Material> iconFn) {
        List<Option> list = new ArrayList<>();
        for (E value : type.getEnumConstants()) {
            list.add(new Option(value.name(), displayFn.apply(value), groupFn.apply(value), iconFn.apply(value)));
        }
        return list;
    }

    /** 气候变种：寒冷 → 温和 → 炎热（僵尸鹦鹉螺无 cold，仍按此序） */
    private List<Option> sortClimate(List<Option> options) {
        List<Option> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparingInt(a -> climateOrder(a.id())));
        return sorted;
    }

    private int climateOrder(String id) {
        return switch (normalizePath(id)) {
            case "cold" -> 0;
            case "temperate" -> 1;
            case "warm" -> 2;
            default -> 9;
        };
    }

    private String normalizePath(String id) {
        if (id == null) {
            return "";
        }
        String raw = id.trim();
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            raw = raw.substring(colon + 1);
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private @Nullable <T extends Keyed> T resolveKeyed(RegistryKey<T> registryKey, String id) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        NamespacedKey key = parseKey(id);
        if (key == null) {
            return null;
        }
        return registry.get(key);
    }

    private @Nullable NamespacedKey parseKey(String id) {
        String raw = id.trim();
        if (raw.contains(":")) {
            return NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        }
        return NamespacedKey.minecraft(raw.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    private String keyId(Keyed keyed) {
        return keyed.getKey().toString();
    }

    private String normalizeEnum(String id) {
        String raw = id.trim();
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            raw = raw.substring(colon + 1);
        }
        return raw.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String pretty(String raw) {
        String s = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        s = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                sb.append(c);
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String climateDisplay(String path) {
        return switch (path) {
            case "cold" -> "寒冷群系";
            case "warm" -> "炎热群系";
            default -> "温和群系";
        };
    }

    private String climateGroup(String path) {
        return climateDisplay(path);
    }

    private String wolfDisplay(String path) {
        return switch (path) {
            case "pale" -> "苍白 · 温带草原";
            case "woods" -> "密林 · 森林";
            case "ashen" -> "灰烬 · 灰化林";
            case "black" -> "黑色 · 旧世界";
            case "chestnut" -> "栗色 · 疏林";
            case "rusty" -> "锈色 · 稀树草原";
            case "spotted" -> "斑点 · 热带草原";
            case "striped" -> "条纹 · 恶地";
            case "snowy" -> "雪白 · 雪原";
            default -> pretty(path);
        };
    }

    private String wolfGroup(String path) {
        return switch (path) {
            case "snowy", "ashen" -> "寒冷群系";
            case "spotted", "striped", "rusty" -> "炎热群系";
            case "woods", "chestnut" -> "森林群系";
            default -> "温带群系";
        };
    }

    private String villagerDisplay(String path) {
        return switch (path) {
            case "desert" -> "沙漠村民";
            case "jungle" -> "丛林村民";
            case "plains" -> "平原村民";
            case "savanna" -> "热带草原村民";
            case "snow" -> "雪原村民";
            case "swamp" -> "沼泽村民";
            case "taiga" -> "针叶林村民";
            default -> pretty(path);
        };
    }

    private String villagerGroup(String path) {
        return switch (path) {
            case "snow", "taiga" -> "寒冷群系";
            case "desert", "savanna", "jungle" -> "炎热群系";
            case "swamp" -> "湿地群系";
            default -> "温带群系";
        };
    }

    private Material villagerIcon(String path) {
        return switch (path) {
            case "desert" -> Material.SAND;
            case "jungle" -> Material.JUNGLE_LEAVES;
            case "savanna" -> Material.ACACIA_SAPLING;
            case "snow" -> Material.SNOW_BLOCK;
            case "swamp" -> Material.LILY_PAD;
            case "taiga" -> Material.SPRUCE_SAPLING;
            default -> Material.EMERALD;
        };
    }

    private String catDisplay(String path) {
        return switch (path) {
            case "tabby" -> "虎斑猫";
            case "black" -> "黑猫";
            case "all_black" -> "全黑猫";
            case "red" -> "红猫";
            case "siamese" -> "暹罗猫";
            case "british_shorthair" -> "英国短毛";
            case "calico" -> "三花猫";
            case "persian" -> "波斯猫";
            case "ragdoll" -> "布偶猫";
            case "white" -> "白猫";
            case "jellie" -> "果冻猫";
            default -> pretty(path);
        };
    }

    private String rabbitDisplay(Rabbit.Type type) {
        return switch (type) {
            case BROWN -> "棕色兔";
            case WHITE -> "白兔 · 雪原";
            case BLACK -> "黑兔";
            case BLACK_AND_WHITE -> "黑白兔";
            case GOLD -> "金色兔";
            case SALT_AND_PEPPER -> "胡椒兔";
            case THE_KILLER_BUNNY -> "杀手兔";
        };
    }

    private String rabbitGroup(Rabbit.Type type) {
        return type == Rabbit.Type.WHITE ? "寒冷群系" : "外观变种";
    }

    private String axolotlDisplay(Axolotl.Variant variant) {
        return switch (variant) {
            case LUCY -> "粉色美西螈";
            case WILD -> "棕色美西螈";
            case GOLD -> "金色美西螈";
            case CYAN -> "青色美西螈";
            case BLUE -> "蓝色美西螈";
        };
    }

    private String parrotDisplay(Parrot.Variant variant) {
        return switch (variant) {
            case RED -> "红色鹦鹉";
            case BLUE -> "蓝色鹦鹉";
            case GREEN -> "绿色鹦鹉";
            case CYAN -> "青色鹦鹉";
            case GRAY -> "灰色鹦鹉";
        };
    }

    private String llamaDisplay(Llama.Color color) {
        return switch (color) {
            case CREAMY -> "奶油色羊驼";
            case WHITE -> "白色羊驼";
            case BROWN -> "棕色羊驼";
            case GRAY -> "灰色羊驼";
        };
    }

    private Material llamaIcon(Llama.Color color) {
        return switch (color) {
            case CREAMY -> Material.SAND;
            case WHITE -> Material.WHITE_WOOL;
            case BROWN -> Material.BROWN_WOOL;
            case GRAY -> Material.GRAY_WOOL;
        };
    }

    private String salmonDisplay(Salmon.Variant variant) {
        return switch (variant) {
            case SMALL -> "小型鲑鱼";
            case MEDIUM -> "中型鲑鱼";
            case LARGE -> "大型鲑鱼";
        };
    }

    private Material salmonIcon(Salmon.Variant variant) {
        return switch (variant) {
            case SMALL -> Material.COD;
            case MEDIUM -> Material.SALMON;
            case LARGE -> Material.COOKED_SALMON;
        };
    }

    private String pandaDisplay(Panda.Gene gene) {
        return switch (gene) {
            case NORMAL -> "普通熊猫";
            case LAZY -> "懒惰熊猫";
            case WORRIED -> "忧虑熊猫";
            case PLAYFUL -> "顽皮熊猫";
            case BROWN -> "棕色熊猫";
            case WEAK -> "虚弱熊猫";
            case AGGRESSIVE -> "好斗熊猫";
        };
    }

    private Material pandaIcon(Panda.Gene gene) {
        return switch (gene) {
            case BROWN -> Material.BROWN_DYE;
            case AGGRESSIVE -> Material.RED_DYE;
            case LAZY -> Material.GRAY_DYE;
            case PLAYFUL -> Material.PINK_DYE;
            case WORRIED -> Material.LIGHT_BLUE_DYE;
            case WEAK -> Material.WHITE_DYE;
            default -> Material.BLACK_DYE;
        };
    }

    private String horseColorDisplay(Horse.Color color) {
        return switch (color) {
            case WHITE -> "白色";
            case CREAMY -> "奶油色";
            case CHESTNUT -> "栗色";
            case BROWN -> "棕色";
            case BLACK -> "黑色";
            case GRAY -> "灰色";
            case DARK_BROWN -> "深棕";
        };
    }

    private Material horseColorIcon(Horse.Color color) {
        return switch (color) {
            case WHITE -> Material.WHITE_WOOL;
            case CREAMY -> Material.SAND;
            case CHESTNUT -> Material.ORANGE_DYE;
            case BROWN -> Material.BROWN_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case GRAY -> Material.GRAY_WOOL;
            case DARK_BROWN -> Material.BROWN_DYE;
        };
    }

    private String horseStyleDisplay(Horse.Style style) {
        return switch (style) {
            case NONE -> "无花纹";
            case WHITE -> "白袜";
            case WHITEFIELD -> "白斑";
            case WHITE_DOTS -> "白点";
            case BLACK_DOTS -> "黑点";
        };
    }

    private Material climateIcon(String path) {
        return switch (path) {
            case "cold" -> Material.SNOWBALL;
            case "warm" -> Material.ORANGE_DYE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private Material wolfIcon(String path) {
        return switch (path) {
            case "snowy" -> Material.SNOW_BLOCK;
            case "black" -> Material.BLACK_DYE;
            case "ashen" -> Material.GRAY_DYE;
            case "chestnut" -> Material.BROWN_DYE;
            case "rusty" -> Material.ORANGE_DYE;
            case "spotted" -> Material.WHITE_DYE;
            case "striped" -> Material.YELLOW_DYE;
            case "woods" -> Material.OAK_LOG;
            default -> Material.BONE;
        };
    }

    private Material frogIcon(String path) {
        return switch (path) {
            case "cold" -> Material.VERDANT_FROGLIGHT;
            case "warm" -> Material.OCHRE_FROGLIGHT;
            default -> Material.PEARLESCENT_FROGLIGHT;
        };
    }

    private Material catIcon(String path) {
        return switch (path) {
            case "all_black", "black" -> Material.BLACK_DYE;
            case "white" -> Material.WHITE_DYE;
            case "red" -> Material.ORANGE_DYE;
            case "jellie" -> Material.LIGHT_GRAY_DYE;
            case "siamese" -> Material.LIGHT_BLUE_DYE;
            default -> Material.STRING;
        };
    }

    private Material rabbitIcon(Rabbit.Type type) {
        return switch (type) {
            case WHITE -> Material.WHITE_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case BLACK_AND_WHITE -> Material.LIGHT_GRAY_WOOL;
            case GOLD -> Material.GOLD_INGOT;
            case SALT_AND_PEPPER -> Material.GRAY_WOOL;
            case THE_KILLER_BUNNY -> Material.REDSTONE;
            default -> Material.BROWN_WOOL;
        };
    }

    private Material axolotlIcon(Axolotl.Variant variant) {
        return switch (variant) {
            case LUCY -> Material.PINK_DYE;
            case WILD -> Material.BROWN_DYE;
            case GOLD -> Material.GOLD_INGOT;
            case CYAN -> Material.CYAN_DYE;
            case BLUE -> Material.BLUE_DYE;
        };
    }

    private Material parrotIcon(Parrot.Variant variant) {
        return switch (variant) {
            case RED -> Material.RED_DYE;
            case BLUE -> Material.BLUE_DYE;
            case GREEN -> Material.GREEN_DYE;
            case CYAN -> Material.CYAN_DYE;
            case GRAY -> Material.GRAY_DYE;
        };
    }
}
