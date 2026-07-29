package top.yourzi.dialog.editor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 内置纹理浏览屏幕：列出模组资源包中自带的纹理（立绘/背景）供选择。
 * 用于让可视化编辑器选择模组内置的默认图片，而不仅限于配置目录中的文件。
 */
public class BuiltInTextureBrowserScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_Y = 30;
    private static final int LIST_BOTTOM_PAD = 40;
    private static final int PREVIEW_SIZE = 64;

    private final String texturePrefix;
    private final Consumer<String> onTextureSelected;
    private final Screen parent;
    private final List<String> textureNames = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;

    public BuiltInTextureBrowserScreen(String texturePrefix, Consumer<String> onTextureSelected, Screen parent) {
        super(Component.translatable("gui.vn_edit.builtin_texture.title"));
        this.texturePrefix = texturePrefix;
        this.onTextureSelected = onTextureSelected;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.scanTextures();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.select"), btn -> this.confirmSelection())
                .bounds(this.width / 2 - 110, this.height - 30, 50, 20).build());
    }

    private void scanTextures() {
        this.textureNames.clear();
        // listResources 不接受尾部斜杠，去掉后传入
        String listPath = this.texturePrefix.endsWith("/") ? this.texturePrefix.substring(0, this.texturePrefix.length() - 1) : this.texturePrefix;
        Map<ResourceLocation, Resource> resources = Minecraft.getInstance().getResourceManager()
                .listResources(listPath, loc -> loc.getPath().endsWith(".png"));
        for (ResourceLocation loc : resources.keySet()) {
            String path = loc.getPath();
            String relative = path.substring(this.texturePrefix.length());
            this.textureNames.add(relative);
        }
        this.textureNames.sort(String::compareToIgnoreCase);
    }

    private void confirmSelection() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.textureNames.size()) {
            this.onTextureSelected.accept(this.textureNames.get(this.selectedIndex));
        }
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int listHeight = this.height - LIST_Y - LIST_BOTTOM_PAD;
        int listWidth = this.width - PREVIEW_SIZE - 30;
        graphics.enableScissor(10, LIST_Y, 10 + listWidth, LIST_Y + listHeight);
        int yOffset = LIST_Y - this.scrollOffset;
        for (int i = 0; i < this.textureNames.size(); i++) {
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < LIST_Y || rowY > LIST_Y + listHeight) {
                continue;
            }
            boolean selected = i == this.selectedIndex;
            boolean hovered = mouseX >= 10 && mouseX <= 10 + listWidth && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            if (selected) {
                graphics.fill(10, rowY, 10 + listWidth, rowY + ROW_HEIGHT, EditorTheme.BG_SELECTED);
            } else if (hovered) {
                graphics.fill(10, rowY, 10 + listWidth, rowY + ROW_HEIGHT, EditorTheme.BG_HOVER);
            }
            int color = selected ? EditorTheme.ACCENT : (hovered ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY);
            String name = this.textureNames.get(i);
            String trimmed = this.font.plainSubstrByWidth(name, listWidth - 10);
            graphics.drawString(this.font, trimmed, 15, rowY + 2, color);
        }
        graphics.disableScissor();
        this.renderPreview(graphics, listWidth);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphics graphics, int listWidth) {
        int px = 10 + listWidth + 10;
        int py = LIST_Y;
        graphics.fill(px, py, px + PREVIEW_SIZE, py + PREVIEW_SIZE, EditorTheme.BG_SURFACE);
        if (this.selectedIndex >= 0 && this.selectedIndex < this.textureNames.size()) {
            String name = this.textureNames.get(this.selectedIndex);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("dialog", this.texturePrefix + name);
            graphics.blit(rl, px, py, 0.0f, 0.0f, PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_SIZE);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_preview"),
                    px + PREVIEW_SIZE / 2, py + PREVIEW_SIZE / 2 - 4, EditorTheme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listHeight = this.height - LIST_Y - LIST_BOTTOM_PAD;
            int listWidth = this.width - PREVIEW_SIZE - 30;
            if (mouseX >= 10 && mouseX <= 10 + listWidth && mouseY >= LIST_Y && mouseY <= LIST_Y + listHeight) {
                int index = (int) ((mouseY - LIST_Y + this.scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < this.textureNames.size()) {
                    this.selectedIndex = index;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) scrollY * ROW_HEIGHT);
        int maxScroll = Math.max(0, this.textureNames.size() * ROW_HEIGHT - (this.height - LIST_Y - LIST_BOTTOM_PAD));
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
