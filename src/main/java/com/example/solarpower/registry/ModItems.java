package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 物品注册（1.21.1）：面板/电缆/机器 BlockItem + 全部材料物品族 + 合成链物品。 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SolarPower.MODID);

    private static final Map<SolarTier, DeferredItem<BlockItem>> PANEL_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<GlassCableTier, DeferredItem<BlockItem>> CABLE_ITEMS = new EnumMap<>(GlassCableTier.class);
    private static final Map<SolarTier, DeferredItem<BlockItem>> STORAGE_BOX_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<EuTier, DeferredItem<BlockItem>> TRANSFORMER_ITEMS = new EnumMap<>(EuTier.class);
    private static final Map<SolarTier, DeferredItem<BlockItem>> CHARGEPAD_ITEMS = new EnumMap<>(SolarTier.class);
    private static DeferredItem<BlockItem> MT_ITEM;
    private static final Map<SolarTier, DeferredItem<Item>> PANE_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<SolarTier, DeferredItem<Item>> SUNNARIUM_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<SolarTier, DeferredItem<Item>> SUNNARIUM_PART_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<SolarTier, DeferredItem<Item>> SUNNARIUM_ALLOY_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<SolarTier, DeferredItem<Item>> CORE_ITEMS = new EnumMap<>(SolarTier.class);
    private static final Map<String, DeferredItem<Item>> CRAFTING_ITEMS = new LinkedHashMap<>();

    /** 太阳能合成物品（ASP 合成链 + SSP 光谱组件族，注册名与材质沿用其命名）。 */
    private static final String[] CRAFTING_IDS = {
            "sunnarium_part", "sunnarium", "enriched_sunnarium",
            "sunnarium_alloy", "enriched_sunnarium_alloy",
            "iridium_ingot", "iridium_iron_plate", "reinforced_iridium_iron_plate",
            "uranium_ingot", "irradiant_uranium",
            "irradiant_glass_pane", "irradiant_reinforced_plate",
            "mt_core", "quantum_core",
            "solarsplitter", "bluecomponent", "greencomponent", "redcomponent"
    };

    static {
        for (SolarTier tier : SolarTier.values()) {
            PANEL_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.PANEL_LIST.get(tier.ordinal())));
            STORAGE_BOX_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.STORAGE_LIST.get(tier.ordinal())));
            CHARGEPAD_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.CHARGEPAD_LIST.get(tier.ordinal())));
        }
        for (GlassCableTier tier : GlassCableTier.values()) {
            CABLE_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.CABLE_LIST.get(tier.ordinal())));
        }
        // 分子重组仪的方块物品
        MT_ITEM = ITEMS.registerItem("molecular_transformer",
                props -> new BlockItem(ModBlocks.molecularTransformer(), props),
                Item.Properties::new);
        // 变压器的方块物品
        for (EuTier tier : EuTier.values()) {
            if (tier.iuTier() > 65) {
                continue;
            }
            TRANSFORMER_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(ModBlocks.TRANSFORMER_LIST.get(tier.iuTier() - 1)));
        }
        // 合成链物品
        for (String id : CRAFTING_IDS) {
            CRAFTING_ITEMS.put(id, ITEMS.registerItem(id, Item::new, Item.Properties::new));
        }
        // 「XX玻璃板」67 档
        for (SolarTier tier : SolarTier.values()) {
            PANE_ITEMS.put(tier, ITEMS.registerItem(tier.paneId(), Item::new, Item.Properties::new));
        }
        // 「XX核心」65 档（基础档无本体；量子核心走 ASP 官方 quantum_core）
        for (SolarTier tier : SolarTier.values()) {
            if (tier == SolarTier.BASIC || tier == SolarTier.QUANTUM) {
                continue;
            }
            CORE_ITEMS.put(tier, ITEMS.registerItem(tier.coreId(), Item::new, Item.Properties::new));
        }
        // 阳光化合物/小块/合金 66 档（基础档沿用 ASP 本体，跳过）
        for (SolarTier tier : SolarTier.values()) {
            if (tier == SolarTier.BASIC) {
                continue;
            }
            SUNNARIUM_PART_ITEMS.put(tier, ITEMS.registerItem(tier.sunnariumPartId(), Item::new, Item.Properties::new));
            SUNNARIUM_ITEMS.put(tier, ITEMS.registerItem(tier.sunnariumId(), Item::new, Item.Properties::new));
            SUNNARIUM_ALLOY_ITEMS.put(tier, ITEMS.registerItem(tier.sunnariumAlloyId(), Item::new, Item.Properties::new));
        }
    }

    public static Item panelItem(SolarTier tier) {
        return PANEL_ITEMS.get(tier).get();
    }

    public static Item cableItem(GlassCableTier tier) {
        return CABLE_ITEMS.get(tier).get();
    }

    public static Item storageBoxItem(SolarTier tier) {
        return STORAGE_BOX_ITEMS.get(tier).get();
    }

    public static Item transformerItem(EuTier tier) {
        return TRANSFORMER_ITEMS.get(tier).get();
    }

    public static Item molecularTransformerItem() {
        return MT_ITEM.get();
    }

    public static Item chargepadItem(SolarTier tier) {
        return CHARGEPAD_ITEMS.get(tier).get();
    }

    public static Item paneItem(SolarTier tier) {
        return PANE_ITEMS.get(tier).get();
    }

    public static Item sunnariumItem(SolarTier tier) {
        return tier == SolarTier.BASIC ? crafting("sunnarium") : SUNNARIUM_ITEMS.get(tier).get();
    }

    public static Item sunnariumPartItem(SolarTier tier) {
        return tier == SolarTier.BASIC ? crafting("sunnarium_part") : SUNNARIUM_PART_ITEMS.get(tier).get();
    }

    public static Item sunnariumAlloyItem(SolarTier tier) {
        return tier == SolarTier.BASIC ? crafting("sunnarium_alloy") : SUNNARIUM_ALLOY_ITEMS.get(tier).get();
    }

    public static Item coreItem(SolarTier tier) {
        return tier == SolarTier.QUANTUM ? crafting("quantum_core") : CORE_ITEMS.get(tier).get();
    }

    /** 按注册名取合成链物品（配方表使用）。 */
    public static Item crafting(String id) {
        DeferredItem<Item> item = CRAFTING_ITEMS.get(id);
        if (item == null) {
            throw new IllegalStateException("Unknown crafting item: " + id);
        }
        return item.get();
    }

    private ModItems() {
    }
}
