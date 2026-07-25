package com.bird.birdlibraryapi.api;

import com.bird.birdlibraryapi.command.BirdDynamicCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class BirdAPI {

    private final JavaPlugin plugin;
    private final String scriptName;
    private final Listener dummyListener = new Listener() {};
    private final List<Runnable> cleanupTasks = new ArrayList<>();

    private static CommandMap commandMap;

    private static final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> customEventBus = new ConcurrentHashMap<>();

    private final File dataFile;
    private final Properties data = new Properties();
    private boolean dataLoaded = false;

    private final File filesFolder;

    private static final java.util.Set<String> BLOCKED_EXTENSIONS = java.util.Set.of(
            "exe", "bat", "cmd", "dll", "so", "autorun", "ps1", "ps2", "psm1",
            "php", "sh", "bash", "vbs", "vbe", "wsf", "wsh", "jse", "jar", "msi", "scr"
    );

    public BirdAPI(JavaPlugin plugin, String scriptName) {
        this.plugin = plugin;
        this.scriptName = scriptName;

        String lowerName = scriptName.toLowerCase();
        String baseName = lowerName.endsWith(".js") || lowerName.endsWith(".lua")
                ? scriptName.substring(0, scriptName.lastIndexOf('.'))
                : scriptName;
        File dataFolder = new File(plugin.getDataFolder().getParentFile(), "BirdApi/data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.dataFile = new File(dataFolder, baseName + ".properties");

        this.filesFolder = new File(plugin.getDataFolder().getParentFile(), "BirdApi/files/" + baseName);
    }

    public void log(Object message) {
        plugin.getLogger().info("[" + scriptName + "] " + message);
    }

    public void warn(Object message) {
        plugin.getLogger().warning("[" + scriptName + "] " + message);
    }

    public void onEvent(String eventClassName, String priority, EventCallback callback) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(eventClassName);

            EventPriority p;
            try {
                p = EventPriority.valueOf(priority.toUpperCase());
            } catch (Exception ex) {
                p = EventPriority.NORMAL;
            }

            EventExecutor executor = (listener, event) -> {
                if (eventClass.isInstance(event)) {
                    try {
                        callback.execute(event);
                    } catch (Throwable t) {
                        warn("Runtime error in event handler (" + eventClass.getSimpleName() + "): "
                                + (t.getMessage() != null ? t.getMessage() : t));
                        t.printStackTrace();
                    }
                }
            };

            plugin.getServer().getPluginManager()
                    .registerEvent(eventClass, dummyListener, p, executor, plugin, false);

            cleanupTasks.add(() -> HandlerList.unregisterAll(dummyListener));
            log("Registered event listener: " + eventClassName + " (priority=" + p + ")");
        } catch (ClassNotFoundException e) {
            warn("Unknown event class: " + eventClassName
                    + " (use the fully-qualified name, e.g. org.bukkit.event.player.PlayerJoinEvent)");
        }
    }

    public void onEvent(String eventClassName, EventCallback callback) {
        onEvent(eventClassName, "NORMAL", callback);
    }

    public void onCommand(String name, CommandCallback callback) {
        onCommand(name, null, callback, null);
    }

    public void onCommand(String name, String permission, CommandCallback callback) {
        onCommand(name, permission, callback, null);
    }

    public void onCommand(String name, CommandCallback callback, TabCompleteCallback tabCompleteCallback) {
        onCommand(name, null, callback, tabCompleteCallback);
    }

    public void onCommand(String name, String permission, CommandCallback callback, TabCompleteCallback tabCompleteCallback) {
        CommandMap map = getCommandMap();
        if (map == null) {
            warn("Could not access the CommandMap - command /" + name + " will not work");
            return;
        }
        BirdDynamicCommand cmd = new BirdDynamicCommand(name, callback);
        if (permission != null && !permission.isEmpty()) {
            cmd.setPermission(permission);
        }
        if (tabCompleteCallback != null) {
            cmd.setTabCompleteCallback(tabCompleteCallback);
        }
        map.register(plugin.getName().toLowerCase(), cmd);
        cleanupTasks.add(() -> cmd.unregister(map));
        log("Registered command: /" + name + (permission != null ? " (permission=" + permission + ")" : ""));
    }

    public int runTask(Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTask(plugin, wrap(task));
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskLater(Runnable task, long delayTicks) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskLater(plugin, wrap(task), delayTicks);
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskTimer(plugin, wrap(task), delayTicks, periodTicks);
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskAsync(Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, wrap(task));
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, wrap(task), delayTicks, periodTicks);
        trackTask(t);
        return t.getTaskId();
    }

    public void cancelTask(int taskId) {
        plugin.getServer().getScheduler().cancelTask(taskId);
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                warn("Runtime error in scheduled task: " + (t.getMessage() != null ? t.getMessage() : t));
                t.printStackTrace();
            }
        };
    }

    private void trackTask(BukkitTask t) {
        cleanupTasks.add(() -> {
            try {
                t.cancel();
            } catch (Exception ignored) {
            }
        });
    }

    public void broadcast(String message) {
        plugin.getServer().broadcastMessage(colorize(message));
    }

    public void broadcast(String message, String permission) {
        String colored = colorize(message);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                p.sendMessage(colored);
            }
        }
    }

    public void broadcastChat(String message) {
        broadcast(message);
    }

    public void broadcastChat(String message, String permission) {
        broadcast(message, permission);
    }

    public void tell(Player player, String message) {
        if (player != null) {
            player.sendMessage(colorize(message));
        }
    }

    public boolean tell(String playerName, String message) {
        Player player = getPlayer(playerName);
        if (player == null) {
            return false;
        }
        player.sendMessage(colorize(message));
        return true;
    }

    public String colorize(String message) {
        return message == null ? null : ChatColor.translateAlternateColorCodes('&', message);
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.showTitle(buildTitle(title, subtitle, fadeInTicks, stayTicks, fadeOutTicks));
    }

    public void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, -1, -1, -1);
    }

    public void broadcastTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Title t = buildTitle(title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.showTitle(t);
        }
    }

    public void broadcastTitle(String title, String subtitle) {
        broadcastTitle(title, subtitle, -1, -1, -1);
    }

    public void broadcastTitle(String title, String subtitle, String permission) {
        Title t = buildTitle(title, subtitle, -1, -1, -1);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                p.showTitle(t);
            }
        }
    }

    private Title buildTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Duration fadeIn = fadeInTicks >= 0 ? ticks(fadeInTicks) : Title.DEFAULT_TIMES.fadeIn();
        Duration stay = stayTicks >= 0 ? ticks(stayTicks) : Title.DEFAULT_TIMES.stay();
        Duration fadeOut = fadeOutTicks >= 0 ? ticks(fadeOutTicks) : Title.DEFAULT_TIMES.fadeOut();
        return Title.title(
                toComponent(title != null ? title : ""),
                toComponent(subtitle != null ? subtitle : ""),
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    private Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }

    public void sendActionBar(Player player, String message) {
        player.sendActionBar(toComponent(message));
    }

    public void broadcastActionBar(String message) {
        Component c = toComponent(message);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendActionBar(c);
        }
    }

    public void broadcastActionBar(String message, String permission) {
        Component c = toComponent(message);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                p.sendActionBar(c);
            }
        }
    }

    private Component toComponent(String message) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(message.replace('§', '&'));
    }

    public Player getPlayer(String name) {
        return plugin.getServer().getPlayer(name);
    }

    public Player getPlayerExact(String name) {
        return plugin.getServer().getPlayerExact(name);
    }

    public List<Player> getOnlinePlayers() {
        return new ArrayList<>(plugin.getServer().getOnlinePlayers());
    }

    public Server getServer() {
        return plugin.getServer();
    }

    private void ensureLoaded() {
        if (dataLoaded) return;
        dataLoaded = true;
        if (dataFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
                data.load(reader);
            } catch (IOException e) {
                warn("Failed to load stored data (" + dataFile.getName() + "): " + e.getMessage());
            }
        }
    }

    public void setData(String key, String value) {
        ensureLoaded();
        data.setProperty(key, value);
        saveData();
    }

    public String getData(String key) {
        ensureLoaded();
        return data.getProperty(key);
    }

    public String getData(String key, String defaultValue) {
        ensureLoaded();
        return data.getProperty(key, defaultValue);
    }

    public void removeData(String key) {
        ensureLoaded();
        data.remove(key);
        saveData();
    }

    public List<String> getDataKeys() {
        ensureLoaded();
        return new ArrayList<>(data.stringPropertyNames());
    }

    public void saveData() {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
            data.store(writer, "BirdLibraryApi persistent data for " + scriptName);
        } catch (IOException e) {
            warn("Failed to save data (" + dataFile.getName() + "): " + e.getMessage());
        }
    }

    public void giveItem(Player player, String materialName) {
        giveItem(player, materialName, 1, null, null);
    }

    public void giveItem(Player player, String materialName, int amount) {
        giveItem(player, materialName, amount, null, null);
    }

    public void giveItem(Player player, String materialName, int amount, String displayName) {
        giveItem(player, materialName, amount, displayName, null);
    }

    public void giveItem(Player player, String materialName, int amount, String displayName, List<String> lore) {
        org.bukkit.Material material;
        try {
            material = org.bukkit.Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            warn("Unknown material: " + materialName);
            return;
        }
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        if (displayName != null || (lore != null && !lore.isEmpty())) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (displayName != null) {
                    meta.setDisplayName(colorize(displayName));
                }
                if (lore != null) {
                    List<String> coloredLore = new ArrayList<>();
                    for (String line : lore) {
                        coloredLore.add(colorize(line));
                    }
                    meta.setLore(coloredLore);
                }
                item.setItemMeta(meta);
            }
        }
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    public void playSound(Player player, String soundName) {
        playSound(player, soundName, 1f, 1f);
    }

    public void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            warn("Unknown sound: " + soundName);
        }
    }

    public void broadcastSound(String soundName, float volume, float pitch) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            playSound(p, soundName, volume, pitch);
        }
    }

    public void spawnParticle(Player player, String particleName, int count) {
        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            player.getWorld().spawnParticle(particle, player.getLocation(), count);
        } catch (IllegalArgumentException e) {
            warn("Unknown particle: " + particleName);
        }
    }

    public void teleport(Player player, double x, double y, double z) {
        Location loc = new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        player.teleport(loc);
    }

    public void teleport(Player player, double x, double y, double z, String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return;
        }
        player.teleport(new Location(world, x, y, z));
    }

    public Block getBlock(String worldName, int x, int y, int z) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    public String getBlockType(String worldName, int x, int y, int z) {
        Block block = getBlock(worldName, x, y, z);
        return block != null ? block.getType().name() : null;
    }

    public boolean isBlockType(String worldName, int x, int y, int z, String materialName) {
        String actual = getBlockType(worldName, x, y, z);
        return actual != null && actual.equalsIgnoreCase(materialName);
    }

    public boolean setBlockType(String worldName, int x, int y, int z, String materialName) {
        Block block = getBlock(worldName, x, y, z);
        if (block == null) return false;
        try {
            block.setType(Material.valueOf(materialName.toUpperCase()));
            return true;
        } catch (IllegalArgumentException e) {
            warn("Unknown material: " + materialName);
            return false;
        }
    }

    public Block getTargetBlock(Player player) {
        return getTargetBlock(player, 100);
    }

    public Block getTargetBlock(Player player, int maxDistance) {
        return player.getTargetBlockExact(maxDistance);
    }

    public Block getBlockPlayerIsOn(Player player) {
        return player.getLocation().getBlock().getRelative(BlockFace.DOWN);
    }

    public void setCooldown(String key, Player player, double seconds) {
        cooldowns.put(cooldownKey(key, player), System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public boolean hasCooldown(String key, Player player) {
        Long until = cooldowns.get(cooldownKey(key, player));
        return until != null && until > System.currentTimeMillis();
    }

    public double getCooldownRemaining(String key, Player player) {
        Long until = cooldowns.get(cooldownKey(key, player));
        if (until == null) return 0;
        long remainingMs = until - System.currentTimeMillis();
        return remainingMs > 0 ? remainingMs / 1000.0 : 0;
    }

    public void clearCooldown(String key, Player player) {
        cooldowns.remove(cooldownKey(key, player));
    }

    private String cooldownKey(String key, Player player) {
        return key + ":" + player.getUniqueId();
    }

    public int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(Math.min(min, max), Math.max(min, max) + 1);
    }

    public int getOnlineCount() {
        return plugin.getServer().getOnlinePlayers().size();
    }

    public int getMaxPlayers() {
        return plugin.getServer().getMaxPlayers();
    }

    public boolean isOnline(String playerName) {
        return getPlayer(playerName) != null;
    }

    public double getHealth(Player player) {
        return player.getHealth();
    }

    public void setHealth(Player player, double amount) {
        double max = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.max(0, Math.min(amount, max)));
    }

    public void giveExp(Player player, int amount) {
        player.giveExp(amount);
    }

    public boolean setGameMode(Player player, String mode) {
        try {
            player.setGameMode(GameMode.valueOf(mode.toUpperCase()));
            return true;
        } catch (IllegalArgumentException e) {
            warn("Unknown game mode: " + mode);
            return false;
        }
    }

    public String getGameMode(Player player) {
        return player.getGameMode().name();
    }

    public void kick(Player player, String reason) {
        player.kick(toComponent(reason));
    }

    public double getDistance(Player a, Player b) {
        if (!a.getWorld().equals(b.getWorld())) return -1;
        return a.getLocation().distance(b.getLocation());
    }

    public String stripColor(String message) {
        return ChatColor.stripColor(colorize(message));
    }

    public String formatTime(long totalSeconds) {
        totalSeconds = Math.max(0, totalSeconds);
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public List<String> getWorldNames() {
        List<String> names = new ArrayList<>();
        for (World w : plugin.getServer().getWorlds()) {
            names.add(w.getName());
        }
        return names;
    }

    public boolean hasItem(Player player, String materialName, int amount) {
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            return player.getInventory().containsAtLeast(new ItemStack(material), amount);
        } catch (IllegalArgumentException e) {
            warn("Unknown material: " + materialName);
            return false;
        }
    }

    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    public int getFood(Player player) {
        return player.getFoodLevel();
    }

    public void setFood(Player player, int level) {
        player.setFoodLevel(Math.max(0, Math.min(20, level)));
    }

    public boolean addPotionEffect(Player player, String effectName, int seconds, int amplifier) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
        if (type == null) {
            warn("Unknown potion effect: " + effectName);
            return false;
        }
        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier));
        return true;
    }

    public boolean removePotionEffect(Player player, String effectName) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
        if (type == null) {
            warn("Unknown potion effect: " + effectName);
            return false;
        }
        player.removePotionEffect(type);
        return true;
    }

    public boolean hasPotionEffect(Player player, String effectName) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
        return type != null && player.hasPotionEffect(type);
    }

    public long getWorldTime(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return -1;
        }
        return world.getTime();
    }

    public boolean setWorldTime(String worldName, long time) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return false;
        }
        world.setTime(time);
        return true;
    }

    public boolean setWeather(String worldName, boolean storm) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return false;
        }
        world.setStorm(storm);
        return true;
    }

    public boolean isStorming(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        return world != null && world.hasStorm();
    }

    public boolean isNight(String worldName) {
        long time = getWorldTime(worldName);
        return time >= 13000 && time <= 23000;
    }

    public int getPing(Player player) {
        return player.getPing();
    }

    public String getPlayerUUID(Player player) {
        return player.getUniqueId().toString();
    }

    public void setDisplayName(Player player, String name) {
        player.setDisplayName(colorize(name));
    }

    public String getDisplayName(Player player) {
        return player.getDisplayName();
    }

    public void clearInventory(Player player) {
        player.getInventory().clear();
    }

    public String getItemInHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType().name();
    }

    public boolean removeItem(Player player, String materialName, int amount) {
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            warn("Unknown material: " + materialName);
            return false;
        }
        ItemStack toRemove = new ItemStack(material, amount);
        if (!player.getInventory().containsAtLeast(toRemove, amount)) {
            return false;
        }
        player.getInventory().removeItem(toRemove);
        return true;
    }

    public List<Player> getNearbyPlayers(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && other.getLocation().distance(player.getLocation()) <= radius) {
                result.add(other);
            }
        }
        return result;
    }

    public void strikeLightning(String worldName, double x, double y, double z) {
        strikeLightning(worldName, x, y, z, false);
    }

    public void strikeLightning(String worldName, double x, double y, double z, boolean visualOnly) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return;
        }
        Location loc = new Location(world, x, y, z);
        if (visualOnly) {
            world.strikeLightningEffect(loc);
        } else {
            world.strikeLightning(loc);
        }
    }

    public void on(String eventName, Consumer<Object> callback) {
        customEventBus.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(callback);
        cleanupTasks.add(() -> {
            CopyOnWriteArrayList<Consumer<Object>> list = customEventBus.get(eventName);
            if (list != null) list.remove(callback);
        });
    }

    public void emit(String eventName, Object data) {
        CopyOnWriteArrayList<Consumer<Object>> list = customEventBus.get(eventName);
        if (list == null) return;
        for (Consumer<Object> c : list) {
            try {
                c.accept(data);
            } catch (Throwable t) {
                warn("Runtime error in custom event handler (" + eventName + "): " + (t.getMessage() != null ? t.getMessage() : t));
                t.printStackTrace();
            }
        }
    }

    public void fetch(String url, Consumer<HttpResult> callback) {
        fetchInternal(url, "GET", null, null, callback);
    }

    public void fetch(String url, String method, Consumer<HttpResult> callback) {
        fetchInternal(url, method, null, null, callback);
    }

    public void fetch(String url, String method, String body, Consumer<HttpResult> callback) {
        fetchInternal(url, method, body, null, callback);
    }

    public void fetch(String url, String method, String body, Map<String, String> headers, Consumer<HttpResult> callback) {
        fetchInternal(url, method, body, headers, callback);
    }

    private void fetchInternal(String url, String method, String body, Map<String, String> headers, Consumer<HttpResult> callback) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpResult result;
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10));

                boolean hasContentType = false;
                if (headers != null) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        builder.header(e.getKey(), e.getValue());
                        if (e.getKey().equalsIgnoreCase("Content-Type")) hasContentType = true;
                    }
                }

                String m = (method == null || method.isEmpty()) ? "GET" : method.toUpperCase();
                HttpRequest.BodyPublisher publisher = body != null
                        ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                        : HttpRequest.BodyPublishers.noBody();
                if (body != null && !hasContentType) {
                    builder.header("Content-Type", "application/json");
                }
                builder.method(m, publisher);

                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                result = new HttpResult(response.statusCode(), response.body());
            } catch (Exception e) {
                result = new HttpResult(0, "Request failed: " + e.getMessage());
            }

            HttpResult finalResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    callback.accept(finalResult);
                } catch (Throwable t) {
                    warn("Runtime error in fetch callback: " + (t.getMessage() != null ? t.getMessage() : t));
                    t.printStackTrace();
                }
            });
        });
        trackTask(task);
    }

    public void sendDiscordWebhook(String webhookUrl, String message) {
        sendDiscordWebhook(webhookUrl, message, null, null);
    }

    public void sendDiscordWebhook(String webhookUrl, String message, String username, String avatarUrl) {
        StringBuilder json = new StringBuilder("{\"content\":\"").append(jsonEscape(message)).append("\"");
        if (username != null) json.append(",\"username\":\"").append(jsonEscape(username)).append("\"");
        if (avatarUrl != null) json.append(",\"avatar_url\":\"").append(jsonEscape(avatarUrl)).append("\"");
        json.append("}");
        fetchInternal(webhookUrl, "POST", json.toString(), null, result -> {
            if (!result.ok) {
                warn("Discord webhook failed (" + result.status + "): " + result.body);
            }
        });
    }

    public void sendDiscordEmbed(String webhookUrl, String title, String description, String colorHex) {
        int color = 0x3498db;
        if (colorHex != null) {
            try {
                color = Integer.parseInt(colorHex.replace("#", ""), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        String json = "{\"embeds\":[{\"title\":\"" + jsonEscape(title)
                + "\",\"description\":\"" + jsonEscape(description)
                + "\",\"color\":" + color + "}]}";
        fetchInternal(webhookUrl, "POST", json, null, result -> {
            if (!result.ok) {
                warn("Discord embed webhook failed (" + result.status + "): " + result.body);
            }
        });
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    public boolean saveFile(String filename, String content) {
        File target = resolveFile(filename);
        if (target == null) return false;
        try {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
                w.write(content != null ? content : "");
            }
            return true;
        } catch (IOException e) {
            warn("Failed to save file \"" + filename + "\": " + e.getMessage());
            return false;
        }
    }

    public String readFile(String filename) {
        File target = resolveFile(filename);
        if (target == null || !target.isFile()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn("Failed to read file \"" + filename + "\": " + e.getMessage());
            return null;
        }
    }

    public boolean fileExists(String filename) {
        File target = resolveFile(filename);
        return target != null && target.isFile();
    }

    public boolean deleteFile(String filename) {
        File target = resolveFile(filename);
        return target != null && target.delete();
    }

    public List<String> listFiles() {
        List<String> result = new ArrayList<>();
        if (filesFolder.isDirectory()) {
            String[] names = filesFolder.list();
            if (names != null) {
                for (String n : names) result.add(n);
            }
        }
        return result;
    }

    private File resolveFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            warn("File name cannot be empty");
            return null;
        }

        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            warn("Blocked file extension \"." + ext + "\" - not allowed for security reasons: " + filename);
            return null;
        }

        try {
            if (!filesFolder.exists()) filesFolder.mkdirs();
            File target = new File(filesFolder, filename);
            String basePath = filesFolder.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator)) {
                warn("Invalid file path (must stay inside your script's own file folder): " + filename);
                return null;
            }
            return target;
        } catch (IOException e) {
            warn("Invalid file path: " + filename);
            return null;
        }
    }

    public void sleep(double seconds) {
        sleepMillis((long) (seconds * 1000));
    }

    public void waitTicks(int ticks) {
        sleepMillis(ticks * 50L);
    }

    private void sleepMillis(long millis) {
        if (Bukkit.isPrimaryThread()) {
            warn("Bird.sleep()/waitTicks() was called on the main thread - refusing, since it would freeze the whole server. "
                    + "Call it from inside Bird.runTaskAsync(function() { ... }) instead.");
            return;
        }
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void runCommand(String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd));
    }

    public void runCommandAs(Player player, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand(cmd));
    }

    public void unregisterAll() {
        for (Runnable r : cleanupTasks) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
        cleanupTasks.clear();
    }

    private static CommandMap getCommandMap() {
        if (commandMap != null) return commandMap;
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            commandMap = (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commandMap;
    }
}
