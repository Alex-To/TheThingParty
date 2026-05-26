package com.helltoxx.thethingparty.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентские клавиши мода. KeyMapping регистрируется через {@code RegisterKeyMappingsEvent} (MOD bus).
 * Нажатие ловится в {@link ClientEvents#onClientTick}.
 */
@OnlyIn(Dist.CLIENT)
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.thethingparty";

    /** Трансформация Нечто (по умолчанию G). */
    public static final KeyMapping TRANSFORM = new KeyMapping(
            "key.thethingparty.transform",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    private KeyBindings() {}

    @Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {
        private Registrar() {}

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TRANSFORM);
        }
    }
}
