package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.menu.StorageBoxMenu;
import com.example.solarpower.menu.TransformerMenu;
import com.example.solarpower.menu.ChargepadMenu;
import com.example.solarpower.menu.MolecularTransformerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 菜单类型注册（1.21.1）：从打开缓冲读方块坐标定位方块实体。 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SolarPower.MODID);

    public static final Supplier<MenuType<StorageBoxMenu>> STORAGE_BOX =
            MENUS.register("storage_box", () -> IMenuTypeExtension.create(
                    (id, inventory, buf) -> new StorageBoxMenu(id, inventory, buf.readBlockPos())));

    public static final Supplier<MenuType<TransformerMenu>> TRANSFORMER =
            MENUS.register("transformer", () -> IMenuTypeExtension.create(
                    (id, inventory, buf) -> new TransformerMenu(id, inventory, buf.readBlockPos())));

    public static final Supplier<MenuType<MolecularTransformerMenu>> MOLECULAR_TRANSFORMER =
            MENUS.register("molecular_transformer", () -> IMenuTypeExtension.create(
                    (id, inventory, buf) -> new MolecularTransformerMenu(id, inventory, buf.readBlockPos())));

    public static final Supplier<MenuType<ChargepadMenu>> CHARGEPAD =
            MENUS.register("chargepad", () -> IMenuTypeExtension.create(
                    (id, inventory, buf) -> new ChargepadMenu(id, inventory, buf.readBlockPos())));

    private ModMenus() {
    }
}
