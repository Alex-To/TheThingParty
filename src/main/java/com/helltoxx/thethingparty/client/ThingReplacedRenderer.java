package com.helltoxx.thethingparty.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * Renderer, рендерящий {@link Player} как singleton-Animatable {@link ThingAnimatable}.
 * Вызывается вручную из {@code RenderPlayerEvent.Pre} при {@code isMonsterForm == true}.
 */
@OnlyIn(Dist.CLIENT)
public class ThingReplacedRenderer extends GeoReplacedEntityRenderer<Player, ThingAnimatable> {
    public ThingReplacedRenderer(EntityRendererProvider.Context context) {
        super(context, new ThingGeoModel(), ThingAnimatable.INSTANCE);
    }
}
