package com.helltoxx.thethingparty.init;

import com.helltoxx.thethingparty.blocks.BloodIVStandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockInit {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "thethingparty");

    // Регистрируем капельницу. Задаем свойства: звук металла, прочность 1.5, и ВАЖНО: noOcclusion()
    // noOcclusion() говорит игре, что блок не сплошной, и сквозь него видно другие блоки (иначе мир вокруг станет невидимым)
    public static final RegistryObject<Block> BLOOD_IV_STAND = BLOCKS.register("blood_iv_stand",
            () -> new BloodIVStandBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(1.5f)
                    .noOcclusion()));
}