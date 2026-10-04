package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuCableNet;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.IEuEnergy;
import com.example.solarpower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.math.BigInteger;
import java.util.Base64;

/**
 * 变压器方块实体（1.21.1，65 档，桥接电压 T↔T+1）。IC2 语义的双向转换：
 * 正面（能量芯面）= 高压侧（T+1），其余五面 = 低压侧（T）。不设模式开关——
 * 电从哪一侧进来，就从另一侧按对方电压输出（高压进→低压出 = 降压，反之 = 升压）。
 * <p>实现上按进电电压分流到两个内部缓冲池；每 tick 双向各自向相邻电缆推流一次，
 * 方向吞吐上限 = 低压档电压 2^(2T+3)（IC2「每 tick 一个本档电压包」的连续化）。
 * 池容量 = 吞吐 ×8，只作缓冲不做储能（储电请用储电盒）。
 */
public class TransformerTile extends BlockEntity implements IEuEnergy, IEnergyStorage {

    private static final int POOL_CAPACITY_TICKS = 8;

    private static final String TAG_LOW_B64 = "Low_b64";
    private static final String TAG_HIGH_B64 = "High_b64";

    private BigInteger lowStored = BigInteger.ZERO;
    private BigInteger highStored = BigInteger.ZERO;
    private EuTier resolvedLowTier;

    private EuTier lowTierCache;
    private EuTier highTierCache;
    private BigInteger rateCache;

    private BigInteger comparatorFor;
    private BigInteger comparatorForHigh;
    private int comparatorCache;

    private BigInteger lowPartsFor;
    private int[] lowPartsCache;
    private BigInteger highPartsFor;
    private int[] highPartsCache;

