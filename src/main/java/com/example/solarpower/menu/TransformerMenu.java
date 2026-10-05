package com.example.solarpower.menu;

import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.tileentity.TransformerTile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

import javax.annotation.Nullable;

/**
 * 变压器界面容器：无物品槽，同步「低压池/高压池」显示分量
 * （首 4 位有效数字 + 十进制指数，各两个 int）。
 */
public class TransformerMenu extends AbstractContainerMenu {

    public static final int ID_LOW_MANTISSA = 0;
    public static final int ID_LOW_EXP = 1;
    public static final int ID_HIGH_MANTISSA = 2;
    public static final int ID_HIGH_EXP = 3;
    public static final int ID_MODE = 4;

    private final TransformerTile tile;
    private final ContainerData data;

    /** 客户端镜像。 */
    public BigDecimal lowStored = BigDecimal.ZERO;
    public BigDecimal highStored = BigDecimal.ZERO;
    public int mode;
    private int lowMantissa;
    private int lowExp;
    private int highMantissa;
    private int highExp;

    public static TransformerMenu fromBuf(int id, Inventory inv, BlockPos pos) {
        return new TransformerMenu(id, inv, inv.player.level().getBlockEntity(pos) instanceof TransformerTile tile ? tile : null);
    }

    public TransformerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof TransformerTile tile ? tile : null);
    }

    public TransformerMenu(int id, Inventory inv, @Nullable TransformerTile tile) {
        super(ModMenus.TRANSFORMER.get(), id);
        this.tile = tile;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                int[] low = tile != null ? tile.getLowDisplayParts() : new int[2];
                int[] high = tile != null ? tile.getHighDisplayParts() : new int[2];
                return switch (index) {
                    case 0 -> low[0];
                    case 1 -> low[1];
                    case 2 -> high[0];
                    case 3 -> high[1];
                    default -> tile != null ? tile.getMode() : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 5;
            }
        };
        this.addDataSlots(this.data);
    }

    @Nullable
    public TransformerTile tile() {
        return this.tile;
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        switch (id) {
            case ID_LOW_MANTISSA -> this.lowMantissa = value;
            case ID_LOW_EXP -> this.lowExp = value;
            case ID_HIGH_MANTISSA -> this.highMantissa = value;
            case ID_HIGH_EXP -> this.highExp = value;
            case ID_MODE -> this.mode = value;
        }
        this.lowStored = rebuild(this.lowMantissa, this.lowExp);
        this.highStored = rebuild(this.highMantissa, this.highExp);
    }

    /** 界面按钮 0：循环切换放电方向（双向 → 升压 → 降压）。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && this.tile != null) {
            this.tile.setMode((this.tile.getMode() + 1) % 4);
            return true;
        }
        return false;
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
