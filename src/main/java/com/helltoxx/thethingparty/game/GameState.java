package com.helltoxx.thethingparty.game;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.entity.HelicopterEntity;
import com.helltoxx.thethingparty.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
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

    // Смещение модели вертолёта от центра блока зоны эвакуации (в блоках).
    // Зона сама не двигается — это только визуальная посадочная позиция вертолёта.
    public static final double HELICOPTER_OFFSET_X = -3.0;
    public static final double HELICOPTER_OFFSET_Y = 0.0;
    public static final double HELICOPTER_OFFSET_Z = 0.0;

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

    // Активный вертолёт эвакуации
    private HelicopterEntity activeHelicopter = null;
    // После взлёта вертолёта — задержка до объявления исхода (ждём окончание анимации takeoff)
    private int takeoffOutcomeDelayTicks = 0;
    // Сообщение об исходе, зафиксированное в момент triggerEvacuation; отыграется в determineEvacuationOutcome.
    private Component pendingEvacuationOutcome = null;

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
    public boolean hasActiveHelicopter() { return activeHelicopter != null && !activeHelicopter.isRemoved(); }

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
        takeoffOutcomeDelayTicks = 0;
        pendingEvacuationOutcome = null;
        if (activeHelicopter != null && !activeHelicopter.isRemoved()) {
            activeHelicopter.remove(Entity.RemovalReason.DISCARDED);
            activeHelicopter = null;
        }

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
                // не return — параллельно может тикать takeoffOutcomeDelayTicks (запускается в triggerEvacuation)
            }
        }

        // Отложенный исход после старта анимации взлёта.
        if (takeoffOutcomeDelayTicks > 0) {
            takeoffOutcomeDelayTicks--;
            if (takeoffOutcomeDelayTicks == 0) {
                determineEvacuationOutcome(server);
                return;
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
        if (evacuationZonePos == null) {
            broadcast(server, Component.literal("[Эвакуация] Все задачи выполнены, но зона эвакуации не размещена! Поставьте блок Evacuation Zone.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // Спавним вертолёт в LANDING. Анимация ~7 сек, потом entity сам перейдёт в WAITING.
        spawnHelicopter(server);

        broadcast(server, Component.literal("[Эвакуация] Все задачи выполнены! Вертолёт прибывает через "
                        + (EVACUATION_TIMER_DURATION_TICKS / 20) + " сек. Зона: "
                        + evacuationZonePos.getX() + ", " + evacuationZonePos.getY() + ", " + evacuationZonePos.getZ())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /**
     * Спавнит вертолёт точно над зоной эвакуации (анимация landing двигает его сама).
     */
    private void spawnHelicopter(MinecraftServer server) {
        if (evacuationZonePos == null) return;
        // Если вертолёт уже летает — переиспользуем
        if (activeHelicopter != null && !activeHelicopter.isRemoved()) return;

        ServerLevel overworld = server.overworld();
        HelicopterEntity heli = HelicopterEntity.create(overworld);
        heli.setPos(
                evacuationZonePos.getX() + 0.5 + HELICOPTER_OFFSET_X,
                evacuationZonePos.getY() + 1.0 + HELICOPTER_OFFSET_Y,
                evacuationZonePos.getZ() + 0.5 + HELICOPTER_OFFSET_Z
        );
        heli.setState(HelicopterEntity.State.LANDING);
        overworld.addFreshEntity(heli);
        activeHelicopter = heli;
        LOGGER.info("Helicopter spawned at {}", evacuationZonePos);
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
     * В момент таймера=0 фиксирует исход (кто в зоне), переводит эвакуированных в spectator,
     * запускает анимацию TAKEOFF и отложенный {@link #determineEvacuationOutcome}, который
     * объявит уже зафиксированный исход после окончания анимации.
     */
    private void triggerEvacuation(MinecraftServer server) {
        if (evacuationZonePos == null) {
            LOGGER.warn("triggerEvacuation: evacuationZonePos == null");
            return;
        }

        // Фиксируем "кто в зоне" прямо сейчас — после spectator игроки могут разлететься,
        // и AABB-проверка через 4 сек дала бы неверный результат.
        AABB zone = new AABB(evacuationZonePos).inflate(EVACUATION_ZONE_RADIUS);
        boolean thingInfiltrated = false;
        boolean humanEvacuated = false;
        List<ServerPlayer> evacuatedPlayers = new ArrayList<>();

        for (UUID aliveId : alivePlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(aliveId);
            if (p == null) continue;
            if (!zone.contains(p.position())) continue;

            evacuatedPlayers.add(p);

            var capOpt = p.getCapability(ThingPlayerProvider.THING_DATA).resolve();
            if (capOpt.isEmpty()) continue;
            IThingPlayerData data = capOpt.get();

            if (data.getRole() == IThingPlayerData.Role.THING && !data.isMonsterForm()) {
                thingInfiltrated = true;
            } else if (data.getRole() == IThingPlayerData.Role.HUMAN) {
                humanEvacuated = true;
            }
        }

        // Перевод в spectator всех, кто был в зоне на момент таймера=0.
        for (ServerPlayer p : evacuatedPlayers) {
            p.setGameMode(GameType.SPECTATOR);
        }

        // Сохраняем исход для отложенного объявления.
        if (thingInfiltrated) {
            pendingEvacuationOutcome = Component.literal("[Победа Нечто] Инфильтрация: Нечто улетело вместе с экипажем!")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        } else if (humanEvacuated) {
            pendingEvacuationOutcome = Component.literal("[Победа Экипажа] Спасение: вертолёт увёз выживших!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        } else {
            pendingEvacuationOutcome = Component.literal("[Победа Нечто] Вертолёт улетел пустым.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        }

        if (activeHelicopter != null && !activeHelicopter.isRemoved()) {
            activeHelicopter.setState(HelicopterEntity.State.TAKEOFF);
        } else {
            LOGGER.warn("triggerEvacuation: вертолёт не заспавнен — определяем исход немедленно");
            determineEvacuationOutcome(server);
            return;
        }

        // Анимация takeoff длится ~3.5 сек; даём чуть больше, чтобы entity успел despawn-нуться.
        takeoffOutcomeDelayTicks = 20 * 4;
        broadcast(server, Component.literal("[Эвакуация] Вертолёт взлетает!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /**
     * Финальное объявление исхода после окончания анимации takeoff.
     * Сам исход и spectator уже зафиксированы в {@link #triggerEvacuation}.
     */
    private void determineEvacuationOutcome(MinecraftServer server) {
        // Зачищаем активный вертолёт, если ещё не despawn-нулся.
        if (activeHelicopter != null && !activeHelicopter.isRemoved()) {
            activeHelicopter.remove(Entity.RemovalReason.DISCARDED);
        }
        activeHelicopter = null;

        Component outcome = pendingEvacuationOutcome;
        pendingEvacuationOutcome = null;
        if (outcome == null) {
            LOGGER.warn("determineEvacuationOutcome: pendingEvacuationOutcome == null — исход не зафиксирован");
            return;
        }
        endGame(server, outcome);
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
