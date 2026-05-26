package com.helltoxx.thethingparty.command;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.network.NetworkHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Debug-команды управления ролью и формой конкретного игрока.
 * Регистрируется через {@link CommandRegistry#onCommandsRegister}.
 *
 *   /thingparty setrole &lt;thing|human&gt;
 *   /thingparty form    &lt;monster|human&gt;
 */
public final class RoleCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RoleCommand() {}

    public static void append(LiteralArgumentBuilder<CommandSourceStack> root) {
        root
                .then(Commands.literal("setrole")
                        .then(Commands.literal("thing")
                                .executes(ctx -> setPlayerRole(ctx, IThingPlayerData.Role.THING)))
                        .then(Commands.literal("human")
                                .executes(ctx -> setPlayerRole(ctx, IThingPlayerData.Role.HUMAN))))
                .then(Commands.literal("form")
                        .then(Commands.literal("monster")
                                .executes(ctx -> setMonsterForm(ctx, true)))
                        .then(Commands.literal("human")
                                .executes(ctx -> setMonsterForm(ctx, false))));
    }

    // Логика, которая выполняется при вводе команды
    private static int setPlayerRole(CommandContext<CommandSourceStack> context, IThingPlayerData.Role newRole) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("§c[The Thing Party] Команда должна быть выполнена игроком."));
            return 0;
        }

        var cap = player.getCapability(ThingPlayerProvider.THING_DATA);
        if (!cap.isPresent()) {
            LOGGER.warn("setrole: у игрока {} нет THING_DATA capability", player.getGameProfile().getName());
            context.getSource().sendFailure(Component.literal("§c[The Thing Party] Capability не прикреплено."));
            return 0;
        }

        cap.ifPresent(data -> {
            data.setRole(newRole);
            context.getSource().sendSuccess(() -> Component.literal("§a[The Thing Party] Твоя роль успешно изменена на: " + newRole.name()), false);
        });
        NetworkHandler.syncToPlayer(player);
        return 1;
    }

    /**
     * Тестовая команда для этапа 1 (рендер). Меняет флаг isMonsterForm у вызывающего игрока
     * и отправляет SyncThingDataPacket — без него клиент не узнает о смене (даже в single-player
     * у LocalPlayer и ServerPlayer независимые capability-инстансы).
     */
    private static int setMonsterForm(CommandContext<CommandSourceStack> context, boolean monsterForm) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("§c[The Thing Party] Команда должна быть выполнена игроком."));
            return 0;
        }

        var cap = player.getCapability(ThingPlayerProvider.THING_DATA);
        if (!cap.isPresent()) {
            LOGGER.warn("form: у игрока {} нет THING_DATA capability", player.getGameProfile().getName());
            context.getSource().sendFailure(Component.literal("§c[The Thing Party] Capability не прикреплено."));
            return 0;
        }

        cap.ifPresent(data -> {
            data.setMonsterForm(monsterForm);
            // 100 тиков = 5 секунд, под длину animation.thing.transform.
            // При обратном переходе (monster -> human) обнуляем, чтобы анимация не цеплялась.
            data.setTransformTicks(monsterForm ? 100 : 0);
            String label = monsterForm ? "Нечто (монстр)" : "Человек";
            context.getSource().sendSuccess(() -> Component.literal("§a[The Thing Party] Форма изменена: " + label), false);
        });
        NetworkHandler.syncToPlayer(player);
        return 1;
    }
}