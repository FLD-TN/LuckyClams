package org.example.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Location;
import org.example.Main;

public class LuckyClamCommand implements CommandExecutor {
    private final Main plugin;

    public LuckyClamCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("luckyclam.admin")) {
            sender.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eSử dụng: /" + label + " [start|reload|list]");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            plugin.startClamEvent();
            sender.sendMessage("§aSự kiện LuckyClam đã được kích hoạt! Sò May Mắn đã xuất hiện.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.loadConfigValues();
            sender.sendMessage("§aĐã tải lại cấu hình LuckyClam!");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            java.util.List<Location> clamLocations = plugin.getClamLocations();
            if (clamLocations.isEmpty()) {
                sender.sendMessage("§cHiện tại không có Sò May Mắn nào!");
            } else {
                sender.sendMessage("§aDanh sách vị trí Sò May Mắn:");
                for (int i = 0; i < clamLocations.size(); i++) {
                    Location loc = clamLocations.get(i);
                    sender.sendMessage("§e" + (i + 1) + ": x=" + loc.getBlockX() + ", y=" + loc.getBlockY() + ", z="
                            + loc.getBlockZ());
                }
            }
            return true;
        }

        sender.sendMessage("§cLệnh không hợp lệ! Sử dụng: /" + label + " [start|reload|list]");
        return true;
    }
}