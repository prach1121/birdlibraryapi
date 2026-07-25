package com.bird.birdlibraryapi.api;

import org.bukkit.command.CommandSender;

@FunctionalInterface
public interface CommandCallback {
    void execute(CommandSender sender, String label, String[] args);
}
