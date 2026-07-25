package com.bird.birdlibraryapi;

import com.bird.birdlibraryapi.command.ReloadCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BirdLibraryApi extends JavaPlugin {

    private static BirdLibraryApi instance;
    private ScriptManager scriptManager;

    @Override
    public void onEnable() {
        instance = this;

        scriptManager = new ScriptManager(this);

        try {
            List<ScriptLoadResult> results = scriptManager.loadAll();
            long failed = results.stream().filter(r -> !r.success()).count();
            if (failed > 0) {
                getLogger().warning("Failed to load " + failed + "/" + results.size()
                        + " script(s) - see errors above (the plugin is still running normally, broken scripts were just skipped)");
            }
        } catch (Throwable t) {
            getLogger().severe("Unexpected error while loading scripts, but the plugin will stay enabled: " + t);
            t.printStackTrace();
        }

        PluginCommand reloadCmd = getCommand("birdlib");
        if (reloadCmd != null) {
            ReloadCommand handler = new ReloadCommand(this);
            reloadCmd.setExecutor(handler);
            reloadCmd.setTabCompleter(handler);
        }

        getLogger().info("BirdLibraryApi enabled - " + scriptManager.getLoadedCount()
                + " script(s) loaded from " + scriptManager.getScriptsFolder().getPath());
    }

    @Override
    public void onDisable() {
        try {
            if (scriptManager != null) {
                scriptManager.unloadAll();
            }
        } catch (Throwable t) {
            getLogger().severe("Error while unloading scripts during plugin shutdown: " + t);
        }
        getLogger().info("BirdLibraryApi disabled.");
    }

    public static BirdLibraryApi getInstance() {
        return instance;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }
}