    public TransformerTile(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSFORMER.get(), pos, state);
    }

    /** 低压侧档位（懒解析，回落 LV）。 */
    public EuTier lowTier() {
        if (this.lowTierCache == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.TransformerBlock block) {
                this.lowTierCache = block.tier();
            }
        }
        return this.lowTierCache == null ? EuTier.LV : this.lowTierCache;
    }

    public EuTier highTier() {
        if (this.highTierCache == null) {
            this.highTierCache = EuTier.byIuTier(lowTier().iuTier() + 1);
        }
        return this.highTierCache;
    }

    /** 每 tick 每方向的转换吞吐上限 = 低压档电压 2^(2T+3)。 */
    public BigInteger rate() {
        if (this.rateCache == null) {
            this.rateCache = lowTier().maxVoltage();
        }
        return this.rateCache;
    }

    /** 池容量 = 吞吐 ×8。 */
    public BigInteger poolCapacity() {
        return rate().shiftLeft(3);
    }

    /** 正面（高压侧）朝向，读方块状态。 */
    public Direction facing() {
        if (this.level != null
                && this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.TransformerBlock) {
            return this.level.getBlockState(this.worldPosition)
                    .getValue(com.example.solarpower.block.TransformerBlock.FACING);
        }
        return Direction.NORTH;
    }

    public BigInteger getLowStored() {
        return this.lowStored;
    }

    public BigInteger getHighStored() {
        return this.highStored;
    }

    public int[] getLowDisplayParts() {
        if (this.lowPartsFor != this.lowStored) {
            this.lowPartsFor = this.lowStored;
            this.lowPartsCache = com.example.solarpower.energy.EuFormat.displayParts(this.lowStored);
        }
        return this.lowPartsCache;
    }

    public int[] getHighDisplayParts() {
        if (this.highPartsFor != this.highStored) {
            this.highPartsFor = this.highStored;
            this.highPartsCache = com.example.solarpower.energy.EuFormat.displayParts(this.highStored);
        }
        return this.highPartsCache;
    }

    /** 比较器：两池合计占两池总容量的比例 0~15。 */
    public int comparatorLevel() {
        BigInteger cap = poolCapacity();
        if (this.comparatorFor != this.lowStored || this.comparatorForHigh != this.highStored) {
            this.comparatorFor = this.lowStored;
            this.comparatorForHigh = this.highStored;
            BigInteger total = this.lowStored.add(this.highStored);
            this.comparatorCache = total.shiftLeft(4).divide(cap.shiftLeft(1)).intValueExact();
        }
        return Math.max(0, Math.min(15, this.comparatorCache));
    }

    // ------------------------------------------------------------------
    // 转换循环
    // ------------------------------------------------------------------

    public void tick() {
        if (this.level.isClientSide) {
            return;
        }
        BigInteger cap = poolCapacity();
        if (this.lowStored.compareTo(cap) > 0) {
            this.lowStored = cap;
            setChanged();
        }
        if (this.highStored.compareTo(cap) > 0) {
            this.highStored = cap;
            setChanged();
        }
        // 高压池：从正面（唯一高压面）输出
        if (this.highStored.signum() > 0) {
            EuCableNet.pushFromFacing(this.level, this.worldPosition, this, highTier(),
                    rate(), facing());
        }
        // 低压池：从其余五面输出（IU 变压器低压侧全面放电）
        if (this.lowStored.signum() > 0) {
            for (Direction dir : Direction.values()) {
                if (dir == facing()) {
                    continue;
                }
                if (this.lowStored.signum() <= 0) {
                    break;
                }
                EuCableNet.pushFromFacing(this.level, this.worldPosition, this, lowTier(),
                        rate(), dir);
            }
        }
    }

    // ------------------------------------------------------------------
    // IEuEnergy：按来包电压分流
    // ------------------------------------------------------------------

    @Override
    public BigInteger getStoredEu() {
        return this.lowStored.add(this.highStored);
    }

    @Override
    public BigInteger getCapacityEu() {
        return poolCapacity().shiftLeft(1);   // 两池合计
    }

    @Override
    public BigInteger getRoomEu() {
        BigInteger cap = poolCapacity();
        return cap.subtract(this.lowStored).max(BigInteger.ZERO)
                .add(cap.subtract(this.highStored).max(BigInteger.ZERO));
    }

    @Override
    public EuTier getTier() {
        return highTier();
    }

    /**
     * 按来包电压分流：低压包（≤T）进低压池、高压包（≤T+1）进高压池，更高拒收。
     * 面无关——电网按来方电压即可正确路由，等价于 IC2 的「哪侧进电走哪侧池」。
     */
    @Override
    public BigInteger receiveEu(BigInteger amount, EuTier tier, boolean simulate) {
        if (amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger cap = poolCapacity();
        boolean toLow = lowTier().accepts(tier);
        if (!toLow && !highTier().accepts(tier)) {
            return BigInteger.ZERO;
        }
        BigInteger target = toLow ? this.lowStored : this.highStored;
        BigInteger accepted = amount.min(cap.subtract(target).max(BigInteger.ZERO));
        if (accepted.signum() <= 0) {
            return BigInteger.ZERO;
        }
        if (!simulate) {
            if (toLow) {
                this.lowStored = this.lowStored.add(accepted);
            } else {
                this.highStored = this.highStored.add(accepted);
            }
            setChanged();
        }
        return accepted;
    }

    /** 通用抽取：先低压池后高压池（FE 桥与电网 drain 用）。 */
    @Override
    public BigInteger extractEu(BigInteger amount, boolean simulate) {
        if (amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger fromLow = amount.min(this.lowStored);
        BigInteger fromHigh = amount.subtract(fromLow).min(this.highStored);
        BigInteger extracted = fromLow.add(fromHigh);
        if (extracted.signum() <= 0) {
            return BigInteger.ZERO;
        }
        if (!simulate) {
            this.lowStored = this.lowStored.subtract(fromLow);
            this.highStored = this.highStored.subtract(fromHigh);
            setChanged();
        }
        return extracted;
    }

    // ------------------------------------------------------------------
    // Forge Energy 桥（挂低压池，1 EU = 4 FE）
    // ------------------------------------------------------------------

    @Override
    public int getEnergyStored() {
        return FeConvert.toFe(this.lowStored);
    }

    @Override
    public int getMaxEnergyStored() {
        return FeConvert.toFe(poolCapacity());
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
        return FeConvert.toFe(receiveEu(FeConvert.toEu(maxReceive), lowTier(), simulate));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_LOW_B64, Base64.getEncoder().encodeToString(this.lowStored.toByteArray()));
        tag.putString(TAG_HIGH_B64, Base64.getEncoder().encodeToString(this.highStored.toByteArray()));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_LOW_B64)) {
            this.lowStored = new BigInteger(Base64.getDecoder().decode(tag.getString(TAG_LOW_B64)));
        }
        if (tag.contains(TAG_HIGH_B64)) {
            this.highStored = new BigInteger(Base64.getDecoder().decode(tag.getString(TAG_HIGH_B64)));
        }
    }
}
