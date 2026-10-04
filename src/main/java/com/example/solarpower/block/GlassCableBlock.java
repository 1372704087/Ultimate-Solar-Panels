package com.example.solarpower.block;

import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.tileentity.GlassCableTile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * 玻璃电缆方块（1.21.1）。等级决定线损与容量；六向连接用
 * {@link BlockStateProperties} 的布尔属性表达，multipart blockstate 渲染，
 * 碰撞/轮廓由属性直接推导（无需读取世界）。
 */
public class GlassCableBlock extends Block implements EntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final VoxelShape CORE = Shapes.box(
            6 / 16D, 6 / 16D, 6 / 16D, 10 / 16D, 10 / 16D, 10 / 16D);
    private static final VoxelShape ARM_NORTH = Shapes.box(
            6 / 16D, 6 / 16D, 0 / 16D, 10 / 16D, 10 / 16D, 6 / 16D);
    private static final VoxelShape ARM_SOUTH = Shapes.box(
            6 / 16D, 6 / 16D, 10 / 16D, 10 / 16D, 10 / 16D, 16 / 16D);
    private static final VoxelShape ARM_WEST = Shapes.box(
            0 / 16D, 6 / 16D, 6 / 16D, 6 / 16D, 10 / 16D, 10 / 16D);
    private static final VoxelShape ARM_EAST = Shapes.box(
            10 / 16D, 6 / 16D, 6 / 16D, 16 / 16D, 10 / 16D, 10 / 16D);
    private static final VoxelShape ARM_UP = Shapes.box(
            6 / 16D, 10 / 16D, 6 / 16D, 10 / 16D, 16 / 16D, 10 / 16D);
    private static final VoxelShape ARM_DOWN = Shapes.box(
            6 / 16D, 0 / 16D, 6 / 16D, 10 / 16D, 6 / 16D, 10 / 16D);

    private final GlassCableTier tier;

    public GlassCableBlock(GlassCableTier tier) {
        super(Properties.of()
                .strength(0.5F)
                .sound(net.minecraft.world.level.block.SoundType.GLASS)
                .noOcclusion()
                .requiresCorrectToolForDrops());
        this.tier = tier;
        registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(EAST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    public GlassCableTier tier() {
        return this.tier;
    }

    /** 电缆与相邻的电缆（任意等级）和太阳能板连接。 */
    public static boolean connectsTo(BlockState neighbor) {
        return neighbor.getBlock() instanceof GlassCableBlock
                || neighbor.getBlock() instanceof SolarPanelBlock;
    }

    public static BooleanProperty propOf(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    /** 放置时按六向邻居初始化连接。 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        for (Direction dir : Direction.values()) {
            state = state.setValue(propOf(dir), connectsTo(level.getBlockState(pos.relative(dir))));
        }
        return state;
    }

    /** 邻居方块变化时刷新该向连接（放置/拆除电缆、面板都会实时伸/收臂）。 */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(propOf(dir), connectsTo(neighborState));
    }

    /** 碰撞/轮廓由连接属性直接推导。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, ARM_NORTH);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, ARM_SOUTH);
        if (state.getValue(WEST)) shape = Shapes.or(shape, ARM_WEST);
        if (state.getValue(EAST)) shape = Shapes.or(shape, ARM_EAST);
        if (state.getValue(UP)) shape = Shapes.or(shape, ARM_UP);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, ARM_DOWN);
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false; // 贴图带透明镂空（玻璃质感），避免天光直接穿透
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GlassCableTile(pos, state);
    }
}
