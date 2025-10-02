package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public abstract class SubCommand {

    protected final String subCommand;
    protected final BlackjackPlugin plugin;

    public SubCommand(BlackjackPlugin plugin, String subCommand) {
        this.plugin = plugin;
        this.subCommand = subCommand;
    }

    public abstract boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args);

    public String getSubCommandName() {
        return this.subCommand;
    }

    public boolean isPlayerOnly() {
        return true;
    }

}
