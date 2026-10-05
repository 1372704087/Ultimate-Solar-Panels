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
 * 鍒嗗瓙閲嶇粍浠柟鍧楋紙1.21.1锛夈€傛満鍒剁収鎼?Advanced Solar Panels锛? * 鎺ュ彈 EU 娉ㄥ叆锛屾妸杈撳叆鐗╁搧鎸夐厤鏂规€婚噺杞崲鎴愪骇鍑猴紱闈欐€?JSON 妯″瀷娓叉煋锛堝悓 IU锛? * active 涓ゆ€佸叡鐢ㄥ悓涓€妯″瀷锛夛紝姣旇緝鍣ㄨ緭鍑哄悎鎴愯繘搴︼紙0~15锛夈€? */
public class MolecularTransformerBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MolecularTransformerBlock(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(MolecularTransformerBlock::new);
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
        return level.isClientSide() ? null
                : type == ModBlockEntities.MOLECULAR_TRANSFORMER.get()
                        ? (BlockEntityTicker<T>) (BlockEntityTicker<MolecularTransformerTile>) (l, p, s, be) -> be.tick()
                        : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MolecularTransformerTile tile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.example.solarpower.menu.MolecularTransformerMenu(id, inv, tile),
                    Component.translatable("block.solarpower.molecular_transformer")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
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
