package com.autoanswer;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoAnswer implements ModInitializer {
    public static final String MOD_ID = "autoanswer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("AutoAnswer mod loaded!");
    }
}
