package top.morndream.mcPets.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.model.PetData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * 单文件 pets.yml：脏标记 + 定时/关服落盘，避免每次变更写盘。
 */
public final class PetStorage {

    private final McPets plugin;
    private final File file;
    private FileConfiguration yaml;
    private final Map<UUID, PetData> byPetId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entityToPet = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, UUID>> ownerNameIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PetStorage(McPets plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pets.yml");
    }

    public void load() {
        byPetId.clear();
        entityToPet.clear();
        ownerNameIndex.clear();
        dirty.set(false);
        if (!file.exists()) {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录: " + folder.getAbsolutePath());
            }
            yaml = new YamlConfiguration();
            yaml.createSection("pets");
            dirty.set(true);
            flush();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pets = yaml.getConfigurationSection("pets");
        if (pets == null) {
            return;
        }
        for (String key : pets.getKeys(false)) {
            ConfigurationSection section = pets.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                PetData data = PetData.read(section);
                index(data);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "无法加载宠物条目: " + key, ex);
            }
        }
        dirty.set(false);
        plugin.getLogger().info("已加载 " + byPetId.size() + " 只宠物。");
    }

    public void markDirty() {
        dirty.set(true);
    }

    /** 仅在有变更时写盘。 */
    public void flush() {
        if (!dirty.get()) {
            return;
        }
        save(true);
    }

    /** @param force true 时忽略脏标记强制写盘（关服用） */
    public synchronized void save(boolean force) {
        if (!force && !dirty.get()) {
            return;
        }
        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection pets = out.createSection("pets");
        for (PetData data : byPetId.values()) {
            ConfigurationSection section = pets.createSection(data.getPetId().toString());
            data.write(section);
        }
        try {
            out.save(file);
            yaml = out;
            dirty.set(false);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "保存 pets.yml 失败", ex);
        }
    }

    private void index(PetData data) {
        byPetId.put(data.getPetId(), data);
        entityToPet.put(data.getEntityId(), data.getPetId());
        ownerNameIndex
                .computeIfAbsent(data.getOwnerId(), _ -> new ConcurrentHashMap<>())
                .put(data.getInternalName().toLowerCase(), data.getPetId());
    }

    private void unindex(PetData data) {
        byPetId.remove(data.getPetId());
        entityToPet.remove(data.getEntityId());
        Map<String, UUID> names = ownerNameIndex.get(data.getOwnerId());
        if (names != null) {
            names.remove(data.getInternalName().toLowerCase());
            if (names.isEmpty()) {
                ownerNameIndex.remove(data.getOwnerId());
            }
        }
    }

    public void add(PetData data) {
        index(data);
        markDirty();
    }

    public void remove(PetData data) {
        unindex(data);
        markDirty();
    }

    public void rebindEntity(PetData data, UUID newEntityId) {
        UUID old = data.getEntityId();
        if (old != null) {
            entityToPet.remove(old);
        }
        data.setEntityId(newEntityId);
        entityToPet.put(newEntityId, data.getPetId());
        markDirty();
    }

    public PetData byPetId(UUID id) {
        return byPetId.get(id);
    }

    public PetData byEntityId(UUID entityId) {
        UUID petId = entityToPet.get(entityId);
        return petId == null ? null : byPetId.get(petId);
    }

    public PetData byOwnerAndName(UUID owner, String name) {
        Map<String, UUID> names = ownerNameIndex.get(owner);
        if (names == null) {
            return null;
        }
        UUID id = names.get(name.toLowerCase());
        return id == null ? null : byPetId.get(id);
    }

    public boolean hasName(UUID owner, String name) {
        Map<String, UUID> names = ownerNameIndex.get(owner);
        return names != null && names.containsKey(name.toLowerCase());
    }

    public List<PetData> byOwner(UUID owner) {
        List<PetData> list = new ArrayList<>();
        for (PetData data : byPetId.values()) {
            if (data.getOwnerId().equals(owner)) {
                list.add(data);
            }
        }
        return list;
    }

    public Collection<PetData> all() {
        return List.copyOf(byPetId.values());
    }
}
