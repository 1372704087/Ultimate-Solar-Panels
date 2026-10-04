package com.example.solarpower.block;

import com.example.solarpower.component.ModComponents;
import com.example.solarpower.component.StoredEu;
import com.example.solarpower.menu.StorageBoxMenu;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.solar.SolarTier;
import com.example.solarpower.tileentity.StorageBoxTile;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

import java.math.BigInteger;

/**
 * 储电盒方块（1.21.1，每档一个实例——1.21 无方块 id 上限）。
 * 正面（能量芯/输出面）放置时朝向玩家；比较器按存量比例输出；
 * 拆机掉落物经 DataComponents 保留电量（放置时读回）。
 */
public class StorageBoxBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final SolarTier tier;

    public StorageBoxBlock(SolarTier tier) {
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
        return simpleCodec(properties -> new StorageBoxBlock(this.tier));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageBoxTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == ModBlockEntities.STORAGE_BOX.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<StorageBoxTile>) (l, p, s, be) -> be.tick()
                : null;
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 右键打开界面（蓄电量 + 容量 + 输出行）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StorageBoxTile) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new StorageBoxMenu(id, inv,
                            level.getBlockEntity(pos) instanceof StorageBoxTile box ? box : null),
                    Component.translatable("block.solarpower." + this.tier.id() + "_storage_box.name")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 放置时读回掉落物携带的电量（DataComponent）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        StoredEu stored = stack.get(ModComponents.STORED_EU);
        if (stored != null && level.getBlockEntity(pos) instanceof StorageBoxTile box) {
            box.restoreEnergy(stored.value());
        }
    }

    /** 拆机保留电量：玩家破坏时掉落自身 + 电量 DataComponent（爆炸破坏暂不保留）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        ItemStack stack = new ItemStack(this);
        if (blockEntity instanceof StorageBoxTile box) {
            BigInteger stored = box.getStoredEu();
            if (stored.signum() > 0) {
                stack.set(ModComponents.STORED_EU, new StoredEu(stored));
            }
        }
        popResource(level, pos, stack);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StorageBoxTile box ? box.comparatorLevel() : 0;
    }
}
