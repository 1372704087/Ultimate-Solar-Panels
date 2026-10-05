package com.example.solarpower.client;

import com.example.solarpower.block.SolarPanelBlock;
import com.example.solarpower.block.StorageBoxBlock;
import com.example.solarpower.block.ChargepadBlock;
import com.example.solarpower.block.TransformerBlock;
import com.example.solarpower.block.GlassCableBlock;
import com.example.solarpower.registry.ModBlocks;
import com.example.solarpower.registry.ModItems;
import com.example.solarpower.solar.SolarTier;
import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.energy.EuFormat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "solarpower", value = Dist.CLIENT)
public class TooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        var block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
        if (block instanceof SolarPanelBlock panel) {
            SolarTier tier = panel.tier();
            // 物品名称用档位主题色
            if (!event.getToolTip().isEmpty()) {
                Component name = stack.getHoverName().copy()
                        .withStyle(Style.EMPTY.withColor(tier.accentColor()));
                event.getToolTip().set(0, name);
            }
            event.getToolTip().add(Component.translatable("tooltip.solarpower.energy_tier",
                    tier.iuTier(), tier.voltage().name()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.day_gen",
                    tier.generationAnnotated()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.night_gen",
                    tier.nightGenerationAnnotated()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.max_output",
                    tier.maxOutputAnnotated()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.capacity",
                    tier.capacityAnnotated()));
        } else if (block instanceof StorageBoxBlock sb) {
            SolarTier tier = sb.tier();
            if (!event.getToolTip().isEmpty()) {
                Component name = stack.getHoverName().copy()
                        .withStyle(Style.EMPTY.withColor(tier.accentColor()));
                event.getToolTip().set(0, name);
            }
            event.getToolTip().add(Component.translatable("tooltip.solarpower.energy_tier",
                    tier.iuTier(), tier.voltage().name()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.capacity",
                    tier.capacityAnnotated()));
        } else if (block instanceof ChargepadBlock cp) {
            SolarTier tier = cp.tier();
            if (!event.getToolTip().isEmpty()) {
                Component name = stack.getHoverName().copy()
                        .withStyle(Style.EMPTY.withColor(tier.accentColor()));
                event.getToolTip().set(0, name);
            }
            event.getToolTip().add(Component.translatable("tooltip.solarpower.energy_tier",
                    tier.iuTier(), tier.voltage().name()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.charge_rate",
                    tier.maxOutputAnnotated()));
        } else if (block instanceof TransformerBlock tr) {
            SolarTier st = tr.solarTier();
            if (!event.getToolTip().isEmpty()) {
                Component name = stack.getHoverName().copy()
                        .withStyle(Style.EMPTY.withColor(st.accentColor()));
                event.getToolTip().set(0, name);
            }
            event.getToolTip().add(Component.translatable("tooltip.solarpower.energy_tier",
                    st.iuTier(), st.voltage().name()));
            // 降压吞吐 = 前一档面板最大输出（基础档 = 自身发电量）；升压吞吐 = 本档面板最大输出
            SolarTier lowSt = st.ordinal() >= 1 ? SolarTier.values()[st.ordinal() - 1] : null;
            event.getToolTip().add(Component.translatable("tooltip.solarpower.low_side",
                    Math.max(0, st.iuTier() - 1),
                    lowSt != null ? lowSt.maxOutputAnnotated() : st.generationAnnotated()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.high_side",
                    st.iuTier(), st.maxOutputAnnotated()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.transformer_mode"));
        } else if (block instanceof GlassCableBlock cable) {
            GlassCableTier ct = cable.tier();
            if (!event.getToolTip().isEmpty()) {
                Component name = stack.getHoverName().copy()
                        .withStyle(Style.EMPTY.withColor(ct.midColor()));
                event.getToolTip().set(0, name);
            }
            SolarTier src = ct.source();
            event.getToolTip().add(Component.translatable("tooltip.solarpower.energy_tier",
                    src.iuTier(), src.voltage().name()));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.cable_loss",
                    String.format("%.2f EU/格", ct.lossPerBlock())));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.cable_rate",
                    EuFormat.formatEu(ct.capacityPerTick())));
            event.getToolTip().add(Component.translatable("tooltip.solarpower.cable_fuse"));
        } else {
            // 玻璃板/阳光化合物/合金/核心等档位物品：名称用对应档位色
            var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            String path = itemKey.getPath();
            for (SolarTier tier : SolarTier.values()) {
                if (path.equals(tier.paneId())
                        || path.equals(tier.sunnariumPartId())
                        || path.equals(tier.sunnariumId())
                        || path.equals(tier.sunnariumAlloyId())
                        || path.equals(tier.coreId())
                        || (tier == SolarTier.QUANTUM && path.equals("quantum_core"))) {
                    if (!event.getToolTip().isEmpty()) {
                        Component name = stack.getHoverName().copy()
                                .withStyle(Style.EMPTY.withColor(tier.accentColor()));
                        event.getToolTip().set(0, name);
                    }
                    break;
                }
            }
        }
    }
}
