package com.vortex.blackjack.listener;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerInputPacketListener extends SimplePacketListenerAbstract {

    private final BlackjackPlugin plugin;

    public PlayerInputPacketListener(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        PacketType.Play.Client type = event.getPacketType();

        if (type != PacketType.Play.Client.PLAYER_INPUT || !(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientPlayerInput wrapper = new WrapperPlayClientPlayerInput(event);
        if (!wrapper.isShift()) return;

        BlackjackTable table = this.plugin.getTableManager().getPlayerTable(player);
        if (table == null) return;

        // todo; verify they want to leave and they want to surrender half their bet
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            table.removePlayer(player);
            this.plugin.getBetManager().removePersistent(player);
        });
//            if (!yes) {
//                event.setCancelled(true);
//                return;
//            }
    }

}
