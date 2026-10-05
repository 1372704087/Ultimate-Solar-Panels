package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuCableNet;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.OverVoltage;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * 玻璃电缆方块实体（1.21.1，全等级共用）。等级由方块决定，电缆不储能。
 * <p>Forge Energy 桥接：本电缆对外暴露 {@link IEnergyStorage}，把 FE 请求转给所在
 * {@code EuCableNet} 网络——抽电＝从相连面板取，进电＝送给相连的汇。这样 Mekanism
 * 的万能线缆（以及其它 FE 机器）可以直接接在玻璃电缆上，无需任何额外依赖。
 */
public class GlassCableTile extends BlockEntity implements IEnergyStorage {

    /** 超压温度上限：连续暴露 100 tick（约 5 秒）后熔断。 */
    public static final int HEAT_MAX = 100;

    @Nullable
    private GlassCableTier resolvedTier;

    /** 超压暴露累积的温度（0~{@link #HEAT_MAX}），随暴露中断冷却。 */
    private int heat;
    private long lastHeatTick = Long.MIN_VALUE;

    /** 冷却减速：冷却速率 = 升温的 1/4，烧红的电缆约 20 秒才凉透。 */
    private static final int COOL_SLOWDOWN = 4;

    /** 超压暴露加热：以当前有效温度为基准 +1；达到 {@link #HEAT_MAX} 返回 true。 */
    public boolean heatUp(long tick) {
        this.heat = effectiveHeat(tick);
        this.lastHeatTick = tick;
        this.heat = Math.min(HEAT_MAX, this.heat + 1);
        return this.heat >= HEAT_MAX;
    }

    /** 当前温度（0~{@link #HEAT_MAX}），冒烟配色用。 */
    public int heat() {
        return this.heat;
    }

    /** 有效温度 = 温度 −（距上次暴露的 tick 数 ÷ 冷却减速），随时间自动回落。 */
    private int effectiveHeat(long now) {
        if (this.lastHeatTick == Long.MIN_VALUE) {
            return 0;
        }
        long elapsed = now - this.lastHeatTick;
        return Math.max(0, this.heat - (int) (elapsed / COOL_SLOWDOWN));
    }

    /** 服务端 tick：冷却期（有效温度 &gt; 0）持续冒烟，随温度下降渐稀渐黑、凉透消失。 */
    public void serverTick() {
        if (!(this.level instanceof ServerLevel server) || this.heat <= 0) {
            return;
        }
        long now = this.level.getGameTime();
        int effective = effectiveHeat(now);
        if (effective > 0 && now % 2 == 0) {
            OverVoltage.spawnOverheatSmoke(server,
                    this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                    this.worldPosition.getZ() + 0.5, effective / (float) HEAT_MAX);
        }
    }

    public GlassCableTile(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GLASS_CABLE.get(), pos, state);
    }

    /** 等级由所在方块的实例决定（懒解析）。 */
    public GlassCableTier tier() {
        if (this.resolvedTier == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof com.example.solarpower.block.GlassCableBlock cable) {
                this.resolvedTier = cable.tier();
            }
        }
        return this.resolvedTier == null ? GlassCableTier.BASIC : this.resolvedTier;
    }

    /** 网络当前可抽出的 FE（来源于相连的太阳能板）。 */
    @Override
    public int getEnergyStored() {
        return this.level != null && !this.level.isClientSide()
                ? FeConvert.toFe(EuCableNet.storedEu(this.level, this.worldPosition)) : 0;
    }

    /** 本档电缆每 tick 的传输上限折算成 FE，作为"管道容量"对外报告。 */
    @Override
    public int getMaxEnergyStored() {
        return FeConvert.toFe(this.tier().capacityPerTick());
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
        if (this.level == null || this.level.isClientSide()) {
            return 0;
        }
        return FeConvert.toFe(EuCableNet.drain(this.level, this.worldPosition, FeConvert.toEu(maxExtract), simulate));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (this.level == null || this.level.isClientSide()) {
            return 0;
        }
        return FeConvert.toFe(EuCableNet.fill(this.level, this.worldPosition, FeConvert.toEu(maxReceive), simulate));
    }
}
