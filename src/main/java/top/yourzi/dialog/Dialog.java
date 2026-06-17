package top.yourzi.dialog;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.config.ServerConfig;
import top.yourzi.dialog.network.NetworkHandler;

@Mod(Dialog.MODID)
public class Dialog {
    public static final String MODID = "dialog";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Dialog(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        modEventBus.addListener(NetworkHandler::registerPayloads);
    }
}
