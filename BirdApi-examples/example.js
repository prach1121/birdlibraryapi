Bird.log("Loading example.js...");

Bird.onEvent("org.bukkit.event.player.PlayerJoinEvent", function (event) {
    var player = event.getPlayer();
    event.setJoinMessage("§a[+] " + player.getName() + " joined the server");
    player.sendMessage("§bWelcome to the server! (powered by BirdLibraryApi)");

    var key = "joins_" + player.getUniqueId();
    var count = parseInt(Bird.getData(key, "0")) + 1;
    Bird.setData(key, String(count));
    if (count === 1) {
        Bird.broadcast("§d" + player.getName() + " is joining for the first time, welcome them!");
    }
});

Bird.onEvent("org.bukkit.event.block.BlockBreakEvent", function (event) {
    var Material = Java.type("org.bukkit.Material");
    if (event.getBlock().getType() === Material.BEDROCK) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou can't break Bedrock!");
    }
});

Bird.onCommand("hello", function (sender, label, args) {
    sender.sendMessage("§eHello from BirdLibraryApi! You sent " + args.length + " argument(s)");
});

Bird.onCommand("heal", function (sender, label, args) {
    var Player = Java.type("org.bukkit.entity.Player");
    if (!Player.class.isInstance(sender)) {
        sender.sendMessage("§cThis command can only be used by players in-game");
        return;
    }
    var Attribute = Java.type("org.bukkit.attribute.Attribute");
    var maxHealth = sender.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
    sender.setHealth(maxHealth);
    sender.setFoodLevel(20);
    sender.sendMessage("§aFully healed and fed!");
});

Bird.onCommand("goto", "example.goto", function (sender, label, args) {
    if (args.length < 1) {
        sender.sendMessage("§cUsage: /goto <player>");
        return;
    }
    var target = Bird.getPlayer(args[0]);
    if (target === null) {
        sender.sendMessage("§cPlayer not found or not online: " + args[0]);
        return;
    }
    sender.teleport(target.getLocation());
    sender.sendMessage("§aTeleported to " + target.getName());
}, function (sender, alias, args) {

    if (args.length === 1) {
        var names = [];
        var players = Bird.getOnlinePlayers();
        for (var i = 0; i < players.size(); i++) {
            names.push(players.get(i).getName());
        }
        return java.util.Arrays.asList(names);
    }
    return java.util.Collections.emptyList();
});

Bird.onCommand("countdown", function (sender, label, args) {
    var seconds = args.length > 0 ? parseInt(args[0]) : 5;
    if (isNaN(seconds) || seconds <= 0) {
        sender.sendMessage("§cUsage: /countdown <seconds>");
        return;
    }

    var remaining = [seconds];
    var taskId = Bird.runTaskTimer(function () {
        if (remaining[0] <= 0) {
            Bird.broadcast("§aGO!");
            Bird.broadcastActionBar("§a§lGO!");
            Bird.broadcastTitle("§a§lGO!", "");
            Bird.cancelTask(taskId);
            return;
        }
        Bird.broadcast("§e" + remaining[0] + "...");
        Bird.broadcastActionBar("§e" + remaining[0] + "...");
        remaining[0] = remaining[0] - 1;
    }, 0, 20);
});

Bird.onCommand("warn", "example.warn", function (sender, label, args) {
    if (args.length < 2) {
        sender.sendMessage("§cUsage: /warn <player> <message>");
        return;
    }
    var target = Bird.getPlayer(args[0]);
    if (target === null) {
        sender.sendMessage("§cPlayer not found or not online: " + args[0]);
        return;
    }
    var message = args.slice(1).join(" ");
    Bird.sendActionBar(target, "§c⚠ " + message);
    Bird.tell(target, "§c[Warning] §7" + message);
    sender.sendMessage("§aSent warning to " + target.getName());
});

Bird.onCommand("announce", "example.announce", function (sender, label, args) {
    if (args.length < 1) {
        sender.sendMessage("§cUsage: /announce <message>");
        return;
    }
    Bird.broadcastChat("§6[Announcement] §f" + args.join(" "));
});

Bird.onCommand("whatblock", function (sender, label, args) {
    var target = Bird.getTargetBlock(sender, 100);
    if (target === null) {
        sender.sendMessage("§cYou're not looking at any block within range");
        return;
    }
    var loc = target.getLocation();
    sender.sendMessage("§aYou're looking at §e" + target.getType().name()
        + " §aat (§e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "§a)");

    var under = Bird.getBlockPlayerIsOn(sender);
    sender.sendMessage("§7Standing on: " + under.getType().name());
});

