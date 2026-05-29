package com.helltoxx.thethingparty.client;

import com.helltoxx.thethingparty.entity.HelicopterEntity;
import com.helltoxx.thethingparty.init.EntityInit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Регистрация клиентских рендереров кастомных entity.
 * Отдельный класс — на MOD-bus, чтобы не мешать FORGE-bus подпискам в {@link ClientEvents}.
 */
@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EntityRenderers {
    private EntityRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityInit.HELICOPTER.get(), HelicopterEntity.HelicopterRenderer::new);
    }
}
