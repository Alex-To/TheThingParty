package com.helltoxx.thethingparty.command;

import com.helltoxx.thethingparty.game.GamePhase;
import com.helltoxx.thethingparty.game.GameState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.StringJoiner;
import java.util.UUID;

/**
 * Команды управления матчем:
 *
 *   /thingparty game start [thingCount]   - запустить игру (опционально с явным числом Нечто)
 *   /thingparty game stop                 - остановить игру
 *   /thingparty game status               - текущая фаза, остаток времени, список Нечто
 */
public final class GameCommand {
    private GameCommand() {}

    public static void append(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("game")
                .then(Commands.literal("start")
                        .executes(ctx -> startGame(ctx, 0))
                        .then(Commands.argument("thingCount", IntegerArgumentType.integer(1))
                                .executes(ctx -> startGame(ctx, IntegerArgumentType.getInteger(ctx, "thingCount")))))
                .then(Commands.literal("stop")
                        .executes(GameCommand::stopGame))
                .then(Commands.literal("status")
                        .executes(GameCommand::status))
                .then(Commands.literal("task")
                        .then(Commands.literal("complete")
                                .executes(GameCommand::taskComplete))
                        .then(Commands.literal("setrequired")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                        .executes(ctx -> taskSetRequired(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("zone")
                        .then(Commands.literal("where")
                                .executes(GameCommand::zoneWhere))
                        .then(Commands.literal("tp")
                                .executes(GameCommand::zoneTp))));
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx, int thingCount) {
        MinecraftServer server = ctx.getSource().getServer();
        boolean ok = GameState.get().startGame(server, thingCount);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("[The Thing Party] Не удалось запустить игру (мало игроков или уже идёт).")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> ctx) {
        GameState.get().stopGame(ctx.getSource().getServer(), "по команде администратора");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        GameState gs = GameState.get();
        GamePhase phase = gs.getPhase();
        int sec = gs.getPhaseTicksRemaining() / 20;

        StringJoiner things = new StringJoiner(", ");
        MinecraftServer server = ctx.getSource().getServer();
        for (UUID id : gs.getThingPlayers()) {
            var p = server.getPlayerList().getPlayer(id);
            things.add(p != null ? p.getGameProfile().getName() : id.toString().substring(0, 8));
        }

        BlockPos zone = gs.getEvacuationZonePos();
        String zoneStr = zone == null ? "не задана" : (zone.getX() + ", " + zone.getY() + ", " + zone.getZ());
        int evacSec = gs.getEvacuationTimerTicks() / 20;

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[The Thing Party] Фаза: " + phase
                        + ", осталось: " + sec + " сек, Нечто: ["
                        + (things.length() == 0 ? "—" : things) + "], живых: "
                        + gs.getAlivePlayers().size()
                        + ", задачи: " + gs.getTasksCompleted() + "/" + gs.getTasksRequired()
                        + ", эвакуация: " + (evacSec > 0 ? evacSec + " сек" : "—")
                        + ", зона: " + zoneStr
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int taskComplete(CommandContext<CommandSourceStack> ctx) {
        boolean ok = GameState.get().notifyTaskComplete(ctx.getSource().getServer());
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("[The Thing Party] Не удалось засчитать задачу (игра не идёт или все уже выполнены).")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int taskSetRequired(CommandContext<CommandSourceStack> ctx, int n) {
        GameState.get().setTasksRequired(n);
        ctx.getSource().sendSuccess(() -> Component.literal("[The Thing Party] Установлено задач для эвакуации: " + n)
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int zoneWhere(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = GameState.get().getEvacuationZonePos();
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("[The Thing Party] Зона эвакуации не размещена. Поставь блок Evacuation Zone.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[The Thing Party] Зона эвакуации: "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int zoneTp(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = GameState.get().getEvacuationZonePos();
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("[The Thing Party] Зона эвакуации не размещена.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("[The Thing Party] Команда должна быть выполнена игроком.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        return 1;
    }
}
