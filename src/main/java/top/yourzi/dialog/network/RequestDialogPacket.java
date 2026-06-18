package top.yourzi.dialog.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.DialogSequence;

public record RequestDialogPacket(String dialogId) implements CustomPacketPayload {
    public static final Type<RequestDialogPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "request_dialog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDialogPacket> STREAM_CODEC = StreamCodec.ofMember(RequestDialogPacket::write, RequestDialogPacket::new);

    private RequestDialogPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dialogId);
    }

    public static void handle(RequestDialogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            DialogSequence sequence = DialogManager.getInstance().getDialogSequence(packet.dialogId);
            if (sequence != null) {
                DialogProtectionHeartbeatPacket.setActiveDialogEffect(sender, sequence.getEffect());
                NetworkHandler.sendDialogDataToPlayer(sender, packet.dialogId, DialogManager.GSON.toJson(sequence));
            } else {
                Dialog.LOGGER.warn("Player {} requested dialog '{}' which was not found on the server.",
                        sender.getName().getString(), packet.dialogId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
