# example.py - a Python equivalent of example.js / example.lua, showing the
# same core features (events, commands, persistent data) written in Python
# instead. Drop this in plugins/BirdApi/ alongside (or instead of) the
# others.
#
# Java classes are reached via `import java` then `java.type(...)`, GraalPy's
# equivalent of Java.type(...) in the JS scripts / luajava.bindClass(...) in
# the Lua ones. Method calls on Java objects use plain Python dot-call syntax.

import java

Material = java.type("org.bukkit.Material")
Attribute = java.type("org.bukkit.attribute.Attribute")

Bird.log("Loading example.py...")


def on_join(event):
    player = event.getPlayer()
    event.setJoinMessage("§a[+] " + player.getName() + " joined the server")
    player.sendMessage("§bWelcome to the server! (powered by BirdLibraryApi)")

    key = "joins_" + str(player.getUniqueId())
    count = int(Bird.getData(key, "0")) + 1
    Bird.setData(key, str(count))
    if count == 1:
        Bird.broadcast("§d" + player.getName() + " is joining for the first time, welcome them!")


Bird.onEvent("org.bukkit.event.player.PlayerJoinEvent", on_join)


def on_block_break(event):
    if event.getBlock().getType() == Material.BEDROCK:
        event.setCancelled(True)
        event.getPlayer().sendMessage("§cYou can't break Bedrock!")


Bird.onEvent("org.bukkit.event.block.BlockBreakEvent", on_block_break)


def hello_command(sender, label, args):
    sender.sendMessage("§eHello from BirdLibraryApi (Python)! You sent " + str(len(args)) + " argument(s)")


Bird.onCommand("hello", hello_command)


def heal_command(sender, label, args):
    # getAttribute only exists on players (not the console) - guard with a
    # try/except as a simple way to detect "wrong sender type" without
    # needing Class.isInstance() plumbing from Python.
    try:
        max_health = sender.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()
    except Exception:
        sender.sendMessage("§cThis command can only be used by players in-game")
        return
    sender.setHealth(max_health)
    sender.setFoodLevel(20)
    sender.sendMessage("§aFully healed and fed!")


Bird.onCommand("heal", heal_command)


def goto_command(sender, label, args):
    if len(args) < 1:
        sender.sendMessage("§cUsage: /goto <player>")
        return
    target = Bird.getPlayer(args[0])
    if target is None:
        sender.sendMessage("§cPlayer not found or not online: " + args[0])
        return
    sender.teleport(target.getLocation())
    sender.sendMessage("§aTeleported to " + target.getName())


Bird.onCommand("goto", "example.goto", goto_command)

Bird.log("example.py loaded successfully")
