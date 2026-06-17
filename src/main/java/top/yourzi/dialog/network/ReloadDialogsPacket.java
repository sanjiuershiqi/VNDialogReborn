package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

public record ReloadDialogsPacket() implements CustomPacketPayload {
    public static final ReloadDialogsPacket INSTANCE = new ReloadDialogsPacket();
    public static final Type<ReloadDialogsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "reload_dialogs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadDialogsPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(ReloadDialogsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().execute(() ->
                DialogManager.getInstance().loadDialogsFromServer(Minecraft.getInstance().getResourceManager())));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
