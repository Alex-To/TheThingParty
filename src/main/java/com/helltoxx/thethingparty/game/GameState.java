package com.helltoxx.thethingparty.game;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Серверный singleton состояния игры. Не персистится: при рестарте сервера состояние сбрасывается
 * (для соревновательного матча это норма - игра либо доиграна, либо стартует заново).
 *
 * Все методы предполагают вызов с серверного потока.
 */
public final class GameState {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Длительности фаз (тики). 20 тиков = 1 секунда.
    public static final int HEAD_START_DURATION_TICKS = 20 * 60;       // 60 сек
    public static final int PARANOIA_DURATION_TICKS   = 20 * 60 * 10;  // 10 минут
    public static final int MIN_PLAYERS_TO_START      = 1;             // для теста; продакшен будет 4-8

    // Эвакуация
    public static final int EVACUATION_TIMER_DURATION_TICKS = 20 * 30; // 30 сек обратного отсчёта после выполнения задач
    public static final double EVACUATION_ZONE_RADIUS = 4.0;            // AABB-радиус вокруг блока
    public static final int DEFAULT_TASKS_REQUIRED = 3;

    private static final GameState INSTANCE = new GameState();
    public static GameState get() { return INSTANCE; }

    private GamePhase phase = GamePhase.LOBBY;
    private int phaseTicksRemaining = 0;
    private final Set<UUID> thingPlayers = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();

    // Прогресс задач и эвакуации
    private int tasksCompleted = 0;
    private int tasksRequired = DEFAULT_TASKS_REQUIRED;
    private BlockPos evacuationZonePos = null;
    private int evacuationTimerTicks = 0;   // >0 = идёт обратный отсчёт; 0 = не запущен либо истёк

    private GameState() {}

    public GamePhase getPhase() { return phase; }
    public int getPhaseTicksRemaining() { return phaseTicksRemaining; }
    public boolean isInProgress() { return phase == GamePhase.HEAD_START || phase == GamePhase.PARANOIA; }
    public boolean isThingAbilityBlocked() { return phase == GamePhase.HEAD_START; }
    public Set<UUID> getThingPlayers() { return Collections.unmodifiableSet(thingPlayers); }
    public Set<UUID> getAlivePlayers() { return Collections.unmodifiableSet(alivePlayers); }
    public int getTasksCompleted() { return tasksCompleted; }
    public int getTasksRequired() { return tasksRequired; }
    public int getEvacuationTimerTicks() { return evacuationTimerTicks; }
    public BlockPos getEvacuationZonePos() { return evacuationZonePos; }

