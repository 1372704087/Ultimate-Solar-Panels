package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** 方块物品注册（1.21.1）：面板/电缆的 BlockItem 与方块同 id。 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SolarPower.MODID);

    private static final Map<SolarTier, DeferredItem<BlockItem>> PANEL_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<GlassCableTier, DeferredItem<BlockItem>> CABLE_ITEMS = new EnumMap<>(GlassCableTier.class);

    static {
        for (SolarTier tier : SolarTier.values()) {
            PANEL_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.PANEL_LIST.get(tier.ordinal())));
        }
        for (GlassCableTier tier : GlassCableTier.values()) {
            CABLE_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.CABLE_LIST.get(tier.ordinal())));
        }
    }

    public static Item panelItem(SolarTier tier) {
        return PANEL_ITEMS.get(tier).get();
    }

    public static Item cableItem(GlassCableTier tier) {
        return CABLE_ITEMS.get(tier).get();
    }

    private ModItems() {
    }
}
