package com.example.solarpower.menu;

import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.tileentity.MolecularTransformerTile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * 分子重组仪界面容器（布局照搬 ASP 官方 1.20.1 移植版，220×193）：
 * 输入槽 (20,27) / 输出槽 (20,68)，玩家背包 x=18+col*21（y=98/119/140），热栏 y=165。
 * <p>同步：能量已注入/总量各拆 lo/hi 16 位（窗口属性 short 上限），进度一个 int。
 */
public class MolecularTransformerMenu extends AbstractContainerMenu {

    public static final int ID_ENERGY_USED_LO = 0;
    public static final int ID_ENERGY_USED_HI = 1;
    public static final int ID_TOTAL_LO = 2;
    public static final int ID_TOTAL_HI = 3;
    public static final int ID_STORED_LO = 4;
    public static final int ID_STORED_HI = 5;
    public static final int ID_PROGRESS = 6;

    private static final int MACHINE_SLOTS = 2;

    private final MolecularTransformerTile tile;
    private final ContainerData data;

    /** 客户端镜像。 */
    public long energyUsed;
    public long totalEu;
    public long stored;
    public int progress;

    public static MolecularTransformerMenu fromBuf(int id, Inventory inv, BlockPos pos) {
        return new MolecularTransformerMenu(id, inv,
                inv.player.level().getBlockEntity(pos) instanceof MolecularTransformerTile t ? t : null);
    }

    public MolecularTransformerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof MolecularTransformerTile t ? t : null);
    }

    public MolecularTransformerMenu(int id, Inventory playerInv, @Nullable MolecularTransformerTile tile) {
        super(ModMenus.MOLECULAR_TRANSFORMER.get(), id);
        this.tile = tile;
        IItemHandler input = tile != null ? tile.getInput() : null;
        IItemHandler output = tile != null ? tile.getOutput() : null;
        // 机器槽（tile 缺失时用空处理器占位，索引保持一致）
        this.addSlot(new SlotItemHandler(input != null ? input : new net.neoforged.neoforge.items.ItemStackHandler(1), 0, 20, 27));
        this.addSlot(new SlotItemHandler(output != null ? output : new net.neoforged.neoforge.items.ItemStackHandler(1), 0, 20, 68));
        // 玩家背包（21px 栅格，与底图一致）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        18 + col * 21, 98 + row * 21));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 18 + col * 21, 165));
        }
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (tile == null) {
                    return 0;
                }
                return switch (index) {
                    case ID_ENERGY_USED_LO -> lo16(tile.getEnergyUsed());
                    case ID_ENERGY_USED_HI -> hi16(tile.getEnergyUsed());
                    case ID_TOTAL_LO -> lo16(tile.getTotalEu());
                    case ID_TOTAL_HI -> hi16(tile.getTotalEu());
                    case ID_STORED_LO -> lo16(sat32(tile.getStoredEu().longValue()));
                    case ID_STORED_HI -> hi16(sat32(tile.getStoredEu().longValue()));
                    default -> tile.comparatorLevel();
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 7;
            }
        };
        this.addDataSlots(this.data);
    }

    private static int sat32(long v) {
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    private static int lo16(int v) {
        return v & 0xFFFF;
    }

    private static int hi16(int v) {
        return (v >>> 16) & 0xFFFF;
    }

    @Nullable
    public MolecularTransformerTile tile() {
        return this.tile;
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        switch (id) {
            case ID_ENERGY_USED_LO -> this.energyUsed = (this.energyUsed & ~0xFFFFL) | value;
            case ID_ENERGY_USED_HI -> this.energyUsed = (this.energyUsed & ~0xFFFF0000L) | ((long) value << 16);
            case ID_TOTAL_LO -> this.totalEu = (this.totalEu & ~0xFFFFL) | value;
            case ID_TOTAL_HI -> this.totalEu = (this.totalEu & ~0xFFFF0000L) | ((long) value << 16);
            case ID_STORED_LO -> this.stored = (this.stored & ~0xFFFFL) | value;
            case ID_STORED_HI -> this.stored = (this.stored & ~0xFFFF0000L) | ((long) value << 16);
            case ID_PROGRESS -> this.progress = value;
        }
    }

    /** shift-click：机器槽 ⇄ 玩家背包。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack rest = original.copy();
        int size = this.slots.size();
        if (index < MACHINE_SLOTS) {
            if (!this.moveItemStackTo(original, MACHINE_SLOTS, size, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(original, 0, MACHINE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (rest.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return rest;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.tile == null || !this.tile.isRemoved();
    }
}
