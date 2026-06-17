package top.yourzi.dialog.network;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;

public record ExecuteServerCommandPacket(String command, int executorEntityId) implements CustomPacketPayload {
    public static final Type<ExecuteServerCommandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "execute_server_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteServerCommandPacket> STREAM_CODEC = StreamCodec.ofMember(ExecuteServerCommandPacket::write, ExecuteServerCommandPacket::new);

    private ExecuteServerCommandPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(command);
        buf.writeInt(executorEntityId);
    }

    public static void handle(ExecuteServerCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket received from non-server player context.");
                return;
            }

            MinecraftServer server = sender.getServer();
            if (server == null) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket handler: MinecraftServer instance is null.");
                return;
            }

            CommandSourceStack commandSource = createCommandSource(packet.executorEntityId, sender, server);
            try {
                server.getCommands().performPrefixedCommand(commandSource, packet.command);
            } catch (Exception e) {
                Dialog.LOGGER.error("Error executing command on server: {}", packet.command, e);
            }
        });
    }

    private static CommandSourceStack createCommandSource(int executorEntityId, ServerPlayer sender, MinecraftServer server) {
        if (executorEntityId == -1) {
            return sender.createCommandSourceStack()
                    .withPermission(Commands.LEVEL_GAMEMASTERS)
                    .withSuppressedOutput();
        }

        Entity executorEntity = sender.level().getEntity(executorEntityId);
        if (executorEntity == null) {
            Dialog.LOGGER.warn("ExecuteServerCommandPacket: Executor entity with ID {} not found, falling back to player.", executorEntityId);
            return sender.createCommandSourceStack()
                    .withPermission(Commands.LEVEL_GAMEMASTERS)
                    .withSuppressedOutput();
        }

        return new CommandSourceStack(
                executorEntity,
                executorEntity.position(),
                executorEntity.getRotationVector(),
                sender.serverLevel(),
                Commands.LEVEL_GAMEMASTERS,
                executorEntity.getName().getString(),
                executorEntity.getDisplayName(),
                server,
                executorEntity
        ).withSuppressedOutput();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
