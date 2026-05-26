package com.helltoxx.thethingparty.game;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

    private static final GameState INSTANCE = new GameState();
    public static GameState get() { return INSTANCE; }

    private GamePhase phase = GamePhase.LOBBY;
    private int phaseTicksRemaining = 0;
    private final Set<UUID> thingPlayers = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();

    private GameState() {}

    public GamePhase getPhase() { return phase; }
    public int getPhaseTicksRemaining() { return phaseTicksRemaining; }
    public boolean isInProgress() { return phase == GamePhase.HEAD_START || phase == GamePhase.PARANOIA; }
    public boolean isThingAbilityBlocked() { return phase == GamePhase.HEAD_START; }
    public Set<UUID> getThingPlayers() { return Collections.unmodifiableSet(thingPlayers); }
    public Set<UUID> getAlivePlayers() { return Collections.unmodifiableSet(alivePlayers); }

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
        broadcast(server, Component.literal("[Игра] Фаза Форы. " + (HEAD_START_DURATION_TICKS / 20) + " сек.")
                .withStyle(ChatFormatting.YELLOW));
        LOGGER.info("Game started: {} players, {} things", players.size(), thingCount);
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
