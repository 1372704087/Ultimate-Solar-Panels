package com.example.solarpower.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.menu.MolecularTransformerMenu;
import com.example.solarpower.solar.MtRecipes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;

/** 分子重组仪界面（ASP 原版布局 220x193，五排右对齐文本）。 */
public class MolecularTransformerScreen extends AbstractContainerScreen<MolecularTransformerMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("solarpower", "textures/gui/molecular_transformer.png");

    private static final int WIDTH = 220;
    private static final int HEIGHT = 193;
    private static final int GAUGE_X = 23;
    private static final int GAUGE_Y = 48;
    private static final int GAUGE_HEIGHT = 15;
    private static final int FILL_U = 221;
    private static final int FILL_V = 7;

    private static final int LABEL_RIGHT_X = 107;
    private static final int VALUE_X = 112;
    private static final int ROW0_Y = 26;
    private static final int ROW_STEP = 12;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final MolecularTransformerMenu menu;

    public MolecularTransformerScreen(MolecularTransformerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.menu = menu;
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelX = 18;
        this.inventoryLabelY = 87;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 1.21.1 的 blit 不自带 alpha 混合，半透明底图必须手动开混合
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0f, 0f, WIDTH, HEIGHT, 256, 256);
        int total = (int) this.menu.totalEu;
        int used = (int) this.menu.energyUsed;
        int filled = total <= 0 ? 0 : Math.max(0, Math.min(GAUGE_HEIGHT, used * GAUGE_HEIGHT / total));
        if (filled > 0) {
            graphics.blit(TEXTURE,
                    this.leftPos + GAUGE_X, this.topPos + GAUGE_Y,
                    FILL_U, FILL_V, 10, filled, 256, 256);
        }
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题居中
        graphics.drawString(this.font, this.title,
                (WIDTH - this.font.width(this.title)) / 2, 9, TEXT_COLOR, false);

        // 输入/输出物品名
        ItemStack input = this.menu.slots.get(0).getItem();
        String inputName = input.isEmpty() ? "" : input.getHoverName().getString();
        String outputName = "";
        if (!input.isEmpty()) {
            MtRecipes.MtRecipe recipe = MtRecipes.find(input);
            if (recipe != null) {
                outputName = recipe.output.getHoverName().getString();
            }
        }

        long total = this.menu.totalEu;
        long used = this.menu.energyUsed;
        String percent = total <= 0 ? "" : (used * 100 / total) + "%";

        // 五排：标签右对齐 x=107，值 x=112
        label(graphics, Component.translatable("gui.solarpower.mt.input").getString(), ROW0_Y);
        label(graphics, Component.translatable("gui.solarpower.mt.output").getString(), ROW0_Y + ROW_STEP);
        label(graphics, Component.translatable("gui.solarpower.mt.energy_per_op").getString(), ROW0_Y + ROW_STEP * 2);
        label(graphics, Component.translatable("gui.solarpower.mt.energy_used").getString(), ROW0_Y + ROW_STEP * 3);
        label(graphics, Component.translatable("gui.solarpower.mt.progress").getString(), ROW0_Y + ROW_STEP * 4);

        value(graphics, inputName, ROW0_Y);
        value(graphics, outputName, ROW0_Y + ROW_STEP);
        value(graphics, total <= 0 ? "" : EuFormat.formatEu(BigInteger.valueOf(total)) + " EU", ROW0_Y + ROW_STEP * 2);
        value(graphics, used <= 0 ? "" : EuFormat.formatEu(BigInteger.valueOf(used)) + " EU", ROW0_Y + ROW_STEP * 3);
        value(graphics, percent, ROW0_Y + ROW_STEP * 4);
    }

    private void label(GuiGraphics g, String text, int y) {
        g.drawString(this.font, text, LABEL_RIGHT_X - this.font.width(text), y, TEXT_COLOR, false);
    }

    private void value(GuiGraphics g, String text, int y) {
        g.drawString(this.font, text, VALUE_X, y, TEXT_COLOR, false);
    }
}
