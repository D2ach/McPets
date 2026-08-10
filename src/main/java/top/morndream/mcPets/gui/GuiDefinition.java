package top.morndream.mcPets.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiDefinition {

    private final String title;
    private final int size;
    private final ItemStack filler;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private final List<Integer> petSlots = new ArrayList<>();
    private final GuiButton petIconTemplate;

    public GuiDefinition(String id, FileConfiguration yaml) {
        this.title = yaml.getString("title", id);
        this.size = Math.clamp(yaml.getInt("size", 27), 9, 54);
        this.filler = readItem(yaml.getConfigurationSection("filler"));
        this.petSlots.addAll(yaml.getIntegerList("pet-slots"));
        this.petIconTemplate = readButton(-1, yaml.getConfigurationSection("pet-icon"));

        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                int slot = section.getInt("slot", -1);
                if (slot < 0 || slot >= size) {
                    continue;
                }
                GuiButton button = readButton(slot, section);
                if (button != null) {
                    buttons.put(slot, button);
                }
            }
        }
    }

    private GuiButton readButton(int slot, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        ItemStack stack = readItem(section);
        String action = section.getString("action", "none");
        String name = section.getString("name", " ");
        List<String> lore = section.getStringList("lore");
        return new GuiButton(slot, stack, action, name, lore);
    }

    private ItemStack readItem(ConfigurationSection section) {
        if (section == null) {
            return new ItemStack(Material.AIR);
        }
        Material mat;
        try {
            mat = Material.valueOf(section.getString("material", "STONE").toUpperCase());
        } catch (IllegalArgumentException ex) {
            mat = Material.STONE;
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = section.getString("name");
            if (name != null) {
                meta.displayName(Text.parse(name));
            }
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> components = new ArrayList<>();
                for (String line : lore) {
                    components.add(Text.parse(line));
                }
                meta.lore(components);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Inventory create(PetGuiHolder holder, Map<String, String> placeholders) {
        Inventory inv = Bukkit.createInventory(holder, size, Text.parse(title, placeholders));
        if (holder instanceof GuiManager.Holder h) {
            h.bind(inv);
        }
        if (filler != null && filler.getType() != Material.AIR) {
            for (int i = 0; i < size; i++) {
                inv.setItem(i, applyPlaceholders(filler.clone(), " ", List.of(), placeholders));
            }
        }
        for (GuiButton button : buttons.values()) {
            inv.setItem(button.slot(), renderButton(button, placeholders));
        }
        return inv;
    }

    public ItemStack renderButton(GuiButton button, Map<String, String> placeholders) {
        return applyPlaceholders(button.base().clone(), button.nameTemplate(), button.loreTemplate(), placeholders);
    }

    public ItemStack renderPetIcon(Map<String, String> placeholders) {
        if (petIconTemplate == null) {
            ItemStack stack = new ItemStack(Material.BONE);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(Text.parse(placeholders.getOrDefault("name", "Pet")));
                stack.setItemMeta(meta);
            }
            return stack;
        }
        return renderButton(petIconTemplate, placeholders);
    }

    private ItemStack applyPlaceholders(ItemStack stack, String nameTemplate, List<String> loreTemplate,
                                        Map<String, String> placeholders) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (nameTemplate != null) {
            meta.displayName(Text.parse(nameTemplate, placeholders));
        }
        if (loreTemplate != null && !loreTemplate.isEmpty()) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreTemplate) {
                lore.add(Text.parse(line, placeholders));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public Map<Integer, GuiButton> buttons() {
        return buttons;
    }

    public List<Integer> petSlots() {
        return petSlots;
    }

    public static GuiDefinition load(McPets plugin, String name) {
        File dir = new File(plugin.getDataFolder(), "gui");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建 GUI 目录: " + dir.getAbsolutePath());
        }
        File file = new File(dir, name + ".yml");
        if (!file.exists()) {
            plugin.saveResource("gui/" + name + ".yml", false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new GuiDefinition(name, yaml);
    }

    public record GuiButton(int slot, ItemStack base, String action, String nameTemplate, List<String> loreTemplate) {
    }

    public interface PetGuiHolder extends InventoryHolder {
        String guiId();

        java.util.UUID petId();
    }
}
