package com.example.solarpower.client;

import com.example.solarpower.block.SolarPanelBlock;
import com.example.solarpower.block.StorageBoxBlock;
import com.example.solarpower.block.ChargepadBlock;
import com.example.solarpower.block.TransformerBlock;
import com.example.solarpower.solar.SolarTier;

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
            event.getToolTip().add(Component.translatable("tooltip.solarpower.capacity",
                    tier.capacityAnnotated()));
        }
    }
}
