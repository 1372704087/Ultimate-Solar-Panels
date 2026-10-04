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
 * 充电座方块（1.21.1）。每个档位一个实例，数值由 {@link SolarTier} 决定。
 * 站上去即给盔甲/背包里的 FE 物品充电（逻辑在 {@link ChargepadTile}），
 * 充电中顶面切换发光态（ACTIVE 属性两态模型，同 IU top_active），右键打开界面。
 */
public class ChargepadBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final SolarTier tier;

    public ChargepadBlock(SolarTier tier) {
        super(Properties.of()
                .strength(3.0F)
                .sound(net.minecraft.world.level.block.SoundType.METAL));
        this.tier = tier;
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    public SolarTier tier() {
        return this.tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new ChargepadBlock(this.tier));
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

    /** 右键打开界面（蓄电量 + 充电状态）。 */
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

    /** 拆机掉落自身。 */
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
