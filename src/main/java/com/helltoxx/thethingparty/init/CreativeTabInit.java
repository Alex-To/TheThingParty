package com.helltoxx.thethingparty.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CreativeTabInit {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "thethingparty");

    public static final RegistryObject<CreativeModeTab> THING_TAB = TABS.register("thing_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.thethingparty"))
                    // Ставим канистру на иконку вкладки, так как пакета больше нет
                    .icon(() -> ItemInit.HEAVY_CANISTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ItemInit.HEAVY_CANISTER.get());
                        output.accept(ItemInit.BLOOD_IV_STAND_ITEM.get());
                    }).build());
}