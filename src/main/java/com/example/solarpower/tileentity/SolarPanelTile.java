package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuCableNet;
import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.IEuEnergy;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigInteger;
import java.util.Base64;

/**
 * 太阳能板方块实体（1.21.1，全档位共用）。
 * 判定与旧版一致：有天空光的维度 + 上方无遮挡才发电；白天按档位，
 * 夜晚（月光）为白天的分数（基础档不发电）；雨天/雷暴 ×0.65。
 * <p>发电量与蓄电量用 {@link BigInteger} 承载，不设数值上限。
 * <p>对 NeoForge 1.21.1 暴露 Forge Energy（1 EU = 4 FE，双向）。
 */
public class SolarPanelTile extends BlockEntity implements IEnergyStorage {

    /** 雨天/雷暴系数 ×0.65，用整数 65/100 精确计算。 */
    private static final BigInteger RAIN_NUMERATOR = BigInteger.valueOf(65L);
    private static final BigInteger RAIN_DENOMINATOR = BigInteger.valueOf(100L);

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ENERGY_B64 = TAG_ENERGY + "_b64";
    private static final int NBT_STRING = 8;

    private final TileEnergy storage = new TileEnergy();
    private BigInteger generating = BigInteger.ZERO;
    /** 发电量显示分量缓存（同上，按 generating 引用命中）。 */
    private BigInteger genPartsFor;
    private int[] genPartsCache;
    /** 发电量本体缓存：天气/昼夜组合 + 档位不变时直接复用（免掉每 tick 的大数乘除）。 */
    private int genWeatherKey = -1;
    private SolarTier genForTier;
    private BigInteger genCache = BigInteger.ZERO;
    private SolarTier resolvedTier;

    public SolarPanelTile(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    /** 档位由所在方块的实例决定（懒解析）。 */
    public SolarTier getTier() {
        if (this.resolvedTier == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.SolarPanelBlock panel) {
                this.resolvedTier = panel.tier();
            }
        }
        return this.resolvedTier == null ? SolarTier.BASIC : this.resolvedTier;
    }

    public IEuEnergy getEnergy() {
        return this.storage;
    }

    public BigInteger getGenerating() {
        return this.generating;
    }

    /** 蓄电量的显示分量（首 4 位有效数字 + 十进制指数），按 stored 引用惰性缓存。 */
    public int[] getStoredDisplayParts() {
        return this.storage.displayPartsCached();
    }

    /** 发电量的显示分量，按 generating 引用惰性缓存（同 stored，每 tick 取用）。 */
    public int[] getGeneratingDisplayParts() {
        if (this.genPartsFor != this.generating) {
            this.genPartsFor = this.generating;
            this.genPartsCache = EuFormat.displayParts(this.generating);
        }
        return this.genPartsCache;
    }

    public boolean isNight() {
        return this.level != null && !this.level.isDay();
    }

    // ------------------------------------------------------------------
    // 发电循环
    // ------------------------------------------------------------------

    public void tick() {
        if (this.level.isClientSide()) {
            return;
        }
        SolarTier tier = this.getTier();
        this.storage.clampToCapacity();   // 读档时档位未解析，存量在首个 tick 才夹回容量
        BigInteger generated = this.computeGeneration(tier);
        this.generating = generated;
        if (generated.signum() > 0) {
            this.storage.receiveEu(generated, tier.voltage(), false);
        }
        if (this.storage.getStoredEu().signum() > 0) {
            this.pushToCables();
        }
    }

    private BigInteger computeGeneration(SolarTier tier) {
        if (!this.level.dimensionType().hasSkyLight()) {
            return BigInteger.ZERO; // 下界/末地这类没有天空光照的维度
        }
        if (!this.level.canSeeSky(this.worldPosition.above())) {
            return BigInteger.ZERO; // 上方被方块遮挡
        }
        // 晴/雨与昼夜的组合只有 4 种，且 base 是档位常量：结果直接缓存，
        // 避免每 tick 对上百位的大数做 O(n²) 的 multiply/divide。
        int key = (this.level.isDay() ? 0 : 1) | ((this.level.isRaining() || this.level.isThundering()) ? 2 : 0);
        if (this.genWeatherKey == key && this.genForTier == tier) {
            return this.genCache;
        }
        BigInteger base;
        switch (key) {
            case 1:
                base = tier.nightGenerationEu();
                break;
            case 2:
                base = rainScaled(tier.generationEu());
                break;
            case 3:
                base = rainScaled(tier.nightGenerationEu());
                break;
            default:
                base = tier.generationEu();
                break;
        }
        this.genWeatherKey = key;
        this.genForTier = tier;
        this.genCache = base;
        return base;
    }

