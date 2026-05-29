package com.helltoxx.thethingparty.client;

import com.helltoxx.thethingparty.entity.HelicopterEntity;
import com.helltoxx.thethingparty.init.EntityInit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентская часть, подписанная на MOD-bus (тут нужны события lifecycle/registry).
 * Регистрирует рендереры кастомных entity.
 */
@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModSetup {
    private ClientModSetup() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityInit.HELICOPTER.get(), HelicopterEntity.HelicopterRenderer::new);
    }
}
