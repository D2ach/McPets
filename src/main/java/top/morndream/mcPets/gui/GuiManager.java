package top.morndream.mcPets.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.model.PetState;
import top.morndream.mcPets.service.MessageService;
import top.morndream.mcPets.service.PetService;
import top.morndream.mcPets.service.VariantService;
import top.morndream.mcPets.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager implements Listener {

    private final McPets plugin;
    private final PetService pets;
    private final MessageService messages;
    private final Map<String, GuiDefinition> definitions = new HashMap<>();
    private final Set<UUID> openViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> renameSessions = new ConcurrentHashMap<>();

    public GuiManager(McPets plugin) {
        this.plugin = plugin;
        this.pets = plugin.getPetService();
        this.messages = plugin.getMessageService();
    }

    public void loadAll() {
        definitions.clear();
        for (String id : List.of("main", "manage", "float", "variant")) {
            definitions.put(id, GuiDefinition.load(plugin, id));
        }
    }

    public void openMain(Player player) {
        List<PetData> list = pets.storage().byOwner(player.getUniqueId());
        if (list.isEmpty()) {
            messages.send(player, "gui-no-pets");
            return;
        }
        GuiDefinition def = definitions.get("main");
        Holder holder = new Holder("main", null);
        Map<String, String> ph = Map.of("count", String.valueOf(list.size()));
        Inventory inv = def.create(holder, ph);
        int i = 0;
        for (PetData data : list) {
            if (i >= def.petSlots().size()) {
                break;
            }
            int slot = def.petSlots().get(i++);
            inv.setItem(slot, def.renderPetIcon(placeholders(data)));
            holder.slotPets.put(slot, data.getPetId());
        }
        player.openInventory(inv);
        openViewers.add(player.getUniqueId());
    }

    public void openManage(Player player, PetData data) {
        if (denyManage(player, data)) {
            messages.send(player, "not-owner");
            return;
        }
        if (!player.hasPermission("mcpets.gui") && !player.hasPermission("mcpets.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        GuiDefinition def = definitions.get("manage");
        Holder holder = new Holder("manage", data.getPetId());
        Inventory inv = def.create(holder, placeholders(data));
        for (var e : def.buttons().entrySet()) {
            inv.setItem(e.getKey(), def.renderButton(e.getValue(), placeholders(data)));
        }
        player.openInventory(inv);
        openViewers.add(player.getUniqueId());
    }

    public void openFloat(Player player, PetData data) {
        if (denyManage(player, data)) {
            messages.send(player, "not-owner");
            return;
        }
        GuiDefinition def = definitions.get("float");
        Holder holder = new Holder("float", data.getPetId());
        Inventory inv = def.create(holder, placeholders(data));
        for (var e : def.buttons().entrySet()) {
            inv.setItem(e.getKey(), def.renderButton(e.getValue(), placeholders(data)));
        }
        player.openInventory(inv);
        openViewers.add(player.getUniqueId());
    }

    public void openVariant(Player player, PetData data) {
        if (denyManage(player, data)) {
            messages.send(player, "not-owner");
            return;
        }
        VariantService variants = plugin.getVariantService();
        List<VariantService.Option> options = variants.list(data.getEntityType());
        if (options.isEmpty()) {
            messages.send(player, "variant-unsupported", Map.of("name", data.getName()));
            return;
        }
        GuiDefinition def = definitions.get("variant");
        if (def == null) {
            messages.sendRaw(player, "<red>群系选择界面未加载，请检查 gui/variant.yml</red>");
            return;
        }
        Holder holder = new Holder("variant", data.getPetId());
        Map<String, String> ph = placeholders(data);
        Inventory inv = def.create(holder, ph);
        List<Integer> slots = def.variantSlots();
        if (slots.isEmpty()) {
            messages.sendRaw(player, "<red>gui/variant.yml 缺少 variant-slots 配置。</red>");
            return;
        }
        if (options.size() > slots.size()) {
            plugin.getLogger().warning("变种选项 " + options.size() + " 超过 GUI 槽位 "
                    + slots.size() + "，类型 " + data.getEntityType() + " 将截断显示。");
        }
        String current = data.getVariant();
        for (int i = 0; i < options.size() && i < slots.size(); i++) {
            VariantService.Option opt = options.get(i);
            int slot = slots.get(i);
            inv.setItem(slot, renderVariantIcon(opt, isVariantSelected(current, opt.id())));
            holder.slotVariants.put(slot, opt.id());
        }
        player.openInventory(inv);
        openViewers.add(player.getUniqueId());
    }

    private ItemStack renderVariantIcon(VariantService.Option opt, boolean selected) {
        ItemStack stack = new ItemStack(opt.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse((selected ? "<green><bold>" : "<white><bold>")
                    + opt.display() + (selected ? " ✓" : "")
                    + "</bold>" + (selected ? "</green>" : "</white>")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.parse("<aqua>群系: </aqua><white>" + opt.biomeGroup() + "</white>"));
            lore.add(Text.parse("<dark_gray>" + opt.id() + "</dark_gray>"));
            lore.add(Text.parse(selected
                    ? "<green>当前选用的群系外观</green>"
                    : "<yellow>点击切换到此群系</yellow>"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 比较 minecraft:snowy 与 snowy；马匹支持 color:/style: 与 COLOR|STYLE */
    private static boolean isVariantSelected(String current, String optionId) {
        if (current == null || optionId == null) {
            return false;
        }
        if (Objects.equals(normalizeVariantId(current), normalizeVariantId(optionId))) {
            return true;
        }
        String cur = current.trim();
        String opt = optionId.trim();
        if (opt.startsWith("color:") && cur.contains("|")) {
            return normalizeVariantId(cur.split("\\|", 2)[0])
                    .equals(normalizeVariantId(opt.substring("color:".length())));
        }
        if (opt.startsWith("style:") && cur.contains("|")) {
            String[] parts = cur.split("\\|", 2);
            return parts.length == 2 && normalizeVariantId(parts[1])
                    .equals(normalizeVariantId(opt.substring("style:".length())));
        }
        if (!opt.contains("|") && cur.contains("|")) {
            // 熊猫等：选项为单一基因，存档为 MAIN|HIDDEN
            String main = cur.split("\\|", 2)[0];
            return normalizeVariantId(main).equals(normalizeVariantId(opt));
        }
        return false;
    }

    /** 比较 minecraft:snowy 与 snowy */
    private static String normalizeVariantId(String id) {
        if (id == null) {
            return "";
        }
        String raw = id.trim();
        if (raw.startsWith("color:") || raw.startsWith("style:")) {
            raw = raw.substring(raw.indexOf(':') + 1);
        } else {
            int colon = raw.indexOf(':');
            if (colon >= 0) {
                raw = raw.substring(colon + 1);
            }
        }
        return raw.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    /** 非主人且无 mcpets.admin 时拒绝管理。 */
    private boolean denyManage(Player player, PetData data) {
        return data == null || (!data.getOwnerId().equals(player.getUniqueId())
                && !player.hasPermission("mcpets.admin"));
    }

    /** 若已打开对应管理页则只刷新槽位，否则整页打开。 */
    public void refreshManage(Player player, PetData data) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && "manage".equals(holder.guiId())
                && data.getPetId().equals(holder.petId())) {
            GuiDefinition def = definitions.get("manage");
            Inventory inv = player.getOpenInventory().getTopInventory();
            Map<String, String> ph = placeholders(data);
            for (var e : def.buttons().entrySet()) {
                inv.setItem(e.getKey(), def.renderButton(e.getValue(), ph));
            }
            return;
        }
        openManage(player, data);
    }

    public void refreshFloat(Player player, PetData data) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && "float".equals(holder.guiId())
                && data.getPetId().equals(holder.petId())) {
            GuiDefinition def = definitions.get("float");
            Inventory inv = player.getOpenInventory().getTopInventory();
            Map<String, String> ph = placeholders(data);
            for (var e : def.buttons().entrySet()) {
                inv.setItem(e.getKey(), def.renderButton(e.getValue(), ph));
            }
            return;
        }
        openFloat(player, data);
    }

    public void refreshVariant(Player player, PetData data) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && "variant".equals(holder.guiId())
                && data.getPetId().equals(holder.petId())) {
            openVariant(player, data);
            return;
        }
        openVariant(player, data);
    }

    private Map<String, String> placeholders(PetData data) {
        Map<String, String> map = new HashMap<>();
        map.put("name", data.getName());
        map.put("internal", data.getName());
        map.put("display", data.getName());
        map.put("type", data.getEntityType() == null ? "?" : data.getEntityType().name());
        map.put("state", data.getState().display());
        if (data.isAttackEnabled() && data.getAttackTargetId() != null) {
            Player target = Bukkit.getPlayer(data.getAttackTargetId());
            map.put("attack", target != null
                    ? "<red>→ " + target.getName() + "</red>"
                    : "<red>战斗中</red>");
        } else {
            map.put("attack", "<gray>待机</gray>");
        }
        map.put("ai", data.isAiEnabled()
                ? (data.isInvincible() ? "<yellow>暂停(无敌)</yellow>" : "<green>开</green>")
                : "<red>关</red>");
        map.put("mode", data.isInvincible() ? "无敌" : "普通");
        map.put("scale", String.valueOf(plugin.getPluginConfig().scaleValue(data.getScaleTier())));
        map.put("baby", data.isBaby() ? "是" : "否");
        map.put("particle", data.getParticlePreset());
        map.put("float", data.getFloatItem());
        map.put("variant", variantDisplayLabel(data));
        return map;
    }

    private String variantDisplayLabel(PetData data) {
        String id = data.getVariant();
        if (id == null || id.isBlank()) {
            return "默认";
        }
        List<String> labels = new ArrayList<>();
        for (VariantService.Option opt : plugin.getVariantService().list(data.getEntityType())) {
            if (isVariantSelected(id, opt.id())) {
                labels.add(opt.display());
            }
        }
        if (!labels.isEmpty()) {
            return String.join(" · ", labels);
        }
        return id;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        GuiDefinition def = definitions.get(holder.guiId());
        if (def == null) {
            return;
        }

        if ("main".equals(holder.guiId())) {
            UUID petId = holder.slotPets.get(slot);
            if (petId != null) {
                PetData data = pets.storage().byPetId(petId);
                if (data != null) {
                    openManage(player, data);
                }
                return;
            }
        }

        if ("variant".equals(holder.guiId())) {
            String variantId = holder.slotVariants.get(slot);
            if (variantId != null) {
                PetData data = holder.petId() == null ? null : pets.storage().byPetId(holder.petId());
                if (data == null) {
                    return;
                }
                if (denyManage(player, data)) {
                    messages.send(player, "not-owner");
                    player.closeInventory();
                    return;
                }
                if (!pets.setVariant(data, variantId)) {
                    messages.send(player, "variant-failed");
                    return;
                }
                String label = variantDisplayLabel(data);
                if ("默认".equals(label)) {
                    for (VariantService.Option opt : plugin.getVariantService().list(data.getEntityType())) {
                        if (opt.id().equals(variantId)) {
                            label = opt.display();
                            break;
                        }
                    }
                    if ("默认".equals(label)) {
                        label = variantId;
                    }
                }
                messages.send(player, "variant-changed", Map.of(
                        "name", data.getName(),
                        "variant", label
                ));
                refreshVariant(player, data);
                return;
            }
        }

        GuiDefinition.GuiButton button = def.buttons().get(slot);
        if (button == null) {
            return;
        }
        handleAction(player, holder, button.action());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void handleAction(Player player, Holder holder, String action) {
        if (action == null || action.equals("none")) {
            return;
        }
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        PetData data = holder.petId() == null ? null : pets.storage().byPetId(holder.petId());
        if (action.equals("back")) {
            if (("float".equals(holder.guiId()) || "variant".equals(holder.guiId())) && data != null) {
                openManage(player, data);
            } else {
                openMain(player);
            }
            return;
        }
        if (data == null) {
            return;
        }
        if (denyManage(player, data)) {
            messages.send(player, "not-owner");
            player.closeInventory();
            return;
        }

        switch (action) {
            case "state_follow" -> {
                pets.setState(data, PetState.FOLLOW);
                messages.send(player, "toggle-follow", Map.of("name", data.getName(), "state", "跟随"));
                refreshManage(player, data);
            }
            case "state_idle" -> {
                pets.setState(data, PetState.IDLE);
                messages.send(player, "toggle-follow", Map.of("name", data.getName(), "state", "待命"));
                refreshManage(player, data);
            }
            case "state_wander" -> {
                pets.setState(data, PetState.WANDER);
                messages.send(player, "toggle-follow", Map.of("name", data.getName(), "state", "漫步"));
                refreshManage(player, data);
            }
            case "toggle_attack", "click_attack" -> {
                PetService.ClickAttackResult result = pets.clickAttack(data);
                switch (result) {
                    case INVINCIBLE -> messages.send(player, "attack-blocked-invincible",
                            Map.of("name", data.getName()));
                    case ENTITY_MISSING -> messages.send(player, "pet-entity-missing");
                    case NO_TARGET -> messages.send(player, "attack-no-target",
                            Map.of("name", data.getName(),
                                    "range", String.valueOf(plugin.getPluginConfig().getAutoRange())));
                    case STARTED -> {
                        String targetName = "?";
                        UUID tid = data.getAttackTargetId();
                        if (tid != null) {
                            Player t = Bukkit.getPlayer(tid);
                            if (t != null) {
                                targetName = t.getName();
                            }
                        }
                        messages.send(player, "attack-started", Map.of(
                                "name", data.getName(),
                                "target", targetName
                        ));
                    }
                }
                refreshManage(player, data);
            }
            case "toggle_mode" -> {
                pets.setInvincible(data, !data.isInvincible());
                messages.send(player, data.isInvincible() ? "invincible-on" : "invincible-off",
                        Map.of("name", data.getName()));
                refreshManage(player, data);
            }
            case "toggle_ai" -> {
                pets.setAiEnabled(data, !data.isAiEnabled());
                messages.send(player, data.isAiEnabled() ? "ai-on" : "ai-off",
                        Map.of("name", data.getName()));
                refreshManage(player, data);
            }
            case "rename" -> {
                renameSessions.put(player.getUniqueId(), data.getPetId());
                player.closeInventory();
                messages.sendRaw(player, "<gradient:#7EE8FA:#80FF72>请在聊天中输入新名称</gradient> <gray>(可见≤"
                        + plugin.getPluginConfig().getMaxDisplayNameLength()
                        + "字，不含颜色；支持 & / MiniMessage / #RRGGBB)</gray>");
                messages.sendRaw(player, "<gray>改完后名称即为此名。输入</gray> <aqua>c</aqua> <gray>或</gray> <aqua>cancel</aqua> <gray>取消。</gray>");
            }
            case "cycle_scale" -> {
                pets.cycleScale(data);
                messages.send(player, "scale-changed", Map.of(
                        "name", data.getName(),
                        "scale", String.valueOf(plugin.getPluginConfig().scaleValue(data.getScaleTier()))
                ));
                refreshManage(player, data);
            }
            case "toggle_baby" -> {
                if (!pets.toggleBaby(data)) {
                    messages.sendRaw(player, "<red>该生物不支持幼体形态。</red>");
                    return;
                }
                messages.send(player, "baby-changed", Map.of(
                        "name", data.getName(),
                        "baby", data.isBaby() ? "是" : "否"
                ));
                refreshManage(player, data);
            }
            case "cycle_particle" -> {
                pets.cycleParticle(data);
                messages.send(player, "particle-changed", Map.of(
                        "name", data.getName(),
                        "preset", data.getParticlePreset()
                ));
                refreshManage(player, data);
            }
            case "open_float" -> openFloat(player, data);
            case "open_variant" -> openVariant(player, data);
            case "tpa" -> {
                if (pets.teleportPlayerToPet(player, data)) {
                    messages.send(player, "tpa-success", Map.of("name", data.getName()));
                } else {
                    messages.send(player, "pet-entity-missing");
                }
            }
            case "tph" -> {
                if (pets.teleportPetToPlayer(player, data)) {
                    messages.send(player, "tph-success", Map.of("name", data.getName()));
                } else {
                    messages.send(player, "pet-entity-missing");
                }
            }
            case "float_none" -> {
                pets.setFloatItem(data, "none");
                messages.send(player, "float-changed", Map.of("name", data.getName(), "item", "无"));
                refreshFloat(player, data);
            }
            case "float_bone", "float_stick", "float_rose", "float_porkchop",
                 "float_diamond", "float_sword" -> {
                String id = action.substring("float_".length());
                pets.setFloatItem(data, id);
                messages.send(player, "float-changed", Map.of("name", data.getName(), "item", id));
                refreshFloat(player, data);
            }
            default -> {
            }
        }
    }

    public boolean isRenameSession(UUID playerId) {
        return renameSessions.containsKey(playerId);
    }

    public void handleRenameChat(Player player, String message) {
        UUID petId = renameSessions.remove(player.getUniqueId());
        if (petId == null) {
            return;
        }
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("c")) {
            messages.sendRaw(player, "<gray>已取消重命名。</gray>");
            return;
        }
        PetData data = pets.storage().byPetId(petId);
        if (data == null || !data.getOwnerId().equals(player.getUniqueId())) {
            return;
        }
        if (!pets.setName(data, message)) {
            if (Text.plainLength(message) == 0
                    || Text.plainLength(message) > plugin.getPluginConfig().getMaxDisplayNameLength()) {
                messages.send(player, "rename-too-long", Map.of(
                        "max", String.valueOf(plugin.getPluginConfig().getMaxDisplayNameLength()),
                        "length", String.valueOf(Text.plainLength(message))
                ));
            } else {
                messages.send(player, "rename-name-taken", Map.of("name", message));
            }
            renameSessions.put(player.getUniqueId(), petId);
            return;
        }
        messages.send(player, "rename-success", Map.of("name", message));
        top.morndream.mcPets.util.SchedulerUtil.run(player, plugin, () -> openManage(player, data));
    }

    /** 关闭正在管理指定宠物的玩家界面（转让后踢掉原主人 GUI）。 */
    public void closeViewersOf(UUID petId) {
        for (UUID id : Set.copyOf(openViewers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                openViewers.remove(id);
                continue;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) {
                continue;
            }
            if (petId.equals(holder.petId())) {
                try {
                    player.closeInventory();
                } catch (Exception ignored) {
                }
            }
        }
        renameSessions.entrySet().removeIf(e -> petId.equals(e.getValue()));
    }

    public void closeAll() {
        for (UUID id : Set.copyOf(openViewers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                try {
                    player.closeInventory();
                } catch (Exception ignored) {
                }
            }
        }
        openViewers.clear();
        renameSessions.clear();
    }

    public void onClose(UUID playerId) {
        openViewers.remove(playerId);
    }

    public static final class Holder implements GuiDefinition.PetGuiHolder {
        private final String guiId;
        private final UUID petId;
        private final Map<Integer, UUID> slotPets = new HashMap<>();
        private final Map<Integer, String> slotVariants = new HashMap<>();
        private Inventory inventory;

        public Holder(String guiId, UUID petId) {
            this.guiId = guiId;
            this.petId = petId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("GUI inventory 尚未绑定");
            }
            return inventory;
        }

        public void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public String guiId() {
            return guiId;
        }

        @Override
        public UUID petId() {
            return petId;
        }
    }
}
