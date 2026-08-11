package top.morndream.mcPets.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.model.PetState;
import top.morndream.mcPets.service.MessageService;
import top.morndream.mcPets.service.PetService;
import top.morndream.mcPets.service.TransferService;
import top.morndream.mcPets.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PetCommand implements CommandExecutor, TabCompleter {

    private final McPets plugin;
    private final PetService pets;
    private final MessageService messages;
    private final PluginConfig config;
    private final TransferService transfers;

    public PetCommand(McPets plugin) {
        this.plugin = plugin;
        this.pets = plugin.getPetService();
        this.messages = plugin.getMessageService();
        this.config = plugin.getPluginConfig();
        this.transfers = plugin.getTransferService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            messages.sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "tame" -> tame(sender, args);
            case "list" -> list(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "toggle" -> toggle(sender, args);
            case "tpa" -> tpa(sender, args);
            case "tph" -> tph(sender, args);
            case "gui" -> gui(sender, args);
            case "transfer" -> transfer(sender, args);
            case "reload" -> reload(sender);
            default -> messages.send(sender, "unknown-command");
        }
        return true;
    }

    /** @return true 表示应中止后续逻辑 */
    private boolean denyUnlessPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return false;
        }
        messages.send(sender, "player-only");
        return true;
    }

    /** @return true 表示应中止后续逻辑 */
    private boolean denyUnlessPerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) {
            return false;
        }
        messages.send(sender, "no-permission");
        return true;
    }

    private void tame(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender) || denyUnlessPerm(sender, "mcpets.tame")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            messages.sendRaw(player, "<red>用法: /pet tame <名字></red> <gray>(支持颜色/MiniMessage)</gray>");
            return;
        }
        // 拼接后续参数，便于 <gradient:...>名字</gradient> 等带空格写法
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (!pets.isValidDisplayName(name)) {
            messages.send(player, "tame-name-invalid", Map.of(
                    "max", String.valueOf(config.getMaxDisplayNameLength()),
                    "length", String.valueOf(Text.plainLength(name))
            ));
            return;
        }
        if (pets.storage().hasName(player.getUniqueId(), name)) {
            messages.send(player, "tame-name-taken", Map.of("name", name));
            return;
        }
        int owned = pets.storage().byOwner(player.getUniqueId()).size();
        int max = config.getMaxPetsPerPlayer();
        if (max > 0 && owned >= max) {
            messages.send(player, "tame-limit", Map.of(
                    "max", String.valueOf(max),
                    "count", String.valueOf(owned)
            ));
            return;
        }
        Entity target = player.getTargetEntity((int) Math.ceil(config.getTameRange()), false);
        if (!(target instanceof LivingEntity living) || living instanceof Player) {
            messages.send(player, "tame-no-target");
            return;
        }
        if (config.isBlacklisted(living.getType())) {
            messages.send(player, "tame-blacklisted");
            return;
        }
        if (pets.storage().byEntityId(living.getUniqueId()) != null) {
            messages.send(player, "tame-already-pet");
            return;
        }
        if (config.isBlockVillagerProfession() && pets.hasWorkingVillagerProfession(living)) {
            messages.send(player, "tame-villager-employed");
            return;
        }
        PetData data = pets.tame(player, living, name);
        messages.send(player, "tame-success", Map.of(
                "name", data.getName(),
                "type", data.getEntityType().name()
        ));
    }

    private void list(CommandSender sender, String[] args) {
        if (denyUnlessPerm(sender, "mcpets.list")) {
            return;
        }
        if (args.length >= 2) {
            if (denyUnlessPerm(sender, "mcpets.list.others")) {
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.sendRaw(sender, "<red>玩家不在线或不存在。</red>");
                return;
            }
            List<PetData> list = pets.storage().byOwner(target.getUniqueId());
            messages.send(sender, "list-other-header", Map.of(
                    "player", target.getName(),
                    "count", String.valueOf(list.size())
            ));
            if (list.isEmpty()) {
                messages.send(sender, "list-empty");
                return;
            }
            for (PetData data : list) {
                messages.send(sender, "list-entry", Map.of(
                        "name", data.getName(),
                        "type", data.getEntityType() == null ? "?" : data.getEntityType().name(),
                        "state", data.getState().display()
                ));
            }
            return;
        }
        if (denyUnlessPlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        List<PetData> list = pets.storage().byOwner(player.getUniqueId());
        messages.send(player, "list-header", Map.of("count", String.valueOf(list.size())));
        if (list.isEmpty()) {
            messages.send(player, "list-empty");
            return;
        }
        for (PetData data : list) {
            messages.send(player, "list-entry", Map.of(
                    "name", data.getName(),
                    "type", data.getEntityType() == null ? "?" : data.getEntityType().name(),
                    "state", data.getState().display()
            ));
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender) || denyUnlessPerm(sender, "mcpets.delete")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            messages.sendRaw(player, "<red>用法: /pet delete <名字></red>");
            return;
        }
        PetData data = pets.storage().byOwnerAndName(player.getUniqueId(), args[1]);
        if (data == null) {
            messages.send(player, "delete-not-found", Map.of("name", args[1]));
            return;
        }
        String name = data.getName();
        pets.delete(data, true);
        messages.send(player, "delete-success", Map.of("name", name));
    }

    private void toggle(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender) || denyUnlessPerm(sender, "mcpets.toggle")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 3) {
            messages.sendRaw(player, "<red>用法: /pet toggle <名字> follow|attack</red> <gray>(attack=点击攻击)</gray>");
            return;
        }
        PetData data = pets.storage().byOwnerAndName(player.getUniqueId(), args[1]);
        if (data == null) {
            messages.send(player, "pet-not-found", Map.of("name", args[1]));
            return;
        }
        String opt = args[2].toLowerCase(Locale.ROOT);
        switch (opt) {
            case "follow" -> {
                PetState next = switch (data.getState()) {
                    case FOLLOW -> PetState.IDLE;
                    case IDLE -> PetState.WANDER;
                    default -> PetState.FOLLOW;
                };
                if (data.isAttackEnabled()) {
                    pets.setAttackEnabled(data, false);
                }
                pets.setState(data, next);
                messages.send(player, "toggle-follow", Map.of(
                        "name", data.getName(),
                        "state", next.display()
                ));
            }
            case "attack" -> {
                PetService.ClickAttackResult result = pets.clickAttack(data);
                switch (result) {
                    case INVINCIBLE -> messages.send(player, "attack-blocked-invincible",
                            Map.of("name", data.getName()));
                    case ENTITY_MISSING -> messages.send(player, "pet-entity-missing");
                    case NO_TARGET -> messages.send(player, "attack-no-target", Map.of(
                            "name", data.getName(),
                            "range", String.valueOf(config.getAutoRange())
                    ));
                    case STARTED -> {
                        String targetName = "?";
                        var tid = data.getAttackTargetId();
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
            }
            default -> messages.sendRaw(player, "<red>只能 toggle follow 或 attack。</red>");
        }
    }

    private void tpa(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender) || denyUnlessPerm(sender, "mcpets.tpa")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            messages.sendRaw(player, "<red>用法: /pet tpa <名字></red>");
            return;
        }
        PetData data = pets.storage().byOwnerAndName(player.getUniqueId(), args[1]);
        if (data == null) {
            messages.send(player, "pet-not-found", Map.of("name", args[1]));
            return;
        }
        if (pets.teleportPlayerToPet(player, data)) {
            messages.send(player, "tpa-success", Map.of("name", data.getName()));
        } else {
            messages.send(player, "pet-entity-missing");
        }
    }

    private void tph(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender) || denyUnlessPerm(sender, "mcpets.tph")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            messages.sendRaw(player, "<red>用法: /pet tph <名字></red>");
            return;
        }
        PetData data = pets.storage().byOwnerAndName(player.getUniqueId(), args[1]);
        if (data == null) {
            messages.send(player, "pet-not-found", Map.of("name", args[1]));
            return;
        }
        if (pets.teleportPetToPlayer(player, data)) {
            messages.send(player, "tph-success", Map.of("name", data.getName()));
        } else {
            messages.send(player, "pet-entity-missing");
        }
    }

    private void gui(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender) || denyUnlessPerm(sender, "mcpets.gui")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length >= 2) {
            PetData data = pets.storage().byOwnerAndName(player.getUniqueId(), args[1]);
            if (data == null) {
                messages.send(player, "pet-not-found", Map.of("name", args[1]));
                return;
            }
            plugin.getGuiManager().openManage(player, data);
            return;
        }
        plugin.getGuiManager().openMain(player);
    }

    private void transfer(CommandSender sender, String[] args) {
        if (denyUnlessPlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            messages.sendRaw(player, "<red>用法: /pet transfer <宠物名> <玩家></red>");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "confirm" -> {
                if (args.length < 3) {
                    messages.send(player, "transfer-invalid");
                    return;
                }
                transfers.confirm(player, args[2]);
            }
            case "cancel" -> transfers.cancelConfirm(player);
            case "accept" -> {
                if (args.length < 3) {
                    messages.send(player, "transfer-invalid");
                    return;
                }
                transfers.accept(player, args[2]);
            }
            case "deny" -> {
                if (args.length < 3) {
                    messages.send(player, "transfer-invalid");
                    return;
                }
                transfers.deny(player, args[2]);
            }
            default -> {
                if (denyUnlessPerm(player, "mcpets.transfer")) {
                    return;
                }
                if (args.length < 3) {
                    messages.sendRaw(player, "<red>用法: /pet transfer <宠物名> <玩家></red>");
                    return;
                }
                transfers.startTransfer(player, args[1], args[2]);
            }
        }
    }

    private void reload(CommandSender sender) {
        if (denyUnlessPerm(sender, "mcpets.admin")) {
            return;
        }
        plugin.reloadAll();
        messages.sendRaw(sender, "<green>McPets 配置已重载。</green>");
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("tame", "list", "delete", "toggle", "tpa", "tph", "gui", "transfer", "help", "reload"),
                    new ArrayList<>());
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = pets.storage().byOwner(player.getUniqueId()).stream()
                .map(d -> Text.plain(d.getName()))
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toList());
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "delete", "toggle", "tpa", "tph", "gui", "transfer" ->
                        StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
                case "list" -> {
                    if (player.hasPermission("mcpets.list.others")) {
                        yield StringUtil.copyPartialMatches(args[1],
                                Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),
                                new ArrayList<>());
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            return StringUtil.copyPartialMatches(args[2], List.of("follow", "attack"), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("transfer")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("confirm") || sub.equals("accept") || sub.equals("deny") || sub.equals("cancel")) {
                return List.of();
            }
            return StringUtil.copyPartialMatches(args[2],
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> !n.equalsIgnoreCase(player.getName()))
                            .toList(),
                    new ArrayList<>());
        }
        return List.of();
    }
}
