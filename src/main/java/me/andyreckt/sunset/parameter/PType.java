package me.andyreckt.sunset.parameter;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public interface PType<T> {

    T transform(CommandSender sender, String string);

    List<String> complete(Player sender, String string);

}
