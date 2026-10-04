package com.example.solarpower.menu;

import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.tileentity.ChargepadTile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

import javax.annotation.Nullable;

/**
 * 充电座界面容器：无物品槽，同步「蓄电量」显示分量与「充电中」标志。
 */
public class ChargepadMenu extends AbstractContainerMenu {

    public static final int ID_STORED_MANTISSA = 0;
    public static final int ID_STORED_EXP = 1;
    public static final int ID_CHARGING = 2;

    private final ChargepadTile tile;
    private final ContainerData data;

    /** 客户端镜像。 */
    public BigDecimal stored = BigDecimal.ZERO;
    public boolean charging;
    private int storedMantissa;
    private int storedExp;

    public static ChargepadMenu fromBuf(int id, Inventory inv, BlockPos pos) {
        return new ChargepadMenu(id, inv, inv.player.level().getBlockEntity(pos) instanceof ChargepadTile tile ? tile : null);
    }

    public ChargepadMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof ChargepadTile tile ? tile : null);
    }

    public ChargepadMenu(int id, Inventory inv, @Nullable ChargepadTile tile) {
        super(ModMenus.CHARGEPAD.get(), id);
        this.tile = tile;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (tile == null) {
                    return 0;
                }
                return switch (index) {
                    case 0 -> tile.getStoredDisplayParts()[0];
                    case 1 -> tile.getStoredDisplayParts()[1];
                    default -> tile.isCharging() ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        this.addDataSlots(this.data);
    }

    @Nullable
    public ChargepadTile tile() {
        return this.tile;
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        switch (id) {
            case ID_STORED_MANTISSA -> this.storedMantissa = value;
            case ID_STORED_EXP -> this.storedExp = value;
            case ID_CHARGING -> this.charging = value != 0;
        }
        if (id == ID_STORED_MANTISSA || id == ID_STORED_EXP) {
            this.stored = rebuild(this.storedMantissa, this.storedExp);
        }
    }

    private static BigDecimal rebuild(int mantissa, int exponent) {
        if (mantissa <= 0) {
            return BigDecimal.ZERO;
        }
        int digits = (int) Math.log10(mantissa) + 1;
        return BigDecimal.valueOf(mantissa).scaleByPowerOfTen(exponent - (digits - 1));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.tile != null && !this.tile.isRemoved()
                && player.distanceToSqr(this.tile.getBlockPos().getX() + 0.5D,
                        this.tile.getBlockPos().getY() + 0.5D,
                        this.tile.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
