Bird.log("Loading broken-example.js...")

Bird.onCommand("broken" function (sender, label, args) {
    sender.sendMessage("This line will never be reached");
});
