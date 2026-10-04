package com.example.solarpower.block;

import com.example.solarpower.SolarPower;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.menu.TransformerMenu;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.tileentity.TransformerTile;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 变压器方块（1.21.1，65 档，桥接电压 T↔T+1）。
 * 正面（能量芯）= 高压侧（T+1），其余五面 = 低压侧（T）；转换逻辑全在
 * {@link TransformerTile}，无模式开关（IC2 语义：按进电方向自动升/降压），
 * 右键打开双池条界面，比较器输出两池合计占用比例。
 */
public class TransformerBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final EuTier tier;

    public TransformerBlock(EuTier tier) {
        super(Properties.of()
                .strength(3.0F)
                .sound(net.minecraft.world.level.block.SoundType.METAL));
        this.tier = tier;
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** 低压侧档位（桥接 tier ↔ tier+1）。 */
    public EuTier tier() {
        return this.tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new TransformerBlock(this.tier));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransformerTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : type == ModBlockEntities.TRANSFORMER.get()
                        ? (BlockEntityTicker<T>) (BlockEntityTicker<TransformerTile>) (l, p, s, be) -> be.tick()
                        : null;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** 右键打开界面（双池条 + 吞吐/电压桥接行）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TransformerTile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new TransformerMenu(id, inv,
                            level.getBlockEntity(pos) instanceof TransformerTile transformer
                                    ? transformer : null),
                    Component.translatable("block.solarpower."
                            + com.example.solarpower.registry.ModBlocks
                                    .representativePanel(this.tier.iuTier()).id()
                            + "_transformer.name")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 拆机掉落自身（变压器缓冲为暂存数据，不随掉落物保留）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(this));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TransformerTile tile ? tile.comparatorLevel() : 0;
    }
}
