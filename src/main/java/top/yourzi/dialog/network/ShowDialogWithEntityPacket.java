package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

public record ShowDialogWithEntityPacket(String dialogId, String dialogJson, int speakerEntityId) implements CustomPacketPayload {
    public static final Type<ShowDialogWithEntityPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "show_dialog_with_entity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowDialogWithEntityPacket> STREAM_CODEC = StreamCodec.ofMember(ShowDialogWithEntityPacket::write, ShowDialogWithEntityPacket::new);

    private ShowDialogWithEntityPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), buf.readInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dialogId);
        buf.writeUtf(dialogJson);
        buf.writeInt(speakerEntityId);
    }

    public static void handle(ShowDialogWithEntityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().execute(() ->
                DialogManager.getInstance().receiveAndShowPlayerSpecificDialogWithEntity(
                        packet.dialogId, packet.dialogJson, packet.speakerEntityId)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
