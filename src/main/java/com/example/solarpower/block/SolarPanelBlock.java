package com.example.solarpower.block;

import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.SolarTier;
import com.example.solarpower.tileentity.SolarPanelTile;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** 太阳能板方块（1.21.1）。每个档位一个实例，数值由 {@link SolarTier} 决定。 */
public class SolarPanelBlock extends BaseEntityBlock {

    private final SolarTier tier;

    public SolarPanelBlock(SolarTier tier) {
        super(Properties.ofFullCopy(Blocks.IRON_BLOCK));
        this.tier = tier;
    }

    public SolarTier tier() {
        return this.tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new SolarPanelBlock(this.tier));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == ModBlockEntities.SOLAR_PANEL.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<SolarPanelTile>) (l, p, s, be) -> be.tick()
                : null;
    }
}
