package com.example.solarpower;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * 终极太阳能（Ultimate Solar）——工业级档位太阳能与 EU 机械设备。
 * NeoForge 1.21.1 重写版：全量 BigInteger EU 数值体系、67 档太阳能板、
 * 68 档玻璃电缆、储电盒/变压器/充电座/分子重组仪与全套材料链。
 */
@Mod(SolarPower.MODID)
public final class SolarPower {

    public static final String MODID = "solarpower";
    public static final String NAME = "Ultimate Solar";
    public static final String VERSION = "1.0.0";

    public SolarPower(IEventBus modBus) {
    }
}
