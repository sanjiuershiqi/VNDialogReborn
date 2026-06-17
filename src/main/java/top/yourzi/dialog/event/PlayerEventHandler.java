package top.yourzi.dialog.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.network.NetworkHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Dialog.MODID)
public class PlayerEventHandler {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Map<String, String> allDialogJsons = DialogManager.getInstance().getAllDialogJsonsForSync();
            NetworkHandler.sendAllDialogsToPlayer(player, allDialogJsons.isEmpty() ? new HashMap<>() : allDialogJsons);
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                                  ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                                  Executor backgroundExecutor, Executor gameExecutor) {
                return CompletableFuture.runAsync(() ->
                        DialogManager.getInstance().loadDialogsFromServer(resourceManager), backgroundExecutor
                ).thenCompose(preparationBarrier::wait);
            }

            @Override
            public String getName() {
                return Dialog.MODID + "_dialog_reloader";
            }
        });
    }
}
