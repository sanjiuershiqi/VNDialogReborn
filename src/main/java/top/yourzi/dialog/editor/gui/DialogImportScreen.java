package top.yourzi.dialog.editor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.util.EditorConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对话导入屏幕：扫描资源包中所有可用的对话 JSON 文件，允许用户复制到编辑器目录。
 * 融合自 visual_mod_edit_vndialog，并适配 NeoForge 1.21.1 API。
 */
public class DialogImportScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 30;
    private static final int LIST_BOTTOM = 40;

    private final Screen parent;
    private final Consumer<String> onImportSuccess;
    private final List<ImportEntry> entries = new ArrayList<>();
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
        this.entries.clear();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        // 服务端数据位于 PackType.SERVER_DATA，但客户端资源管理器只能读取 CLIENT_RESOURCES。
        // 这里同时尝试两种方式：直接扫描客户端资源管理器（CLient Resources）以及遍历命名空间。
        try {
            Map<ResourceLocation, Resource> found = rm.listResources("dialogs", location -> location.getPath().endsWith(".json"));
            found.forEach((location, resource) -> this.entries.add(new ImportEntry(location, resource)));
        } catch (Exception e) {
            Dialog.LOGGER.warn("Failed to scan dialogs for import", e);
        }
        this.entries.sort(Comparator.comparing(e -> e.location.toString()));
        Dialog.LOGGER.info("Found {} importable dialogs", this.entries.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
        graphics.enableScissor(0, LIST_TOP, this.width, LIST_TOP + listHeight);
        int yOffset = LIST_TOP - this.scrollOffset;
        for (int i = 0; i < this.entries.size(); i++) {
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < LIST_TOP || rowY > LIST_TOP + listHeight) {
                continue;
            }
            ImportEntry entry = this.entries.get(i);
            boolean hovered = mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            int color = hovered ? -256 : -1;
            if (hovered) {
                graphics.fill(20, rowY, this.width - 20, rowY + ROW_HEIGHT, 0x44FFFFFF);
            }
            String display = entry.location.getNamespace() + ":" + entry.location.getPath().replace("dialogs/", "");
            graphics.drawString(this.font, display, 25, rowY + 2, color);
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
                if (index >= 0 && index < this.entries.size()) {
                    this.importEntry(this.entries.get(index));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) scrollY * ROW_HEIGHT);
        int maxScroll = Math.max(0, this.entries.size() * ROW_HEIGHT - (this.height - LIST_TOP - LIST_BOTTOM));
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        return true;
    }

    private void importEntry(ImportEntry entry) {
        Dialog.LOGGER.info("Importing dialog: {}", entry.location);
        try (InputStream is = entry.resource.open()) {
            if (is == null) {
                throw new IOException("Resource returned null stream for " + entry.location);
            }
            String path = entry.location.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            Path destDir = EditorConfig.DIALOG_JSON_DIR;
            Files.createDirectories(destDir);
            Path destFile = destDir.resolve(fileName);
            Files.copy(is, destFile, StandardCopyOption.REPLACE_EXISTING);
            Dialog.LOGGER.info("Copied to {}", destFile);
            this.onImportSuccess.accept(fileName);
            this.onClose();
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to import {}: {}", entry.location, e.getMessage());
            this.onImportSuccess.accept(null);
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private record ImportEntry(ResourceLocation location, Resource resource) {
    }
}
