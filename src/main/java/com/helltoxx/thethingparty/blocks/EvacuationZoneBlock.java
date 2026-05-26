package com.helltoxx.thethingparty.blocks;

import com.helltoxx.thethingparty.game.GameState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Невидимая площадка эвакуации. Хитбокс полный (можно поставить/сломать оператором),
 * но рендер выключен — позже сюда встанет модель посадочной зоны / вертолёта.
 *
 * При установке регистрирует позицию в {@link GameState#setEvacuationZone}; при удалении
 * — снимает. В одной игре активна только последняя поставленная зона.
 */
public class EvacuationZoneBlock extends Block {
    public EvacuationZoneBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && oldState.getBlock() != this) {
            GameState.get().setEvacuationZone(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && newState.getBlock() != this) {
            GameState.get().clearEvacuationZoneIfMatches(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
