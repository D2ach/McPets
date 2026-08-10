package top.morndream.mcPets.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 清理旧版本误写入实体的插件 PDC。
 * 仅在确认存在键时才 remove，避免无意义地弄脏实体。
 */
public final class LegacyPdcCleaner {

    private static final String[] KEYS = {
            "pet_id", "owner_id", "mouth_display", "parked"
    };

    private LegacyPdcCleaner() {
    }

    public static void strip(Plugin plugin, Entity entity) {
        if (entity == null) {
            return;
        }
        var pdc = entity.getPersistentDataContainer();
        for (String key : KEYS) {
            NamespacedKey nk = new NamespacedKey(plugin, key);
            if (pdc.has(nk, PersistentDataType.STRING) || pdc.has(nk, PersistentDataType.BYTE)
                    || pdc.has(nk)) {
                pdc.remove(nk);
            }
        }
    }
}
