package com.bird.birdlibraryapi.api;

import org.bukkit.command.CommandSender;

import java.util.List;

@FunctionalInterface
public interface TabCompleteCallback {
    List<String> execute(CommandSender sender, String alias, String[] args);
}
