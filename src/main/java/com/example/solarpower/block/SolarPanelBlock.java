package com.example.solarpower.block;

import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.registry.ModMenus;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** 太阳能板方块（1.21.1）。每个档位一个实例，数值由 {@link SolarTier} 决定。 */
public class SolarPanelBlock extends BaseEntityBlock {

    private final SolarTier tier;

    public SolarPanelBlock(SolarTier tier) {
        super(Properties.of()
                .strength(3.0F)
                .sound(net.minecraft.world.level.block.SoundType.METAL));
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

    /** 右键打开界面（能量管 + 三行数据 + 指示灯）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               net.minecraft.world.entity.player.Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SolarPanelTile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.example.solarpower.menu.SolarPanelMenu(id, inv,
                            level.getBlockEntity(pos) instanceof SolarPanelTile tile ? tile : null),
                    Component.translatable("block.solarpower." + this.tier.id())),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == ModBlockEntities.SOLAR_PANEL.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<SolarPanelTile>) (l, p, s, be) -> be.tick()
                : null;
    }
}
