package me.andyreckt.sunset.example;

import me.andyreckt.sunset.annotations.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestCommands {

    @Command(names = "test", async = true)
    public void hello(CommandSender sender, @Param(name = "player") Player player) {
        sender.sendMessage("hello " + player.getName());
    }

    @Command(names = "quit", permission = "quit.reason")
    public void quit(Player sender, @Param(name = "reason", wildcard = true) String reason) {
        sender.kickPlayer(reason);
    }
}
