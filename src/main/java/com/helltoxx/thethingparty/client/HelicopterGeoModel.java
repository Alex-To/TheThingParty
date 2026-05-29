package com.helltoxx.thethingparty.client;

import com.helltoxx.thethingparty.entity.HelicopterEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class HelicopterGeoModel extends GeoModel<HelicopterEntity> {
    private static final ResourceLocation MODEL     = ResourceLocation.fromNamespaceAndPath("thethingparty", "geo/helicopter.geo.json");
    private static final ResourceLocation TEXTURE   = ResourceLocation.fromNamespaceAndPath("thethingparty", "textures/entity/helicopter_tex.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath("thethingparty", "animations/helicopter.animation.json");

    @Override
    public ResourceLocation getModelResource(HelicopterEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HelicopterEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(HelicopterEntity animatable) {
        return ANIMATION;
    }

    }