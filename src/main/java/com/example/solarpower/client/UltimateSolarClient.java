package com.example.solarpower.client;

import com.example.solarpower.SolarPower;
import com.example.solarpower.client.screen.ChargepadScreen;
import com.example.solarpower.client.screen.SolarPanelScreen;
import com.example.solarpower.client.screen.StorageBoxScreen;
import com.example.solarpower.client.screen.TransformerScreen;
import com.example.solarpower.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 客户端注册：界面与菜单类型绑定。 */
@EventBusSubscriber(modid = SolarPower.MODID, value = Dist.CLIENT)
public final class UltimateSolarClient {

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SOLAR_PANEL.get(), SolarPanelScreen::new);
        event.register(ModMenus.STORAGE_BOX.get(), StorageBoxScreen::new);
        event.register(ModMenus.TRANSFORMER.get(), TransformerScreen::new);
        event.register(ModMenus.CHARGEPAD.get(), ChargepadScreen::new);
    }

    private UltimateSolarClient() {
    }
}
