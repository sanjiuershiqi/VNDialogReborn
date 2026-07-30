package top.yourzi.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.network.NetworkHandler;
import top.yourzi.dialog.ui.DialogScreen;
import top.yourzi.dialog.util.ComponentJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogManager {
    public static final Gson GSON = new GsonBuilder().create();
    private static final DialogManager INSTANCE = new DialogManager();

    private final Map<String, DialogSequence> dialogSequences = new HashMap<>();
    private final List<DialogEntry> dialogHistory = new ArrayList<>();
    private DialogSequence currentSequence;
    private DialogEntry currentEntry;
    private String currentDialogPlayerName = "";
    /** 测试用返回屏幕：编辑器测试对话时设置，对话关闭后返回编辑器界面。 */
    private Screen testReturnScreen = null;
    private static boolean isFastForwardingNext;
    private static boolean isAutoPlaying;
    private static SimpleSoundInstance currentAudioInstance;
    private static boolean audioPlaying;

    private DialogManager() {
    }

    public static DialogManager getInstance() {
        return INSTANCE;
    }

    public void loadDialogsFromServer(ResourceManager resourceManager) {
        dialogSequences.clear();
        resourceManager.listResources("dialogs", location -> location.getPath().endsWith(".json")).forEach((location, resource) -> {
            if (!Dialog.MODID.equals(location.getNamespace())) {
                return;
            }
            try {
                DialogSequence sequence = parseDialogSequenceFromFile(resource);
                if (sequence != null && sequence.getId() != null) {
                    dialogSequences.put(sequence.getId(), sequence);
                } else {
                    Dialog.LOGGER.warn("Empty dialog sequence or empty ID. {}", location);
                }
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to load dialog file {}: {}", location, e.getMessage(), e);
            }
        });
        Dialog.LOGGER.info("Loaded {} dialog sequences.", dialogSequences.size());
        loadDialogsFromConfigDir();
    }

    /**
     * 从编辑器配置目录加载对话 JSON。替代原 visual_mod_edit_vndialog 的 MixinDialogManager 功能。
     * 编辑器保存的对话文件存放在 config/vndialog_editor/dialog_json/ 目录。
     */
    private void loadDialogsFromConfigDir() {
        Path configDir = top.yourzi.dialog.editor.util.EditorConfig.DIALOG_JSON_DIR;
        if (!Files.isDirectory(configDir)) {
            return;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    DialogSequence sequence = GSON.fromJson(reader, DialogSequence.class);
                    if (sequence != null && sequence.getId() != null) {
                        dialogSequences.put(sequence.getId(), sequence);
                        count++;
                    }
                } catch (Exception e) {
                    Dialog.LOGGER.error("Failed to load dialog from config dir {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to scan config dialog dir", e);
        }
        if (count > 0) {
            Dialog.LOGGER.info("Loaded {} dialog sequences from editor config dir.", count);
        }
    }

    private DialogSequence parseDialogSequenceFromFile(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            return GSON.fromJson(reader, DialogSequence.class);
        } catch (IOException | JsonSyntaxException e) {
            Dialog.LOGGER.error("Failure to read or parse dialog JSON file ({}): {}", resource.sourcePackId(), e.getMessage());
            return null;
        }
    }

    public void clearAllDialogsOnClient() {
        dialogSequences.clear();
        currentSequence = null;
        currentEntry = null;
        clearDialogHistory();
    }

    public void receiveAllDialogsFromServer(Map<String, String> dialogDataMap) {
        clearAllDialogsOnClient();
        dialogDataMap.forEach((id, json) -> {
            try {
                DialogSequence sequence = GSON.fromJson(json, DialogSequence.class);
                if (sequence != null && sequence.getId() != null) {
                    dialogSequences.put(id, sequence);
                }
            } catch (JsonSyntaxException e) {
                Dialog.LOGGER.error("Failed to parse dialog JSON received from server. ID: {}, error: {}", id, e.getMessage());
            }
        });
    }

    public Map<String, String> getAllDialogJsonsForSync() {
        Map<String, String> dialogJsons = new HashMap<>();
        dialogSequences.forEach((id, sequence) -> dialogJsons.put(id, GSON.toJson(sequence)));
        return dialogJsons;
    }

    public DialogSequence getDialogSequence(String id) {
        DialogSequence original = dialogSequences.get(id);
        return original == null ? null : GSON.fromJson(GSON.toJson(original), DialogSequence.class);
    }

    public Map<String, DialogSequence> getAllDialogSequences() {
        return new HashMap<>(dialogSequences);
    }

    public DialogSequence createPlayerSpecificSequence(DialogSequence originalSequence, ServerPlayer player, MinecraftServer server) {
        if (originalSequence == null) {
            return null;
        }
        DialogSequence sequence = GSON.fromJson(GSON.toJson(originalSequence), DialogSequence.class);
        if (sequence == null || sequence.getEntries() == null) {
            return sequence;
        }

        CommandSourceStack source = player.createCommandSourceStack()
                .withPermission(server.getOperatorUserPermissionLevel())
                .withSuppressedOutput();
        List<DialogEntry> visibleEntries = new ArrayList<>();

        for (DialogEntry entry : sequence.getEntries()) {
            if (entry == null || !passesVisibility(entry.getVisibilityCommand(), source, server)) {
                continue;
            }

            resolveEntryComponents(entry, source, player);
            if (entry.hasOptions()) {
                List<DialogOption> visibleOptions = new ArrayList<>();
                for (DialogOption option : entry.getOptions()) {
                    if (option != null && passesVisibility(option.getVisibilityCommand(), source, server)) {
                        visibleOptions.add(option);
                    }
                }
                entry.setOptions(visibleOptions.toArray(new DialogOption[0]));
            }
            visibleEntries.add(entry);
        }

        sequence.setEntries(visibleEntries.toArray(new DialogEntry[0]));
        return sequence;
    }

    private boolean passesVisibility(String command, CommandSourceStack source, MinecraftServer server) {
        if (command == null || command.isEmpty()) {
            return true;
        }
        try {
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            return server.getCommands().getDispatcher().execute(cmd, source) > 0;
        } catch (Exception e) {
            Dialog.LOGGER.warn("Error executing visibility command '{}': {}", command, e.getMessage());
            return false;
        }
    }

    private void resolveEntryComponents(DialogEntry entry, CommandSourceStack source, ServerPlayer player) {
        try {
            if (entry.getText() != null) {
                Component resolved = ComponentUtils.updateForEntity(source, ComponentJson.fromJson(entry.getText()), player, 0);
                entry.setText(ComponentJson.toJsonTree(resolved));
            }
            if (entry.getSpeaker() != null) {
                Component resolved = ComponentUtils.updateForEntity(source, ComponentJson.fromJson(entry.getSpeaker()), player, 0);
                entry.setSpeaker(ComponentJson.toJsonTree(resolved));
            }
        } catch (Exception e) {
            Dialog.LOGGER.warn("Failed to resolve dialog component for entry '{}': {}", entry.getId(), e.getMessage());
        }
    }

    public void receiveDialogData(String dialogId, String dialogJson) {
        try {
            DialogSequence sequence = GSON.fromJson(dialogJson, DialogSequence.class);
            if (sequence != null && sequence.getId() != null) {
                dialogSequences.put(sequence.getId(), sequence);
                Minecraft.getInstance().execute(() -> showDialog(dialogId));
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to parse dialog '{}' JSON received from server", dialogId, e);
            sendPlayerMessage(Component.translatable("dialog.manager.received_parse_failed", dialogId, e.getMessage()));
        }
    }

    public void showDialog(String dialogId) {
        stopAutoPlay();
        DialogSequence sequence = getDialogSequence(dialogId);
        if (sequence == null) {
            NetworkHandler.sendRequestDialogToServer(dialogId);
            sendPlayerMessage(Component.translatable("dialog.manager.requesting_from_server", dialogId));
            return;
        }
        openSequence(sequence);
    }

    public void receiveAndShowPlayerSpecificDialog(String dialogId, String sequenceJson) {
        try {
            DialogSequence sequence = GSON.fromJson(sequenceJson, DialogSequence.class);
            if (sequence != null) {
                openSequence(sequence);
            }
        } catch (JsonSyntaxException e) {
            Dialog.LOGGER.error("Failed to parse player-specific dialog sequence JSON for ID {}: {}", dialogId, e.getMessage());
        }
    }

    public void receiveAndShowPlayerSpecificDialogWithEntity(String dialogId, String sequenceJson, int speakerEntityId) {
        try {
            DialogSequence sequence = GSON.fromJson(sequenceJson, DialogSequence.class);
            Entity speaker = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(speakerEntityId);
            if (sequence != null) {
                openSequence(sequence, speaker);
            }
        } catch (JsonSyntaxException e) {
            Dialog.LOGGER.error("Failed to parse player-specific dialog sequence JSON for ID {}: {}", dialogId, e.getMessage());
        }
    }

    private void openSequence(DialogSequence sequence) {
        openSequence(sequence, null);
    }

    private void openSequence(DialogSequence sequence, Entity speakerEntity) {
        clearDialogHistory();
        currentSequence = sequence;
        currentEntry = sequence.getFirstEntry();
        if (currentEntry == null) {
            sendPlayerMessage(Component.translatable("dialog.manager.no_entries", sequence.getId()));
            currentSequence = null;
            return;
        }
        addDialogToHistory(currentEntry);
        currentDialogPlayerName = "";
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getGameProfile() != null) {
            currentDialogPlayerName = Minecraft.getInstance().player.getGameProfile().getName();
        }
        showDialogScreen(speakerEntity);
    }

    /** 创建并显示 DialogScreen，自动注入测试返回屏幕（若有）。 */
    private void showDialogScreen(net.minecraft.world.entity.Entity speakerEntity) {
        DialogScreen screen = new DialogScreen(currentSequence, currentEntry, currentDialogPlayerName, speakerEntity);
        if (this.testReturnScreen != null) {
            screen.setReturnScreen(this.testReturnScreen);
        }
        Minecraft.getInstance().setScreen(screen);
    }

    /** 设置测试返回屏幕，对话关闭后返回此屏幕。 */
    public void setTestReturnScreen(Screen screen) {
        this.testReturnScreen = screen;
    }

    public void showNextDialog() {
        if (currentSequence == null || currentEntry == null) {
            return;
        }
        if (currentEntry.isEndDialog()) {
            closeCurrentDialog();
            return;
        }
        DialogEntry nextEntry = currentSequence.getNextEntry(currentEntry);
        if (nextEntry == null) {
            closeCurrentDialog();
            return;
        }
        currentEntry = nextEntry;
        addDialogToHistory(currentEntry);
        stopCurrentAudio();
        showDialogScreen(null);
    }

    public void jumpToDialog(String targetId) {
        if (currentSequence == null) {
            return;
        }
        DialogEntry targetEntry = currentSequence.findEntryById(targetId);
        if (targetEntry == null) {
            sendPlayerMessage(Component.translatable("dialog.manager.target_not_found", targetId));
            return;
        }
        currentEntry = targetEntry;
        addDialogToHistory(currentEntry);
        stopCurrentAudio();
        showDialogScreen(null);
    }

    private void closeCurrentDialog() {
        // 测试模式下返回编辑器界面，否则回到游戏
        if (this.testReturnScreen != null) {
            Screen returnTo = this.testReturnScreen;
            this.testReturnScreen = null;
            stopCurrentAudio();
            currentSequence = null;
            currentEntry = null;
            Minecraft.getInstance().setScreen(returnTo);
            return;
        }
        Minecraft.getInstance().setScreen(null);
        stopCurrentAudio();
        currentSequence = null;
        currentEntry = null;
    }

    public void executeCommands(Player player, List<String> commands) {
        executeCommands(player, commands, null);
    }

    public void executeCommands(Player player, List<String> commands, Entity executorEntity) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            if (command != null && !command.isEmpty()) {
                if (executorEntity == null) {
                    NetworkHandler.sendExecuteCommandToServer(command);
                } else {
                    NetworkHandler.sendExecuteCommandToServerWithEntity(command, executorEntity.getId());
                }
            }
        }
    }

    @Deprecated
    public void executeCommand(Player player, String command) {
        if (command != null && !command.isEmpty()) {
            executeCommands(player, List.of(command));
        }
    }

    public List<DialogEntry> getDialogHistory() {
        return new ArrayList<>(dialogHistory);
    }

    public void recordChoiceForCurrentDialog(String optionText) {
        if (currentEntry != null) {
            currentEntry.setSelectedOptionText(optionText);
            if (!dialogHistory.isEmpty()) {
                dialogHistory.getLast().setSelectedOptionText(optionText);
            }
        }
    }

    private void addDialogToHistory(DialogEntry entry) {
        if (entry != null) {
            dialogHistory.add(entry);
        }
    }

    private void clearDialogHistory() {
        dialogHistory.clear();
    }

    private void sendPlayerMessage(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }

    public static boolean isFastForwardingNext() {
        return isFastForwardingNext;
    }

    public static void setFastForwardingNext(boolean fastForwardingNext) {
        isFastForwardingNext = fastForwardingNext;
    }

    public static boolean isAutoPlaying() {
        return isAutoPlaying;
    }

    public static void setAutoPlaying(boolean autoPlaying) {
        isAutoPlaying = autoPlaying;
    }

    public static void stopAutoPlay() {
        isAutoPlaying = false;
    }

    public static void playDialogAudio(String audioPath) {
        try {
            stopCurrentAudio();
            // 优先检查编辑器配置目录中的音频文件（替代原 MixinDialogManagerAudio 功能）
            Path fsAudio = top.yourzi.dialog.editor.util.EditorConfig.SOUNDS_DIR.resolve(audioPath);
            if (Files.exists(fsAudio)) {
                top.yourzi.dialog.editor.util.AudioPreviewPlayer.play(fsAudio.toFile());
                audioPlaying = true;
                return;
            }
            // 回退到资源包音频
            String soundName = audioPath.replace(".ogg", "");
            ResourceLocation audioLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, soundName);
            currentAudioInstance = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(audioLocation), 1.0f, 1.0f);
            Minecraft.getInstance().getSoundManager().play(currentAudioInstance);
            audioPlaying = true;
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to play dialog audio: {}", audioPath, e);
        }
    }

    public static void stopCurrentAudio() {
        top.yourzi.dialog.editor.util.AudioPreviewPlayer.stop();
        if (currentAudioInstance != null && audioPlaying) {
            Minecraft.getInstance().getSoundManager().stop(currentAudioInstance);
            currentAudioInstance = null;
        }
        audioPlaying = false;
    }

    public static boolean isAudioPlaying() {
        return audioPlaying;
    }

    public static boolean isAudioFinished() {
        if (!audioPlaying) {
            return true;
        }
        if (currentAudioInstance != null) {
            if (!Minecraft.getInstance().getSoundManager().isActive(currentAudioInstance)) {
                audioPlaying = false;
                return true;
            }
            return false;
        }
        // AudioPreviewPlayer 播放的音频
        if (!top.yourzi.dialog.editor.util.AudioPreviewPlayer.isRunning()) {
            audioPlaying = false;
            return true;
        }
        return false;
    }
}
