package com.helltoxx.thethingparty.init;

import com.helltoxx.thethingparty.entity.HelicopterEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Регистрация кастомных entity. Helicopter — не-Mob (no AI, no physics),
 * поэтому категория MISC. Рендерер подключается в ClientSetup via
 * RenderingRegistry.registerEntityRenderer.
 */
public final class EntityInit {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, "thethingparty");

    public static final RegistryObject<EntityType<HelicopterEntity>> HELICOPTER = ENTITIES.register(
            "helicopter",
            () -> EntityType.Builder.<HelicopterEntity>of(HelicopterEntity::new, MobCategory.MISC)
                    .sized(6.0f, 3.0f)          // ширина/высота коллизии (ориентир, noPhysics=true)
                    .clientTrackingRange(64)   // отслеживание для синхронизации позиции
                    .fireImmune()              // не горит
                    .noSummon()                // нельзя заспавнить командой /summon ванильной
                    .build("helicopter")
    );
}