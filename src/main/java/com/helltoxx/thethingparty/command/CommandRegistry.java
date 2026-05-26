package com.helltoxx.thethingparty.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Единая точка сборки команд мода под root {@code /thingparty}.
 * Каждая отдельная команда экспортирует static {@code append(root)} - сюда мы их подвешиваем.
 */
@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandRegistry {
    private CommandRegistry() {}

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("thingparty")
                .requires(source -> source.hasPermission(2));

        RoleCommand.append(root);
        GameCommand.append(root);

        event.getDispatcher().register(root);
    }
}
