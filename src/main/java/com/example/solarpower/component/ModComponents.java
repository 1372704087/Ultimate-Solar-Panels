package com.example.solarpower.component;

import com.example.solarpower.SolarPower;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 数据组件注册（1.21.1）：掉落物携带的模组自有数据。 */
public final class ModComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SolarPower.MODID);

    public static final Supplier<DataComponentType<StoredEu>> STORED_EU =
            COMPONENTS.register("stored_eu", () -> DataComponentType.<StoredEu>builder()
                    .persistent(StoredEu.CODEC)
                    .networkSynchronized(StoredEu.STREAM_CODEC)
                    .build());

    private ModComponents() {
    }
}
