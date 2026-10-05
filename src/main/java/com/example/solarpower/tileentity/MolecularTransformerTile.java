package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuTier;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.IEuEnergy;
import com.example.solarpower.energy.OverVoltage;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.MtRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 分子重组仪方块实体（1.21.1）。机制照搬 Advanced Solar Panels 的 TileEntityMolecularTransformer：
 * <ul>
 *     <li>单输入/单输出；输入必须有配方才收；开始转换时先扣 1 个输入并锁定产出；</li>
 *     <li>每 tick 把缓存里的 EU 注入当前任务，直到注满配方的总 EU，产出进入输出槽；</li>
 *     <li>缓存上限 2^31 EU（≈21.5 亿，用户指定）；受电等级放宽到最高级
 *         （{@link #SINK_TIER}），ASP 原值为 14 级 + 缓存 100k EU；</li>
 * </ul>
 * <p>能量入口两通道：本模组玻璃电缆网络（暴露 {@link IEuEnergy}，汇）、
 * Forge Energy（1 EU = 4 FE，只进不出）。工作进度走方块 active 状态与比较器（0~15）。
 */
public class MolecularTransformerTile extends BlockEntity implements IEuEnergy, IEnergyStorage {

    /** 内部缓存上限（EU）：14 级电压上限（2^31 ≈ 21.5 亿，用户指定）。 */
    public static final BigInteger CAPACITY_EU = BigInteger.ONE.shiftLeft(31);
    /** 受电电压等级（2^121 EU/packet，所有面板均可直喂，用户指定的有意放宽）。 */
    public static final int SINK_TIER = 59;

    private static final String TAG_ENERGY = "Energy_b64";
    private static final String TAG_INPUT = "Input";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_JOB = "Job";
    private static final String TAG_TOTAL = "Total";
    private static final String TAG_USED = "Used";

    private final ItemStackHandler input = new ItemStackHandler(1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // 只收有配方的输入（自动化管道同样受此约束）
            return MtRecipes.find(stack) == null ? stack : super.insertItem(slot, stack, simulate);
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;   // 只出不进
        }
    };

    /** 当前任务的产出预锁定（输入已扣、能量未注满），照搬 ASP 的 currentOutput。 */
    private ItemStack jobOutput = ItemStack.EMPTY;
    private int totalEu;
    private int energyUsed;
    private boolean working;

    private final MtEnergy storage = new MtEnergy();
    private final OverVoltage overVoltage = new OverVoltage();

    public MolecularTransformerTile(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOLECULAR_TRANSFORMER.get(), pos, state);
    }

    public ItemStackHandler getInput() {
        return this.input;
    }

    public ItemStackHandler getOutput() {
        return this.output;
    }

    public IEuEnergy getEnergy() {
        return this.storage;
    }

    @Override
    public BigInteger getStoredEu() {
        return this.storage.getStoredEu();
    }

    @Override
    public BigInteger getCapacityEu() {
        return this.storage.getCapacityEu();
    }

    @Override
    public EuTier getTier() {
        return this.storage.getTier();
    }

    @Override
    public BigInteger receiveEu(BigInteger amount, EuTier tier, boolean simulate) {
        return this.storage.receiveEu(amount, tier, simulate);
    }

    @Override
    public BigInteger extractEu(BigInteger amount, boolean simulate) {
        return this.storage.extractEu(amount, simulate);
    }

    public int getTotalEu() {
        return this.totalEu;
    }

    public int getEnergyUsed() {
        return this.energyUsed;
    }

    public boolean isWorking() {
        return this.working;
    }

    /** 比较器输出 0~15（照搬 ASP 公式）。 */
    public int comparatorLevel() {
        return this.totalEu <= 0 ? 0 : this.energyUsed * 15 / this.totalEu;
    }

    /** 拆机时掉落的物品（输入槽 + 输出槽）。 */
    public List<ItemStack> dropContents() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStackHandler handler : new ItemStackHandler[]{this.input, this.output}) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                    handler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
        return drops;
    }

    public void tick() {
        if (this.level.isClientSide()) {
            return;
        }
        if (this.overVoltage.tick(this.level, this.worldPosition)) {
            explode();
            return;
        }
        boolean active = false;

        // 空闲且有输入：锁定配方、扣除 1 个输入
        if (this.jobOutput.isEmpty() && !this.input.getStackInSlot(0).isEmpty()) {
            MtRecipes.MtRecipe recipe = MtRecipes.find(this.input.getStackInSlot(0));
            if (recipe != null && canInsertOutput(recipe.output)) {
                this.jobOutput = recipe.output.copy();
                this.totalEu = recipe.totalEu;
                this.energyUsed = 0;
                this.input.extractItem(0, 1, false);
                setChanged();
                this.level.updateNeighbourForOutputSignal(this.worldPosition, getBlock());
            }
        }

        // 有任务：把缓存里的 EU 尽量注入，注满则出料
        if (!this.jobOutput.isEmpty()) {
            BigInteger need = BigInteger.valueOf(this.totalEu - this.energyUsed);
            BigInteger took = this.storage.extractEu(need.min(this.storage.getStoredEu()), false);
            if (took.signum() > 0) {
                this.energyUsed += took.intValueExact();
                active = true;
                setChanged();
            }
            if (this.energyUsed >= this.totalEu) {
                // 出料：直接写入输出槽（insertItem 被「只出不进」覆写拒绝，insertItemStacked
                // 会全数弹回）；输出槽被占/塞满时保留任务下 tick 重试，绝不吞物品
                ItemStack produced = this.jobOutput.copy();
                ItemStack existing = this.output.getStackInSlot(0);
                boolean stored;
                if (existing.isEmpty()) {
                    this.output.setStackInSlot(0, produced);
                    stored = true;
                } else if (ItemStack.isSameItemSameComponents(existing, produced)
                        && existing.getCount() + produced.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(produced.getCount());
                    this.output.setStackInSlot(0, existing);
                    stored = true;
                } else {
                    stored = false;
                }
                if (stored) {
                    this.jobOutput = ItemStack.EMPTY;
                    this.totalEu = 0;
                    this.energyUsed = 0;
                }
                setChanged();
                this.level.updateNeighbourForOutputSignal(this.worldPosition, getBlock());
            }
        }

        this.working = active;
        setActive(active);
    }

    private Block getBlock() {
        return this.level.getBlockState(this.worldPosition).getBlock();
    }

    private boolean canInsertOutput(ItemStack stack) {
        ItemStack existing = this.output.getStackInSlot(0);
        return existing.isEmpty()
                || (ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() + stack.getCount() <= existing.getMaxStackSize());
    }

    private void setActive(boolean active) {
        if (this.level.getBlockEntity(this.worldPosition) != this) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.getBlock() instanceof com.example.solarpower.block.MolecularTransformerBlock
                && state.getValue(com.example.solarpower.block.MolecularTransformerBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.example.solarpower.block.MolecularTransformerBlock.ACTIVE, active), 3);
        }
    }

    /** 超压爆炸：机器消失（不掉自身），先弹出槽内物品，原版爆炸附带范围伤害与破坏方块。 */
    private void explode() {
        if (this.level == null) {
            return;
        }
        for (ItemStack stack : dropContents()) {
            Block.popResource(this.level, this.worldPosition, stack);
        }
        this.level.destroyBlock(this.worldPosition, false);
        this.level.explode(null, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.BLOCK);
    }

    // ------------------------------------------------------------------
    // Forge Energy：只进不出（本机是纯用电器），1 EU = 4 FE
    // ------------------------------------------------------------------

    @Override
    public int getEnergyStored() {
        return FeConvert.toFe(this.storage.getStoredEu());
    }

    @Override
    public int getMaxEnergyStored() {
        return FeConvert.toFe(this.storage.getCapacityEu());
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        // FE 无电压概念，按最低等级注入（不会超过 14 级上限）
        return FeConvert.toFe(this.storage.receiveEu(FeConvert.toEu(maxReceive), EuTier.LV, simulate));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_ENERGY, this.storage.getStoredEu().toString());
        tag.put("input_slot", this.input.getStackInSlot(0).saveOptional(registries));
        tag.put("output_slot", this.output.getStackInSlot(0).saveOptional(registries));
        if (!this.jobOutput.isEmpty()) {
            tag.put(TAG_JOB, this.jobOutput.saveOptional(registries));
        }
        tag.putInt(TAG_TOTAL, this.totalEu);
        tag.putInt(TAG_USED, this.energyUsed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String s = tag.getString(TAG_ENERGY);
        if (!s.isEmpty()) {
            try {
                this.storage.setStored(new BigInteger(s));
            } catch (NumberFormatException e) {
                this.storage.setStored(BigInteger.ZERO);
            }
        }
        this.input.setStackInSlot(0, ItemStack.parseOptional(registries, tag.getCompound("input_slot")));
        this.output.setStackInSlot(0, ItemStack.parseOptional(registries, tag.getCompound("output_slot")));
        this.jobOutput = ItemStack.parseOptional(registries, tag.getCompound(TAG_JOB));
        this.totalEu = tag.getInt(TAG_TOTAL);
        this.energyUsed = tag.getInt(TAG_USED);
    }

    // ------------------------------------------------------------------
    // 储能容器：固定容量与等级
    // ------------------------------------------------------------------

    private final class MtEnergy implements IEuEnergy {

        private BigInteger stored = BigInteger.ZERO;

        @Override
        public BigInteger getStoredEu() {
            return this.stored;
        }

        @Override
        public BigInteger getCapacityEu() {
            return CAPACITY_EU;
        }

        @Override
        public EuTier getTier() {
            return EuTier.byIuTier(SINK_TIER);
        }

        @Override
        public BigInteger receiveEu(BigInteger amount, EuTier tier, boolean simulate) {
            if (amount == null || amount.signum() <= 0) {
                return BigInteger.ZERO;
            }
            if (!getTier().accepts(tier)) {
                overVoltage.flag();   // 超压暴露：冒烟累积
                return BigInteger.ZERO;
            }
            BigInteger accepted = amount.min(getCapacityEu().subtract(this.stored));
            if (accepted.signum() <= 0) {
                return BigInteger.ZERO;
            }
            if (!simulate) {
                this.stored = this.stored.add(accepted);
                setChanged();
            }
            return accepted;
        }

        @Override
        public BigInteger extractEu(BigInteger amount, boolean simulate) {
            if (amount == null || amount.signum() <= 0) {
                return BigInteger.ZERO;
            }
            BigInteger extracted = amount.min(this.stored);
            if (extracted.signum() <= 0) {
                return BigInteger.ZERO;
            }
            if (!simulate) {
                this.stored = this.stored.subtract(extracted);
                setChanged();
            }
            return extracted;
        }

        void setStored(BigInteger value) {
            if (value == null || value.signum() < 0) {
                this.stored = BigInteger.ZERO;
            } else {
                this.stored = value.min(getCapacityEu());
            }
            setChanged();
        }
    }
}
