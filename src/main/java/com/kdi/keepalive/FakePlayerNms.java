package com.kdi.keepalive;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FakePlayerNms {
    private MinecraftServer server;
    private SilentConnection connection;
    private FakeServerPlayer handle;
    private UUID uuid;

    public void spawn(String name, UUID playerUuid, Location location) {
        server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        GameProfile profile = new GameProfile(playerUuid, name);
        ClientInformation clientInformation = ClientInformation.createDefault();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        FakeServerPlayer player = new FakeServerPlayer(server, level, profile, clientInformation);
        player.setPos(location.getX(), location.getY(), location.getZ());
        player.setRot(location.getYaw(), location.getPitch());
        player.setInvulnerable(true);
        player.getAbilities().invulnerable = true;
        player.collides = false;
        player.noPhysics = true;
        connection = new SilentConnection();
        handle = player;
        uuid = playerUuid;
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection = new SilentListener(server, connection, player,
                    CommonListenerCookie.createInitial(profile, false));
        } catch (RuntimeException | LinkageError exception) {
            if (Bukkit.getPlayer(playerUuid) != null) {
                server.getPlayerList().remove(player);
            }
            clear();
            throw exception;
        }
    }

    public boolean move(UUID playerUuid, Location location) {
        Player player = Bukkit.getPlayer(playerUuid);
        return player != null && player.teleport(location);
    }

    public void protect() {
        if (handle == null) {
            return;
        }
        handle.setInvulnerable(true);
        handle.getAbilities().invulnerable = true;
        handle.collides = false;
        handle.noPhysics = true;
    }

    public boolean hasHandle() {
        return handle != null;
    }

    public Player getPlayer(UUID playerUuid) {
        return Bukkit.getPlayer(playerUuid);
    }

    public void remove() {
        if (handle == null) {
            return;
        }
        FakeServerPlayer oldHandle = handle;
        UUID oldUuid = uuid;
        try {
            if (oldUuid != null && Bukkit.getPlayer(oldUuid) != null && server != null) {
                server.getPlayerList().remove(oldHandle);
            }
        } finally {
            clear();
        }
    }

    private void clear() {
        handle = null;
        connection = null;
        server = null;
        uuid = null;
    }

    private static final class FakeServerPlayer extends ServerPlayer {
        private FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                 ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public void tick() {
        }

        @Override
        public void doTick() {
        }
    }

    private static final class SilentConnection extends Connection {
        private SilentConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetInfo) {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener sendListener) {
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener sendListener, boolean flush) {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void flushChannel() {
        }
    }

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private SilentListener(MinecraftServer server, Connection connection, ServerPlayer player,
                               CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void tick() {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener sendListener) {
        }

        @Override
        public void disconnect(net.kyori.adventure.text.Component reason) {
        }

        @Override
        public void disconnect(net.kyori.adventure.text.Component reason, PlayerKickEvent.Cause cause) {
        }

        @Override
        public void disconnect(Component reason, PlayerKickEvent.Cause cause) {
        }
    }
}