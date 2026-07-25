Bird:log("Loading broken-example.lua...")

Bird:onCommand("broken" function(sender, label, args)
    sender:sendMessage("This line will never be reached")
end)
