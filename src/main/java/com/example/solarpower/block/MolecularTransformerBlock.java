package com.example.solarpower.block;

import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.tileentity.MolecularTransformerTile;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

import java.util.List;

/**
 * 分子重组仪方块（1.21.1）。机制照搬 Advanced Solar Panels：
 * 接受 EU 注入，把输入物品按配方总量转换成产出；静态 JSON 模型渲染（同 IU，
 * active 两态共用同一模型），比较器输出合成进度（0~15）。
 */
public class MolecularTransformerBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MolecularTransformerBlock() {
        super(Properties.of()
                .strength(3.0F)
                .sound(net.minecraft.world.level.block.SoundType.METAL)
                .noOcclusion());
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new MolecularTransformerBlock());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MolecularTransformerTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : type == ModBlockEntities.MOLECULAR_TRANSFORMER.get()
                        ? (BlockEntityTicker<T>) (BlockEntityTicker<MolecularTransformerTile>) (l, p, s, be) -> be.tick()
                        : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MolecularTransformerTile tile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.example.solarpower.menu.MolecularTransformerMenu(id, inv, tile),
                    Component.translatable("block.solarpower.molecular_transformer.name")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MolecularTransformerTile tile) {
            List<net.minecraft.world.item.ItemStack> drops = tile.dropContents();
            for (net.minecraft.world.item.ItemStack stack : drops) {
                net.minecraft.world.Containers.dropItemStack(level,
                        pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MolecularTransformerTile tile
                ? tile.comparatorLevel() : 0;
    }
}
