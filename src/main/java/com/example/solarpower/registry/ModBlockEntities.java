package com.example.solarpower.registry;

import com.example.solarpower.SolarPower;
import com.example.solarpower.tileentity.ChargepadTile;
import com.example.solarpower.tileentity.GlassCableTile;
import com.example.solarpower.tileentity.MolecularTransformerTile;
import com.example.solarpower.tileentity.SolarPanelTile;
import com.example.solarpower.tileentity.StorageBoxTile;
import com.example.solarpower.tileentity.TransformerTile;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

/** 方块实体类型注册（1.21.1）：全档位各共用一个类型。 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SolarPower.MODID);

    public static final Supplier<BlockEntityType<SolarPanelTile>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> new BlockEntityType<>(
                    SolarPanelTile::new, Set.<Block>of(ModBlocks.allPanels()), null));

    public static final Supplier<BlockEntityType<MolecularTransformerTile>> MOLECULAR_TRANSFORMER =
            BLOCK_ENTITIES.register("molecular_transformer", () -> new BlockEntityType<>(
                    MolecularTransformerTile::new, Set.<Block>of(ModBlocks.molecularTransformer()), null));

    public static final Supplier<BlockEntityType<GlassCableTile>> GLASS_CABLE =
            BLOCK_ENTITIES.register("glass_cable", () -> new BlockEntityType<>(
                    GlassCableTile::new, Set.<Block>of(ModBlocks.allCables()), null));

    public static final Supplier<BlockEntityType<StorageBoxTile>> STORAGE_BOX =
            BLOCK_ENTITIES.register("storage_box", () -> new BlockEntityType<>(
                    StorageBoxTile::new, Set.<Block>of(ModBlocks.allStorageBoxes()), null));

    public static final Supplier<BlockEntityType<TransformerTile>> TRANSFORMER =
            BLOCK_ENTITIES.register("transformer", () -> new BlockEntityType<>(
                    TransformerTile::new, Set.<Block>of(ModBlocks.allTransformers()), null));

    public static final Supplier<BlockEntityType<ChargepadTile>> CHARGEPAD =
            BLOCK_ENTITIES.register("chargepad", () -> new BlockEntityType<>(
                    ChargepadTile::new, Set.<Block>of(ModBlocks.allChargepads()), null));

    private ModBlockEntities() {
    }
}
