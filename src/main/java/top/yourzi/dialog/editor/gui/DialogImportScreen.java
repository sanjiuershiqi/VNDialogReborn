package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对话导入屏幕：从已加载的对话列表中选择并导入到编辑器目录。
 * 融合自 visual_mod_edit_vndialog，适配 NeoForge 1.21.1。
 * 原 mod 通过扫描 PackType.SERVER_DATA 导入，但客户端资源管理器无法访问 SERVER_DATA。
 * 改为从 DialogManager 已加载的对话列表获取，更可靠。
 */
public class DialogImportScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 30;
    private static final int LIST_BOTTOM = 40;

    private final Screen parent;
    private final Consumer<String> onImportSuccess;
    private final List<String> dialogIds = new ArrayList<>();
    private int scrollOffset = 0;

    public DialogImportScreen(Screen parent, Consumer<String> onImportSuccess) {
        super(Component.translatable("gui.vn_edit.import_dialog.title"));
        this.parent = parent;
        this.onImportSuccess = onImportSuccess;
    }

    @Override
    protected void init() {
        super.init();
        this.scanAvailableDialogs();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void scanAvailableDialogs() {
        this.dialogIds.clear();
        Map<String, DialogSequence> all = DialogManager.getInstance().getAllDialogSequences();
        this.dialogIds.addAll(all.keySet());
        this.dialogIds.sort(Comparator.naturalOrder());
        Dialog.LOGGER.info("Found {} importable dialogs", this.dialogIds.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
        graphics.enableScissor(0, LIST_TOP, this.width, LIST_TOP + listHeight);
        int yOffset = LIST_TOP - this.scrollOffset;
        for (int i = 0; i < this.dialogIds.size(); i++) {
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < LIST_TOP || rowY > LIST_TOP + listHeight) {
                continue;
            }
            String id = this.dialogIds.get(i);
            boolean hovered = mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            int color = hovered ? EditorTheme.ACCENT : EditorTheme.TEXT_PRIMARY;
            if (hovered) {
                graphics.fill(20, rowY, this.width - 20, rowY + ROW_HEIGHT, EditorTheme.BG_HOVER);
            }
            graphics.drawString(this.font, id, 25, rowY + 2, color);
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
            if (mouseY >= LIST_TOP && mouseY <= LIST_TOP + listHeight) {
                int index = (int) ((mouseY - LIST_TOP + this.scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < this.dialogIds.size()) {
                    this.importDialog(this.dialogIds.get(index));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) scrollY * ROW_HEIGHT);
        int maxScroll = Math.max(0, this.dialogIds.size() * ROW_HEIGHT - (this.height - LIST_TOP - LIST_BOTTOM));
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        return true;
    }

    private void importDialog(String dialogId) {
        Dialog.LOGGER.info("Importing dialog: {}", dialogId);
        try {
            DialogSequence sequence = DialogManager.getInstance().getDialogSequence(dialogId);
            if (sequence == null) {
                throw new IOException("Dialog not found: " + dialogId);
            }
            String json = DialogManager.GSON.toJson(sequence);
            Path destDir = EditorConfig.DIALOG_JSON_DIR;
            Files.createDirectories(destDir);
            String fileName = dialogId.endsWith(".json") ? dialogId : dialogId + ".json";
            Path destFile = destDir.resolve(fileName);
            Files.writeString(destFile, json);
            Dialog.LOGGER.info("Saved to {}", destFile);
            this.onImportSuccess.accept(fileName);
            this.onClose();
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to import {}: {}", dialogId, e.getMessage());
            this.onImportSuccess.accept(null);
            this.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
