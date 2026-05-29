package com.helltoxx.thethingparty.entity;

import com.helltoxx.thethingparty.client.HelicopterGeoModel;
import com.helltoxx.thethingparty.init.EntityInit;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Entity вертолёта эвакуации, рендерящийся через GeckoLib.
 *
 * Жизненный цикл управляется сервером:
 *  1. LANDING  — анимация ~7 сек, затем переключается на WAITING.
 *  2. WAITING  — idle loop.
 *  3. TAKEOFF  — анимация ~3.5 сек, затем despawn.
 *
 * Состояние синхронизируется через {@link SynchedEntityData}; клиент перечитывает
 * {@code currentState} в animation predicate.
 */
public class HelicopterEntity extends Entity implements GeoAnimatable {

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(
            HelicopterEntity.class, EntityDataSerializers.INT);

    public enum State {
        LANDING,
        WAITING,
        TAKEOFF
    }

    private static final RawAnimation ANIM_LANDING  = RawAnimation.begin().thenPlay("animation.helicopter.landing");
    private static final RawAnimation ANIM_IDLE     = RawAnimation.begin().thenLoop("animation.helicopter.idle");
    private static final RawAnimation ANIM_TAKEOFF  = RawAnimation.begin().thenPlay("animation.helicopter.takeof");

    private static final int LANDING_DURATION_TICKS = 20 * 7;   // 7 сек
    private static final int TAKEOFF_DURATION_TICKS = 20 * 4;   // 3.5 сек, округлено

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private long stateStartTick = 0;

    public HelicopterEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Спавн вертолёта со стартовой анимацией LANDING. */
    public static HelicopterEntity create(Level level) {
        return new HelicopterEntity(EntityInit.HELICOPTER.get(), level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, State.LANDING.ordinal());
    }

    public State getState() {
        return State.values()[this.entityData.get(DATA_STATE)];
    }

    public void setState(State state) {
        this.entityData.set(DATA_STATE, state.ordinal());
        this.stateStartTick = this.tickCount;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        long elapsed = this.tickCount - this.stateStartTick;
        switch (getState()) {
            case LANDING -> {
                if (elapsed >= LANDING_DURATION_TICKS) {
                    setState(State.WAITING);
                }
            }
            case TAKEOFF -> {
                if (elapsed >= TAKEOFF_DURATION_TICKS) {
                    this.remove(Entity.RemovalReason.DISCARDED);
                }
            }
            case WAITING -> {
                // idle — ждём пассажиров
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        // not persisted
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        // not persisted
    }

    // ============================== GeoAnimatable ==============================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<HelicopterEntity> state) {
        switch (getState()) {
            case LANDING -> state.setAnimation(ANIM_LANDING);
            case TAKEOFF -> state.setAnimation(ANIM_TAKEOFF);
            case WAITING -> state.setAnimation(ANIM_IDLE);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object instance) {
        return this.tickCount;
    }

    // ============================== Renderer ==============================
    @OnlyIn(Dist.CLIENT)
    public static class HelicopterRenderer extends software.bernie.geckolib.renderer.GeoEntityRenderer<HelicopterEntity> {
        public HelicopterRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
            super(context, new HelicopterGeoModel());
            withScale(3.0f);
        }
    }
}
