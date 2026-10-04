package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.block.GlassCableBlock;
import com.example.solarpower.block.SolarPanelBlock;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 方块注册（1.21.1 DeferredRegister）：67 档面板 + 68 档电缆，逐档一个 Block。 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SolarPower.MODID);

    private static final Map<SolarTier, DeferredBlock<SolarPanelBlock>> PANELS = new EnumMap<>(SolarTier.class);
    private static final Map<GlassCableTier, DeferredBlock<GlassCableBlock>> CABLES = new EnumMap<>(GlassCableTier.class);
    public static final List<DeferredBlock<SolarPanelBlock>> PANEL_LIST = new ArrayList<>();
    public static final List<DeferredBlock<GlassCableBlock>> CABLE_LIST = new ArrayList<>();

    static {
        for (SolarTier tier : SolarTier.values()) {
            DeferredBlock<SolarPanelBlock> block = BLOCKS.register(tier.id(), () -> new SolarPanelBlock(tier));
            PANELS.put(tier, block);
            PANEL_LIST.add(block);
        }
        for (GlassCableTier tier : GlassCableTier.values()) {
            DeferredBlock<GlassCableBlock> block = BLOCKS.register(tier.id(), () -> new GlassCableBlock(tier));
            CABLES.put(tier, block);
            CABLE_LIST.add(block);
        }
    }

    public static SolarPanelBlock panel(SolarTier tier) {
        return PANELS.get(tier).get();
    }

    public static GlassCableBlock cable(GlassCableTier tier) {
        return CABLES.get(tier).get();
    }

    public static Block[] allPanels() {
        return PANEL_LIST.stream().map(DeferredBlock::get).toArray(Block[]::new);
    }

    public static Block[] allCables() {
        return CABLE_LIST.stream().map(DeferredBlock::get).toArray(Block[]::new);
    }

    private ModBlocks() {
    }
}
