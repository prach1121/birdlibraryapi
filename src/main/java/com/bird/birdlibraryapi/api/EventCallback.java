package com.bird.birdlibraryapi.api;

import org.bukkit.event.Event;

@FunctionalInterface
public interface EventCallback {
    void execute(Event event);
}
