package me.andyreckt.sunset.example;

import me.andyreckt.sunset.Sunset;
import org.bukkit.plugin.java.JavaPlugin;

public final class Plugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        Sunset sunset = new Sunset(this);
        sunset.registerCommands(new TestCommands());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
