package com.kdi.keepalive;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class KdiKeepAlivePlugin extends JavaPlugin {
    private FakeBotManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new FakeBotManager(this);
        getServer().getPluginManager().registerEvents(manager, this);
        FkCommand commandHandler = new FkCommand(manager);
        PluginCommand command = Objects.requireNonNull(getCommand("fk"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        manager.start();
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdownPlugin();
        }
    }
}