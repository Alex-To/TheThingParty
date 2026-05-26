package com.helltoxx.thethingparty.client;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Singleton GeoAnimatable, представляющий "виртуальное" Нечто, рендерящееся на месте игрока.
 *
 * Два контроллера играют параллельно:
 *  - movement: idle / walk / run по скорости и спринту;
 *  - action:   transform / attack как one-shot.
 *
 * Animatable один на все Нечто-игроки, но {@link #getTick(Object)} возвращает {@code tickCount}
 * конкретного entity, поэтому таймлайны loop-анимаций индивидуальны. Action-контроллер share-нится,
 * что для MVP с 1-2 Нечто на лобби терпимо; при необходимости перейдём на GeoEntityRenderer
 * с per-entity animatable.
 */
@OnlyIn(Dist.CLIENT)
public final class ThingAnimatable implements GeoAnimatable {
    public static final ThingAnimatable INSTANCE = new ThingAnimatable();

    private static final RawAnimation IDLE      = RawAnimation.begin().thenLoop("animation.thing.idle");
    private static final RawAnimation WALK      = RawAnimation.begin().thenLoop("animation.thing.walk");
    private static final RawAnimation RUN       = RawAnimation.begin().thenLoop("animation.thing.run");
    private static final RawAnimation ATTACK    = RawAnimation.begin().thenPlay("animation.thing.attack");
    private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay("animation.thing.transform");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Хранит transformTicks из предыдущего вызова actionPredicate per-player, чтобы детектить
     * rising edge (0 -> >0). При rising edge принудительно ресетим контроллер - иначе GeckoLib
     * считает, что TRANSFORM ещё "последняя анимация" (она в STOPPED), и не перезапускает её
     * при повторном setAnimation с той же RawAnimation-ссылкой.
     *
     * Утечка ключей при отключении игроков незначительная для MVP лобби (5-15 человек).
     */
    private final Map<UUID, Integer> prevTransformTicks = new HashMap<>();

    private ThingAnimatable() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // transitionLength 5 - 0.25 секунды плавного перехода между walk/run/idle.
        controllers.add(new AnimationController<>(this, "movement", 5, this::movePredicate));
        // 0 - action-контроллер триггерится резко (удар, рев трансформации), без интерполяции.
        controllers.add(new AnimationController<>(this, "action", 0, this::actionPredicate));
    }

    private PlayState movePredicate(AnimationState<ThingAnimatable> state) {
        Entity entity = state.getData(DataTickets.ENTITY);
        if (!(entity instanceof Player player)) {
            return PlayState.STOP;
        }

        if (state.isMoving()) {
            state.setAnimation(player.isSprinting() ? RUN : WALK);
        } else {
            state.setAnimation(IDLE);
        }
        return PlayState.CONTINUE;
    }

    private PlayState actionPredicate(AnimationState<ThingAnimatable> state) {
        Entity entity = state.getData(DataTickets.ENTITY);
        if (!(entity instanceof Player player)) {
            return PlayState.STOP;
        }

        IThingPlayerData data = player.getCapability(ThingPlayerProvider.THING_DATA).orElse(null);
        if (data == null) return PlayState.STOP;

        AnimationController<ThingAnimatable> controller = state.getController();
        int curTransformTicks = data.getTransformTicks();
        int prev = prevTransformTicks.getOrDefault(player.getUUID(), 0);
        prevTransformTicks.put(player.getUUID(), curTransformTicks);

        // 1) Трансформация - наивысший приоритет. Пока transformTicks > 0, держим анимацию.
        if (curTransformTicks > 0) {
            // Rising edge (0 -> >0): принудительно ресетим, иначе после первой трансформации
            // controller остаётся в STOPPED с last=TRANSFORM, и setAnimation(TRANSFORM) ничего не делает.
            if (prev <= 0) {
                controller.forceAnimationReset();
            }
            state.setAnimation(TRANSFORM);
            return PlayState.CONTINUE;
        }

        // 2) Если предыдущая one-shot (attack/transform) ещё доигрывается - не перебиваем.
        if (controller.getCurrentAnimation() != null && !controller.hasAnimationFinished()) {
            return PlayState.CONTINUE;
        }

        // 3) Начало удара. swingTime растёт от 0 до swingDuration; ловим самое начало,
        //    чтобы анимация запускалась только на rising-edge, а не каждый тик пока swinging.
        if (player.swinging && player.swingTime <= 1) {
            controller.forceAnimationReset();
            state.setAnimation(ATTACK);
            return PlayState.CONTINUE;
        }

        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object instance) {
        // instance - текущий рендерящийся Entity (см. GeoReplacedEntityRenderer#getInstanceId).
        // Возвращаем его tickCount, чтобы loop-анимации шли независимо у каждого игрока.
        if (instance instanceof Entity e) {
            return e.tickCount;
        }
        return 0;
    }
}
