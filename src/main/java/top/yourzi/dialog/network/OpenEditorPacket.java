package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.util.EditorConfig;

/**
 * 服务器发送至客户端的空载荷数据包，用于在客户端打开 VNDialog 可视化编辑器。
 * 融合自 visual_mod_edit_vndialog 的 OpenEditorPacket，并适配 NeoForge 1.21.1。
 */
public record OpenEditorPacket() implements CustomPacketPayload {
    public static final OpenEditorPacket INSTANCE = new OpenEditorPacket();
    public static final Type<OpenEditorPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "open_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditorPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                openEditorScreen();
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void openEditorScreen() {
        Minecraft.getInstance().execute(() -> {
            EditorConfig.createDirectories();
            Minecraft.getInstance().setScreen(new VNDialogEditorScreen());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
