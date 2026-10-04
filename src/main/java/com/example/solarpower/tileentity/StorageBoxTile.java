package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuCableNet;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.IEuEnergy;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.math.BigInteger;
import java.util.Base64;

/**
 * 储电盒方块实体（1.21.1，全档位共用，档位绑定 {@link SolarTier}）。
 * IU 语义：五面进电（电网自动当汇）、正面出电（每 tick 推流一次，预算 = 本档
 * 面板最大输出）；容量 = 本档面板缓冲 ×64；电压包超压拒收。
 * <p>存档 Base64 大端补码（百万位大数 toString 平方级，禁用）；读档时档位
 * 未解析，首个 tick 才夹回容量。
 */
public class StorageBoxTile extends BlockEntity implements IEuEnergy, IEnergyStorage {

    private static final int CAPACITY_SHIFT = 6;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ENERGY_B64 = TAG_ENERGY + "_b64";
    private static final int NBT_STRING = 8;

    private BigInteger stored = BigInteger.ZERO;
    private SolarTier resolvedTier;

    private SolarTier capacityForTier;
    private BigInteger capacityCache;

    private BigInteger roomForStored;
    private BigInteger roomForCapacity;
    private BigInteger roomCache = BigInteger.ZERO;

    private BigInteger partsFor;
    private int[] partsCache;

    private BigInteger comparatorFor;
    private BigInteger comparatorForCapacity;
    private int comparatorCache;

    public StorageBoxTile(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_BOX.get(), pos, state);
    }

    /** 档位由所在方块的实例决定（懒解析，回落基础档）。 */
    public SolarTier getSolarTier() {
        if (this.resolvedTier == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.StorageBoxBlock box) {
                this.resolvedTier = box.tier();
            }
        }
        return this.resolvedTier == null ? SolarTier.BASIC : this.resolvedTier;
    }

    /** 正面（能量芯/输出面）朝向，读方块状态。 */
    public Direction facing() {
        if (this.level != null
                && this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.StorageBoxBlock) {
            return this.level.getBlockState(this.worldPosition).getValue(com.example.solarpower.block.StorageBoxBlock.FACING);
        }
        return Direction.NORTH;
    }

    /** 容量 = 面板缓冲 ×64（按档位实例缓存）。 */
    public BigInteger capacityEu() {
        SolarTier tier = getSolarTier();
        if (this.capacityForTier != tier) {
            this.capacityForTier = tier;
            this.capacityCache = tier.capacityEu().shiftLeft(CAPACITY_SHIFT);
        }
        return this.capacityCache;
    }

    public void restoreEnergy(BigInteger value) {
        this.stored = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        setChanged();
    }

    public int[] getStoredDisplayParts() {
        if (this.partsFor != this.stored) {
            this.partsFor = this.stored;
            this.partsCache = com.example.solarpower.energy.EuFormat.displayParts(this.stored);
        }
        return this.partsCache;
    }

    /** 比较器：存量×16 ÷ 容量 向下取整（按存量实例缓存）。 */
    public int comparatorLevel() {
        BigInteger capacity = capacityEu();
        if (this.comparatorFor != this.stored || this.comparatorForCapacity != capacity) {
            this.comparatorFor = this.stored;
            this.comparatorForCapacity = capacity;
            this.comparatorCache = capacity.signum() <= 0 ? 0
                    : this.stored.shiftLeft(4).divide(capacity).intValueExact();
        }
        return Math.max(0, Math.min(15, this.comparatorCache));
    }

    public void tick() {
        if (this.level.isClientSide()) {
            return;
        }
        BigInteger capacity = capacityEu();
        if (this.stored.compareTo(capacity) > 0) {
            this.stored = capacity;
            setChanged();
        }
        if (this.stored.signum() > 0) {
            // IU 语义：仅正面出电，且排除自己（贴着自己输出面的汇就是自己）
            EuCableNet.pushFromFacing(this.level, this.worldPosition, this, getTier(),
                    getSolarTier().maxOutputEu(), facing());
        }
    }

    @Override
    public BigInteger getStoredEu() {
        return this.stored;
    }

    @Override
    public BigInteger getCapacityEu() {
        return capacityEu();
    }

    @Override
    public BigInteger getRoomEu() {
        BigInteger capacity = capacityEu();
        BigInteger stored = this.stored;
        if (this.roomForCapacity == capacity && this.roomForStored == stored) {
            return this.roomCache;
        }
        this.roomForCapacity = capacity;
        this.roomForStored = stored;
        this.roomCache = capacity.subtract(stored).max(BigInteger.ZERO);
        return this.roomCache;
    }

    @Override
    public EuTier getTier() {
        return getSolarTier().voltage();
    }

    @Override
    public BigInteger receiveEu(BigInteger amount, EuTier tier, boolean simulate) {
        if (amount == null || amount.signum() <= 0 || !getTier().accepts(tier)) {
            return BigInteger.ZERO;
        }
        BigInteger accepted = amount.min(getRoomEu());
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

    // ------------------------------------------------------------------
    // Forge Energy 桥（1 EU = 4 FE，双向）
    // ------------------------------------------------------------------

    @Override
    public int getEnergyStored() {
        return FeConvert.toFe(this.stored);
    }

    @Override
    public int getMaxEnergyStored() {
        return FeConvert.toFe(capacityEu());
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return FeConvert.toFe(extractEu(FeConvert.toEu(maxExtract), simulate));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return FeConvert.toFe(receiveEu(FeConvert.toEu(maxReceive), getTier(), simulate));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_ENERGY_B64, Base64.getEncoder().encodeToString(this.stored.toByteArray()));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BigInteger value;
        if (tag.contains(TAG_ENERGY_B64, NBT_STRING)) {
            value = new BigInteger(Base64.getDecoder().decode(tag.getString(TAG_ENERGY_B64)));
        } else if (tag.contains(TAG_ENERGY, NBT_STRING)) {
            try {
                value = new BigInteger(tag.getString(TAG_ENERGY));
            } catch (NumberFormatException e) {
                value = BigInteger.ZERO;
            }
        } else {
            value = BigInteger.valueOf(tag.getLong(TAG_ENERGY));
        }
        this.stored = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
    }
}
