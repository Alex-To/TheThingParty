package com.helltoxx.thethingparty;

import com.helltoxx.thethingparty.network.NetworkHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

// Аннотация @Mod говорит Форджу: "Этот класс - главный для мода thethingparty"
// Имя здесь ДОЛЖНО совпадать с mod_id из gradle.properties и mods.toml
@Mod("thethingparty")
public class TheThingParty {
    // Создаём логгер, чтобы выводить сообщения в консоль IDEA
    private static final Logger LOGGER = LogUtils.getLogger();

    // Конструктор класса - запускается в самую первую очередь
    public TheThingParty() {
        // Получаем главную шину событий мода
        var bus = FMLJavaModLoadingContext.get().getModEventBus();

        // РЕГИСТРИРУЕМ НАШИ ПРЕДМЕТЫ И ВКЛАДКУ!
        com.helltoxx.thethingparty.init.BlockInit.BLOCKS.register(bus);
        com.helltoxx.thethingparty.init.ItemInit.ITEMS.register(bus);
        com.helltoxx.thethingparty.init.CreativeTabInit.TABS.register(bus);


        // Подписываем метод commonSetup на шину событий мода
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        // Регистрируем наш класс в главной шине событий Forge
        MinecraftForge.EVENT_BUS.register(this);
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Метод, который срабатывает во время общей настройки игры
    private void commonSetup(final FMLCommonSetupEvent event) {
        // Инициализируем сетевые пакеты
        NetworkHandler.register();
        // Выводим сообщение в консоль, чтобы убедиться, что мод "завёлся"
        LOGGER.info(">>> ВНИМАНИЕ! Мод The Thing Party от HellToxx успешно загружается! <<<");
    }
}