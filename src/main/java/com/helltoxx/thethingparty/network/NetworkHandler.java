package com.helltoxx.thethingparty.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("thethingparty:main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        // Регистрируем наш S2C пакет для синхронизации данных
        INSTANCE.registerMessage(id(),
                SyncThingDataPacket.class,
                SyncThingDataPacket::toBytes,
                SyncThingDataPacket::new,
                SyncThingDataPacket::handle);

        // Здесь позже добавим C2S пакеты (MorphRequestPacket, AbilityUsePacket)
    }
}