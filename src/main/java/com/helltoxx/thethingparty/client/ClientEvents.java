package com.helltoxx.thethingparty.client;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские обработчики событий рендера. Логика подмены модели игрока на Нечто-монстра.
 *
 * Подход (вариант C из плана): отменяем стандартный {@code RenderPlayerEvent.Pre} при
 * {@code isMonsterForm == true} и вручную вызываем {@link ThingReplacedRenderer}.
 *
 * Capability читается с {@code AbstractClientPlayer} — на клиенте флаг обновляется через
 * {@code SyncThingDataPacket}, который сервер шлёт после изменений (см. RoleCommand,
 * ModEvents.onPlayerLoggedIn/Respawn/ChangedDimension).
 */
@Mod.EventBusSubscriber(modid = "thethingparty", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static ThingReplacedRenderer renderer;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();

        boolean monsterForm = player.getCapability(ThingPlayerProvider.THING_DATA)
                .map(IThingPlayerData::isMonsterForm)
                .orElse(false);

        if (!monsterForm) return;

        event.setCanceled(true);

        if (renderer == null) {
            renderer = new ThingReplacedRenderer(buildContext());
        }

        renderer.render(
                player,
                player.getYRot(),
                event.getPartialTick(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight()
        );
    }

    /**
     * EntityRendererProvider.Context из EntityRenderDispatcher не достать публично,
     * поэтому собираем его вручную из доступных Minecraft-инстансов.
     */
    private static EntityRendererProvider.Context buildContext() {
        Minecraft mc = Minecraft.getInstance();
        return new EntityRendererProvider.Context(
                mc.getEntityRenderDispatcher(),
                mc.getItemRenderer(),
                mc.getBlockRenderer(),
                mc.getEntityRenderDispatcher().getItemInHandRenderer(),
                mc.getResourceManager(),
                mc.getEntityModels(),
                mc.font
        );
    }
}
