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
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModItems.panelItem(tier)));
                            output.accept(new ItemStack(ModItems.storageBoxItem(tier)));
                        }
                        for (int iu = 1; iu <= 65; iu++) {
                            var block = ModBlocks.transformer(com.example.solarpower.energy.EuTier.byIuTier(iu));
                            output.accept(new ItemStack(block));
                        }
                        for (SolarTier tier : SolarTier.values()) {
                            output.accept(new ItemStack(ModItems.chargepadItem(tier)));
                        }
                        for (GlassCableTier tier : GlassCableTier.values()) {
                            output.accept(new ItemStack(ModItems.cableItem(tier)));
                        }
                        // 合成材料区段（与 1.12.2 一致：链上材料走合成获取，不进栏）
                        for (net.minecraft.world.item.Item item : ModItems.craftingItems()) {
                            output.accept(new ItemStack(item));
                        }
                        output.accept(new ItemStack(ModItems.molecularTransformerItem()));
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
