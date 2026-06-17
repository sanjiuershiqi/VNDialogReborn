package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

public record ShowDialogPacket(String dialogId, String dialogJson) implements CustomPacketPayload {
    public static final Type<ShowDialogPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "show_dialog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowDialogPacket> STREAM_CODEC = StreamCodec.ofMember(ShowDialogPacket::write, ShowDialogPacket::new);

    private ShowDialogPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dialogId);
        buf.writeUtf(dialogJson);
    }

    public static void handle(ShowDialogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().execute(() -> {
            if (packet.dialogJson != null && !packet.dialogJson.isEmpty()) {
                DialogManager.getInstance().receiveAndShowPlayerSpecificDialog(packet.dialogId, packet.dialogJson);
            } else {
                Dialog.LOGGER.warn("ShowDialogPacket received for id '{}' but dialogJson is empty.", packet.dialogId);
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
