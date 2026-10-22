package com.kdi.keepalive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class FkCommand implements CommandExecutor, TabCompleter {
    private final FakeBotManager manager;

    public FkCommand(FakeBotManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fk.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            manager.sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, args);
            case "kill" -> {
                if (args.length != 1) {
                    usage(sender);
                } else if (manager.kill()) {
                    sender.sendMessage(ChatColor.GREEN + "The fake player was removed.");
                } else {
                    sender.sendMessage(ChatColor.RED + "No fake player was found.");
                }
            }
            case "status" -> {
                if (args.length != 1) {
                    usage(sender);
                } else {
                    manager.sendStatus(sender);
                }
            }
            case "help" -> manager.sendHelp(sender);
            case "reload" -> {
                if (args.length != 1) {
                    usage(sender);
                } else {
                    manager.reloadSettings();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded. The configured UUID is used on the next respawn.");
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3 || !args[1].equalsIgnoreCase("add")) {
            usage(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can spawn or move the fake player; a location is required.");
            return;
        }
        String name = args.length == 3 ? args[2] : manager.getDefaultName();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            sender.sendMessage(ChatColor.RED + "The fake-player name must be 1–16 letters, numbers, or underscores.");
            return;
        }
        if (manager.spawn(player.getLocation(), name)) {
            sender.sendMessage(ChatColor.GREEN + "The fake player is at your location.");
        } else {
            sender.sendMessage(ChatColor.RED + "Could not spawn the fake player; check the console for details.");
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /fk spawn add [name], /fk kill, /fk status, /fk reload, /fk help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fk.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("spawn", "kill", "status", "help", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return prefix(List.of("add"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("add")) {
            List<String> names = new ArrayList<>();
            names.add(manager.getDefaultName());
            if (sender instanceof Player player && !names.contains(player.getName())) {
                names.add(player.getName());
            }
            return prefix(names, args[2]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> choices, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}