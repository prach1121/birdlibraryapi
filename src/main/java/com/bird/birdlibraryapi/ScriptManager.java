package com.bird.birdlibraryapi;

import com.bird.birdlibraryapi.api.BirdAPI;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptManager {

    private final BirdLibraryApi plugin;
    private final File scriptsFolder;
    private final Map<String, LoadedScript> loaded = new LinkedHashMap<>();
    private final ScriptEngineManager engineManager = new ScriptEngineManager(ScriptManager.class.getClassLoader());

    public ScriptManager(BirdLibraryApi plugin) {
        this.plugin = plugin;

        this.scriptsFolder = new File(plugin.getDataFolder().getParentFile(), "BirdApi");
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs();
        }
    }

    public List<ScriptLoadResult> loadAll() {
        List<ScriptLoadResult> results = new ArrayList<>();
        try {
            File[] files = scriptsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".js"));
            if (files == null || files.length == 0) {
                plugin.getLogger().info("No .js files found in " + scriptsFolder.getPath());
                return results;
            }
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                results.add(loadScript(f));
            }
        } catch (Throwable t) {

            plugin.getLogger().severe("Fatal error while scanning the scripts folder: " + t);
            t.printStackTrace();
        }
        return results;
    }

    public ScriptLoadResult loadScript(File file) {
        String name = file.getName();
        try {
            ScriptEngine engine = engineManager.getEngineByName("graal.js");
            if (engine == null) {
                String msg = "GraalJS engine not found - check that the jar was built with the shade plugin";
                plugin.getLogger().severe("[" + name + "] " + msg);
                try {
                    Class<?> factoryClass = Class.forName(
                            "com.oracle.truffle.js.scriptengine.GraalJSEngineFactory",
                            true, ScriptManager.class.getClassLoader());
                    Object factory = factoryClass.getDeclaredConstructor().newInstance();
                    plugin.getLogger().warning("Diagnostic: manual instantiation actually succeeded (" + factory + ") - this is unexpected, please report it.");
                } catch (Throwable diag) {
                    plugin.getLogger().severe("Diagnostic - real cause of the GraalJS load failure:");
                    diag.printStackTrace();
                }
                return ScriptLoadResult.error(name, msg);
            }

            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

            bindings.put("polyglot.js.allowAllAccess", true);
            bindings.put("polyglot.js.allowHostAccess", true);
            bindings.put("polyglot.js.allowHostClassLookup", (java.util.function.Predicate<String>) (s -> true));
            bindings.put("polyglot.js.allowIO", true);
            bindings.put("polyglot.js.nashorn-compat", true);

            BirdAPI api = new BirdAPI(plugin, name);
            bindings.put("Bird", api);
            bindings.put("Bukkit", org.bukkit.Bukkit.class);
            bindings.put("server", plugin.getServer());
            bindings.put("plugin", plugin);

            try (FileReader reader = new FileReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
                engine.eval(reader);
            }

            loaded.put(name, new LoadedScript(name, file, engine, api));
            plugin.getLogger().info("Loaded script: " + name);
            return ScriptLoadResult.ok(name);

        } catch (ScriptException e) {

            String msg = firstLine(e.getMessage());
            plugin.getLogger().severe("[" + name + "] Syntax/Runtime error: " + msg
                    + " (line " + e.getLineNumber() + ", col " + e.getColumnNumber() + ")");
            return ScriptLoadResult.error(name, msg, e.getLineNumber(), e.getColumnNumber());

        } catch (Throwable t) {

            String msg = firstLine(String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            t.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        }
    }

    private String firstLine(String message) {
        if (message == null) return "unknown error";
        String[] parts = message.split("\n");
        return parts[0].trim();
    }

    public void unloadAll() {
        for (LoadedScript s : loaded.values()) {
            try {
                s.api().unregisterAll();
            } catch (Exception e) {
                plugin.getLogger().warning("Problem unloading script " + s.name() + ": " + e.getMessage());
            }
        }
        loaded.clear();
    }

    public List<ScriptLoadResult> reloadAll() {
        unloadAll();
        return loadAll();
    }

    public ScriptLoadResult reloadOne(String fileNameInput) {
        String fileName = fileNameInput.toLowerCase().endsWith(".js") ? fileNameInput : fileNameInput + ".js";

        File file = new File(scriptsFolder, fileName);
        if (!file.exists() || !file.isFile()) {
            return ScriptLoadResult.notFound(fileName);
        }

        LoadedScript existing = loaded.remove(fileName);
        if (existing != null) {
            try {
                existing.api().unregisterAll();
            } catch (Exception e) {
                plugin.getLogger().warning("Problem unloading previous version of " + fileName + ": " + e.getMessage());
            }
        }

        return loadScript(file);
    }

    public List<String> listScriptFileNames() {
        File[] files = scriptsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".js"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                names.add(f.getName());
            }
        }
        return names;
    }

    public int getLoadedCount() {
        return loaded.size();
    }

    public File getScriptsFolder() {
        return scriptsFolder;
    }
}
