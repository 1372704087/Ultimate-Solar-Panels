package com.example.solarpower.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.energy.EuTier;
import com.example.solarpower.menu.TransformerMenu;
import com.example.solarpower.solar.SolarTier;
import com.example.solarpower.tileentity.TransformerTile;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/** 变压器界面：低压/高压双池条（各自档位主题色）+ 存量/吞吐/电压桥接行。 */
public class TransformerScreen extends AbstractContainerScreen<TransformerMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("solarpower", "textures/gui/transformer.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 124;  // 底部 20px 为放电方向模式按钮区（无物品栏）
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

    private static final int TEXT_COLOR = 0xFF000000;

    private static final Map<Integer, Integer> ACCENTS = new HashMap<>();

    private final TransformerMenu menu;
    private Button modeButton;

    public TransformerScreen(TransformerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.menu = menu;
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
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

    /** 标题配色：跟随本体自己的面板档主题色（与方块/物品外观一致，不再是电压代表色）。 */
    private static int titleAccent(TransformerTile tile) {
        return tile.getSolarTier().accentColor();
    }

    /** 电压行的方向符号跟随模式：关闭 ×、自动 ↔（不转压）、升压 →、降压 ←。 */
    private String modeArrow() {
        if (this.menu.mode == TransformerTile.MODE_STEP_UP) {
            return "→";
        }
        if (this.menu.mode == TransformerTile.MODE_STEP_DOWN) {
            return "←";
        }
        return this.menu.mode == TransformerTile.MODE_OFF ? "×" : "↔";
    }

    /** 池容量 = 吞吐 ×8（数值小，BigDecimal 直接承载）。 */
    private static BigDecimal capacity(TransformerTile tile) {
        return new BigDecimal(tile.poolCapacity());
    }

    private static String poolLabel(TransformerTile tile) {
        return EuFormat.formatEu(tile.poolCapacity());
    }

    private static Component tierLabel(SolarTier panel) {
        return Component.translatable("block.solarpower." + panel.id());
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int accent,
                         BigDecimal value, BigDecimal capacity) {
        int filled;
        if (capacity.signum() <= 0 || value.signum() <= 0) {
            filled = 0;
        } else if (value.compareTo(capacity) >= 0) {
            filled = BAR_WIDTH;
        } else {
            double ratio = value.divide(capacity, 4, RoundingMode.DOWN).doubleValue();
            filled = (int) (ratio * BAR_WIDTH);
        }
        filled = Math.max(0, Math.min(BAR_WIDTH, filled));
        if (filled > 0) {
            // 从左往右横向填充（原实现锚定底边向上长厚度，看起来像竖条）
            graphics.fill(this.leftPos + x, this.topPos + y,
                    this.leftPos + x + filled, this.topPos + y + BAR_HEIGHT,
                    0xFF000000 | accent);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.modeButton = Button.builder(Component.translatable("gui.solarpower.transformer.mode.0"),
                        b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0))
                .bounds(this.leftPos + 8, this.topPos + 104, 160, 16)
                .build();
        this.addRenderableWidget(this.modeButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.modeButton.setMessage(Component.translatable(
                "gui.solarpower.transformer.mode." + this.menu.mode));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        TransformerTile tile = this.menu.tile();
        if (tile == null) {
            return;
        }
        // 1.21.1 的 blit 不自带 alpha 混合，半透明底图必须手动开混合
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0f, 0f, WIDTH, HEIGHT, 256, 256);
        BigDecimal capacity = capacity(tile);
        drawBar(graphics, LOW_BAR_X, LOW_BAR_Y, accent(tile.lowTier().iuTier()),
                this.menu.lowStored, capacity);
        drawBar(graphics, HIGH_BAR_X, HIGH_BAR_Y, accent(tile.highTier().iuTier()),
                this.menu.highStored, capacity);
        RenderSystem.disableBlend();
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
                0xFF000000 | titleAccent(tile), true);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.low",
                        EuFormat.formatEu(this.menu.lowStored), poolLabel(tile)),
                8, ROW_LOW_Y, TEXT_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.high",
                        EuFormat.formatEu(this.menu.highStored), poolLabel(tile)),
                8, ROW_HIGH_Y, TEXT_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.rate",
                        EuFormat.formatEu(tile.rate()), EuFormat.formatEu(tile.rateDown())),
                8, ROW_RATE_Y, TEXT_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.transformer.voltage",
                        low.iuTier(), modeArrow(), high.iuTier()),
                8, ROW_VOLTAGE_Y, TEXT_COLOR, false);
    }
}
