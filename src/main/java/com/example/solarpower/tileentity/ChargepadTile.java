package com.example.solarpower.tileentity;

import com.example.solarpower.energy.EuTier;
import com.example.solarpower.energy.FeConvert;
import com.example.solarpower.energy.IEuEnergy;
import com.example.solarpower.block.ChargepadBlock;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

/**
 * 充电座方块实体（全档位共用，档位绑定 SolarTier）。
 * 纯受电（不向电缆放电），容量 = 面板缓冲 x64；
 * 玩家站在顶面上时，按本档面板最大输出（EU/t）给其盔甲 + 背包里所有暴露
 * Forge Energy 的物品充电（1 EU = 4 FE），充电中顶面切换为发光态。
 */
public class ChargepadTile extends BlockEntity implements IEuEnergy, IEnergyStorage {

    private static final int CAPACITY_SHIFT = 6;

    private static final String TAG_ENERGY_B64 = "Energy_b64";

    private BigInteger stored = BigInteger.ZERO;
    private SolarTier resolvedTier;

    private SolarTier capacityForTier;
    private BigInteger capacityCache;

    private BigInteger roomForStored;
    private BigInteger roomForCapacity;
    private BigInteger roomCache = BigInteger.ZERO;

    private BigInteger partsFor;
    private int[] partsCache;

    public ChargepadTile(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGEPAD.get(), pos, state);
    }

    /** 档位由所在方块的实例决定（懒解析，回落基础档）。 */
    public SolarTier getSolarTier() {
        if (this.resolvedTier == null && this.level != null) {
            if (this.level.getBlockState(this.worldPosition).getBlock() instanceof ChargepadBlock pad) {
                this.resolvedTier = pad.tier();
            }
        }
        return this.resolvedTier == null ? SolarTier.BASIC : this.resolvedTier;
    }

    /** 容量 = 面板缓冲 x64（按档位实例缓存）。 */
    public BigInteger capacityEu() {
        SolarTier tier = getSolarTier();
        if (this.capacityForTier != tier) {
            this.capacityForTier = tier;
            this.capacityCache = tier.capacityEu().shiftLeft(CAPACITY_SHIFT);
        }
        return this.capacityCache;
    }

    /** 存量的显示分量（首 4 位有效数字 + 十进制指数，惰性缓存）。 */
    public int[] getStoredDisplayParts() {
        if (this.partsFor != this.stored) {
            this.partsFor = this.stored;
            this.partsCache = com.example.solarpower.energy.EuFormat.displayParts(this.stored);
        }
        return this.partsCache;
    }

    /** 是否正在给玩家充电（读方块 ACTIVE 态；GUI 显示用）。 */
    public boolean isCharging() {
        return this.level != null
                && this.level.getBlockState(this.worldPosition).getBlock() instanceof ChargepadBlock pad
                && this.level.getBlockState(this.worldPosition).getValue(ChargepadBlock.ACTIVE);
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
        boolean charging = chargePlayersAbove();
        setActive(charging);
    }

    /** 给顶面上玩家的盔甲 + 背包 FE 物品充电，返回本 tick 是否充出了电量。 */
    private boolean chargePlayersAbove() {
        if (this.stored.signum() <= 0) {
            return false;
        }
        BigInteger budget = getSolarTier().maxOutputEu().min(this.stored);
        if (budget.signum() <= 0) {
            return false;
        }
        AABB above = new AABB(this.worldPosition).move(0, 1, 0);
        List<Player> players = this.level.getEntitiesOfClass(Player.class, above);
        boolean charged = false;
        for (Player player : players) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                BigInteger before = budget;
                budget = chargeStack(stack, budget);
                if (budget.compareTo(before) < 0) {
                    charged = true;
                }
                if (budget.signum() <= 0) {
                    return true;
                }
            }
        }
        return charged;
    }

    /** 用预算给单件物品充 FE（1 EU = 4 FE），扣减座内蓄电。 */
    private BigInteger chargeStack(ItemStack stack, BigInteger budget) {
        if (stack.isEmpty() || budget.signum() <= 0) {
            return budget;
        }
        EnergyHandler item = stack.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(stack));
        if (item == null) {
            return budget;
        }
        int fe = FeConvert.toFe(budget);
        if (fe <= 0) {
            return budget;
        }
        int accepted;
        try (Transaction tx = Transaction.openRoot()) {
            accepted = item.insert(fe, tx);
            if (accepted > 0) {
                tx.commit();
            }
        }
        if (accepted <= 0) {
            return budget;
        }
        BigInteger cost = FeConvert.toEu(accepted);
        this.stored = this.stored.subtract(cost).max(BigInteger.ZERO);
        setChanged();
        return budget.subtract(cost).max(BigInteger.ZERO);
    }

    /** 充电中切换顶面发光态。 */
    private void setActive(boolean active) {
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.getBlock() instanceof ChargepadBlock pad
                && state.getValue(ChargepadBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition, state.setValue(ChargepadBlock.ACTIVE, active), 3);
        }
    }

    // ------------------------------------------------------------------
    // IEuEnergy（纯受电：不向电缆放电，电网自动把它当汇）
    // ------------------------------------------------------------------

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
        return BigInteger.ZERO;
    }

    // ------------------------------------------------------------------
    // Forge Energy 桥（纯受电，1 EU = 4 FE）
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
        return FeConvert.toFe(receiveEu(FeConvert.toEu(maxReceive), getTier(), simulate));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString(TAG_ENERGY_B64, Base64.getEncoder().encodeToString(this.stored.toByteArray()));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.stored = in.getString(TAG_ENERGY_B64)
                .map(s -> new BigInteger(Base64.getDecoder().decode(s)))
                .orElse(BigInteger.ZERO);
    }
}
