package net.permutated.advancementquests;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collection;

@Mod(AdvancementQuests.MODID)
public class AdvancementQuests {
    // Directly reference a slf4j logger
    public static final Logger logger = LogUtils.getLogger();
    public static final String MODID = "advancementquests";

    public AdvancementQuests() {
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    public void onServerStarted(ServerStartedEvent event) {
        logger.info("Loading advancements for quest creation");
        Collection<Advancement> advancements = event.getServer().getAdvancements().getAllAdvancements();

        ConversionTask task = new ConversionTask(advancements);
        logger.info("Starting quest conversion task");
        event.getServer().submit(task);
    }
}