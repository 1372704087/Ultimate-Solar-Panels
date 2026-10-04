package com.example.solarpower.block;

import com.example.solarpower.component.ModComponents;
import com.example.solarpower.component.StoredEu;
import com.example.solarpower.menu.ChargepadMenu;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.SolarTier;
import com.example.solarpower.tileentity.ChargepadTile;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * 鍏呯數搴ф柟鍧楋紙1.21.1锛夈€傛瘡涓。浣嶄竴涓疄渚嬶紝鏁板€肩敱 {@link SolarTier} 鍐冲畾銆? * 绔欎笂鍘诲嵆缁欑洈鐢?鑳屽寘閲岀殑 FE 鐗╁搧鍏呯數锛堥€昏緫鍦?{@link ChargepadTile}锛夛紝
 * 鍏呯數涓《闈㈠垏鎹㈠彂鍏夋€侊紙ACTIVE 灞炴€т袱鎬佹ā鍨嬶紝鍚?IU top_active锛夛紝鍙抽敭鎵撳紑鐣岄潰銆? */
public class ChargepadBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final SolarTier tier;

    public ChargepadBlock(SolarTier tier, Properties props) {
        super(props);
        this.tier = tier;
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    public SolarTier tier() {
        return this.tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new ChargepadBlock(this.tier, properties));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargepadTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : type == ModBlockEntities.CHARGEPAD.get()
                        ? (BlockEntityTicker<T>) (BlockEntityTicker<ChargepadTile>) (l, p, s, be) -> be.tick()
                        : null;
    }

    /** 鍙抽敭鎵撳紑鐣岄潰锛堣搫鐢甸噺 + 鍏呯數鐘舵€侊級銆?*/
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChargepadTile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new ChargepadMenu(id, inv,
                            level.getBlockEntity(pos) instanceof ChargepadTile pad ? pad : null),
                    Component.translatable("block.solarpower." + this.tier.id() + "_chargepad")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 鎷嗘満鎺夎惤鑷韩銆?*/
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(this));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }
}
