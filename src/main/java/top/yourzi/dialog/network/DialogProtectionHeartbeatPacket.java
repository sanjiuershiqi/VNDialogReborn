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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record DialogProtectionHeartbeatPacket(String effectId) implements CustomPacketPayload {
    public static final Type<DialogProtectionHeartbeatPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_protection_heartbeat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogProtectionHeartbeatPacket> STREAM_CODEC = StreamCodec.ofMember(DialogProtectionHeartbeatPacket::write, DialogProtectionHeartbeatPacket::new);

    private static final String AFLING_DATAPACK_MOD_ID = "aflingdatapack";
    private static final ResourceLocation INVULNERABLE_EFFECT_ID = ResourceLocation.fromNamespaceAndPath(AFLING_DATAPACK_MOD_ID, "invulnerable");
    private static final int HEARTBEAT_EFFECT_DURATION_TICKS = 60;
    private static final long ACTIVE_DIALOG_EFFECT_TIMEOUT_MS = 10_000L;
    private static final Map<UUID, ActiveDialogEffect> ACTIVE_DIALOG_EFFECTS = new ConcurrentHashMap<>();

    public DialogProtectionHeartbeatPacket {
        effectId = effectId == null ? "" : effectId;
    }

    private DialogProtectionHeartbeatPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(effectId);
    }

    public static void setActiveDialogEffect(ServerPlayer player, String effectId) {
        if (player == null) {
            return;
        }
        ACTIVE_DIALOG_EFFECTS.put(player.getUUID(), new ActiveDialogEffect(normalizeEffectId(effectId), System.currentTimeMillis() + ACTIVE_DIALOG_EFFECT_TIMEOUT_MS));
    }

    public static void clearActiveDialogEffect(ServerPlayer player) {
        if (player != null) {
            ACTIVE_DIALOG_EFFECTS.remove(player.getUUID());
        }
    }

    public static void handle(DialogProtectionHeartbeatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ActiveDialogEffect activeEffect = ACTIVE_DIALOG_EFFECTS.get(player.getUUID());
            long now = System.currentTimeMillis();
            if (activeEffect == null || activeEffect.expiresAtMillis < now) {
                ACTIVE_DIALOG_EFFECTS.remove(player.getUUID());
                return;
            }

            ACTIVE_DIALOG_EFFECTS.put(player.getUUID(), activeEffect.refreshed(now + ACTIVE_DIALOG_EFFECT_TIMEOUT_MS));
            ResourceLocation effectId = resolveEffectId(activeEffect.effectId);
            if (effectId == null) {
                return;
            }

            BuiltInRegistries.MOB_EFFECT.getHolder(effectId)
                    .ifPresent(effect -> applyInvulnerableEffect(player, effect));
        });
    }

    private static String normalizeEffectId(String effectId) {
        return effectId == null ? "" : effectId.trim();
    }

    private static ResourceLocation resolveEffectId(String configuredEffectId) {
        if (configuredEffectId == null || configuredEffectId.isBlank()) {
            return ModList.get().isLoaded(AFLING_DATAPACK_MOD_ID) ? INVULNERABLE_EFFECT_ID : null;
        }

        return ResourceLocation.tryParse(configuredEffectId.trim());
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

    private record ActiveDialogEffect(String effectId, long expiresAtMillis) {
        private ActiveDialogEffect refreshed(long expiresAtMillis) {
            return new ActiveDialogEffect(effectId, expiresAtMillis);
        }
    }
}
