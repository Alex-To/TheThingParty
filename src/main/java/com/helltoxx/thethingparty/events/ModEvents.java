package com.helltoxx.thethingparty.events;

import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.resources.ResourceLocation;
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
}