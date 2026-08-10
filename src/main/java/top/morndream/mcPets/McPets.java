package top.morndream.mcPets;

import org.bukkit.plugin.java.JavaPlugin;
import top.morndream.mcPets.ai.PetAIManager;
import top.morndream.mcPets.command.PetCommand;
import top.morndream.mcPets.config.PluginConfig;
import top.morndream.mcPets.gui.GuiManager;
import top.morndream.mcPets.listener.PetListener;
import top.morndream.mcPets.service.AppearanceService;
import top.morndream.mcPets.service.MessageService;
import top.morndream.mcPets.service.MouthService;
import top.morndream.mcPets.service.ParticleService;
import top.morndream.mcPets.service.PetService;
import top.morndream.mcPets.storage.PetStorage;
import top.morndream.mcPets.util.SchedulerUtil;

public final class McPets extends JavaPlugin {

    private PluginConfig pluginConfig;
    private PetStorage petStorage;
    private MessageService messageService;
    private MouthService mouthService;
    private ParticleService particleService;
    private PetService petService;
    private GuiManager guiManager;
    private PetAIManager petAIManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("gui/main.yml", false);
        saveResource("gui/manage.yml", false);
        saveResource("gui/mouth.yml", false);

        pluginConfig = new PluginConfig(this);
        pluginConfig.reload();

        petStorage = new PetStorage(this);
        petStorage.load();

        messageService = new MessageService(this);
        AppearanceService appearanceService = new AppearanceService(this);
        mouthService = new MouthService(this);
        particleService = new ParticleService(this);
        petService = new PetService(this, petStorage, messageService, mouthService, appearanceService);
        guiManager = new GuiManager(this);
        guiManager.loadAll();
        petAIManager = new PetAIManager(this);

        PetCommand command = new PetCommand(this);
        var petCmd = getCommand("pet");
        if (petCmd != null) {
            petCmd.setExecutor(command);
            petCmd.setTabCompleter(command);
        } else {
            getLogger().severe("无法注册 /pet 指令，请检查 plugin.yml");
        }

        getServer().getPluginManager().registerEvents(new PetListener(this), this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        petAIManager.start();

        int autoSave = pluginConfig.getAutoSaveSeconds();
        if (autoSave > 0) {
            SchedulerUtil.runAsyncTimer(this, () -> SchedulerUtil.runGlobal(this, petStorage::flush),
                    autoSave, autoSave);
        }

        SchedulerUtil.runGlobalDelayed(this, petService::applyToExistingEntities, 1L);

        getLogger().info("McPets 已启用 · " + petStorage.all().size() + " 只宠物存档"
                + (SchedulerUtil.isFolia() ? " · Folia" : " · Paper"));
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeAll();
        }
        if (petAIManager != null) {
            petAIManager.shutdown();
        }
        if (petService != null) {
            petService.handlePluginUnload();
        }
        if (mouthService != null) {
            mouthService.shutdown();
        }
        if (petStorage != null) {
            petStorage.save(true);
        }
        getLogger().info("McPets 已关闭");
    }

    public void reloadAll() {
        pluginConfig.reload();
        guiManager.closeAll();
        guiManager.loadAll();
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public MouthService getMouthService() {
        return mouthService;
    }

    public ParticleService getParticleService() {
        return particleService;
    }

    public PetService getPetService() {
        return petService;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public PetAIManager getPetAIManager() {
        return petAIManager;
    }
}
