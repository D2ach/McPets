package top.morndream.mcPets.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.model.PetData;
import top.morndream.mcPets.model.PetState;
import top.morndream.mcPets.storage.PetStorage;
import top.morndream.mcPets.util.SchedulerUtil;
import top.morndream.mcPets.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宠物转让：指令发起 → 发起人聊天二次确认 → 接收人接受/拒绝。
 */
public final class TransferService {

    private static final long EXPIRE_MS = 60_000L;

    private final McPets plugin;
    private final PetStorage storage;
    private final MessageService messages;
    private final PluginConfig config;

    /** 发起人待确认（点击确认后才发给对方） */
    private final Map<UUID, PendingConfirm> confirms = new ConcurrentHashMap<>();
    /** 发给接收人的转让请求 */
    private final Map<UUID, PendingOffer> offers = new ConcurrentHashMap<>();

    public TransferService(McPets plugin) {
        this.plugin = plugin;
        this.storage = plugin.getPetService().storage();
        this.messages = plugin.getMessageService();
        this.config = plugin.getPluginConfig();
    }

    public void startTransfer(Player from, String petName, String targetName) {
        purgeExpired();
        if (from.getName().equalsIgnoreCase(targetName)) {
            messages.send(from, "transfer-self");
            return;
        }
        PetData data = storage.byOwnerAndName(from.getUniqueId(), petName);
        if (data == null) {
            messages.send(from, "pet-not-found", Map.of("name", petName));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            messages.send(from, "transfer-target-offline", Map.of("player", targetName));
            return;
        }
        String limitReason = checkReceiverCapacity(target, data);
        if (limitReason != null) {
            messages.send(from, limitReason, Map.of(
                    "player", target.getName(),
                    "max", String.valueOf(config.getMaxPetsPerPlayer()),
                    "count", String.valueOf(storage.byOwner(target.getUniqueId()).size()),
                    "name", Text.plain(data.getName())
            ));
            return;
        }

        // 同一发起人只保留一条待确认
        confirms.entrySet().removeIf(e -> e.getValue().fromId().equals(from.getUniqueId()));

        UUID token = UUID.randomUUID();
        confirms.put(token, new PendingConfirm(
                token,
                from.getUniqueId(),
                target.getUniqueId(),
                data.getPetId(),
                System.currentTimeMillis() + EXPIRE_MS
        ));

        String plainPet = Text.plain(data.getName());
        messages.send(from, "transfer-confirm-prompt", Map.of(
                "name", plainPet,
                "player", target.getName()
        ));
        messages.sendRaw(from,
                "<green><bold><click:run_command:'/pet transfer confirm " + token + "'>"
                        + "[点击确认转让]</click></bold></green> "
                        + "<gray>或</gray> "
                        + "<red><click:run_command:'/pet transfer cancel'>"
                        + "[取消]</click></red> "
                        + "<dark_gray>(60秒内有效)</dark_gray>");
    }

    public void confirm(Player from, String tokenRaw) {
        purgeExpired();
        UUID token;
        try {
            token = UUID.fromString(tokenRaw);
        } catch (IllegalArgumentException ex) {
            messages.send(from, "transfer-invalid");
            return;
        }
        PendingConfirm pending = confirms.remove(token);
        if (pending == null || !pending.fromId().equals(from.getUniqueId())) {
            messages.send(from, "transfer-invalid");
            return;
        }
        if (pending.expireAt() < System.currentTimeMillis()) {
            messages.send(from, "transfer-expired");
            return;
        }

        PetData data = storage.byPetId(pending.petId());
        if (data == null || !data.getOwnerId().equals(from.getUniqueId())) {
            messages.send(from, "transfer-invalid");
            return;
        }
        Player target = Bukkit.getPlayer(pending.toId());
        if (target == null || !target.isOnline()) {
            messages.send(from, "transfer-target-offline", Map.of("player", "对方"));
            return;
        }
        String limitReason = checkReceiverCapacity(target, data);
        if (limitReason != null) {
            messages.send(from, limitReason, Map.of(
                    "player", target.getName(),
                    "max", String.valueOf(config.getMaxPetsPerPlayer()),
                    "count", String.valueOf(storage.byOwner(target.getUniqueId()).size()),
                    "name", Text.plain(data.getName())
            ));
            return;
        }

        // 接收人已有未处理请求则覆盖旧的（同一接收人只保留一条）
        offers.entrySet().removeIf(e -> e.getValue().toId().equals(target.getUniqueId()));

        UUID offerToken = UUID.randomUUID();
        offers.put(offerToken, new PendingOffer(
                offerToken,
                from.getUniqueId(),
                target.getUniqueId(),
                data.getPetId(),
                System.currentTimeMillis() + EXPIRE_MS
        ));

        String plainPet = Text.plain(data.getName());
        messages.send(from, "transfer-sent", Map.of(
                "name", plainPet,
                "player", target.getName()
        ));
        messages.send(target, "transfer-offer", Map.of(
                "player", from.getName(),
                "name", plainPet
        ));
        messages.sendRaw(target,
                "<green><bold><click:run_command:'/pet transfer accept " + offerToken + "'>"
                        + "[接受]</click></bold></green> "
                        + "<red><bold><click:run_command:'/pet transfer deny " + offerToken + "'>"
                        + "[拒绝]</click></bold></red> "
                        + "<dark_gray>(60秒内有效)</dark_gray>");
    }

