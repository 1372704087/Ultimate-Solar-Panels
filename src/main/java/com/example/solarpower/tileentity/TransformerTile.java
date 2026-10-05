package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuCableNet;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.IEuEnergy;
import com.example.solarpower.energy.OverVoltage;
import com.example.solarpower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.math.BigInteger;
import java.util.Base64;

/**
 * 变压器方块实体（1.21.1，67 档，桥接电压 T−1↔T）。IC2 语义的双向转换：
 * 正面（能量芯面）= 当前等级（T），其余五面 = 低一级（T−1）。
 * <p>工作模式（界面按钮切换，见 {@link #MODE_OFF}）：
 * 关闭 = 不收电不输出；自动 = 双向转换（按面额定收电：五面 ≤T−1、正面 ≤T，超面额定超压暴露）；
 * 升压 = 只从五面收 ≤T−1 转成 T 从正面输出；降压 = 只从正面收 ≤T 降到 T−1 从五面输出。
 * <p>实现上按进电电压分流到两个内部缓冲池；每 tick 各自向相邻电缆推流一次，
 * 方向吞吐上限 = 本档面板最大输出。
 * 池容量 = 吞吐 ×8，只作缓冲不做储能（储电请用储电盒）。
 */
public class TransformerTile extends BlockEntity implements IEuEnergy, IEnergyStorage {

    private static final int POOL_CAPACITY_TICKS = 8;

    /** 跨档转换损耗：从另一档池取电按取 20 交付 19（损耗 5%）；同档池无损。 */
    private static final BigInteger CROSS_DRAW_NUM = BigInteger.valueOf(20);
    private static final BigInteger CROSS_DELIVER_NUM = BigInteger.valueOf(19);

    private static final String TAG_LOW_B64 = "Low_b64";
    private static final String TAG_HIGH_B64 = "High_b64";
    private static final String TAG_MODE = "Mode";

    /** 关闭：不收电、不输出、不转换（安全的隔离状态，新放置的默认档）。 */
    public static final int MODE_OFF = 0;
    /** 自动：双向转换——五面的电升到当前等级从正面出，正面的电降到低一级从五面出（按面额定收电）。 */
    public static final int MODE_AUTO = 1;
    /** 升压：只从五面收低等级电（≤低一级），转成当前等级从正面输出。 */
    public static final int MODE_STEP_UP = 2;
    /** 降压：只从正面收当前等级电（≤T），降到低一级从五面输出。 */
    public static final int MODE_STEP_DOWN = 3;

    private BigInteger lowStored = BigInteger.ZERO;
    private BigInteger highStored = BigInteger.ZERO;
    private EuTier resolvedLowTier;
    private com.example.solarpower.solar.SolarTier resolvedSolarTier;
    private int mode = MODE_OFF;
    private final OverVoltage overVoltage = new OverVoltage();

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

