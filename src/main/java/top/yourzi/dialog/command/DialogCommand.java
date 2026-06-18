package top.yourzi.dialog.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.network.DialogProtectionHeartbeatPacket;
import top.yourzi.dialog.network.NetworkHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = Dialog.MODID)
public class DialogCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("dialog")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .then(Commands.literal("show")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("dialog_id", StringArgumentType.string())
                                                        .executes(context -> showDialogWithEntity(
                                                                context,
                                                                EntityArgument.getEntity(context, "entity"),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "dialog_id")))))))
                        .then(Commands.literal("show")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> showDialog(context, StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("reload").executes(DialogCommand::reloadDialogs))
                        .then(Commands.literal("list").executes(DialogCommand::listDialogs))
        );
    }

    private static int showDialogWithEntity(CommandContext<CommandSourceStack> context, Entity speakerEntity, ServerPlayer targetPlayer, String dialogId) {
        CommandSourceStack source = context.getSource();
        DialogSequence sequence = DialogManager.getInstance().getDialogSequence(dialogId);
        if (sequence == null) {
            source.sendFailure(Component.translatable("dialog.command.show.dialog_not_found", dialogId));
            return 0;
        }

        DialogSequence playerSequence = DialogManager.getInstance().createPlayerSpecificSequence(sequence, targetPlayer, source.getServer());
        if (playerSequence == null) {
            source.sendFailure(Component.literal("Failed to create player-specific dialog for ID '" + dialogId + "'."));
            return 0;
        }

        DialogProtectionHeartbeatPacket.setActiveDialogEffect(targetPlayer, playerSequence.getEffect());
        NetworkHandler.sendShowDialogToPlayerWithEntity(targetPlayer, dialogId, DialogManager.GSON.toJson(playerSequence), speakerEntity);
        return 1;
    }

    private static int showDialog(CommandContext<CommandSourceStack> context, String dialogId) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("dialog.command.show.player_only"));
            return 0;
        }

        DialogSequence sequence = DialogManager.getInstance().getDialogSequence(dialogId);
        if (sequence == null) {
            source.sendFailure(Component.translatable("dialog.command.show.dialog_not_found", dialogId));
            return 0;
        }

        DialogSequence playerSequence = DialogManager.getInstance().createPlayerSpecificSequence(sequence, player, source.getServer());
        if (playerSequence == null) {
            source.sendFailure(Component.literal("Failed to create player-specific dialog for ID '" + dialogId + "'."));
            return 0;
        }

        DialogProtectionHeartbeatPacket.setActiveDialogEffect(player, playerSequence.getEffect());
        NetworkHandler.sendShowDialogToPlayer(player, dialogId, DialogManager.GSON.toJson(playerSequence));
        return 1;
    }

    private static int reloadDialogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DialogManager.getInstance().loadDialogsFromServer(source.getServer().getResourceManager());
        Map<String, String> allDialogJsons = DialogManager.getInstance().getAllDialogJsonsForSync();
        NetworkHandler.sendAllDialogsToAllPlayers(allDialogJsons.isEmpty() ? new HashMap<>() : allDialogJsons);
        source.sendSuccess(() -> Component.translatable("dialog.command.reload.success_server"), true);
        return 1;
    }

    private static int listDialogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<String, DialogSequence> sequences = DialogManager.getInstance().getAllDialogSequences();
        List<String> dialogIds = new ArrayList<>();
        List<String> dialogNames = new ArrayList<>();
        sequences.forEach((id, sequence) -> {
            dialogIds.add(id);
            dialogNames.add(sequence.getTitle());
        });

        source.sendSuccess(() -> Component.translatable("dialog.command.list.header"), false);
        if (source.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendDialogListToPlayer(player, dialogIds, dialogNames);
        } else {
            for (int i = 0; i < dialogIds.size(); i++) {
                int index = i;
                source.sendSuccess(() -> Component.literal("- " + dialogIds.get(index) + " (" + dialogNames.get(index) + ")"), false);
            }
        }
        return 1;
    }
}
