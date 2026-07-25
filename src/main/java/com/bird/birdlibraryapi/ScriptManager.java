package com.bird.birdlibraryapi;

import com.bird.birdlibraryapi.api.BirdAPI;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
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

    private static boolean isScriptFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".js") || lower.endsWith(".lua") || lower.endsWith(".py");
    }

    public List<ScriptLoadResult> loadAll() {
        List<ScriptLoadResult> results = new ArrayList<>();
        try {
            File[] files = scriptsFolder.listFiles((dir, name) -> isScriptFile(name));
            if (files == null || files.length == 0) {
                plugin.getLogger().info("No .js, .lua or .py files found in " + scriptsFolder.getPath());
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
        String lower = name.toLowerCase();
        if (lower.endsWith(".lua")) {
            return loadLuaScript(file, name);
        }
        if (lower.endsWith(".py")) {
            return loadPythonScript(file, name);
        }
        return loadJsScript(file, name);
    }

    private ScriptLoadResult loadLuaScript(File file, String name) {
        try {
            Globals globals = JsePlatform.standardGlobals();

            BirdAPI api = new BirdAPI(plugin, name);
            globals.set("Bird", CoerceJavaToLua.coerce(api));
            globals.set("Bukkit", CoerceJavaToLua.coerce(org.bukkit.Bukkit.class));
            globals.set("server", CoerceJavaToLua.coerce(plugin.getServer()));
            globals.set("plugin", CoerceJavaToLua.coerce(plugin));

            try (FileInputStream in = new FileInputStream(file)) {
                LuaValue chunk = globals.load(in, name, "t", globals);
                chunk.call();
            }

            loaded.put(name, new LoadedScript(name, file, globals, api));
            plugin.getLogger().info("Loaded script: " + name);
            return ScriptLoadResult.ok(name);

        } catch (LuaError e) {

            String msg = firstLine(e.getMessage());
            plugin.getLogger().severe("[" + name + "] Lua error: " + msg);
            return ScriptLoadResult.error(name, msg);

        } catch (Throwable t) {

            String msg = firstLine(String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            t.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        }
    }

    private ScriptLoadResult loadJsScript(File file, String name) {
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

    private ScriptLoadResult loadPythonScript(File file, String name) {
        Context context = null;
        try {
            // GraalPy has no javax.script ScriptEngine of its own (unlike GraalJS's
            // js-scriptengine), so this talks to the polyglot Context API directly
            // instead of going through the ScriptEngineManager like loadJsScript does.
            context = Context.newBuilder("python")
                    .allowAllAccess(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();

            BirdAPI api = new BirdAPI(plugin, name);
            Value bindings = context.getBindings("python");
            bindings.putMember("Bird", api);
            bindings.putMember("Bukkit", org.bukkit.Bukkit.class);
            bindings.putMember("server", plugin.getServer());
            bindings.putMember("plugin", plugin);

            Source source = Source.newBuilder("python", file).build();
            context.eval(source);

            loaded.put(name, new LoadedScript(name, file, context, api));
            plugin.getLogger().info("Loaded script: " + name);
            return ScriptLoadResult.ok(name);

        } catch (PolyglotException e) {

            closeQuietly(context);
            String msg = firstLine(e.getMessage());
            int line = -1;
            SourceSection section = e.getSourceLocation();
            if (section != null && section.isAvailable()) {
                line = section.getStartLine();
            }
            plugin.getLogger().severe("[" + name + "] Python error: " + msg
                    + (line >= 0 ? " (line " + line + ")" : ""));
            return line >= 0 ? ScriptLoadResult.error(name, msg, line, -1) : ScriptLoadResult.error(name, msg);

        } catch (Throwable t) {

            closeQuietly(context);
            String msg = firstLine(String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            t.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        }
    }

    /** Closes a half-initialized Context after a failed load, ignoring the outcome. */
    private void closeQuietly(Context context) {
        if (context == null) return;
        try {
            context.close(true);
        } catch (Exception ignored) {
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
            // Only the Python engine is a Context (GraalJS's ScriptEngine and LuaJ's
            // Globals don't need explicit closing) - but Contexts hold native
            // resources that leak if never closed, so close it here.
            if (s.engine() instanceof Context ctx) {
                closeQuietly(ctx);
            }
        }
        loaded.clear();
    }

    public List<ScriptLoadResult> reloadAll() {
        unloadAll();
        return loadAll();
    }

    public ScriptLoadResult reloadOne(String fileNameInput) {
        String lowerInput = fileNameInput.toLowerCase();
        String fileName;
        if (lowerInput.endsWith(".js") || lowerInput.endsWith(".lua") || lowerInput.endsWith(".py")) {
            fileName = fileNameInput;
        } else {
            // No extension given: prefer whichever of the three actually exists,
            // falling back to .js (the historical default) if none do.
            File luaCandidate = new File(scriptsFolder, fileNameInput + ".lua");
            File pyCandidate = new File(scriptsFolder, fileNameInput + ".py");
            if (luaCandidate.isFile()) {
                fileName = fileNameInput + ".lua";
            } else if (pyCandidate.isFile()) {
                fileName = fileNameInput + ".py";
            } else {
                fileName = fileNameInput + ".js";
            }
        }

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
            if (existing.engine() instanceof Context ctx) {
                closeQuietly(ctx);
            }
        }

        return loadScript(file);
    }

    public List<String> listScriptFileNames() {
        File[] files = scriptsFolder.listFiles((dir, name) -> isScriptFile(name));
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
