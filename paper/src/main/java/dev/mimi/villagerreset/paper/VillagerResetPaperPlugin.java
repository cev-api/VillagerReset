package dev.cevapi.villagerreset.paper;

import org.bukkit.plugin.java.JavaPlugin;

public final class VillagerResetPaperPlugin extends JavaPlugin {
    private PaperResetConfig resetConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();
        getServer().getPluginManager().registerEvents(new VillagerTradeListener(this), this);
        VillagerResetCommand command = new VillagerResetCommand(this);
        if (getCommand("villagerreset") != null) {
            getCommand("villagerreset").setExecutor(command);
            getCommand("villagerreset").setTabCompleter(command);
        }
    }

    public void reloadLocalConfig() {
        reloadConfig();
        this.resetConfig = PaperResetConfig.from(getConfig());
    }

    public PaperResetConfig resetConfig() {
        return resetConfig;
    }
}
