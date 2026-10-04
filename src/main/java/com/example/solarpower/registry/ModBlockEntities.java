package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.tileentity.ChargepadTile;
import com.example.solarpower.tileentity.GlassCableTile;
import com.example.solarpower.tileentity.MolecularTransformerTile;
import com.example.solarpower.tileentity.SolarPanelTile;
import com.example.solarpower.tileentity.StorageBoxTile;
import com.example.solarpower.tileentity.TransformerTile;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 方块实体类型注册（1.21.11）：全档位各共用一个类型。 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SolarPower.MODID);

    public static final Supplier<BlockEntityType<SolarPanelTile>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> new BlockEntityType<>(
                    SolarPanelTile::new, ModBlocks.allPanels()));

    public static final Supplier<BlockEntityType<MolecularTransformerTile>> MOLECULAR_TRANSFORMER =
            BLOCK_ENTITIES.register("molecular_transformer", () -> new BlockEntityType<>(
                    MolecularTransformerTile::new, ModBlocks.molecularTransformer()));

    public static final Supplier<BlockEntityType<GlassCableTile>> GLASS_CABLE =
            BLOCK_ENTITIES.register("glass_cable", () -> new BlockEntityType<>(
                    GlassCableTile::new, ModBlocks.allCables()));

    public static final Supplier<BlockEntityType<StorageBoxTile>> STORAGE_BOX =
            BLOCK_ENTITIES.register("storage_box", () -> new BlockEntityType<>(
                    StorageBoxTile::new, ModBlocks.allStorageBoxes()));

    public static final Supplier<BlockEntityType<TransformerTile>> TRANSFORMER =
            BLOCK_ENTITIES.register("transformer", () -> new BlockEntityType<>(
                    TransformerTile::new, ModBlocks.allTransformers()));

    public static final Supplier<BlockEntityType<ChargepadTile>> CHARGEPAD =
            BLOCK_ENTITIES.register("chargepad", () -> new BlockEntityType<>(
                    ChargepadTile::new, ModBlocks.allChargepads()));

    private ModBlockEntities() {
    }
}
