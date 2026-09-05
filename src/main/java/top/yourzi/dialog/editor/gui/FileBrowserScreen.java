package top.yourzi.dialog.editor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文件浏览屏幕：在指定目录下列出符合扩展名过滤的文件供选择。融合自 visual_mod_edit_vndialog。
 */
public class FileBrowserScreen extends Screen {
    private static final int ROW_HEIGHT = 12;
    private static final int LIST_Y = 30;
    private static final int LIST_BOTTOM_PAD = 40;

    private final File directory;
    private final String[] extensions;
    private final Consumer<String> onFileSelected;
    private final Screen parent;
    private final List<FileEntry> files = new ArrayList<>();
    private int scrollOffset = 0;

    private FileBrowserScreen(File directory, String[] extensions, Consumer<String> onFileSelected, Screen parent) {
        super(Component.translatable("gui.vn_edit.file_browser.title"));
        this.directory = directory;
        this.extensions = extensions;
        this.onFileSelected = onFileSelected;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.loadFiles();
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void loadFiles() {
        this.files.clear();
        File[] fileArray = this.directory.listFiles(file -> {
            if (!file.isFile()) {
                return false;
            }
            if (this.extensions == null || this.extensions.length == 0) {
                return true;
            }
            String name = file.getName().toLowerCase();
            for (String ext : this.extensions) {
                if (name.endsWith("." + ext.toLowerCase())) {
                    return true;
                }
            }
            return false;
        });
        if (fileArray != null) {
            for (File f : fileArray) {
                this.files.add(new FileEntry(f.getName(), f));
            }
            this.files.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelW = Math.min(420, this.width - 24);
        EditorTheme.drawPanelHeader(graphics, this.font, (this.width - panelW) / 2, 4, panelW, "FS", this.title);
        int listHeight = this.height - LIST_Y - LIST_BOTTOM_PAD;
        graphics.enableScissor(0, LIST_Y, this.width, LIST_Y + listHeight);
        int yOffset = LIST_Y - this.scrollOffset;
        for (int i = 0; i < this.files.size(); i++) {
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < LIST_Y || rowY > LIST_Y + listHeight) {
                continue;
            }
            boolean hovered = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(10, rowY, this.width - 10, rowY + ROW_HEIGHT, EditorTheme.BG_HOVER);
            }
            int color = hovered ? EditorTheme.ACCENT : EditorTheme.TEXT_SECONDARY;
            graphics.drawString(this.font, this.files.get(i).name, 15, rowY + 1, color);
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listHeight = this.height - LIST_Y - LIST_BOTTOM_PAD;
            if (mouseY >= LIST_Y && mouseY <= LIST_Y + listHeight) {
                int index = (int) ((mouseY - LIST_Y + this.scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < this.files.size()) {
                    this.onFileSelected.accept(this.files.get(index).name);
                    Minecraft.getInstance().setScreen(this.parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) scrollY * ROW_HEIGHT);
        int maxScroll = Math.max(0, this.files.size() * ROW_HEIGHT - (this.height - LIST_Y - LIST_BOTTOM_PAD));
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public static void open(File directory, String[] extensions, Consumer<String> onFileSelected, Screen parent) {
        Minecraft.getInstance().setScreen(new FileBrowserScreen(directory, extensions, onFileSelected, parent));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    private static class FileEntry {
        final String name;
        final File file;

        FileEntry(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }
}
