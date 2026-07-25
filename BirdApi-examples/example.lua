-- example.lua - a Lua equivalent of example.js, showing the same core
-- features (events, commands, persistent data) written in Lua instead.
-- Drop this in plugins/BirdApi/ alongside (or instead of) example.js.
--
-- Java classes are reached via luajava.bindClass(...), LuaJ's equivalent of
-- Java.type(...) in the JS scripts. Method calls on Java objects use Lua's
-- colon syntax: object:method(args).

Bird:log("Loading example.lua...")

local Material = luajava.bindClass("org.bukkit.Material")
local Attribute = luajava.bindClass("org.bukkit.attribute.Attribute")

Bird:onEvent("org.bukkit.event.player.PlayerJoinEvent", function(event)
    local player = event:getPlayer()
    event:setJoinMessage("§a[+] " .. player:getName() .. " joined the server")
    player:sendMessage("§bWelcome to the server! (powered by BirdLibraryApi)")

    local key = "joins_" .. tostring(player:getUniqueId())
    local count = tonumber(Bird:getData(key, "0")) + 1
    Bird:setData(key, tostring(count))
    if count == 1 then
        Bird:broadcast("§d" .. player:getName() .. " is joining for the first time, welcome them!")
    end
end)

Bird:onEvent("org.bukkit.event.block.BlockBreakEvent", function(event)
    if event:getBlock():getType() == Material.BEDROCK then
        event:setCancelled(true)
        event:getPlayer():sendMessage("§cYou can't break Bedrock!")
    end
end)

Bird:onCommand("hello", function(sender, label, args)
    sender:sendMessage("§eHello from BirdLibraryApi (Lua)! You sent " .. #args .. " argument(s)")
end)

-- pcall guards the attribute lookup, which only works for players (not the
-- console) - a simple way to detect "wrong sender type" without needing
-- Class.isInstance() plumbing from Lua.
Bird:onCommand("heal", function(sender, label, args)
    local ok, maxHealth = pcall(function()
        return sender:getAttribute(Attribute.GENERIC_MAX_HEALTH):getValue()
    end)
    if not ok then
        sender:sendMessage("§cThis command can only be used by players in-game")
        return
    end
    sender:setHealth(maxHealth)
    sender:setFoodLevel(20)
    sender:sendMessage("§aFully healed and fed!")
end)

Bird:onCommand("goto", "example.goto", function(sender, label, args)
    if #args < 1 then
        sender:sendMessage("§cUsage: /goto <player>")
        return
    end
    local target = Bird:getPlayer(args[0])
    if target == nil then
        sender:sendMessage("§cPlayer not found or not online: " .. args[0])
        return
    end
    sender:teleport(target:getLocation())
    sender:sendMessage("§aTeleported to " .. target:getName())
end)

Bird:log("example.lua loaded successfully")
