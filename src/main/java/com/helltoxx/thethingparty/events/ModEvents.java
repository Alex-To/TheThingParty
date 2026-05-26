package com.helltoxx.thethingparty.events;

import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    // Прикрепляем Capability ко всем новым игрокам
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(ThingPlayerProvider.THING_DATA).isPresent()) {
                event.addCapability(new ResourceLocation("thethingparty", "properties"), new ThingPlayerProvider());
            }
        }
    }

    // Сохраняем данные после смерти/клонирования
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getOriginal().getCapability(ThingPlayerProvider.THING_DATA).ifPresent(oldStore -> {
                event.getEntity().getCapability(ThingPlayerProvider.THING_DATA).ifPresent(newStore -> {
                    newStore.copyFrom(oldStore);
                });
            });
        }
    }

    // Синхронизируем capability на клиент при коннекте / респавне / смене измерения.
    // Без этого LocalPlayer ничего не знает о ServerPlayer-данных, и клиентский рендер/HUD не сработает.
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.syncToPlayer(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.syncToPlayer(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.syncToPlayer(sp);
        }
    }
}