package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

public record SendDialogDataPacket(String dialogId, String dialogJson) implements CustomPacketPayload {
    public static final Type<SendDialogDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "send_dialog_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SendDialogDataPacket> STREAM_CODEC = StreamCodec.ofMember(SendDialogDataPacket::write, SendDialogDataPacket::new);

    private SendDialogDataPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dialogId);
        buf.writeUtf(dialogJson);
    }

    public static void handle(SendDialogDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().execute(() ->
                DialogManager.getInstance().receiveDialogData(packet.dialogId, packet.dialogJson)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
