package com.helltoxx.thethingparty.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Регистрируем класс в главной шине событий Forge
@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ThingEventHandler {

    // Событие: кто-то или что-то получает урон
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // Нас интересует только ситуация, когда урон получает Игрок
        if (!(event.getEntity() instanceof Player targetPlayer)) return;

        // Получаем наши кастомные данные (Capability) игрока-цели
        targetPlayer.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(targetData -> {

            // --- 1. ЛОГИКА ДЛЯ НЕЧТО В ФОРМЕ МОНСТРА ---
            if (targetData.isMonsterForm()) {

                // Слабость к огню (Огнемет) - х2 урон. Используем DamageTypeTags
                if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || event.getSource().getMsgId().contains("fire")) {
                    event.setAmount(event.getAmount() * 2.0f);
                    return; // Пропускаем остальную логику
                }

                // Перехват пуль (TaC:Z). Используем проверку на снаряды через теги
                if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) || event.getSource().getMsgId().contains("tacz") || event.getSource().getMsgId().contains("bullet")) {
                    event.setCanceled(true); // Отменяем потерю HP

                    // Реализация Knockback (отбрасывания)
                    if (event.getSource().getEntity() != null) {
                        // Вычисляем вектор от стрелка к монстру
                        Vec3 knockbackDir = targetPlayer.position().subtract(event.getSource().getEntity().position()).normalize();
                        // Сила отбрасывания по осям X и Z
                        targetPlayer.knockback(1.5, -knockbackDir.x, -knockbackDir.z);
                    }

                    // Накладываем замедление на монстра на 1 секунду (20 тиков)
                    targetPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
                    return;
                }
            }

            // --- 2. ЛОГИКА FRIENDLY FIRE (Человек стреляет в Человека) ---
            if (event.getSource().getEntity() instanceof Player attackerPlayer) {
                attackerPlayer.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(attackerData -> {

                    // Если оба игрока - люди (и цель не в форме монстра)
                    if (attackerData.getRole() == IThingPlayerData.Role.HUMAN &&
                            targetData.getRole() == IThingPlayerData.Role.HUMAN &&
                            !targetData.isMonsterForm()) {

                        // Накладываем статус вины (Weapon Lock) на стрелявшего: 3600 тиков = 3 минуты
                        attackerData.setWeaponLockTicks(3600);
                        // Урон при этом не отменяем — жертва пострадает
                    }
                });
            }
        });
    }

    // --- 3. ПОЕДАНИЕ ТРУПОВ (Взаимодействие с сущностями) ---
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        net.minecraft.world.entity.Entity target = event.getTarget();

        // Выполняем логику начисления биомассы только на сервере
        if (player.level().isClientSide()) return;

        player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {

            // Если игрок - Нечто
            if (data.getRole() == IThingPlayerData.Role.THING) {

                //Ищем сущность, похожую на труп
                if (target instanceof de.maxhenkel.corpse.entities.CorpseEntity corpse) {

                    // Получаем скрытую дату трупа
                    net.minecraft.nbt.CompoundTag targetNbt = corpse.getPersistentData();

                    if (!targetNbt.getBoolean("ThingEaten")) {
                        // Ставим метку "съедено"
                        targetNbt.putBoolean("ThingEaten", true);

                        // Выдаем Нечто биомассу
                        data.addBiomass(15);

                        // Воспроизводим звук чавканья (пока ванильный)
                        player.level().playSound(null, corpse.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_BURP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                        event.setCanceled(true);
                    }
                }
            }
        });
    }

    // Событие: вызывается каждый тик (20 раз в секунду) для каждого игрока
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // ОПТИМИЗАЦИЯ TPS: Выполняем логику только на сервере и только в конце тика
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        // Выполняем проверку не каждый тик, а раз в 20 тиков (каждую 1 секунду), чтобы не грузить сервер
        if (player.tickCount % 20 == 0) {
            // --- ЛОГИКА ТЯЖЕЛЫХ ПРЕДМЕТОВ ---
            boolean hasHeavyItem = false;

            // Проверяем, держит ли игрок в любой руке нашу тяжелую канистру
            if (player.getMainHandItem().getItem() == com.helltoxx.thethingparty.init.ItemInit.HEAVY_CANISTER.get() ||
                    player.getOffhandItem().getItem() == com.helltoxx.thethingparty.init.ItemInit.HEAVY_CANISTER.get()) {
                hasHeavyItem = true;
            }

            // Если держит тяжелый предмет, накладываем сильное замедление (Slowness 3) на 2 секунды (40 тиков)
            // И запрещаем прыгать (эффект прыгучести с отрицательным значением блокирует прыжок)
            if (hasHeavyItem) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, -128, false, false, false));
                // TODO: Добавить блокировку стрельбы из TaC:Z
            }

            player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {

                // Уменьшаем таймер блокировки оружия
                if (data.getWeaponLockTicks() > 0) {
                    data.setWeaponLockTicks(data.getWeaponLockTicks() - 20); // Отнимаем прошедшую секунду

                    if (data.getWeaponLockTicks() < 0) {
                        data.setWeaponLockTicks(0);
                    }

                    // TODO: Здесь позже мы добавим отправку S2C пакета, чтобы обновить UI штрафа у игрока
                }
            });
        }
        // --- ЛОГИКА МЕТЕЛИ (AABB Зоны) ---
        // TODO: Проверять координаты игрока. Если он на улице - применять эффекты холода/замедления.
    }
}