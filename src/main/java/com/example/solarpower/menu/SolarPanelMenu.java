package com.example.solarpower.menu;

import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.tileentity.SolarPanelTile;
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
 * 太阳能板界面容器：无物品槽，同步「蓄电量/发电量」显示分量与「夜晚」标志。
 * <p>数值是 BigInteger（远超窗口属性上限），同步「首 4 位有效数字 + 十进制指数」，
 * 客户端用 BigDecimal 精确重建（同储电盒/变压器容器）。
 */
public class SolarPanelMenu extends AbstractContainerMenu {

    public static final int ID_STORED_MANTISSA = 0;
    public static final int ID_STORED_EXP = 1;
    public static final int ID_GEN_MANTISSA = 2;
    public static final int ID_GEN_EXP = 3;
    public static final int ID_NIGHT = 4;

    private final SolarPanelTile tile;
    private final ContainerData data;

    /** 客户端镜像。 */
    public BigDecimal stored = BigDecimal.ZERO;
    public BigDecimal generating = BigDecimal.ZERO;
    public boolean night;
    private int storedMantissa;
    private int storedExp;
    private int genMantissa;
    private int genExp;

    public static SolarPanelMenu fromBuf(int id, Inventory inv, BlockPos pos) {
        return new SolarPanelMenu(id, inv,
                inv.player.level().getBlockEntity(pos) instanceof SolarPanelTile tile ? tile : null);
    }

    public SolarPanelMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof SolarPanelTile tile ? tile : null);
    }

    public SolarPanelMenu(int id, Inventory inv, @Nullable SolarPanelTile tile) {
        super(ModMenus.SOLAR_PANEL.get(), id);
        this.tile = tile;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (tile == null) {
                    return 0;
                }
                return switch (index) {
                    case ID_STORED_MANTISSA -> tile.getStoredDisplayParts()[0];
                    case ID_STORED_EXP -> tile.getStoredDisplayParts()[1];
                    case ID_GEN_MANTISSA -> tile.getGeneratingDisplayParts()[0];
                    case ID_GEN_EXP -> tile.getGeneratingDisplayParts()[1];
                    default -> tile.isNight() ? 1 : 0;
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
    public SolarPanelTile tile() {
        return this.tile;
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        switch (id) {
            case ID_STORED_MANTISSA -> this.storedMantissa = value;
            case ID_STORED_EXP -> this.storedExp = value;
            case ID_GEN_MANTISSA -> this.genMantissa = value;
            case ID_GEN_EXP -> this.genExp = value;
            case ID_NIGHT -> this.night = value != 0;
        }
        this.stored = rebuild(this.storedMantissa, this.storedExp);
        this.generating = rebuild(this.genMantissa, this.genExp);
    }

    /** 由「首 4 位有效数字 + 十进制指数」精确重建显示值。 */
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
        return this.tile == null || !this.tile.isRemoved();
    }
}
