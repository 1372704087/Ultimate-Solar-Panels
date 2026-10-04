package com.example.solarpower.client.screen;

import com.example.solarpower.menu.StorageBoxMenu;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 储电盒界面：宽能量条（档位主题色）+ 蓄电量/容量/输出三行。 */
public class StorageBoxScreen extends AbstractContainerScreen<StorageBoxMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("solarpower", "textures/gui/storage_box.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int BAR_X = 10;
    private static final int BAR_Y = 22;
    private static final int BAR_WIDTH = 156;
    private static final int BAR_HEIGHT = 12;

    private final StorageBoxMenu menu;

    public StorageBoxScreen(StorageBoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.menu = menu;
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 74;
    }

    private SolarTier tier() {
        return this.menu.tile() != null
                ? this.menu.tile().getSolarTier() : SolarTier.BASIC;
    }

    private BigDecimal capacityMirror(SolarTier tier) {
        return tier.capacityMirror().multiply(BigDecimal.valueOf(64));
    }

    private String capacityLabel(SolarTier tier) {
        return com.example.solarpower.energy.EuFormat.formatEu(tier.capacityEu().shiftLeft(6));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, WIDTH, HEIGHT);

        SolarTier tier = tier();
        BigDecimal capacity = capacityMirror(tier);
        BigDecimal stored = this.menu.stored;
        int filled;
        if (capacity.signum() <= 0 || stored.signum() <= 0) {
            filled = 0;
        } else if (stored.compareTo(capacity) >= 0) {
            filled = BAR_HEIGHT;
        } else {
            double ratio = stored.divide(capacity, 4, RoundingMode.DOWN).doubleValue();
            filled = (int) (ratio * BAR_HEIGHT);
        }
        filled = Math.max(0, Math.min(BAR_HEIGHT, filled));
        if (filled > 0) {
            graphics.fill(this.leftPos + BAR_X, this.topPos + BAR_Y + BAR_HEIGHT - filled,
                    this.leftPos + BAR_X + BAR_WIDTH, this.topPos + BAR_Y + BAR_HEIGHT,
                    0xFF000000 | tier.accentColor());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        SolarTier tier = tier();
        graphics.drawString(this.font, this.title, 8, 6, 0xFF000000 | tier.accentColor(), true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.storage.energy",
                        com.example.solarpower.energy.EuFormat.formatEu(this.menu.stored),
                        capacityLabel(tier)),
                8, 40, 0x3F444E, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.storage.capacity", capacityLabel(tier)),
                8, 51, 0x3F444E, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.storage.output",
                        com.example.solarpower.energy.EuFormat.formatEu(tier.maxOutputEu())),
                8, 62, 0x3F444E, true);
    }
}
