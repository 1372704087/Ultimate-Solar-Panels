package com.example.solarpower.client.screen;

import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.menu.SolarPanelMenu;
import com.example.solarpower.solar.SolarTier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/** 太阳能板界面（1:1 复刻旧版布局：能量管 + 指示灯 + 三行数据）。 */
public class SolarPanelScreen extends AbstractContainerScreen<SolarPanelMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("solarpower", "textures/gui/solar_panel.png");

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 166;

    private static final int TUBE_X = 176;
    private static final int TUBE_Y = 34;
    private static final int TUBE_HEIGHT = 108;
    private static final int FILL_U = 200;
    private static final int FILL_V = 0;

    private static final int LED_X = 181;
    private static final int LED_Y = 21;
    private static final int LED_SIZE = 10;
    private static final int LED_U = 220;
    private static final int LED_LIT_V = 0;
    private static final int LED_NIGHT_V = 24;
    private static final int LED_DIM_V = 12;

    private static final int ROW_TEXT_X = 16;
    private static final int ROW1_Y = 33;
    private static final int ROW2_Y = 53;
    private static final int ROW3_Y = 73;

    private static final int TEXT_COLOR = 0xDFEAF4;

    private final SolarPanelMenu menu;

    public SolarPanelScreen(SolarPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.menu = menu;
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelX = 2000;   // 无背包区，隐藏默认标签
        this.inventoryLabelY = 2000;
    }

    private SolarTier tier() {
        return this.menu.tile() != null
                ? this.menu.tile().getTier() : SolarTier.BASIC;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> hover = rowHoverText(mouseX - this.leftPos, mouseY - this.topPos);
        if (hover != null) {
            graphics.renderComponentTooltip(this.font, hover, mouseX, mouseY);
        }
    }

    /** 三行数据行的悬停科学计数法约数。 */
    @Nullable
    private List<Component> rowHoverText(int rx, int ry) {
        boolean inRow = rx >= ROW_TEXT_X - 4 && rx < 172;
        if (!inRow || ry < ROW1_Y - 2 || ry >= ROW3_Y + 18) {
            return null;
        }
        SolarTier tier = tier();
        if (ry < ROW2_Y - 2) {
            String gen = EuFormat.scientificApprox(this.menu.generating);
            return gen == null ? null : Collections.singletonList(Component.literal("\u00a77\u2248 " + gen + " EU/t"));
        }
        if (ry < ROW3_Y - 2) {
            String volt = tier.voltage().maxVoltageScientific();
            return volt == null ? null : Collections.singletonList(Component.literal("\u00a77\u2248 " + volt + " EU/packet"));
        }
        String stored = EuFormat.scientificApprox(this.menu.stored);
        String cap = tier.capacityScientific();
        if (stored == null && cap == null) {
            return null;
        }
        return Collections.singletonList(Component.literal("\u00a77\u2248 "
                + (stored == null ? "0" : stored) + " / " + (cap == null ? "0" : cap) + " EU"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        SolarTier tier = tier();
        // 容量用「紧凑镜像」（顶档容量百万位级，直接除会物化整个数）
        BigDecimal capacity = tier.capacityMirror();
        BigDecimal stored = this.menu.stored;
        int filled;
        if (capacity.signum() <= 0 || stored.signum() <= 0) {
            filled = 0;
        } else if (stored.compareTo(capacity) >= 0) {
            filled = TUBE_HEIGHT;
        } else {
            double ratio = stored.divide(capacity, 4, RoundingMode.DOWN).doubleValue();
            filled = (int) (ratio * TUBE_HEIGHT);
        }
        filled = Math.max(0, Math.min(TUBE_HEIGHT, filled));
        if (filled > 0) {
            graphics.blit(TEXTURE, this.leftPos + TUBE_X,
                    this.topPos + TUBE_Y + TUBE_HEIGHT - filled,
                    FILL_U, FILL_V + TUBE_HEIGHT - filled, 18, filled);
        }

        int ledV = this.menu.generating.signum() > 0
                ? (this.menu.night ? LED_NIGHT_V : LED_LIT_V)
                : LED_DIM_V;
        graphics.blit(TEXTURE, this.leftPos + LED_X, this.topPos + LED_Y, LED_U, ledV, LED_SIZE, LED_SIZE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        SolarTier tier = tier();
        // 标题用档位主题色的精确 RGB（GUI 字体不受原版 16 色板限制）
        graphics.drawString(this.font, this.title, 20, 8,
                0xFF000000 | tier.accentColor(), true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.generation",
                        EuFormat.formatEu(this.menu.generating)),
                ROW_TEXT_X, ROW1_Y, TEXT_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.voltage",
                        tier.voltage().name() + " (" + tier.voltage().maxVoltageLabel() + " EU/packet)"),
                ROW_TEXT_X, ROW2_Y, TEXT_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.energy",
                        EuFormat.formatEu(this.menu.stored), tier.capacityLabel()),
                ROW_TEXT_X, ROW3_Y, TEXT_COLOR, true);
    }
}
