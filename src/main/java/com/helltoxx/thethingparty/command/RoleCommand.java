package com.helltoxx.thethingparty.command;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Аннотация автоматически зарегистрирует этот класс в шине событий Forge
@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RoleCommand {

    // Событие регистрации команд сервера
    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Строим структуру команды: /thingparty setrole <thing/human>
        dispatcher.register(Commands.literal("thingparty")
                // requires(2) означает, что команду могут вводить только операторы сервера (или игроки с читами)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("setrole")
                        .then(Commands.literal("thing")
                                .executes(context -> setPlayerRole(context, IThingPlayerData.Role.THING)))
                        .then(Commands.literal("human")
                                .executes(context -> setPlayerRole(context, IThingPlayerData.Role.HUMAN)))
                )
        );
    }

    // Логика, которая выполняется при вводе команды
    private static int setPlayerRole(CommandContext<CommandSourceStack> context, IThingPlayerData.Role newRole) {
        try {
            // Получаем игрока, который ввел команду
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Обращаемся к его Capability
            player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {
                data.setRole(newRole);

                // Отправляем игроку зеленое сообщение в чат об успешной смене роли
                context.getSource().sendSuccess(() -> Component.literal("§a[The Thing Party] Твоя роль успешно изменена на: " + newRole.name()), false);
            });

            return 1; // 1 = команда выполнена успешно
        } catch (Exception e) {
            return 0; // 0 = произошла ошибка
        }
    }
}