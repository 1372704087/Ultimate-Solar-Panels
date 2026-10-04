package com.example.solarpower;

import com.example.solarpower.component.ModComponents;
import com.example.solarpower.registry.ModBlockEntities;
import com.example.solarpower.registry.ModBlocks;
import com.example.solarpower.registry.ModCreativeTabs;
import com.example.solarpower.registry.ModMenus;
import com.example.solarpower.registry.ModItems;
import com.example.solarpower.tileentity.ChargepadTile;
import com.example.solarpower.tileentity.GlassCableTile;
import com.example.solarpower.tileentity.SolarPanelTile;
import com.example.solarpower.tileentity.StorageBoxTile;
import com.example.solarpower.tileentity.TransformerTile;
import com.example.solarpower.tileentity.MolecularTransformerTile;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 终极太阳能（Ultimate Solar）——工业级档位太阳能与 EU 机械设备。
 * NeoForge 1.21.1 重写版：全量 BigInteger EU 数值体系、67 档太阳能板、
 * 68 档玻璃电缆、储电盒/变压器/充电座/分子重组仪与全套材料链。
 */
@Mod(SolarPower.MODID)
public final class SolarPower {

    public static final String MODID = "solarpower";
    public static final String NAME = "Ultimate Solar";
    public static final String VERSION = "1.0.0";

    public SolarPower(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(this::registerCapabilities);
    }

    /** 方块实体能力：面板/电缆/储电盒/变压器暴露 Forge Energy（1 EU = 4 FE）。 */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SOLAR_PANEL.get(), (SolarPanelTile be, net.minecraft.core.Direction side) -> be);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.GLASS_CABLE.get(), (GlassCableTile be, net.minecraft.core.Direction side) -> be);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.STORAGE_BOX.get(), (StorageBoxTile be, net.minecraft.core.Direction side) -> be);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.TRANSFORMER.get(), (TransformerTile be, net.minecraft.core.Direction side) -> be);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CHARGEPAD.get(), (ChargepadTile be, net.minecraft.core.Direction side) -> be);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.MOLECULAR_TRANSFORMER.get(),
                (MolecularTransformerTile be, net.minecraft.core.Direction side) -> be);
    }
}
