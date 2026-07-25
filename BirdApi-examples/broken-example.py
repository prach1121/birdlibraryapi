Bird.log("Loading broken-example.py...")


def broken_command(sender, label, args)  # missing ':' before the function body
    sender.sendMessage("This line will never be reached")


Bird.onCommand("broken", broken_command)
