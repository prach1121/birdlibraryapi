package com.bird.birdlibraryapi;

import com.bird.birdlibraryapi.api.BirdAPI;

import javax.script.ScriptEngine;
import java.io.File;

public record LoadedScript(String name, File file, ScriptEngine engine, BirdAPI api) {
}