    /** 雨天/雷暴系数 ×0.65，用整数 65/100 精确计算。 */
    private static BigInteger rainScaled(BigInteger base) {
        if (base.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return base.multiply(RAIN_NUMERATOR).divide(RAIN_DENOMINATOR);
    }

    private void pushToCables() {
        // 四周相邻电缆的扫描由 EuCableNet.push 负责：**每个相邻电缆面各推流一次**。
        // 面板贴着同一张网的多个面时，该网每 tick 会收到多次推流（推送量按面数放大，
        // 最多 6 倍）——这是刻意保留的原始放电行为，不做按网络归并。
        EuCableNet.push(this.level, this.worldPosition, this.storage, this.getTier().voltage(),
                this.getTier().maxOutputEu());
    }

    // ------------------------------------------------------------------
    // Forge Energy（1 EU = 4 FE，双向）
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
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return FeConvert.toFe(this.storage.extractEu(FeConvert.toEu(maxExtract), simulate));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        // FE 无电压概念，按本板自身等级注入，因此不会被误判为超压
        return FeConvert.toFe(this.storage.receiveEu(FeConvert.toEu(maxReceive), this.getTier().voltage(), simulate));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_ENERGY_B64, this.storage.storedAsBase64());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BigInteger stored;
        if (tag.contains(TAG_ENERGY_B64, NBT_STRING)) {
            // 新格式：Base64 大端补码，编解码都是线性开销（巨数 toString 是平方级）
            stored = new BigInteger(Base64.getDecoder().decode(tag.getString(TAG_ENERGY_B64)));
        } else if (tag.contains(TAG_ENERGY, NBT_STRING)) {
            try {
                // 旧格式：十进制串（一次性解析，仅兼容迁移用）
                stored = new BigInteger(tag.getString(TAG_ENERGY));
            } catch (NumberFormatException e) {
                stored = BigInteger.ZERO;
            }
        } else {
            stored = BigInteger.valueOf(tag.getLong(TAG_ENERGY));
        }
        this.storage.setStored(stored);
    }

    /** 储能容器：容量/电压跟随懒解析的档位。 */
    private final class TileEnergy implements IEuEnergy {

        private BigInteger stored = BigInteger.ZERO;
        /** NBT 串缓存：自动存档周期性调用 toString，不变则免掉平方级转换。 */
        private BigInteger stringFor;
        private String stringCache;
        /** displayParts 缓存：同上，按 stored 引用命中（stored 每 tick 至多变一次）。 */
        private BigInteger partsFor;
        private int[] partsCache;
        /** 剩余容量缓存：按「存量实例 + 容量实例」作失效令牌。
         *  <p>{@link BigInteger} 不可变，任何写入都会把 stored 换成新实例，因此实例身份
         *  就是可靠的失效依据；容量取自 {@link SolarTier} 的 final 字段，实例恒定。 */
        private BigInteger roomForStored;
        private BigInteger roomForCapacity;
        private BigInteger roomCache = BigInteger.ZERO;

        @Override
        public BigInteger getStoredEu() {
            return this.stored;
        }

        @Override
        public BigInteger getCapacityEu() {
            return SolarPanelTile.this.getTier().capacityEu();
        }

        /** 剩余可注入量（带缓存，见字段注释）。稳态下整 tick 命中，退化为引用比较。 */
        @Override
        public BigInteger getRoomEu() {
            BigInteger capacity = getCapacityEu();
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
            return SolarPanelTile.this.getTier().voltage();
        }

        @Override
        public BigInteger receiveEu(BigInteger amount, EuTier tier, boolean simulate) {
            if (amount == null || amount.signum() <= 0
                    || !SolarPanelTile.this.getTier().voltage().accepts(tier)) {
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

        /** 存量的 Base64 串（大端补码，编解码线性开销；惰性缓存；自动存档周期性取用）。 */
        String storedAsBase64() {
            if (this.stringFor != this.stored) {
                this.stringFor = this.stored;
                this.stringCache = Base64.getEncoder().encodeToString(this.stored.toByteArray());
            }
            return this.stringCache;
        }

        /** 存量的显示分量（惰性缓存；容器每 tick 取用）。 */
        int[] displayPartsCached() {
            if (this.partsFor != this.stored) {
                this.partsFor = this.stored;
                this.partsCache = EuFormat.displayParts(this.stored);
            }
            return this.partsCache;
        }

        void setStored(BigInteger value) {
            // 只做符号保护、不做容量钳制：读档时档位未解析，钳制推迟到首个 tick
            this.stored = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
            setChanged();
        }

        /** 档位解析后调用：把存量夹回容量内（兼容旧档超容量的情况）。 */
        void clampToCapacity() {
            BigInteger capacity = getCapacityEu();
            if (this.stored.compareTo(capacity) > 0) {
                this.stored = capacity;
                setChanged();
            }
        }
    }
}
