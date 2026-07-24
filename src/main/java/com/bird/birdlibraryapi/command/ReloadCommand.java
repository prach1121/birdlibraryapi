package com.bird.birdlibraryapi.command;

import com.bird.birdlibraryapi.BirdLibraryApi;
import com.bird.birdlibraryapi.ScriptLoadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final BirdLibraryApi plugin;

    public ReloadCommand(BirdLibraryApi plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§eUsage: /birdlib reload [file name]");
            return true;
        }

        if (!sender.hasPermission("birdlib.reload")) {
            sender.sendMessage("§cYou don't have permission to use this command");
            return true;
        }

        long start = System.currentTimeMillis();

        if (args.length >= 2) {

            ScriptLoadResult result = plugin.getScriptManager().reloadOne(args[1]);
            long took = System.currentTimeMillis() - start;
            sender.sendMessage(result.formatted() + " §7(" + took + "ms)");
            return true;
        }

        List<ScriptLoadResult> results = plugin.getScriptManager().reloadAll();
        long took = System.currentTimeMillis() - start;

        if (results.isEmpty()) {
            sender.sendMessage("§eNo .js files found in " + plugin.getScriptManager().getScriptsFolder().getPath());
            return true;
        }

        long okCount = results.stream().filter(ScriptLoadResult::success).count();
        long failCount = results.size() - okCount;

        if (failCount == 0) {
            sender.sendMessage("§aReload complete: all " + okCount + " script(s) succeeded §7(" + took + "ms)");
        } else {
            sender.sendMessage("§eReload complete: §a" + okCount + " succeeded §7/ §c" + failCount
                    + " failed §7(" + took + "ms)");
            for (ScriptLoadResult r : results) {
                if (!r.success()) {
                    sender.sendMessage("  " + r.formatted());
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            String partial = args[1].toLowerCase();
            result.addAll(plugin.getScriptManager().listScriptFileNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList()));
        }
        return result;
    }
}
