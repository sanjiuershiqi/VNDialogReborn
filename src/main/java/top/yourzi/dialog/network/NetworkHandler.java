package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.yourzi.dialog.Dialog;

import java.util.List;
import java.util.Map;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1.0";

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Dialog.MODID).versioned(PROTOCOL_VERSION);

        registrar.playToClient(ShowDialogPacket.TYPE, ShowDialogPacket.STREAM_CODEC, ShowDialogPacket::handle);
        registrar.playToClient(ReloadDialogsPacket.TYPE, ReloadDialogsPacket.STREAM_CODEC, ReloadDialogsPacket::handle);
        registrar.playToClient(ListDialogsPacket.TYPE, ListDialogsPacket.STREAM_CODEC, ListDialogsPacket::handle);
        registrar.playToServer(RequestDialogPacket.TYPE, RequestDialogPacket.STREAM_CODEC, RequestDialogPacket::handle);
        registrar.playToClient(SendDialogDataPacket.TYPE, SendDialogDataPacket.STREAM_CODEC, SendDialogDataPacket::handle);
        registrar.playToClient(SyncAllDialogsPacket.TYPE, SyncAllDialogsPacket.STREAM_CODEC, SyncAllDialogsPacket::handle);
        registrar.playToServer(ExecuteServerCommandPacket.TYPE, ExecuteServerCommandPacket.STREAM_CODEC, ExecuteServerCommandPacket::handle);
        registrar.playToClient(ShowDialogWithEntityPacket.TYPE, ShowDialogWithEntityPacket.STREAM_CODEC, ShowDialogWithEntityPacket::handle);
        registrar.playToServer(DialogProtectionHeartbeatPacket.TYPE, DialogProtectionHeartbeatPacket.STREAM_CODEC, DialogProtectionHeartbeatPacket::handle);
    }

    public static void sendShowDialogToPlayer(ServerPlayer player, String dialogId, String dialogJson) {
        PacketDistributor.sendToPlayer(player, new ShowDialogPacket(dialogId, dialogJson));
    }

    public static void sendShowDialogToPlayerWithEntity(ServerPlayer player, String dialogId, String dialogJson, Entity speakerEntity) {
        PacketDistributor.sendToPlayer(player, new ShowDialogWithEntityPacket(dialogId, dialogJson, speakerEntity.getId()));
    }

    public static void sendReloadDialogsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ReloadDialogsPacket.INSTANCE);
    }

    public static void sendReloadDialogsToAll() {
        PacketDistributor.sendToAllPlayers(ReloadDialogsPacket.INSTANCE);
    }

    public static void sendDialogListToPlayer(ServerPlayer player, List<String> dialogIds, List<String> dialogNames) {
        PacketDistributor.sendToPlayer(player, new ListDialogsPacket(dialogIds, dialogNames));
    }

    public static void sendRequestDialogToServer(String dialogId) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new RequestDialogPacket(dialogId));
        } else {
            Dialog.LOGGER.warn("Cannot send RequestDialogPacket: not on client or no connection.");
        }
    }

    public static void sendDialogDataToPlayer(ServerPlayer player, String dialogId, String dialogJson) {
        PacketDistributor.sendToPlayer(player, new SendDialogDataPacket(dialogId, dialogJson));
    }

    public static void sendAllDialogsToPlayer(ServerPlayer player, Map<String, String> dialogDataMap) {
        PacketDistributor.sendToPlayer(player, new SyncAllDialogsPacket(dialogDataMap));
    }

    public static void sendAllDialogsToAllPlayers(Map<String, String> dialogDataMap) {
        PacketDistributor.sendToAllPlayers(new SyncAllDialogsPacket(dialogDataMap));
    }

    public static void sendExecuteCommandToServer(String command) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ExecuteServerCommandPacket(command, -1));
        } else {
            Dialog.LOGGER.warn("Cannot send ExecuteServerCommandPacket: not on client or no connection.");
        }
    }

    public static void sendExecuteCommandToServerWithEntity(String command, int executorEntityId) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ExecuteServerCommandPacket(command, executorEntityId));
        } else {
            Dialog.LOGGER.warn("Cannot send ExecuteServerCommandPacket with entity: not on client or no connection.");
        }
    }

    public static void sendDialogProtectionHeartbeatToServer(String effectId) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new DialogProtectionHeartbeatPacket(effectId));
        }
    }
}
