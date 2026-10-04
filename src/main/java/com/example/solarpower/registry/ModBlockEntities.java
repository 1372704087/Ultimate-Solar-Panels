package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.tileentity.GlassCableTile;
import com.example.solarpower.tileentity.SolarPanelTile;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 方块实体类型注册（1.21.1）：全档位各共用一个类型。 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SolarPower.MODID);

    public static final Supplier<BlockEntityType<SolarPanelTile>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> BlockEntityType.Builder.of(
                    SolarPanelTile::new, ModBlocks.allPanels()).build(null));

    public static final Supplier<BlockEntityType<GlassCableTile>> GLASS_CABLE =
            BLOCK_ENTITIES.register("glass_cable", () -> BlockEntityType.Builder.of(
                    GlassCableTile::new, ModBlocks.allCables()).build(null));

    private ModBlockEntities() {
    }
}
