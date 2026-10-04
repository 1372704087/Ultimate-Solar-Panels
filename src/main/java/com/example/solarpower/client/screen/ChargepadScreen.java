package com.example.solarpower.client.screen;

import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.menu.ChargepadMenu;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 充电座界面：能量条 + 蓄电量/充电速率/状态三行（状态行充电中为绿色）。 */
public class ChargepadScreen extends AbstractContainerScreen<ChargepadMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("solarpower", "textures/gui/storage_box.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int BAR_X = 10;
    private static final int BAR_Y = 22;
    private static final int BAR_WIDTH = 156;
    private static final int BAR_HEIGHT = 12;

    private static final int ROW_STORED_Y = 40;
    private static final int ROW_RATE_Y = 51;
    private static final int ROW_STATUS_Y = 62;

    private static final int TEXT_COLOR = 0x3F444E;
    private static final int CHARGING_COLOR = 0x3FA84F;

    private final ChargepadMenu menu;

    public ChargepadScreen(ChargepadMenu menu, Inventory inv, Component title) {
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

    private static BigDecimal capacityMirror(SolarTier tier) {
        return tier.capacityMirror().multiply(BigDecimal.valueOf(64));
    }

    private static String capacityLabel(SolarTier tier) {
        return EuFormat.formatEu(tier.capacityEu().shiftLeft(6));
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
                Component.translatable("gui.solarpower.chargepad.energy",
                        EuFormat.formatEu(this.menu.stored), capacityLabel(tier)),
                8, ROW_STORED_Y, TEXT_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.chargepad.rate",
                        EuFormat.formatEu(tier.maxOutputEu())),
                8, ROW_RATE_Y, TEXT_COLOR, true);
        Component status = this.menu.charging
                ? Component.translatable("gui.solarpower.chargepad.status.charging")
                : Component.translatable("gui.solarpower.chargepad.status.idle");
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.chargepad.status", status),
                8, ROW_STATUS_Y,
                this.menu.charging ? CHARGING_COLOR : TEXT_COLOR, true);
    }
}
