package com.helltoxx.thethingparty.client;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Singleton GeoAnimatable, представляющий "виртуальное" Нечто, рендерящееся на месте игрока.
 * Один экземпляр на всех игроков-Нечто — анимационный таймлайн считается через {@link #getTick(Object)}
 * по tickCount каждого конкретного entity, поэтому таймлайны независимы.
 */
@OnlyIn(Dist.CLIENT)
public final class ThingAnimatable implements GeoAnimatable {
    public static final ThingAnimatable INSTANCE = new ThingAnimatable();

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.thing.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private ThingAnimatable() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            state.setAnimation(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object instance) {
        // instance — это Entity, переданный в renderer (см. GeoReplacedEntityRenderer#getInstanceId).
        // Возвращаем его tickCount, чтобы анимация была независимой для каждого игрока.
        if (instance instanceof Entity e) {
            return e.tickCount;
        }
        return 0;
    }
}
