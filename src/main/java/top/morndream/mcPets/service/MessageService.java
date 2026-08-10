package top.morndream.mcPets.service;

import org.bukkit.command.CommandSender;
import top.morndream.mcPets.McPets;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.util.Text;

import java.util.Map;

public final class MessageService {

    private final PluginConfig config;

    public MessageService(McPets plugin) {
        this.config = plugin.getPluginConfig();
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String raw = config.prefix() + config.message(path);
        sender.sendMessage(Text.parse(raw, placeholders));
    }

    public void sendRaw(CommandSender sender, String miniOrLegacy) {
        sender.sendMessage(Text.parse(miniOrLegacy));
    }

    public void sendHelp(CommandSender sender) {
        for (String line : config.messageList("help")) {
            sender.sendMessage(Text.parse(line));
        }
    }
}