    /** 高压侧 = 本机工作电压（当前等级 T，由所在方块决定；懒解析，回落 LV）。 */
    public EuTier highTier() {
        if (this.highTierCache == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.TransformerBlock block) {
                this.highTierCache = block.tier();
            }
        }
        return this.highTierCache == null ? EuTier.LV : this.highTierCache;
    }

    /** 低压侧 = 当前等级 − 1（1 级变压器低压侧仍为 LV）。 */
    public EuTier lowTier() {
        return EuTier.byIuTier(highTier().iuTier() - 1);
    }

    /** 升压方向吞吐 = 本档面板最大输出（进电侧由供电源自然限流；降压方向见 {@link #rateDown()}）。 */
    public BigInteger rate() {
        if (this.rateCache == null) {
            this.rateCache = getSolarTier().maxOutputEu();
        }
        return this.rateCache;
    }

    private BigInteger rateDownCache;

    /** 降压方向吞吐 = 前一档面板最大输出（基础档无前档，取自身发电量 32 EU/t）。 */
    public BigInteger rateDown() {
        if (this.rateDownCache == null) {
            com.example.solarpower.solar.SolarTier self = getSolarTier();
            com.example.solarpower.solar.SolarTier prev = self.ordinal() >= 1
                    ? com.example.solarpower.solar.SolarTier.values()[self.ordinal() - 1] : null;
            this.rateDownCache = prev != null ? prev.maxOutputEu() : self.generationEu();
        }
        return this.rateDownCache;
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

    /** 本体所属面板档（懒解析，回落基础档）：界面标题配色用，与方块/物品外观一致。 */
    public com.example.solarpower.solar.SolarTier getSolarTier() {
        if (this.resolvedSolarTier == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.TransformerBlock block) {
                this.resolvedSolarTier = block.solarTier();
            }
        }
        return this.resolvedSolarTier == null
                ? com.example.solarpower.solar.SolarTier.BASIC : this.resolvedSolarTier;
    }

    /** 当前放电方向模式（{@link #MODE_AUTO}/{@link #MODE_STEP_UP}/{@link #MODE_STEP_DOWN}）。 */
    public int getMode() {
        return this.mode;
    }

    /** 切换工作模式（界面按钮调用，仅服务端）：关闭 → 自动 → 升压 → 降压 循环；越界回落关闭。 */
    public void setMode(int value) {
        int next = value < MODE_OFF || value > MODE_STEP_DOWN ? MODE_OFF : value;
        if (this.mode != next) {
            this.mode = next;
            setChanged();
        }
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

    /** 输出视图：优先从输出档同侧的池无损取电，不足从另一档池跨档补足——
     *  跨档部分按 {@link #CROSS_DRAW_NUM}/{@link #CROSS_DELIVER_NUM} 计 5% 转换损耗。
     *  extract 返回实付可交付量，绝不超出请求（保证电网结算不凭空产生能量）。 */
    private IEuEnergy outletView(boolean preferHigh) {
        return new IEuEnergy() {
            @Override
            public BigInteger getStoredEu() {
                return lowStored.add(highStored);
            }

            @Override
            public BigInteger getCapacityEu() {
                return poolCapacity();
            }

            @Override
            public EuTier getTier() {
                return preferHigh ? highTier() : lowTier();
            }

            @Override
            public BigInteger receiveEu(BigInteger amount, EuTier tier, boolean simulate) {
                return BigInteger.ZERO;
            }

            @Override
            public BigInteger extractEu(BigInteger amount, boolean simulate) {
                BigInteger pref = preferHigh ? highStored : lowStored;
                BigInteger takePref = amount.min(pref);
                BigInteger rest = amount.subtract(takePref);
                BigInteger sec = preferHigh ? lowStored : highStored;
                // 跨档补足：取 sec×20/19 才能交付 sec×19/20，按存量封顶
                BigInteger drawSec = rest.multiply(CROSS_DRAW_NUM)
                        .divide(CROSS_DELIVER_NUM)
                        .min(sec);
                BigInteger deliveredSec = drawSec.multiply(CROSS_DELIVER_NUM)
                        .divide(CROSS_DRAW_NUM);
                if (!simulate) {
                    if (preferHigh) {
                        highStored = highStored.subtract(takePref);
                        lowStored = lowStored.subtract(drawSec);
                    } else {
                        lowStored = lowStored.subtract(takePref);
                        highStored = highStored.subtract(drawSec);
                    }
                    setChanged();
                }
                return takePref.add(deliveredSec);
            }
        };
    }

    // ------------------------------------------------------------------
    // 转换循环
    // ------------------------------------------------------------------

    public void tick() {
        if (this.level.isClientSide()) {
            return;
        }
        if (this.overVoltage.tick(this.level, this.worldPosition)) {
            explode();
            return;
        }
        if (this.mode == MODE_OFF) {
            return;   // 关闭：不转换不输出，池内余量冻结、温度自然冷却
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
        // 输出：正面以当前等级（优先高压池）、五面以低一级（优先低压池）——
        // 跨档取电自动计 5% 转换损耗（见 outletView）
        if (this.mode != MODE_STEP_DOWN && this.getStoredEu().signum() > 0) {
            EuCableNet.pushFromFacing(this.level, this.worldPosition,
                    outletView(true), highTier(), rate(), facing());
        }
        if (this.mode != MODE_STEP_UP && this.getStoredEu().signum() > 0) {
            for (Direction dir : Direction.values()) {
                if (dir == facing() || this.getStoredEu().signum() <= 0) {
                    continue;
                }
                EuCableNet.pushFromFacing(this.level, this.worldPosition,
                        outletView(false), lowTier(), rateDown(), dir);
            }
        }
    }

    /** 超压爆炸：机器消失（不掉自身），原版爆炸附带范围伤害与破坏方块。 */
    private void explode() {
        if (this.level == null) {
            return;
        }
        this.level.destroyBlock(this.worldPosition, false);
        this.level.explode(null, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.BLOCK);
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
        if (this.mode == MODE_OFF) {
            return BigInteger.ZERO;   // 关闭：不收电（静默，不判定超压）
        }
        return routeInput(amount, tier, simulate, null);   // 无方向（FE 桥）：按当前模式进电面额定收
    }

    /** 面感知入电：按所在面在当前模式下的额定收电（进电面超额定即超压暴露；输出面静默拒收）。 */
    @Override
    public BigInteger receiveEuOnFace(Direction face, BigInteger amount, EuTier tier, boolean simulate) {
        if (this.mode == MODE_OFF) {
            return BigInteger.ZERO;
        }
        return routeInput(amount, tier, simulate, face);
    }

    /** 该面在当前模式下是否为进电面：自动 = 全部面；升压 = 只五面；降压 = 只正面；关闭 = 无。 */
    private boolean acceptsInputOnFace(Direction face) {
        if (this.mode == MODE_AUTO) {
            return true;
        }
        if (this.mode == MODE_STEP_UP) {
            return face != facing();
        }
        if (this.mode == MODE_STEP_DOWN) {
            return face == facing();
        }
        return false;
    }

    /** 入电路由：face 为 null 按当前模式的进电面额定收电（FE 桥/汇候选 viability）；
     *  有 face 时先判该面是否进电面（输出面静默拒收），再按所在面额定判定超压。 */
    private BigInteger routeInput(BigInteger amount, EuTier tier, boolean simulate, Direction face) {
        if (amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        boolean toLow;
        if (face == null) {
            if (this.mode == MODE_STEP_UP) {
                // 升压：进电面只有五面（额定低一级）
                if (!lowTier().accepts(tier)) {
                    this.overVoltage.flag();
                    return BigInteger.ZERO;
                }
                toLow = true;
            } else if (this.mode == MODE_STEP_DOWN) {
                // 降压：进电面只有正面（额定当前等级）
                if (!highTier().accepts(tier)) {
                    this.overVoltage.flag();
                    return BigInteger.ZERO;
                }
                toLow = false;
            } else {
                // 自动：按来压分池
                toLow = lowTier().accepts(tier);
                if (!toLow && !highTier().accepts(tier)) {
                    this.overVoltage.flag();
                    return BigInteger.ZERO;
                }
            }
        } else {
            if (!acceptsInputOnFace(face)) {
                return BigInteger.ZERO;   // 该面是输出面/已关闭：静默拒收（不判定超压）
            }
            boolean isFront = face == facing();
            if (!(isFront ? highTier() : lowTier()).accepts(tier)) {
                this.overVoltage.flag();   // 超过所在面额定：超压暴露
                return BigInteger.ZERO;
            }
            toLow = !isFront;
        }
        BigInteger cap = poolCapacity();
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
        tag.putInt(TAG_MODE, this.mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String low = tag.getString(TAG_LOW_B64);
        if (!low.isEmpty()) {
            this.lowStored = new BigInteger(Base64.getDecoder().decode(low));
        }
        String high = tag.getString(TAG_HIGH_B64);
        if (!high.isEmpty()) {
            this.highStored = new BigInteger(Base64.getDecoder().decode(high));
        }
        this.mode = Math.min(MODE_STEP_DOWN, Math.max(MODE_AUTO, tag.getInt(TAG_MODE)));
    }
}
