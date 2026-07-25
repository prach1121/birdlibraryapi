# BirdLibraryApi

A **Paper** plugin that acts as a scripting engine similar to Skript, but using
**real JavaScript** (via [GraalJS](https://www.graalvm.org/javascript/)),
**real Lua** (via [LuaJ](https://github.com/luaj/luaj)), or **real Python**
(via [GraalPy](https://www.graalvm.org/python/)). Drop `.js`, `.lua` or `.py`
files into `plugins/BirdApi/` and write events/commands with direct access to
the Bukkit API - `Java.type(...)` from JavaScript, plain Java-object calls
from Lua, or `java.type(...)` from Python.

## Project structure

```
BirdLibraryApi/
├── pom.xml
├── src/main/java/com/bird/birdlibraryapi/
│   ├── BirdLibraryApi.java     # main plugin class
│   ├── ScriptManager.java      # scans/loads/reloads .js, .lua and .py files
│   ├── ScriptLoadResult.java   # per-file load result (success / error + line/col)
│   ├── LoadedScript.java
│   ├── api/
│   │   ├── BirdAPI.java        # the "Bird" object injected into every script
│   │   ├── EventCallback.java
│   │   └── CommandCallback.java
│   └── command/
│       ├── BirdDynamicCommand.java  # dynamic command registration (no plugin.yml entry needed)
│       └── ReloadCommand.java       # /birdlib reload
├── src/main/resources/plugin.yml
└── BirdApi-examples/
    ├── example.js            # working JavaScript example script
    ├── broken-example.js     # intentional syntax error, for testing error reporting
    ├── example.lua           # working Lua example script
    ├── broken-example.lua    # intentional syntax error, for testing error reporting
    ├── example.py            # working Python example script
    └── broken-example.py     # intentional syntax error, for testing error reporting
```

## Building

Requires Maven and JDK 17+:

```bash
cd BirdLibraryApi
mvn clean package
```

This produces `target/BirdLibraryApi.jar` (fairly large, ~60-80MB now that
GraalPy is included alongside GraalJS - both of those account for most of the
size; LuaJ itself is tiny, under 1MB - all three are shaded into the jar so
it's a single self-contained file with no extra libs needed on the server).

> **Note:** `pom.xml` targets `paper-api` version `1.20.4-R0.1-SNAPSHOT`. If your
> server runs a different version, update that version in `pom.xml` to match.

## Installing

1. Drop `BirdLibraryApi.jar` into `plugins/`
2. Start the server once — the plugin will automatically create `plugins/BirdApi/`
3. Place your `.js`, `.lua` or `.py` files (e.g. `BirdApi-examples/example.js`,
   `BirdApi-examples/example.lua` or `BirdApi-examples/example.py`) in
   `plugins/BirdApi/`
4. Run `/birdlib reload` (requires the `birdlib.reload` permission, default: op)
   or restart the server

All three languages can be mixed freely in the same `BirdApi/` folder - the
engine used is picked automatically per file based on its extension.

### Reloading a single file (no server restart needed)

```
/birdlib reload            -> reload every script (.js, .lua and .py)
/birdlib reload example    -> reload example.lua if it exists, else example.py, else example.js
/birdlib reload example.js -> reload example.js specifically
/birdlib reload example.lua -> reload example.lua specifically
/birdlib reload example.py -> reload example.py specifically
```

Reloading a single file only unregisters that file's events/commands and reloads
that file — other running scripts are unaffected. Tab-completion is provided for
file names. (The old `/birdapi` command still works as an alias.)

## Error reporting (syntax / runtime)

If a script has a syntax error or fails to load, the system will:

- **Never disable the BirdLibraryApi plugin** — every script-loading code path is
  wrapped in try-catch (`ScriptManager` wraps every method, `onEnable`/`onDisable`
  add another safety layer on top). A broken file is simply skipped; every other
  file that loaded fine keeps working.
- **Report the error straight to chat** when you run `/birdlib reload` — no need
  to dig through console logs.

```
/birdlib reload broken-example
> §c✘ broken-example.js - <expected ...> (line 6, col 24)

/birdlib reload
> §eReload complete: §a3 succeeded §7/ §c1 failed (120ms)
>   §c✘ broken-example.js - <expected ...> (line 6, col 24)
```

- Errors that happen while an event handler **runs** (not while loading), such as
  an NPE in your own code, are also caught and logged to the console tagged with
  the offending file/event name, without affecting other handlers or crashing
  the server.
- Test this with `BirdApi-examples/broken-example.js` or `broken-example.lua`
  (both missing a `,`/comma-equivalent before the callback function), or
  `broken-example.py` (missing the `:` before a function body).

## Why this is more capable than Skript for real projects

Skript is great for quick, small edits by non-programmers, but it hits real
limits once a project grows. `BirdLibraryApi` is aimed at people who are
willing to write actual JavaScript, Lua, or Python in exchange for removing
those limits:

| | Skript | BirdLibraryApi |
|---|---|---|
| Language | Custom pseudo-English DSL — no real functions, closures, classes, or standard library | Full ECMAScript (GraalJS) — functions, closures, classes, `Array`/`Math`/`JSON`, npm-style module patterns |
| Access to Bukkit/Spigot/Paper API | Only what the core + installed addons expose; new API = wait for an addon or write a Java addon yourself | `Java.type(...)` gives you **any** class on the server's classpath immediately, no addon needed |
| Cross-file communication | Effectively one global script namespace; splitting logic across files is awkward | `Bird.on("event-name", cb)` / `Bird.emit("event-name", data)` — any script can define and fire custom events other scripts subscribe to |
| Persistent data | Variables system works, but is a black-box binary file, hard to inspect/edit externally | `Bird.setData/getData` writes plain human-readable `.properties` files per script |
| Reload safety | Reloading a big script pack is often an all-or-nothing gamble | Each `.js` file is loaded/unloaded independently; every event listener, command, and scheduled task a script registers is tracked and cleaned up automatically on reload |
| Commands | Argument parsing/tab-completion require verbose `command` blocks with typed arguments | Plain JS function + optional permission + optional tab-complete callback — as much or as little structure as you want |
| Debugging | Parse errors reported in Skript's own terms, stack traces from addons are often unreadable | Real JS/Java stack traces, and you can `console.log`/`Bird.log` anywhere mid-script |
| Performance | Interpreted line-by-line by the Skript engine | Runs on GraalJS, which JIT-compiles hot script code |
| Internet access (webhooks, REST APIs) | Needs a separate HTTP addon (e.g. skript-http), often unmaintained | `Bird.fetch(...)` built in, plus ready-made `Bird.sendDiscordWebhook(...)` |

The trade-off: Skript requires no programming knowledge at all, while
BirdLibraryApi assumes you're comfortable writing JavaScript, Lua, or Python.
If you already can, BirdLibraryApi removes the ceiling Skript eventually hits.

## API reference (global variables available in scripts)

| Variable | Description |
|---|---|
| `Bird` | the main object: events, commands, scheduler, player helpers, persistent storage |
| `Bukkit` | reference to the `org.bukkit.Bukkit` class (call static methods directly) |
| `server` | the `org.bukkit.Server` instance (`plugin.getServer()`) |
| `plugin` | the BirdLibraryApi plugin instance itself |
| `Java.type("...")` | GraalJS built-in used to import any Java class into the script (`.js` only) |
| `java.type("...")` | GraalPy built-in (via `import java`) used to import any Java class into the script (`.py` only) |

Every method documented below is available from **all three** of `.js`,
`.lua`, and `.py` scripts - it's the same `Bird` object either way. Only the
calling syntax differs.

### Lua support

`.lua` scripts run on [LuaJ](https://github.com/luaj/luaj) instead of GraalJS.
The `Bird`, `Bukkit`, `server`, and `plugin` globals are still injected, but
since Lua doesn't have JS-style dot-call-with-implicit-`this`, use Lua's
**colon syntax** to call methods on them:

```lua
Bird:log("Loading example.lua...")

Bird:onEvent("org.bukkit.event.player.PlayerJoinEvent", function(event)
    local player = event:getPlayer()
    player:sendMessage("§bWelcome to the server!")
end)

Bird:onCommand("hello", function(sender, label, args)
    sender:sendMessage("§eHello from Lua! You sent " .. #args .. " argument(s)")
end)
```

A few practical differences from the JavaScript side:

- There's no `Java.type(...)` in Lua, but LuaJ ships with the `luajava` library
  automatically, so `luajava.bindClass("org.bukkit.Material")` is the Lua
  equivalent - it gives you back a class reference you can use for static
  access (`Material:valueOf("BEDROCK")`) or comparisons.
- Java arrays passed back into Lua (like `args` in `onCommand`) stay backed by
  the real Java array, so they're indexed like Java, **starting at 0**, not
  Lua's usual 1-based tables. Use `#args` for the length and
  `for i = 0, #args - 1 do local a = args[i] ... end` to loop over them.
- Lua has no `===`; use `==`. String concatenation is `..` instead of `+`.
- See `BirdApi-examples/example.lua` for a fuller working example, and
  `BirdApi-examples/broken-example.lua` for what a reported syntax error looks
  like.

### Python support

`.py` scripts run on [GraalPy](https://www.graalvm.org/python/) instead of
GraalJS/LuaJ. The `Bird`, `Bukkit`, `server`, and `plugin` globals are still
injected, and Python's normal dot-call syntax works directly on them - no
colon syntax needed like in Lua:

```python
Bird.log("Loading example.py...")

def on_join(event):
    player = event.getPlayer()
    player.sendMessage("§bWelcome to the server!")

Bird.onEvent("org.bukkit.event.player.PlayerJoinEvent", on_join)

def hello_command(sender, label, args):
    sender.sendMessage("§eHello from Python! You sent " + str(len(args)) + " argument(s)")

Bird.onCommand("hello", hello_command)
```

A few practical differences from the JavaScript side:

- There's no `Java.type(...)` in Python; instead `import java` then
  `java.type("org.bukkit.Material")` is the equivalent, giving back a class
  reference for static access (`Material.BEDROCK`) or constructing instances.
  You can also write `from java.util import ArrayList`-style imports for
  anything under the `java` package.
- Unlike `.js`/`.lua` scripts, GraalPy has no dedicated `javax.script`
  engine, so Python scripts run through the lower-level
  `org.graalvm.polyglot.Context` API internally. This is invisible from the
  script itself, but it does mean each loaded `.py` script holds onto a bit
  more native memory than a `.js`/`.lua` one until it's unloaded/reloaded.
- Java functional-interface parameters (event/command/tab-complete callbacks,
  `Bird.runTask*`) just take a plain Python function or lambda, same as
  passing a JS function or a Lua closure.
- `is`/`is not` don't mean the same thing as Java `==`/`equals` for host
  objects - stick to calling `.equals(...)` when you need Java's notion of
  equality (e.g. comparing two `Player` objects), the same as you would
  from Java itself.
- See `BirdApi-examples/example.py` for a fuller working example, and
  `BirdApi-examples/broken-example.py` for what a reported syntax error looks
  like (Python needs the `:` before an indented block, so a missing one is
  the most natural "broken" example here).

### `Bird.onEvent(eventClassName, [priority,] callback)`

Registers a Bukkit event listener using the event's fully-qualified class name.

```js
Bird.onEvent("org.bukkit.event.player.PlayerJoinEvent", function (event) {
    event.getPlayer().sendMessage("Hello!");
});

// priority is optional (LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR)
Bird.onEvent("org.bukkit.event.block.BlockBreakEvent", "HIGH", function (event) {
    event.setCancelled(true);
});
```

### `Bird.onCommand(name, [permission,] callback, [tabCompleteCallback])`

Registers a dynamic command — **no** `plugin.yml` entry required. Permission and
tab-completion are both optional.

```js
Bird.onCommand("hello", function (sender, label, args) {
    sender.sendMessage("Hi " + args.length + " args");
});

// with a required permission node
Bird.onCommand("kick-all", "example.admin", function (sender, label, args) {
    sender.sendMessage("Kicking everyone...");
});

// with tab-completion for the first argument
Bird.onCommand("goto", function (sender, label, args) {
    // ...
}, function (sender, alias, args) {
    return java.util.Arrays.asList("Steve", "Alex");
});
```

### `Bird.log(msg)` / `Bird.warn(msg)`

Prints a log line to console, automatically tagged with the script's file name.

### Scheduler

Wraps the Bukkit scheduler; every task is automatically cancelled when the
script is unloaded/reloaded, so you never end up with orphaned repeating tasks.

```js
Bird.runTask(function () { /* runs next tick, main thread */ });
Bird.runTaskLater(function () { /* runs once, after N ticks */ }, 40);
var id = Bird.runTaskTimer(function () { /* runs every N ticks */ }, 0, 20);
Bird.cancelTask(id);

// Off the main thread - never touch the Bukkit API (players/blocks/etc.) inside these:
Bird.runTaskAsync(function () { /* heavy computation, HTTP calls, file IO, ... */ });
Bird.runTaskTimerAsync(function () { /* ... */ }, 0, 100);
```

(20 ticks = 1 second.)

### Player / server helpers

```js
Bird.broadcast("§aServer restarting in 5 minutes!");
Bird.broadcast("§7[Staff] message", "example.staff"); // only to players with this permission

var p = Bird.getPlayer("Notch");        // fuzzy/partial name match, or null
var exact = Bird.getPlayerExact("Notch"); // exact name match, or null
var online = Bird.getOnlinePlayers();   // a java.util.List<Player>
```

### Chat: tell (private) and broadcast (everyone)

```js
// Private message to one player - accepts a Player object or a name (returns false if not found/online)
Bird.tell(player, "§7This message is just for you");
var ok = Bird.tell("Notch", "§7Hey Notch!"); // true if online, false otherwise

// Same as Bird.broadcast(...) - named to match broadcastActionBar/broadcastTitle
Bird.broadcastChat("§aEvent starting now!");
Bird.broadcastChat("§7[Staff] ...", "example.staff"); // only to players with this permission
```

### Action bar

The small text that appears just above the hotbar. Supports `§` or `&` color codes.

```js
Bird.sendActionBar(player, "§c⚠ Low health!");
Bird.broadcastActionBar("§eBoss fight starts in 10 seconds");
Bird.broadcastActionBar("§7[Staff] ...", "example.staff"); // only to players with this permission
```

### Titles

The big text + subtitle shown in the middle of the screen (e.g. "Round 1"). Timing
args are in ticks (20 ticks = 1 second) - omit them to use Minecraft's defaults
(fade in 0.5s, stay 3.5s, fade out 1s).

```js
Bird.sendTitle(player, "§6§lLEVEL UP!", "§7You reached level 10");
Bird.broadcastTitle("§c§lBOSS DEFEATED", "§7Well done, everyone!");
Bird.broadcastTitle("§e§lWAVE 3", "", 5, 40, 10); // custom fadeIn/stay/fadeOut
Bird.broadcastTitle("§7[Staff] ...", "", "example.staff"); // only to players with this permission
```

### Items

```js
Bird.giveItem(player, "DIAMOND_SWORD");
Bird.giveItem(player, "BREAD", 16);
Bird.giveItem(player, "DIAMOND_SWORD", 1, "&b&lFrost Blade");
Bird.giveItem(player, "DIAMOND_SWORD", 1, "&b&lFrost Blade", ["&7A blade forged in ice", "&7+5 Attack Damage"]);
```

### Sound & particles

```js
Bird.playSound(player, "ENTITY_PLAYER_LEVELUP");
Bird.playSound(player, "ENTITY_ENDER_DRAGON_GROWL", 1.0, 0.8); // volume, pitch
Bird.broadcastSound("BLOCK_NOTE_BLOCK_PLING", 1.0, 2.0);
Bird.spawnParticle(player, "HEART", 10);
```

### Teleport

```js
Bird.teleport(player, 100, 65, -230);                 // same world
Bird.teleport(player, 0, 100, 0, "world_the_end");     // specific world
```

### Basic utilities

```js
Bird.getOnlineCount();               // -> number of players online
Bird.getMaxPlayers();                // -> server's max-players
Bird.isOnline("Notch");              // -> true/false

Bird.getHealth(player);              // -> current health
Bird.setHealth(player, 20);          // clamped to their max health automatically
Bird.giveExp(player, 50);

Bird.setGameMode(player, "CREATIVE"); // -> true/false (false = invalid mode name)
Bird.getGameMode(player);             // -> "SURVIVAL" / "CREATIVE" / ...
Bird.kick(player, "&cYou were kicked for AFK");

Bird.getDistance(playerA, playerB);  // -> blocks apart, or -1 if different worlds
Bird.stripColor("&aHello &cWorld");  // -> "Hello World"
Bird.formatTime(125);                // -> "2:05"
Bird.formatTime(3725);               // -> "1:02:05"

Bird.getWorldNames();                 // -> ["world", "world_nether", "world_the_end"]
Bird.hasItem(player, "DIAMOND", 5);   // -> true if they're carrying 5+ diamonds
```

### More player / world / inventory utilities

```js
Bird.getPlayerNames();                        // -> ["Notch", "jeb_", ...] just names, all online players

Bird.getFood(player);                         // -> 0-20
Bird.setFood(player, 20);

Bird.addPotionEffect(player, "SPEED", 30, 1); // 30 seconds, amplifier 1 (Speed II)
Bird.removePotionEffect(player, "SPEED");
Bird.hasPotionEffect(player, "SPEED");        // -> true/false

Bird.getWorldTime("world");                   // -> 0-24000
Bird.setWorldTime("world", 13000);            // jump to night
Bird.setWeather("world", true);               // start a storm
Bird.isStorming("world");                     // -> true/false
Bird.isNight("world");                        // -> true/false

Bird.getPing(player);                         // -> ms latency
Bird.getPlayerUUID(player);                   // -> stable id, survives name changes
Bird.setDisplayName(player, "&b[VIP] &f" + player.getName());
Bird.getDisplayName(player);

Bird.clearInventory(player);
Bird.getItemInHand(player);                   // -> "DIAMOND_SWORD", "AIR", ...
Bird.removeItem(player, "DIAMOND", 3);        // -> true/false (false = not enough to remove)

Bird.getNearbyPlayers(player, 10);            // -> other online players within 10 blocks
Bird.strikeLightning("world", 100, 64, -230);            // real, damaging strike
Bird.strikeLightning("world", 100, 64, -230, true);      // visual only, no damage/fire
```

### Wait / sleep (async only)

Pauses execution like Skript's `wait 5 seconds` effect. **Only call this from
inside `Bird.runTaskAsync(...)`** — sleeping the main thread would freeze the
whole server, so it's called `sleep`/`waitTicks` (not `wait`, since Java's
`Object` already has a `wait()` method used for thread locks — reusing that
name risks ambiguous overloads and awkward foot-guns) and it refuses to run
if called on the main thread.

```js
Bird.onCommand("sequence", function (sender, label, args) {
    Bird.runTaskAsync(function () {
        Bird.tell(sender, "&e3...");
        Bird.sleep(1);
        Bird.tell(sender, "&e2...");
        Bird.sleep(1);
        Bird.tell(sender, "&e1...");
        Bird.sleep(1);
        Bird.runTask(function () {
            // hop back to the main thread for anything touching Bukkit's world/players state
            Bird.tell(sender, "&aGO!");
        });
    });
});
```

### Running commands

```js
Bird.runCommand("give Notch diamond 1");     // runs as console, works with or without a leading "/"
Bird.runCommandAs(player, "spawn");          // runs as if the player typed it - respects their permissions
```

### Block detection

```js
var type = Bird.getBlockType("world", 100, 64, -230);      // -> "STONE", "DIAMOND_ORE", etc.
Bird.isBlockType("world", 100, 64, -230, "DIAMOND_ORE");    // -> true/false
Bird.setBlockType("world", 100, 64, -230, "AIR");           // clear a block

var block = Bird.getBlock("world", 100, 64, -230);          // full org.bukkit.block.Block if you need more
block.getType(); block.getLocation(); block.breakNaturally();

var looking = Bird.getTargetBlock(player);        // block the player is looking at (up to 100 blocks)
var looking2 = Bird.getTargetBlock(player, 10);   // custom max distance

var underfoot = Bird.getBlockPlayerIsOn(player);  // block the player is standing on
if (underfoot.getType().name() === "LAVA") {
    Bird.tell(player, "&cYou're standing in lava!");
}
```

### Cooldowns

Handy for limiting how often a command or ability can be used per-player.

```js
if (Bird.hasCooldown("fireball", player)) {
    Bird.tell(player, "&cWait " + Bird.getCooldownRemaining("fireball", player).toFixed(1) + "s");
} else {
    Bird.setCooldown("fireball", player, 10); // 10 second cooldown
    // ... cast the fireball ...
}
```

### Colors & random

```js
player.sendMessage(Bird.colorize("&a&lNice!")); // "&a" -> "§a" (broadcast/tell/actionbar/title already do this for you)
var dmg = Bird.random(5, 10); // random int, inclusive
```

### Cross-script custom events

Lets one script fire a named event that any other loaded script can react to
— useful for splitting a big project into multiple `.js` files (e.g. an
economy script and a shop script that don't need to know about each other's
internals).

```js
// in economy.js
Bird.emit("coins-added", { player: player.getName(), amount: 50 });

// in logging.js - completely separate script/file
Bird.on("coins-added", function (data) {
    Bird.log(data.player + " received " + data.amount + " coins");
});
```

### Internet access: `fetch()` and Discord webhooks

Requests always run off the main thread automatically - your callback is then
called back **on** the main thread, so it's always safe to touch
players/blocks/etc. inside it. No manual thread juggling needed.

```js
// Simple GET
Bird.fetch("https://api.example.com/status", function (res) {
    if (res.ok) {
        Bird.log("Status: " + res.status + " body: " + res.body);
    } else {
        Bird.warn("Request failed: " + res.status + " " + res.body);
    }
});

// POST with a JSON body
Bird.fetch("https://api.example.com/events", "POST", JSON.stringify({ type: "join" }), function (res) {
    Bird.log("Response: " + res.body);
});

// Full control: method + body + custom headers
Bird.fetch("https://api.example.com/secure", "POST", JSON.stringify({ a: 1 }),
    new java.util.HashMap({ "Authorization": "Bearer TOKEN" }),
    function (res) { Bird.log(res.body); }
);
```

Discord webhooks (create one in your Discord channel's *Integrations* settings):

```js
Bird.sendDiscordWebhook("https://discord.com/api/webhooks/...", "A player just found a diamond! 💎");
Bird.sendDiscordWebhook("https://discord.com/api/webhooks/...", "Server restarting soon", "Server Bot", "https://example.com/icon.png");

// Rich embed: title, description, hex color
Bird.sendDiscordEmbed("https://discord.com/api/webhooks/...", "Player joined", playerName + " joined the server", "#57F287");
```

### Save files (like a real plugin's data files)

Every script gets its own private folder at
`plugins/BirdApi/files/<scriptname>/` to save/load whatever it wants -
logs, exports, small JSON "databases", etc. Two safety rules are always
enforced and can't be bypassed from JS:

- **Sandboxed** — a script can only read/write inside its own folder. No
  `"../"` path traversal, no absolute paths, no touching another script's
  files or anything else on the server's disk.
- **Extension blacklist** — these are always rejected, since they can execute
  code or run automatically: `exe bat cmd dll so autorun ps1 ps2 psm1 php sh
  bash vbs vbe wsf wsh jse jar msi scr`. This protects the server even if a
  script itself is buggy or was tampered with.

```js
Bird.saveFile("stats.json", JSON.stringify({ kills: 42, deaths: 3 }));
var raw = Bird.readFile("stats.json");
var stats = raw ? JSON.parse(raw) : { kills: 0, deaths: 0 };

Bird.saveFile("logs/2026-07-24.txt", "Server started\n"); // subfolders work too
Bird.fileExists("stats.json");   // -> true
Bird.listFiles();                // -> ["stats.json", "logs"]
Bird.deleteFile("stats.json");   // -> true/false

Bird.saveFile("virus.exe", "..."); // -> false, blocked + warning logged
```

### Persistent per-script storage

For small bits of state (counters, toggles, timestamps) use `setData`/`getData`
below - it's simpler. For whole files (JSON exports, logs, CSVs) use
`Bird.saveFile`/`readFile` from the previous section instead.

Every script gets its own key/value store, saved to
`plugins/BirdApi/data/<scriptname>.properties` and reloaded automatically the
next time the script (or the server) starts. Values are strings - convert
numbers with `parseInt`/`parseFloat`/`String(...)` as needed.

```js
Bird.setData("welcomeCount", "0");
var count = parseInt(Bird.getData("welcomeCount", "0"));
Bird.setData("welcomeCount", String(count + 1));

Bird.getData("missingKey");             // -> null
Bird.getData("missingKey", "default");  // -> "default"
Bird.removeData("welcomeCount");
Bird.getDataKeys();                     // -> java.util.List<String> of all stored keys
Bird.saveData();                        // force an immediate write (set/remove already save automatically)
```

## Things to know / limitations

- Each `.js` file gets its own GraalJS context with its own global scope (a `var x`
  in one file never collides with another file's `var x`); each `.lua` file
  likewise gets its own LuaJ `Globals`, and each `.py` file gets its own GraalPy
  `Context`, so scripts never share state across files unless they go through
  `Bird`'s shared APIs (custom events, persistent storage, etc.).
- LuaJ is a pure-Java Lua interpreter (no native/JNI dependency), which is
  simpler to ship but slower than GraalJS's JIT for CPU-heavy scripts — fine
  for typical event/command handlers, less ideal for tight numeric loops.
  `.lua` scripts don't get `Java.extend(...)` for subclassing, but do get
  `luajava.bindClass(...)` (loaded automatically) as the equivalent of
  `Java.type(...)` for static access and constructing instances; see the
  "Lua support" section above for how Java interop works from Lua.
- GraalPy (like GraalJS) JIT-compiles hot code, so it's much closer to GraalJS
  in performance than LuaJ is - but it's also by far the heaviest of the three
  to load (pulling in the Python standard library), so expect `.py` scripts to
  take noticeably longer to load/reload than `.js`/`.lua` ones, especially the
  first one loaded after a server (re)start.
- `pip`-installed packages aren't available out of the box - GraalPy's standard
  library ships in the jar, but third-party packages would need to be vendored
  alongside your script and imported by path, since there's no `plugins/BirdApi`-relative
  virtualenv set up for you.
- `/birdlib reload` unregisters every script's listeners/commands first, then
  reloads everything.
- `CommandMap` access uses reflection on the `commandMap` field of `CraftServer`,
  which has been stable for years, but in theory could change if PaperMC ever
  refactors its internals.
- GraalJS also supports `Java.extend(JavaClass)` if you need to fully subclass a
  Java class (e.g. complex custom `ItemStack` behavior) — you can build on top of
  `BirdAPI` for that.
- Every `Bird.runTask*` call is tracked per-script and automatically cancelled
  when that script is unloaded/reloaded — you never need to cancel tasks
  yourself just to reload a script safely.
- Persistent storage (`Bird.setData`/`getData`) is per-script and stored as a
  flat string-only `.properties` file - it's meant for small bits of state
  (counters, toggles, last-seen timestamps), not as a database.
