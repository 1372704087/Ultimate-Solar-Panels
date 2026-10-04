package com.example.solarpower.client.screen;

import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.menu.TransformerMenu;
import com.example.solarpower.solar.SolarTier;
import com.example.solarpower.tileentity.TransformerTile;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/** 变压器界面：低压/高压双池条（各自档位主题色）+ 存量/吞吐/电压桥接行。 */
public class TransformerScreen extends AbstractContainerScreen<TransformerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("solarpower", "textures/gui/transformer.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 190;
    private static final int BAR_WIDTH = 156;
    private static final int BAR_HEIGHT = 10;
    private static final int LOW_BAR_X = 10;
    private static final int LOW_BAR_Y = 21;
    private static final int HIGH_BAR_X = 10;
    private static final int HIGH_BAR_Y = 51;

    private static final int ROW_LOW_Y = 37;
    private static final int ROW_HIGH_Y = 67;
    private static final int ROW_RATE_Y = 79;
    private static final int ROW_VOLTAGE_Y = 90;

    private static final int TEXT_COLOR = 0x3F444E;

    private static final Map<Integer, Integer> ACCENTS = new HashMap<>();

    private final TransformerMenu menu;

    public TransformerScreen(TransformerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.menu = menu;
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 101;
    }

    /** 与变压器同 iuTier 的面板档主题色（首个匹配，按档缓存）。 */
    private static int accent(int iuTier) {
        Integer cached = ACCENTS.get(iuTier);
        if (cached == null) {
            int found = 0x9FB0C0;
            for (SolarTier tier : SolarTier.values()) {
                if (tier.iuTier() == iuTier) {
                    found = tier.accentColor();
                    break;
                }
            }
            cached = found;
            ACCENTS.put(iuTier, cached);
        }
        return cached;
    }

    /** 该电压档的代表面板档（变压器命名/配色跟随它）。 */
    private static SolarTier panelOf(int iuTier) {
        for (SolarTier panel : SolarTier.values()) {
            if (panel.iuTier() == iuTier) {
                return panel;
            }
        }
        return SolarTier.BASIC;
    }

    /** 池容量 = 吞吐 ×8（数值小，BigDecimal 直接承载）。 */
    private static BigDecimal capacity(TransformerTile tile) {
        return new BigDecimal(tile.poolCapacity());
    }

    private static String poolLabel(TransformerTile tile) {
        return EuFormat.formatEu(tile.poolCapacity());
    }

    private static String tierLabel(SolarTier panel) {
        return panel.id().replace("_solar_panel", "") + " T" + panel.iuTier()
                + " (" + panel.voltage().maxVoltageLabel() + ")";
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int accent,
                         BigDecimal value, BigDecimal capacity) {
        int filled;
        if (capacity.signum() <= 0 || value.signum() <= 0) {
            filled = 0;
        } else if (value.compareTo(capacity) >= 0) {
            filled = BAR_HEIGHT;
        } else {
            double ratio = value.divide(capacity, 4, RoundingMode.DOWN).doubleValue();
            filled = (int) (ratio * BAR_HEIGHT);
        }
        filled = Math.max(0, Math.min(BAR_HEIGHT, filled));
        if (filled > 0) {
            graphics.fill(this.leftPos + x, this.topPos + y + BAR_HEIGHT - filled,
                    this.leftPos + x + BAR_WIDTH, this.topPos + y + BAR_HEIGHT,
                    0xFF000000 | accent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, WIDTH, HEIGHT);
        TransformerTile tile = this.menu.tile();
        if (tile == null) {
            return;
        }
        BigDecimal capacity = capacity(tile);
        drawBar(graphics, LOW_BAR_X, LOW_BAR_Y, accent(tile.lowTier().iuTier()),
                this.menu.lowStored, capacity);
        drawBar(graphics, HIGH_BAR_X, HIGH_BAR_Y, accent(tile.highTier().iuTier()),
                this.menu.highStored, capacity);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TransformerTile tile = this.menu.tile();
        if (tile == null) {
            return;
        }
        SolarTier low = panelOf(tile.lowTier().iuTier());
        SolarTier high = panelOf(tile.highTier().iuTier());
        graphics.drawString(this.font, this.title, 8, 6,
                0xFF000000 | accent(low.iuTier()), true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.low",
                        tierLabel(low), EuFormat.formatEu(this.menu.lowStored), poolLabel(tile)),
                8, ROW_LOW_Y, TEXT_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.high",
                        tierLabel(high), EuFormat.formatEu(this.menu.highStored), poolLabel(tile)),
                8, ROW_HIGH_Y, TEXT_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.rate",
                        EuFormat.formatEu(tile.rate())),
                8, ROW_RATE_Y, TEXT_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.voltage",
                        tierLabel(low), tierLabel(high)),
                8, ROW_VOLTAGE_Y, TEXT_COLOR, true);
    }
}
