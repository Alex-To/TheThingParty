package com.helltoxx.thethingparty.network;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncThingDataPacket {
    private final String role;
    private final boolean isMonsterForm;
    private final int biomass;
    private final int weaponLockTicks;

    public SyncThingDataPacket(IThingPlayerData data) {
        this.role = data.getRole().name();
        this.isMonsterForm = data.isMonsterForm();
        this.biomass = data.getBiomass();
        this.weaponLockTicks = data.getWeaponLockTicks();
    }

    public SyncThingDataPacket(FriendlyByteBuf buf) {
        this.role = buf.readUtf();
        this.isMonsterForm = buf.readBoolean();
        this.biomass = buf.readInt();
        this.weaponLockTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(role);
        buf.writeBoolean(isMonsterForm);
        buf.writeInt(biomass);
        buf.writeInt(weaponLockTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // Выполняем на клиенте
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {
                    data.setRole(IThingPlayerData.Role.valueOf(role));
                    data.setMonsterForm(isMonsterForm);
                    data.setBiomass(biomass);
                    data.setWeaponLockTicks(weaponLockTicks);
                });
            }
        });
        return true;
    }
}