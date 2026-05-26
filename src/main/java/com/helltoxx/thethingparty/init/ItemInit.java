package com.helltoxx.thethingparty.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.BlockItem;

public class ItemInit {
    // Создаем "список" для регистрации наших предметов. Он привязан к твоему modId
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thethingparty");

    // 1. Тяжелый квестовый предмет (Канистра)
    // Указываем stacksTo(1), чтобы канистры нельзя было складывать в стаки по 64. Одна ячейка = одна канистра.
    public static final RegistryObject<Item> HEAVY_CANISTER = ITEMS.register("heavy_canister",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // 2. Капельница с кровью для пополнения шкалы
    public static final RegistryObject<Item> BLOOD_IV_STAND_ITEM = ITEMS.register("blood_iv_stand",
            () -> new BlockItem(BlockInit.BLOOD_IV_STAND.get(), new Item.Properties()));
}