package com.bird.birdlibraryapi;

import com.bird.birdlibraryapi.api.BirdAPI;

import java.io.File;

/**
 * A loaded script. {@code engine} holds the language runtime that ran it:
 * a {@link javax.script.ScriptEngine} (GraalJS) for {@code .js} files, a
 * {@link org.luaj.vm2.Globals} for {@code .lua} files, or a
 * {@link org.graalvm.polyglot.Context} (GraalPy) for {@code .py} files.
 * It's typed as Object since it's mostly write-only bookkeeping; the one
 * exception is that ScriptManager checks for the Context case on unload,
 * since (unlike the other two) it must be explicitly closed to free
 * native resources.
 */
public record LoadedScript(String name, File file, Object engine, BirdAPI api) {
}