    public void cancelConfirm(Player from) {
        boolean removed = confirms.entrySet().removeIf(e -> e.getValue().fromId().equals(from.getUniqueId()));
        if (removed) {
            messages.send(from, "transfer-cancelled");
        } else {
            messages.send(from, "transfer-invalid");
        }
    }

    public void accept(Player to, String tokenRaw) {
        purgeExpired();
        UUID token;
        try {
            token = UUID.fromString(tokenRaw);
        } catch (IllegalArgumentException ex) {
            messages.send(to, "transfer-invalid");
            return;
        }
        PendingOffer offer = offers.remove(token);
        if (offer == null || !offer.toId().equals(to.getUniqueId())) {
            messages.send(to, "transfer-invalid");
            return;
        }
        if (offer.expireAt() < System.currentTimeMillis()) {
            messages.send(to, "transfer-expired");
            return;
        }

        PetData data = storage.byPetId(offer.petId());
        Player from = Bukkit.getPlayer(offer.fromId());
        if (data == null || !data.getOwnerId().equals(offer.fromId())) {
            messages.send(to, "transfer-invalid");
            if (from != null) {
                messages.send(from, "transfer-failed");
            }
            return;
        }

        String limitReason = checkReceiverCapacity(to, data);
        if (limitReason != null) {
            messages.send(to, limitReason, Map.of(
                    "player", to.getName(),
                    "max", String.valueOf(config.getMaxPetsPerPlayer()),
                    "count", String.valueOf(storage.byOwner(to.getUniqueId()).size()),
                    "name", Text.plain(data.getName())
            ));
            // 对方已满：请求作废，通知发起人
            if (from != null && from.isOnline()) {
                messages.send(from, "transfer-target-full", Map.of(
                        "player", to.getName(),
                        "max", String.valueOf(config.getMaxPetsPerPlayer()),
                        "count", String.valueOf(storage.byOwner(to.getUniqueId()).size())
                ));
            }
            return;
        }

        String plainPet = Text.plain(data.getName());
        completeTransfer(data, to);
        messages.send(to, "transfer-accepted-receiver", Map.of(
                "name", plainPet,
                "player", from != null ? from.getName() : "对方"
        ));
        if (from != null && from.isOnline()) {
            messages.send(from, "transfer-accepted-sender", Map.of(
                    "name", plainPet,
                    "player", to.getName()
            ));
        }
    }

    public void deny(Player to, String tokenRaw) {
        purgeExpired();
        UUID token;
        try {
            token = UUID.fromString(tokenRaw);
        } catch (IllegalArgumentException ex) {
            messages.send(to, "transfer-invalid");
            return;
        }
        PendingOffer offer = offers.remove(token);
        if (offer == null || !offer.toId().equals(to.getUniqueId())) {
            messages.send(to, "transfer-invalid");
            return;
        }
        PetData data = storage.byPetId(offer.petId());
        String plainPet = data == null ? "?" : Text.plain(data.getName());
        messages.send(to, "transfer-denied-receiver", Map.of("name", plainPet));
        Player from = Bukkit.getPlayer(offer.fromId());
        if (from != null && from.isOnline()) {
            messages.send(from, "transfer-denied-sender", Map.of(
                    "name", plainPet,
                    "player", to.getName()
            ));
        }
    }

    /**
     * @return message key if blocked, else null
     */
    private String checkReceiverCapacity(Player target, PetData data) {
        int max = config.getMaxPetsPerPlayer();
        int count = storage.byOwner(target.getUniqueId()).size();
        if (max > 0 && count >= max) {
            return "transfer-target-full";
        }
        if (storage.hasName(target.getUniqueId(), data.getName())) {
            return "transfer-name-taken";
        }
        return null;
    }

    private void completeTransfer(PetData data, Player newOwner) {
        PetService pets = plugin.getPetService();
        data.clearCombat();
        data.setAttackEnabled(false);
        if (data.getState() == PetState.ATTACK) {
            data.setState(PetState.FOLLOW);
        }
        storage.changeOwner(data, newOwner.getUniqueId());
        storage.flush();
        if (plugin.getPetAIManager() != null) {
            plugin.getPetAIManager().resync(data);
        }
        var entity = pets.findEntity(data);
        if (entity != null) {
            SchedulerUtil.run(entity, plugin, () -> pets.applyOwnerVisual(entity, data));
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        confirms.entrySet().removeIf(e -> e.getValue().expireAt() < now);
        offers.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    public void clearPlayer(UUID playerId) {
        confirms.entrySet().removeIf(e ->
                e.getValue().fromId().equals(playerId) || e.getValue().toId().equals(playerId));
        offers.entrySet().removeIf(e ->
                e.getValue().fromId().equals(playerId) || e.getValue().toId().equals(playerId));
    }

    private record PendingConfirm(UUID token, UUID fromId, UUID toId, UUID petId, long expireAt) {
    }

    private record PendingOffer(UUID token, UUID fromId, UUID toId, UUID petId, long expireAt) {
    }
}
