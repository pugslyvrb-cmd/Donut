package com.pugsly.donutcore;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DonutCore implements ModInitializer {
    public static final String MOD_ID = "donutcore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("DonutCore loaded for Minecraft 1.21.8");
    }
}
