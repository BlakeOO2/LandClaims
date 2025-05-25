// WorldSettingsCommand.java
package org.example;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

public class WorldSettingsCommand {
    private final Main plugin;

    public WorldSettingsCommand(Main plugin) {
        this.plugin = plugin;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("landclaims.admin")) {
            sender.sendMessage("§c[LandClaims] You don't have permission to manage world settings.");
            return true;
        }

        if (args.length < 2) {
            showHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "global":
                return handleGlobalFlags(sender, args);
            case "world":
                return handleWorldFlags(sender, args);
            case "toggle":
                return handleToggleClaiming(sender, args);
            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean handleGlobalFlags(CommandSender sender, String[] args) {
        if (args.length < 3) {
            showGlobalFlags(sender);
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage("§c[LandClaims] Usage: /lc world global <flag> <true/false>");
            return true;
        }

        try {
            ClaimFlag flag = ClaimFlag.valueOf(args[2].toUpperCase());
            boolean value = Boolean.parseBoolean(args[3]);

            plugin.getWorldSettingsManager().setGlobalFlag(flag, value);
            sender.sendMessage("§a[LandClaims] Global flag " + flag.name() + " set to " + value);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c[LandClaims] Invalid flag name!");
        }

        return true;
    }

    private boolean handleWorldFlags(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c[LandClaims] Usage: /lc world world <worldname> [flag] [value]");
            return true;
        }

        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            sender.sendMessage("§c[LandClaims] World not found!");
            return true;
        }

        if (args.length == 3) {
            showWorldFlags(sender, world);
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage("§c[LandClaims] Usage: /lc world world <worldname> <flag> <true/false>");
            return true;
        }

        try {
            ClaimFlag flag = ClaimFlag.valueOf(args[3].toUpperCase());
            boolean value = Boolean.parseBoolean(args[4]);

            plugin.getWorldSettingsManager().setWorldFlag(world.getName(), flag, value);
            sender.sendMessage("§a[LandClaims] Flag " + flag.name() + " set to " + value +
                    " for world " + world.getName());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c[LandClaims] Invalid flag name!");
        }

        return true;
    }

    private boolean handleToggleClaiming(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c[LandClaims] Usage: /lc world toggle <worldname>");
            return true;
        }

        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            sender.sendMessage("§c[LandClaims] World not found!");
            return true;
        }

        boolean currentValue = plugin.getWorldSettingsManager().isClaimingAllowed(world);
        plugin.getWorldSettingsManager().setClaimingAllowed(world, !currentValue);

        sender.sendMessage("§a[LandClaims] Claiming in world " + world.getName() +
                " is now " + (!currentValue ? "enabled" : "disabled"));
        return true;
    }

    private void showGlobalFlags(CommandSender sender) {
        sender.sendMessage("§6=== Global Flags ===");
        for (ClaimFlag flag : ClaimFlag.values()) {
            boolean value = plugin.getWorldSettingsManager().getGlobalFlag(flag);
            String status = value ? "§a✔" : "§c✘";
            sender.sendMessage(status + " §7" + flag.name());
        }
    }

    private void showWorldFlags(CommandSender sender, World world) {
        sender.sendMessage("§6=== World Flags: " + world.getName() + " ===");
        sender.sendMessage("§7Claiming: " +
                (plugin.getWorldSettingsManager().isClaimingAllowed(world) ? "§aEnabled" : "§cDisabled"));

        for (ClaimFlag flag : ClaimFlag.values()) {
            boolean value = plugin.getWorldSettingsManager().getWorldFlag(world.getName(), flag);
            String status = value ? "§a✔" : "§c✘";
            sender.sendMessage(status + " §7" + flag.name());
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== LandClaims World Settings ===");
        sender.sendMessage("§f/lc world global §7- View global flags");
        sender.sendMessage("§f/lc world global <flag> <true/false> §7- Set global flag");
        sender.sendMessage("§f/lc world world <worldname> §7- View world flags");
        sender.sendMessage("§f/lc world world <worldname> <flag> <true/false> §7- Set world flag");
        sender.sendMessage("§f/lc world toggle <worldname> §7- Toggle claiming in world");
    }
}
