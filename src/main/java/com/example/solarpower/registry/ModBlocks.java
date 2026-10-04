package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.block.ChargepadBlock;
import com.example.solarpower.block.GlassCableBlock;
import com.example.solarpower.block.MolecularTransformerBlock;
import com.example.solarpower.block.SolarPanelBlock;
import com.example.solarpower.block.StorageBoxBlock;
import com.example.solarpower.block.TransformerBlock;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;

/**
 * 方块注册（1.21.1 DeferredRegister）：67 档面板 + 68 档电缆 + 67 档储电盒 +
 * 65 档变压器 + 67 档充电座，逐档一个 Block（1.21 无方块 id 上限）。
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SolarPower.MODID);

    private static final Map<SolarTier, DeferredBlock<SolarPanelBlock>> PANELS = new EnumMap<>(SolarTier.class);
    private static final Map<GlassCableTier, DeferredBlock<GlassCableBlock>> CABLES = new EnumMap<>(GlassCableTier.class);
    private static final Map<SolarTier, DeferredBlock<StorageBoxBlock>> STORAGE_BOXES = new EnumMap<>(SolarTier.class);
    private static final Map<EuTier, DeferredBlock<TransformerBlock>> TRANSFORMERS = new EnumMap<>(EuTier.class);
    public static final List<DeferredBlock<TransformerBlock>> TRANSFORMER_LIST = new ArrayList<>();
    private static final Map<SolarTier, DeferredBlock<ChargepadBlock>> CHARGEPADS = new EnumMap<>(SolarTier.class);
    private static DeferredBlock<MolecularTransformerBlock> molecularTransformer;
    public static final List<DeferredBlock<StorageBoxBlock>> STORAGE_LIST = new ArrayList<>();
    public static final List<DeferredBlock<ChargepadBlock>> CHARGEPAD_LIST = new ArrayList<>();
    public static final List<DeferredBlock<SolarPanelBlock>> PANEL_LIST = new ArrayList<>();
    public static final List<DeferredBlock<GlassCableBlock>> CABLE_LIST = new ArrayList<>();

    static {
        for (SolarTier tier : SolarTier.values()) {
            DeferredBlock<SolarPanelBlock> panel = BLOCKS.register(tier.id(), () -> new SolarPanelBlock(tier));
            PANELS.put(tier, panel);
            PANEL_LIST.add(panel);
            DeferredBlock<StorageBoxBlock> sb = BLOCKS.register(tier.id() + "_storage_box",
                    () -> new StorageBoxBlock(tier));
            STORAGE_BOXES.put(tier, sb);
            STORAGE_LIST.add(sb);
            DeferredBlock<ChargepadBlock> cb = BLOCKS.register(tier.id() + "_chargepad",
                    () -> new ChargepadBlock(tier));
            CHARGEPADS.put(tier, cb);
            CHARGEPAD_LIST.add(cb);
        }
        for (GlassCableTier tier : GlassCableTier.values()) {
            DeferredBlock<GlassCableBlock> cable = BLOCKS.register(tier.id(), () -> new GlassCableBlock(tier));
            CABLES.put(tier, cable);
            CABLE_LIST.add(cable);
        }
        molecularTransformer = BLOCKS.register("molecular_transformer", MolecularTransformerBlock::new);
        for (EuTier tier : EuTier.values()) {
            if (tier.iuTier() > 65) {
                continue;
            }
            // 命名跟随太阳能面板档（首个匹配 iuTier 的面板），与资产生成器一致
            DeferredBlock<TransformerBlock> tb = BLOCKS.register(representativePanel(tier.iuTier()).id() + "_transformer",
                    () -> new TransformerBlock(tier));
            TRANSFORMERS.put(tier, tb);
            TRANSFORMER_LIST.add(tb);
        }
    }

    public static SolarPanelBlock panel(SolarTier tier) {
        return PANELS.get(tier).get();
    }

    public static GlassCableBlock cable(GlassCableTier tier) {
        return CABLES.get(tier).get();
    }

    public static StorageBoxBlock storageBox(SolarTier tier) {
        return STORAGE_BOXES.get(tier).get();
    }

    public static TransformerBlock transformer(EuTier tier) {
        return TRANSFORMERS.get(tier).get();
    }

    public static ChargepadBlock chargepad(SolarTier tier) {
        return CHARGEPADS.get(tier).get();
    }

    public static MolecularTransformerBlock molecularTransformer() {
        return molecularTransformer.get();
    }

    /** 该电压档的代表面板档（首个匹配 iuTier 的 SolarTier；变压器命名/贴图跟随它）。 */
    public static SolarTier representativePanel(int iuTier) {
        for (SolarTier tier : SolarTier.values()) {
            if (tier.iuTier() == iuTier) {
                return tier;
            }
        }
        throw new IllegalStateException("no solar tier for iuTier " + iuTier);
    }

    public static StorageBoxBlock[] allStorageBoxes() {
        return STORAGE_BOXES.values().stream().map(DeferredBlock::get).toArray(StorageBoxBlock[]::new);
    }

    public static TransformerBlock[] allTransformers() {
        return TRANSFORMERS.values().stream().map(DeferredBlock::get).toArray(TransformerBlock[]::new);
    }

    public static ChargepadBlock[] allChargepads() {
        return CHARGEPADS.values().stream().map(DeferredBlock::get).toArray(ChargepadBlock[]::new);
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
