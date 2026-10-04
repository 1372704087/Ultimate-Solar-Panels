package com.example.solarpower.client.screen;

import com.example.solarpower.energy.EuFormat;
import com.example.solarpower.menu.MolecularTransformerMenu;
import com.example.solarpower.registry.ModMenus;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigInteger;

/** 分子重组仪界面（底图照搬 ASP 官方素材，布局 220×193）：进度计 + 能量文本。 */
public class MolecularTransformerScreen extends AbstractContainerScreen<MolecularTransformerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("solarpower", "textures/gui/molecular_transformer.png");

    private static final int WIDTH = 220;
    private static final int HEIGHT = 193;
    /** 进度计：底图槽 (23,48) 10×15，填充从 (221,7) 向下取。 */
    private static final int GAUGE_X = 23;
    private static final int GAUGE_Y = 48;
    private static final int GAUGE_HEIGHT = 15;
    private static final int FILL_U = 221;
    private static final int FILL_V = 7;

    private static final int TEXT_COLOR = 0x404040;

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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, WIDTH, HEIGHT);
        // 进度计：从顶部向下填充（照搬 ASP 的 Down 方向），进度按 0~15 档折算
        int visible = Math.max(0, Math.min(GAUGE_HEIGHT, this.menu.progress * GAUGE_HEIGHT / 15));
        if (visible > 0) {
            graphics.blit(TEXTURE, this.leftPos + GAUGE_X, this.topPos + GAUGE_Y,
                    FILL_U, FILL_V, 10, visible);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 20, 6, TEXT_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.mt.energy_used",
                        EuFormat.formatEu(BigInteger.valueOf(this.menu.energyUsed))),
                62, 30, TEXT_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.mt.energy_per_op",
                        EuFormat.formatEu(BigInteger.valueOf(this.menu.totalEu))),
                62, 42, TEXT_COLOR, false);
        int percent = this.menu.totalEu <= 0 ? 0
                : (int) (100L * this.menu.energyUsed / this.menu.totalEu);
        graphics.drawString(this.font,
                Component.translatable("gui.solarpower.mt.progress", percent),
                62, 54, TEXT_COLOR, false);
    }
}
