package top.yourzi.dialog.network;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;

public record DialogProtectionHeartbeatPacket() implements CustomPacketPayload {
    public static final DialogProtectionHeartbeatPacket INSTANCE = new DialogProtectionHeartbeatPacket();
    public static final Type<DialogProtectionHeartbeatPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_protection_heartbeat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogProtectionHeartbeatPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private static final String AFLING_DATAPACK_MOD_ID = "aflingdatapack";
    private static final ResourceLocation INVULNERABLE_EFFECT_ID = ResourceLocation.fromNamespaceAndPath(AFLING_DATAPACK_MOD_ID, "invulnerable");
    private static final int HEARTBEAT_EFFECT_DURATION_TICKS = 60;

    public static void handle(DialogProtectionHeartbeatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !ModList.get().isLoaded(AFLING_DATAPACK_MOD_ID)) {
                return;
            }

            BuiltInRegistries.MOB_EFFECT.getHolder(INVULNERABLE_EFFECT_ID)
                    .ifPresent(effect -> applyInvulnerableEffect(player, effect));
        });
    }

    private static void applyInvulnerableEffect(ServerPlayer player, Holder.Reference<MobEffect> effect) {
        MobEffectInstance currentEffect = player.getEffect(effect);
        if (currentEffect == null || currentEffect.getDuration() <= 40) {
            player.addEffect(new MobEffectInstance(effect, HEARTBEAT_EFFECT_DURATION_TICKS, 0, false, false, false));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
