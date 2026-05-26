package com.helltoxx.thethingparty.network;

import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("thethingparty", "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int packetId = 0;
    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.messageBuilder(SyncThingDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncThingDataPacket::toBytes)
                .decoder(SyncThingDataPacket::new)
                .consumerMainThread(SyncThingDataPacket::handle)
                .add();
    }

    /**
     * Считывает THING_DATA с серверного игрока и отправляет S2C-пакет ему же.
     * Вызывать после любого изменения capability на сервере (команды, события).
     */
    public static void syncToPlayer(ServerPlayer player) {
        player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncThingDataPacket(data));
        });
    }
}
