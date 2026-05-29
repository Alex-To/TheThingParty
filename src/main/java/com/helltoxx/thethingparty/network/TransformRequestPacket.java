package com.helltoxx.thethingparty.network;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import com.helltoxx.thethingparty.game.GameState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: игрок-Нечто запрашивает трансформацию (в монстра или обратно в человека).
 * Сервер валидирует роль, кулдаун, активную трансформацию и (для прямого перехода) запас биомассы.
 * Тело пустое - сам факт получения и есть сигнал.
 */
public class TransformRequestPacket {
    /** Стоимость трансформации человек -> монстр (биомасса). */
    public static final int TRANSFORM_BIOMASS_COST = 45;
    /** Длительность анимации трансформации в тиках (5 секунд). */
    public static final int TRANSFORM_DURATION_TICKS = 100;
    /** Кулдаун после трансформации в любую сторону (10 секунд). */
    public static final int TRANSFORM_COOLDOWN_TICKS = 200;

    public TransformRequestPacket() {}

    @SuppressWarnings("unused")
    public TransformRequestPacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(TransformRequestPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ServerPlayer player = ctxSupplier.get().getSender();
        if (player == null) return;

        var capOpt = player.getCapability(ThingPlayerProvider.THING_DATA);
        if (!capOpt.isPresent()) return;

        capOpt.ifPresent(data -> tryTransform(player, data));
    }

    private static void tryTransform(ServerPlayer player, IThingPlayerData data) {
        if (data.getRole() != IThingPlayerData.Role.THING) {
            player.sendSystemMessage(Component.literal("§c[The Thing Party] Ты не Нечто."));
            return;
        }

        if (GameState.get().isThingAbilityBlocked()) {
            player.sendSystemMessage(Component.literal("§c[The Thing Party] Способности заблокированы (Фаза Форы)."));
            return;
        }

        if (data.getTransformTicks() > 0) {
            // Уже идёт трансформация - игнорируем, чтобы не перебивать анимацию.
            return;
        }

        if (data.getTransformCooldownTicks() > 0) {
            int seconds = (data.getTransformCooldownTicks() + 19) / 20;
            player.sendSystemMessage(Component.literal("§c[The Thing Party] Кулдаун трансформации: " + seconds + " сек"));
            return;
        }

        if (data.getMonsterTime() < 0) {
            data.setMonsterForm(false);
            data.setTransformTicks(0);
            data.setTransformCooldownTicks(TRANSFORM_COOLDOWN_TICKS);
            player.sendSystemMessage(Component.literal("§7[The Thing Party] Возврат в человеческую форму."));
        }

        if (data.isMonsterForm()) {
            // Возврат в человека - бесплатно, но кулдаун стартует.
            data.setMonsterForm(false);
            data.setTransformTicks(0);
            data.setTransformCooldownTicks(TRANSFORM_COOLDOWN_TICKS);
            player.sendSystemMessage(Component.literal("§7[The Thing Party] Возврат в человеческую форму."));
        } else {
            // Прямой переход: требуется биомасса.
            if (data.getBiomass() < TRANSFORM_BIOMASS_COST) {
                player.sendSystemMessage(Component.literal(
                        "§c[The Thing Party] Недостаточно биомассы: " + data.getBiomass() + " / " + TRANSFORM_BIOMASS_COST));
                return;
            }

            data.setBiomass(data.getBiomass() - TRANSFORM_BIOMASS_COST);
            data.setMonsterForm(true);
            data.setTransformTicks(TRANSFORM_DURATION_TICKS);
            data.setMonsterTime(800); // Таймер активной формы (40 секунд)
            System.out.println("[THE THING PARTY DEBUG] Игрок трансформировался. Установлено время 800.");
            // Кулдаун накладывается только при возврате - чтобы вход в форму был "взрывным".
            player.sendSystemMessage(Component.literal(
                    "§4[The Thing Party] Трансформация в Нечто. Биомасса: " + data.getBiomass()));
        }

        NetworkHandler.syncToPlayer(player);
    }
}
