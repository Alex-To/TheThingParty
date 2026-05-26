package com.helltoxx.thethingparty.blocks;

import com.helltoxx.thethingparty.capability.IThingPlayerData;
import com.helltoxx.thethingparty.capability.ThingPlayerProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BloodIVStandBlock extends Block {

    // Создаем свойство: есть ли в блоке кровь?
    public static final BooleanProperty HAS_BLOOD = BooleanProperty.create("has_blood");

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public BloodIVStandBlock(Properties properties) {
        super(properties);
        // Устанавливаем, что при постройке блока кровь по умолчанию ЕСТЬ (true)
        this.registerDefaultState(this.stateDefinition.any().setValue(HAS_BLOOD, true));
    }

    // Регистрируем наше свойство в самом блоке
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_BLOOD);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // Метод, который вызывается при клике правой кнопкой мыши по блоку
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Если в блоке уже нет крови, игнорируем клик
        if (!state.getValue(HAS_BLOOD)) {
            return InteractionResult.PASS;
        }

        // Проверяем роль игрока через твое Capability (ТОЛЬКО НА СЕРВЕРЕ)
        if (!level.isClientSide()) {
            player.getCapability(ThingPlayerProvider.THING_DATA).ifPresent(data -> {

                // Если кликнул Нечто
                if (data.getRole() == IThingPlayerData.Role.THING) {

                    // Нечто выпивает кровь: добавляем биомассу
                    data.addBiomass(15);

                    // Меняем состояние блока на "ПУСТОЙ" (HAS_BLOOD = false)
                    level.setBlock(pos, state.setValue(HAS_BLOOD, false), 3);

                    // Воспроизводим звук (пока ванильный звук питья меда, позже поменяешь на свой чавкающий звук)
                    level.playSound(null, pos, SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            });
        }

        // Говорим игре, что взаимодействие прошло успешно (сработает анимация руки)
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}