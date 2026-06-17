package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

import java.util.HashMap;
import java.util.Map;

public record SyncAllDialogsPacket(Map<String, String> dialogDataMap) implements CustomPacketPayload {
    public static final Type<SyncAllDialogsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "sync_all_dialogs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAllDialogsPacket> STREAM_CODEC = StreamCodec.ofMember(SyncAllDialogsPacket::write, SyncAllDialogsPacket::new);

    private SyncAllDialogsPacket(RegistryFriendlyByteBuf buf) {
        this(readMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(dialogDataMap.size());
        dialogDataMap.forEach((id, json) -> {
            buf.writeUtf(id);
            buf.writeUtf(json);
        });
    }

    private static Map<String, String> readMap(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, String> data = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            data.put(buf.readUtf(), buf.readUtf());
        }
        return data;
    }

    public static void handle(SyncAllDialogsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().execute(() ->
                DialogManager.getInstance().receiveAllDialogsFromServer(packet.dialogDataMap)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
