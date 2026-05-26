package com.helltoxx.thethingparty.network;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Клиентские обработчики сетевых пакетов. Класс грузится только на стороне клиента
 * (вызовы идут через DistExecutor), поэтому ссылки на net.minecraft.client.* безопасны.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void applySync(SyncThingDataPacket pkt) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {
            data.setRole(IThingPlayerData.Role.valueOf(pkt.role));
            data.setMonsterForm(pkt.isMonsterForm);
            data.setBiomass(pkt.biomass);
            data.setWeaponLockTicks(pkt.weaponLockTicks);
        });
    }
}
