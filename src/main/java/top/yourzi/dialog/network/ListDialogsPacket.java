package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;

import java.util.ArrayList;
import java.util.List;

public record ListDialogsPacket(List<String> dialogIds, List<String> dialogNames) implements CustomPacketPayload {
    public static final Type<ListDialogsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "list_dialogs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ListDialogsPacket> STREAM_CODEC = StreamCodec.ofMember(ListDialogsPacket::write, ListDialogsPacket::new);

    private ListDialogsPacket(RegistryFriendlyByteBuf buf) {
        this(readStrings(buf), readStrings(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        writeStrings(buf, dialogIds);
        writeStrings(buf, dialogNames);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
        buf.writeInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    public static void handle(ListDialogsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player == null) {
                return;
            }

            int size = Math.min(packet.dialogIds.size(), packet.dialogNames.size());
            for (int i = 0; i < size; i++) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                        "   - " + packet.dialogIds.get(i) + " (" + packet.dialogNames.get(i) + ")"));
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
