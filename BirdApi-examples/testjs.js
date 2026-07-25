Bird.onCommand("testjs", function (sender, label, args) {
    var Player = Java.type("org.bukkit.entity.Player");
    if (!Player.class.isInstance(sender)) {
        sender.sendMessage("§cThis command can only be used by players in-game");
        return;
    }
    Bird.sendActionBar(sender, "working!");
});
