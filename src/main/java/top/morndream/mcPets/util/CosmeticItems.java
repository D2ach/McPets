package top.morndream.mcPets.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** 悬浮物 / 叼物共用的物品 ID → ItemStack。 */
public final class CosmeticItems {

    private CosmeticItems() {
    }

    public static ItemStack fromId(String id) {
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
            case "apple" -> new ItemStack(Material.APPLE);
            case "cookie" -> new ItemStack(Material.COOKIE);
            case "carrot" -> new ItemStack(Material.CARROT);
            default -> {
                try {
                    yield new ItemStack(Material.valueOf(id.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
    }

    public static boolean isNone(String id) {
        return id == null || id.isBlank() || id.equalsIgnoreCase("none");
    }
}
