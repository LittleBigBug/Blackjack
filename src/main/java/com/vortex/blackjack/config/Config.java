package com.vortex.blackjack.config;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Level;

public class Config extends YamlConfiguration {

    private final File file;
    private final BlackjackPlugin plugin;

    public Config(BlackjackPlugin plugin, String cfgFile) {
        super();

        this.plugin = plugin;

        this.file = new File(plugin.getDataFolder(), cfgFile);
        if (!file.exists())
            plugin.saveResource(cfgFile, false);

        try {
            this.load(file);
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Cannot load " + file, ex);
        }
    }

    public File getFile() {
        return this.file;
    }

    public void save() {
        try {
            this.save(file);
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Cannot save " + file, ex);
        }
    }

}