Bird.log("example.js loaded - /hello, /heal, /goto, /countdown, /warn, /announce, /stats, /checkip, /whatblock, /sequence, /serverinfo and /vip are now available");

Bird.onCommand("vip", "example.vip", function (sender, label, args) {
    Bird.setDisplayName(sender, "&b[VIP] &f" + sender.getName());
    Bird.addPotionEffect(sender, "SPEED", 60, 0);
    Bird.giveExp(sender, 20);
    Bird.tell(sender, "&aVIP perks applied! Ping: " + Bird.getPing(sender) + "ms");

    var nearby = Bird.getNearbyPlayers(sender, 15);
    for (var i = 0; i < nearby.size(); i++) {
        Bird.tell(nearby.get(i), "&7" + sender.getName() + " (VIP) is nearby!");
    }
});

Bird.onCommand("serverinfo", function (sender, label, args) {
    sender.sendMessage("§6=== Server Info ===");
    sender.sendMessage("§7Players: §f" + Bird.getOnlineCount() + "/" + Bird.getMaxPlayers());
    sender.sendMessage("§7Worlds: §f" + Bird.getWorldNames().join(", "));

    if (Java.type("org.bukkit.entity.Player").class.isInstance(sender)) {
        sender.sendMessage("§7Your health: §f" + Bird.getHealth(sender) + "  §7Game mode: §f" + Bird.getGameMode(sender));
        sender.sendMessage("§7Carrying 1+ diamonds: §f" + Bird.hasItem(sender, "DIAMOND", 1));
    }
});

Bird.onCommand("sequence", function (sender, label, args) {
    sender.sendMessage("§7Starting sequence...");
    Bird.runTaskAsync(function () {

        Bird.sleep(1);
        Bird.tell(sender, "&e3...");
        Bird.sleep(1);
        Bird.tell(sender, "&e2...");
        Bird.sleep(1);
        Bird.tell(sender, "&e1...");
        Bird.sleep(1);

        Bird.runTask(function () {

            Bird.tell(sender, "&aGO! Giving you a diamond...");
            Bird.runCommandAs(sender, "give " + sender.getName() + " diamond 1");
        });
    });
});

Bird.onCommand("kit", function (sender, label, args) {
    if (Bird.hasCooldown("kit", sender)) {
        var left = Bird.getCooldownRemaining("kit", sender);
        Bird.tell(sender, "&cYou can claim another kit in " + Math.ceil(left) + "s");
        return;
    }
    Bird.giveItem(sender, "IRON_SWORD", 1, "&b&lStarter Sword", ["&7Given by the /kit command"]);
    Bird.giveItem(sender, "BREAD", 8);
    Bird.playSound(sender, "ENTITY_PLAYER_LEVELUP");
    Bird.spawnParticle(sender, "HAPPY_VILLAGER", 15);
    Bird.setCooldown("kit", sender, 300);
    Bird.tell(sender, "&aHere's your starter kit!");

    Bird.emit("kit-claimed", { player: sender.getName() });
});

Bird.on("kit-claimed", function (data) {
    Bird.log(data.player + " claimed the starter kit");
});

var DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/REPLACE/WITH-YOUR-URL";

Bird.onEvent("org.bukkit.event.player.PlayerJoinEvent", function (event) {
    if (DISCORD_WEBHOOK_URL.indexOf("REPLACE") !== -1) return;
    Bird.sendDiscordEmbed(DISCORD_WEBHOOK_URL, "Player joined",
        event.getPlayer().getName() + " joined the server", "#57F287");
});

Bird.onCommand("checkip", "example.admin", function (sender, label, args) {
    sender.sendMessage("§7Checking...");
    Bird.fetch("https://api.ipify.org?format=json", function (res) {
        if (res.ok) {
            sender.sendMessage("§aServer's public IP info: " + res.body);
        } else {
            sender.sendMessage("§cRequest failed (" + res.status + "): " + res.body);
        }
    });
});

Bird.onCommand("stats", function (sender, label, args) {
    var raw = Bird.readFile("playerstats.json");
    var stats = raw ? JSON.parse(raw) : {};

    var name = sender.getName();
    stats[name] = (stats[name] || 0) + 1;
    Bird.saveFile("playerstats.json", JSON.stringify(stats, null, 2));

    sender.sendMessage("§aYou've run /stats " + stats[name] + " time(s)");
});
