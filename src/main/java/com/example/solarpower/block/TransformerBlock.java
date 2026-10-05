package com.example.solarpower.block;

import com.example.solarpower.SolarPower;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.solar.SolarTier;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;

import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 鍙樺帇鍣ㄦ柟鍧楋紙1.21.1锛?5 妗ｏ紝妗ユ帴鐢靛帇 T鈫擳+1锛夈€? * 姝ｉ潰锛堣兘閲忚姱锛? 楂樺帇渚э紙T+1锛夛紝鍏朵綑浜旈潰 = 浣庡帇渚э紙T锛夛紱杞崲閫昏緫鍏ㄥ湪
 * {@link TransformerTile}锛屾棤妯″紡寮€鍏筹紙IC2 璇箟锛氭寜杩涚數鏂瑰悜鑷姩鍗?闄嶅帇锛夛紝
 * 鍙抽敭鎵撳紑鍙屾睜鏉＄晫闈紝姣旇緝鍣ㄨ緭鍑轰袱姹犲悎璁″崰鐢ㄦ瘮渚嬨€? */
public class TransformerBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final EuTier tier;
    private final SolarTier solarTier;

    public TransformerBlock(EuTier tier, SolarTier solarTier, Properties props) {
        super(props);
        this.tier = tier;
        this.solarTier = solarTier;
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public EuTier tier() {
        return this.tier;
    }

    public SolarTier solarTier() {
        return this.solarTier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new TransformerBlock(this.tier, this.solarTier, properties));
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

    /** 鍙抽敭鎵撳紑鐣岄潰锛堝弻姹犳潯 + 鍚炲悙/鐢靛帇妗ユ帴琛岋級銆?*/
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TransformerTile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new TransformerMenu(id, inv,
                            level.getBlockEntity(pos) instanceof TransformerTile transformer
                                    ? transformer : null),
                    Component.translatable("block.solarpower." + this.solarTier.id() + "_transformer")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 鎷嗘満鎺夎惤鑷韩锛堝彉鍘嬪櫒缂撳啿涓烘殏瀛樻暟鎹紝涓嶉殢鎺夎惤鐗╀繚鐣欙級銆?*/
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