    /**
     * Старт игры. Случайно раздаёт роли по формуле {@link #calculateThingCount}.
     * @param requestedThingCount если > 0, явно задаёт количество Нечто (дебаг). Иначе автоформула.
     * @return true если игра запущена, false если игроков мало или уже идёт.
     */
    public boolean startGame(MinecraftServer server, int requestedThingCount) {
        if (isInProgress()) {
            LOGGER.warn("startGame: игра уже идёт ({})", phase);
            return false;
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.size() < MIN_PLAYERS_TO_START) {
            LOGGER.warn("startGame: мало игроков {} < {}", players.size(), MIN_PLAYERS_TO_START);
            return false;
        }

        int thingCount = requestedThingCount > 0
                ? Math.min(requestedThingCount, Math.max(1, players.size() - 1))
                : calculateThingCount(players.size());

        Collections.shuffle(players);

        thingPlayers.clear();
        alivePlayers.clear();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            UUID id = p.getUUID();
            alivePlayers.add(id);

            IThingPlayerData.Role role = i < thingCount ? IThingPlayerData.Role.THING : IThingPlayerData.Role.HUMAN;
            applyRole(p, role);

            if (role == IThingPlayerData.Role.THING) {
                thingPlayers.add(id);
                p.sendSystemMessage(Component.literal("Ты — НЕЧТО. Ассимилируй экипаж или сбеги на вертолёте.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            } else {
                p.sendSystemMessage(Component.literal("Ты — Экипаж. Выживи и вызови вертолёт.")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }

            NetworkHandler.syncToPlayer(p);
        }

        phase = GamePhase.HEAD_START;
        phaseTicksRemaining = HEAD_START_DURATION_TICKS;
        tasksCompleted = 0;
        evacuationTimerTicks = 0;
        broadcast(server, Component.literal("[Игра] Фаза Форы. " + (HEAD_START_DURATION_TICKS / 20) + " сек.")
                .withStyle(ChatFormatting.YELLOW));
        LOGGER.info("Game started: {} players, {} things, tasksRequired={}",
                players.size(), thingCount, tasksRequired);
        return true;
    }

    /**
     * Остановка игры вручную (admin) или после завершения матча.
     * Сбрасывает фазу в LOBBY, очищает живых/Нечто. Capability игроков НЕ сбрасывается (роль/биомасса).
     */
    public void stopGame(MinecraftServer server, String reason) {
        if (phase == GamePhase.LOBBY) return;

        phase = GamePhase.LOBBY;
        phaseTicksRemaining = 0;
        thingPlayers.clear();
        alivePlayers.clear();
        tasksCompleted = 0;
        evacuationTimerTicks = 0;

        broadcast(server, Component.literal("[Игра] Остановлена. " + reason).withStyle(ChatFormatting.GRAY));
        LOGGER.info("Game stopped: {}", reason);
    }

    /**
     * Завершение игры с фиксированным исходом.
     */
    public void endGame(MinecraftServer server, Component message) {
        if (phase == GamePhase.LOBBY || phase == GamePhase.ENDED) return;

        phase = GamePhase.ENDED;
        phaseTicksRemaining = 0;
        broadcast(server, message);
        LOGGER.info("Game ended: {}", message.getString());
    }

    /**
     * Серверный tick. Декрементит таймер фазы, переключает HEAD_START -> PARANOIA,
     * по тайм-ауту PARANOIA -> победа Нечто.
     */
    public void tick(MinecraftServer server) {
        if (!isInProgress()) return;

        // Таймер эвакуации тикает независимо от фазы (но только в PARANOIA — задачи доступны только там).
        if (evacuationTimerTicks > 0) {
            evacuationTimerTicks--;
            // Уведомления-обратные отсчёты на 30/20/10/5/4/3/2/1 секунду
            int secLeft = evacuationTimerTicks / 20;
            if (evacuationTimerTicks % 20 == 0 && (secLeft == 30 || secLeft == 20 || secLeft == 10 || (secLeft > 0 && secLeft <= 5))) {
                broadcast(server, Component.literal("[Эвакуация] До вылета " + secLeft + " сек.")
                        .withStyle(ChatFormatting.GOLD));
            }
            if (evacuationTimerTicks == 0) {
                triggerEvacuation(server);
                return; // endGame уже всё переключил
            }
        }

        phaseTicksRemaining--;
        if (phaseTicksRemaining > 0) return;

        switch (phase) {
            case HEAD_START -> {
                phase = GamePhase.PARANOIA;
                phaseTicksRemaining = PARANOIA_DURATION_TICKS;
                broadcast(server, Component.literal("[Игра] Фаза Паранойи. Способности Нечто разблокированы.")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            case PARANOIA -> {
                // Тайм-аут: генераторы замёрзли -> победа Нечто.
                endGame(server, Component.literal("[Победа Нечто] Время вышло. Генераторы замерзли.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            }
            default -> { /* no-op */ }
        }
    }

    // ============================== ЗАДАЧИ ==============================

    /** Установить требуемое количество задач (до старта или во время игры через команду). */
    public void setTasksRequired(int n) {
        tasksRequired = Math.max(1, n);
    }

    /**
     * Засчитать одну выполненную задачу. Если набрали достаточно — запускает таймер эвакуации.
     * Возвращает true если задача была засчитана (есть ещё что засчитывать в этой игре).
     */
    public boolean notifyTaskComplete(MinecraftServer server) {
        if (!isInProgress()) return false;
        if (tasksCompleted >= tasksRequired) return false;

        tasksCompleted++;
        broadcast(server, Component.literal("[Задачи] " + tasksCompleted + "/" + tasksRequired + " выполнено.")
                .withStyle(ChatFormatting.GREEN));

        if (tasksCompleted >= tasksRequired && evacuationTimerTicks == 0) {
            startEvacuationTimer(server);
        }
        return true;
    }

    private void startEvacuationTimer(MinecraftServer server) {
        evacuationTimerTicks = EVACUATION_TIMER_DURATION_TICKS;
        Component msg = evacuationZonePos != null
                ? Component.literal("[Эвакуация] Все задачи выполнены! Вертолёт прибывает через "
                        + (EVACUATION_TIMER_DURATION_TICKS / 20) + " сек. Зона: "
                        + evacuationZonePos.getX() + ", " + evacuationZonePos.getY() + ", " + evacuationZonePos.getZ())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : Component.literal("[Эвакуация] Все задачи выполнены, но зона эвакуации не размещена! Поставьте блок Evacuation Zone.")
                        .withStyle(ChatFormatting.RED);
        broadcast(server, msg);
    }

    // ============================== ЭВАКУАЦИЯ ==============================

    /** Регистрация позиции блока эвакуации (вызывается из EvacuationZoneBlock#onPlace). */
    public void setEvacuationZone(BlockPos pos) {
        evacuationZonePos = pos.immutable();
        LOGGER.info("Evacuation zone set to {}", evacuationZonePos);
    }

    /** Снять регистрацию, только если pos совпадает с активной (чтобы повторная установка не очистила). */
    public void clearEvacuationZoneIfMatches(BlockPos pos) {
        if (evacuationZonePos != null && evacuationZonePos.equals(pos)) {
            evacuationZonePos = null;
            LOGGER.info("Evacuation zone cleared at {}", pos);
        }
    }

    /**
     * Триггерится по истечению таймера эвакуации.
     * Определяет, кто стоит в радиусе зоны, и выдаёт исход:
     *  - Нечто (в форме человека) в зоне → Победа Нечто (Инфильтрация), доминирует над Спасением (см. GDD).
     *  - Иначе любой HUMAN в зоне → Победа Экипажа (Спасение).
     *  - Никого живого в зоне → Победа Нечто (Эвакуация без выживших).
     */
    private void triggerEvacuation(MinecraftServer server) {
        if (evacuationZonePos == null) {
            LOGGER.warn("triggerEvacuation: evacuationZonePos == null");
            return;
        }

        AABB zone = new AABB(evacuationZonePos).inflate(EVACUATION_ZONE_RADIUS);
        List<ServerPlayer> playersInZone = new ArrayList<>();
        boolean thingInfiltrated = false;
        boolean humanEvacuated = false;

        for (UUID aliveId : alivePlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(aliveId);
            if (p == null) continue;
            if (!zone.contains(p.position())) continue;

            playersInZone.add(p);
            var capOpt = p.getCapability(ThingPlayerProvider.THING_DATA).resolve();
            if (capOpt.isEmpty()) continue;
            IThingPlayerData data = capOpt.get();

            if (data.getRole() == IThingPlayerData.Role.THING && !data.isMonsterForm()) {
                thingInfiltrated = true;
            } else if (data.getRole() == IThingPlayerData.Role.HUMAN) {
                humanEvacuated = true;
            }
        }

        // Воспроизводим "взлёт" для всех в зоне (placeholder-эффекты)
        for (ServerPlayer p : playersInZone) {
            evacuatePlayer(p);
        }

        if (thingInfiltrated) {
            endGame(server, Component.literal("[Победа Нечто] Инфильтрация: Нечто улетело вместе с экипажем!")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else if (humanEvacuated) {
            endGame(server, Component.literal("[Победа Экипажа] Спасение: вертолёт увёз выживших!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        } else {
            endGame(server, Component.literal("[Победа Нечто] Вертолёт улетел пустым.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
    }

    /**
     * Hook "игрок улетает". Сейчас — заглушка: levitation + неуязвимость на 3 сек.
     * Когда будет модель/анимация — заменим тело: спавн entity-вертолёта, startRiding и т.п.
     */
    private static void evacuatePlayer(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 4, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
        p.sendSystemMessage(Component.literal("Ты эвакуирован!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        // TODO(animation): сюда заменить заглушку на:
        //  - спавн entity Helicopter с GeckoLib моделью у позиции игрока
        //  - p.startRiding(helicopter)
        //  - helicopter.triggerAnim("takeoff")
    }

    /**
     * Уведомление о смерти игрока. Проверяет условия победы:
     *  1. ДЕДУКЦИЯ (мгновенная победа Экипажа) - Нечто умерло в человеческой форме.
     *  2. АССИМИЛЯЦИЯ (победа Нечто) - не осталось живых HUMAN.
     *  3. BACKUP (победа Экипажа) - не осталось живых THING (например, всех сожгли огнемётом).
     */
    public void notifyPlayerDeath(MinecraftServer server, ServerPlayer player) {
        if (!isInProgress()) return;

        UUID id = player.getUUID();
        if (!alivePlayers.remove(id)) return; // не участвует в текущем матче

        var capOpt = player.getCapability(ThingPlayerProvider.THING_DATA).resolve();
        IThingPlayerData.Role deadRole = capOpt.map(IThingPlayerData::getRole).orElse(IThingPlayerData.Role.HUMAN);
        boolean wasMonsterForm = capOpt.map(IThingPlayerData::isMonsterForm).orElse(false);

        // 1. Нечто убит в человеческой форме -> моментальная победа Экипажа (см. GDD).
        if (deadRole == IThingPlayerData.Role.THING && !wasMonsterForm) {
            endGame(server, Component.literal("[Победа Экипажа] Нечто разоблачено и убито в человеческой форме!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            return;
        }

        // Считаем живых по ролям.
        boolean thingsAlive = false;
        boolean humansAlive = false;
        for (UUID aliveId : alivePlayers) {
            ServerPlayer alive = server.getPlayerList().getPlayer(aliveId);
            if (alive == null) continue;
            var aliveCap = alive.getCapability(ThingPlayerProvider.THING_DATA).resolve();
            if (aliveCap.isEmpty()) continue;
            IThingPlayerData.Role role = aliveCap.get().getRole();
            if (role == IThingPlayerData.Role.THING) thingsAlive = true;
            else humansAlive = true;
            if (thingsAlive && humansAlive) break; // ни одна из проверок ниже не сработает
        }

        if (!thingsAlive) {
            endGame(server, Component.literal("[Победа Экипажа] Все Нечто уничтожены!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        } else if (!humansAlive) {
            endGame(server, Component.literal("[Победа Нечто] Экипаж ассимилирован.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
    }

    private static int calculateThingCount(int playerCount) {
        // 1 Нечто на каждые 4 игрока, минимум 1, не больше N-1.
        int byFormula = Math.max(1, playerCount / 4);
        return Math.min(byFormula, Math.max(1, playerCount - 1));
    }

    private static void applyRole(ServerPlayer p, IThingPlayerData.Role role) {
        p.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {
            data.setRole(role);
            data.setMonsterForm(false);
            data.setTransformTicks(0);
            data.setTransformCooldownTicks(0);
            data.setBiomass(0);
            data.setWeaponLockTicks(0);
        });
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
