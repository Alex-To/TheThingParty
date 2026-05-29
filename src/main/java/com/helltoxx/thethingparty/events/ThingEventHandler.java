package com.helltoxx.thethingparty.events;

import net.minecraft.nbt.CompoundTag;
import com.helltoxx.thethingparty.capability.IThingPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.network.NetworkHandler;
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
                        attackerData.setWeaponLockTicks(2400);
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
                        data.addBiomass(20);

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

        // Декремент transformTicks и transformCooldownTicks каждый тик.
        // Sync шлём только когда счётчик дошёл до 0 - промежуточные значения клиенту не нужны
        // (transform играется по animation_length, cooldown проверяется только при запросе).
        player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {
            boolean needSync = false;

            if (data.getTransformTicks() > 0) {
                data.setTransformTicks(data.getTransformTicks() - 1);
                if (data.getTransformTicks() == 0) needSync = true;

                // Замораживаем игрока во время трансформации: обнуляем горизонтальную скорость
                // (Y оставляем — гравитация должна работать) и накладываем экстремальный slowness
                // как страховку от модифицированных клиентов. Прыжок блокируем JUMP -128.
                // duration = 3 тика, чтобы эффект гарантированно действовал до следующего тика
                // и не залипал после окончания трансформации.
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
                player.hurtMarked = true; // принудительная sync скорости клиенту
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 3, -128, false, false, false));
            }

            if (data.getTransformCooldownTicks() > 0) {
                data.setTransformCooldownTicks(data.getTransformCooldownTicks() - 1);
                if (data.getTransformCooldownTicks() == 0) needSync = true;
            }

            if (data.isMonsterForm()) {
                if (data.getMonsterTime() > 0) {
                    data.setMonsterTime(data.getMonsterTime() - 1);
                    int time = data.getMonsterTime();

                    // Дебаг: выводим в консоль каждую секунду
                    if (time % 20 == 0) {
                        System.out.println("[THE THING PARTY DEBUG] Таймер монстра: " + time);
                    }

                    // Вывод игроку на экран
                    if (time == 600) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[Таймер] Осталось 30 секунд..."), true);
                    } else if (time == 400) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Таймер] Осталось 20 секунд..."), true);
                    } else if (time == 200) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Таймер] Осталось 10 секунд!"), true);
                    }
                }

                // Срабатывание нуля ДОЛЖНО быть внутри проверки isMonsterForm()
                if (data.getMonsterTime() == 0) {
                    System.out.println("[THE THING PARTY DEBUG] ТАЙМЕР ВЫШЕЛ! Превращаем обратно.");
                    data.setMonsterForm(false);
                    // ВКЛЮЧАЕМ ФЛАГ СИНХРОНИЗАЦИИ ДЛЯ КЛИЕНТА
                    needSync = true;
                    // Звук превращения обратно
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CONVERTED, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[Таймер] Время вышло! Вы вернулись в человеческую форму."), true);
                }
            }

            if (needSync && player instanceof ServerPlayer sp) {
                NetworkHandler.syncToPlayer(sp);
            }
        });

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