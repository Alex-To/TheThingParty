package com.helltoxx.thethingparty.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class ThingGeoModel extends GeoModel<ThingAnimatable> {
    private static final ResourceLocation MODEL = new ResourceLocation("thethingparty", "geo/thing.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("thethingparty", "textures/entity/thing_tex.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation("thethingparty", "animations/thing.animation.json");

    @Override
    public ResourceLocation getModelResource(ThingAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ThingAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ThingAnimatable animatable) {
        return ANIMATION;
    }
}
