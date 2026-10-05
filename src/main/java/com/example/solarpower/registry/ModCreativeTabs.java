package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 创造模式标签：面板(1→67) → 电缆(1→68)，固定排列不随注册顺序漂移。 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SolarPower.MODID);

    public static final Supplier<CreativeModeTab> TAB = TABS.register("solarpower",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.solarpower"))
                    .icon(() -> new ItemStack(ModItems.panelItem(SolarTier.BASIC)))
                    .displayItems((parameters, output) -> {
                        // 1. 太阳能板（67档）
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModItems.panelItem(tier)));
                        }
                        // 2. 储电盒（67档）
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModItems.storageBoxItem(tier)));
                        }
                        // 3. 变压器（67档）
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModBlocks.transformer(tier)));
                        }
                        // 4. 充电座（67档）
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModItems.chargepadItem(tier)));
                        }
                        // 5. 电缆（68档）
                        for (GlassCableTier tier : GlassCableTier.values()) {
                            output.accept(new ItemStack(ModItems.cableItem(tier)));
                        }
                        // 6. 分子重组仪
                        output.accept(new ItemStack(ModItems.molecularTransformerItem()));
                        // 7. 基础合成材料（排除量子核心，它在核心区）
                        for (net.minecraft.world.item.Item item : ModItems.craftingItems()) {
                            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                            if ("quantum_core".equals(key.getPath())) continue;
                            output.accept(new ItemStack(item));
                        }
                        // 8. 玻璃板（67档）
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModItems.paneItem(tier)));
                        }
                        // 9. 阳光化合物小块（66档）
                        for (SolarTier tier : SolarTier.values()) {
                            if (tier == SolarTier.BASIC) continue;
                            output.accept(new ItemStack(ModItems.sunnariumPartItem(tier)));
                        }
                        // 10. 阳光化合物（66档）
                        for (SolarTier tier : SolarTier.values()) {
                            if (tier == SolarTier.BASIC) continue;
                            output.accept(new ItemStack(ModItems.sunnariumItem(tier)));
                        }
                        // 11. 阳光合金（66档）
                        for (SolarTier tier : SolarTier.values()) {
                            if (tier == SolarTier.BASIC) continue;
                            output.accept(new ItemStack(ModItems.sunnariumAlloyItem(tier)));
                        }
                        // 12. 核心（65档，含量子核心）
                        for (SolarTier tier : SolarTier.values()) {
                            if (tier == SolarTier.BASIC) continue;
                            output.accept(new ItemStack(ModItems.coreItem(tier)));
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
