package com.helltoxx.thethingparty.network;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncThingDataPacket {
    final String role;
    final boolean isMonsterForm;
    final int biomass;
    final int weaponLockTicks;
    final int transformTicks;

    public SyncThingDataPacket(IThingPlayerData data) {
        this.role = data.getRole().name();
        this.isMonsterForm = data.isMonsterForm();
        this.biomass = data.getBiomass();
        this.weaponLockTicks = data.getWeaponLockTicks();
        this.transformTicks = data.getTransformTicks();
    }

    public SyncThingDataPacket(FriendlyByteBuf buf) {
        this.role = buf.readUtf();
        this.isMonsterForm = buf.readBoolean();
        this.biomass = buf.readInt();
        this.weaponLockTicks = buf.readInt();
        this.transformTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(role);
        buf.writeBoolean(isMonsterForm);
        buf.writeInt(biomass);
        buf.writeInt(weaponLockTicks);
        buf.writeInt(transformTicks);
    }

    public static void handle(SyncThingDataPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        // consumerMainThread в SimpleChannel уже выполняет enqueueWork + setPacketHandled.
        // Клиентский класс грузим через DistExecutor, чтобы dedicated-сервер не упал на net.minecraft.client.*
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.applySync(pkt));
    }
}
