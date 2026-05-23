package dev.fortunecrops;

import org.bukkit.plugin.java.JavaPlugin;

public class FortuneCrops extends JavaPlugin {

    private static FortuneCrops instance;

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(new CropFortuneListener(this), this);
        getLogger().info("FortuneCrops enabled! Fortune now works on crops.");
    }

    @Override
    public void onDisable() {
        getLogger().info("FortuneCrops disabled.");
    }

    public static FortuneCrops getInstance() {
        return instance;
    }
}
