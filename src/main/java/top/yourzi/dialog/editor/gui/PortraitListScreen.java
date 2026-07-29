package top.yourzi.dialog.editor.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.widget.DropdownWidget;
import top.yourzi.dialog.editor.gui.BuiltInTextureBrowserScreen;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 立绘列编辑屏幕：管理立绘路径、位置、动画类型、顺序。
 * 融合自 visual_mod_edit_vndialog，并适配 VNDialogReborn 新增的立绘动画类型（IMPACT/IMPACT_MAX/ROTATE/REVERSE/FLASH）。
 */
public class PortraitListScreen extends Screen {
    private static final int HEADER = 30;
    private static final int FOOTER = 50;
    private static final int LEFT_W = 130;
    private static final int ROW_H = 12;
    private static final int PREVIEW_SIZE = 50;

    private final List<PortraitInfo> portraits;
    private final Consumer<List<PortraitInfo>> onSave;
    private final Screen parent;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private ResourceLocation previewTex = null;
    private String previewPath = null;
    private int previewW;
    private int previewH;
    private DropdownWidget posDropdown;
    private DropdownWidget animDropdown;
    private EditBox sizeBox;
    private EditBox brightnessBox;
    private Button delBtn;
    private Button upBtn;
    private Button downBtn;
    private Button folderBtn;
    private static final List<String> POS_ITEMS = List.of(
            Component.translatable("gui.vn_edit.position.left").getString(),
            Component.translatable("gui.vn_edit.position.right").getString(),
            Component.translatable("gui.vn_edit.position.center").getString()
    );
    private static final PortraitPosition[] POS_VALUES = PortraitPosition.values();
    private static final List<String> ANIM_ITEMS = List.of(
            Component.translatable("gui.vn_edit.animation.none").getString(),
            Component.translatable("gui.vn_edit.animation.fade_in").getString(),
            Component.translatable("gui.vn_edit.animation.slide_in_from_bottom").getString(),
            Component.translatable("gui.vn_edit.animation.bounce").getString(),
            Component.translatable("gui.vn_edit.animation.impact").getString(),
            Component.translatable("gui.vn_edit.animation.impact_max").getString(),
            Component.translatable("gui.vn_edit.animation.rotate").getString(),
            Component.translatable("gui.vn_edit.animation.reverse").getString(),
            Component.translatable("gui.vn_edit.animation.flash").getString()
    );
    private static final PortraitAnimationType[] ANIM_VALUES = PortraitAnimationType.values();

    public PortraitListScreen(List<PortraitInfo> portraits, Consumer<List<PortraitInfo>> onSave, Screen parent) {
        super(Component.translatable("gui.vn_edit.portrait_list.title"));
        this.portraits = new ArrayList<>(portraits);
        this.onSave = onSave;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.add_portrait"), b -> this.openFileBrowser())
                .bounds(10, this.height - 25, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.builtin_portrait"), b -> this.openBuiltInBrowser())
                .bounds(95, this.height - 25, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.save"), b -> this.onClose())
                .bounds(this.width / 2 - 105, this.height - 25, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 25, 100, 20).build());
        this.posDropdown = this.addRenderableWidget(new DropdownWidget(this.font, 0, 0, 100, 16, new ArrayList<>(POS_ITEMS), selected -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int idx = POS_ITEMS.indexOf(selected);
                if (idx >= 0 && idx < POS_VALUES.length) {
                    info.setPosition(POS_VALUES[idx]);
                }
            }
        }));
        this.animDropdown = this.addRenderableWidget(new DropdownWidget(this.font, 0, 0, 100, 16, new ArrayList<>(ANIM_ITEMS), selected -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int idx = ANIM_ITEMS.indexOf(selected);
                if (idx >= 0 && idx < ANIM_VALUES.length) {
                    info.setAnimationType(ANIM_VALUES[idx]);
                }
            }
        }));
        this.sizeBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.size")));
        this.sizeBox.setMaxLength(10);
        this.sizeBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setSize(Math.max(0.0f, Math.min(5.0f, v)));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.brightnessBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.brightness")));
        this.brightnessBox.setMaxLength(10);
        this.brightnessBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setBrightness(Math.max(0.0f, Math.min(1.0f, v)));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.delBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.delete"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.remove(this.selectedIndex);
                if (this.selectedIndex >= this.portraits.size()) {
                    this.selectedIndex = this.portraits.size() - 1;
                }
                this.updatePreview();
            }
        }).bounds(0, 0, 60, 16).build());
        this.upBtn = this.addRenderableWidget(Button.builder(Component.literal("\u25b2"), b -> {
            if (this.selectedIndex > 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.add(this.selectedIndex - 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex--;
            }
        }).bounds(0, 0, 20, 16).build());
        this.downBtn = this.addRenderableWidget(Button.builder(Component.literal("\u25bc"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size() - 1) {
                this.portraits.add(this.selectedIndex + 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex++;
            }
        }).bounds(0, 0, 20, 16).build());
        this.folderBtn = this.addRenderableWidget(Button.builder(Component.literal("\uD83D\uDCC2"), b -> EditorConfig.openFolder(EditorConfig.PORTRAITS_DIR))
                .bounds(this.width - 30, 25, 25, 16).build());
        this.updateDynamicButtons();
    }

    private PortraitInfo getSelected() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size()) {
            return this.portraits.get(this.selectedIndex);
        }
        return null;
    }

    private void openFileBrowser() {
        FileBrowserScreen.open(EditorConfig.PORTRAITS_DIR.toFile(), new String[]{"png", "jpg", "jpeg"}, path -> {
            String lower = path.toLowerCase(Locale.ROOT);
            boolean exists = this.portraits.stream().anyMatch(p -> p.getPath() != null && p.getPath().equalsIgnoreCase(lower));
            if (!exists) {
                this.portraits.add(new PortraitInfo(lower, PortraitPosition.RIGHT, 1.0f, PortraitAnimationType.NONE));
                this.selectedIndex = this.portraits.size() - 1;
                this.updatePreview();
            }
        }, this);
    }

    private void openBuiltInBrowser() {
        Minecraft.getInstance().setScreen(new BuiltInTextureBrowserScreen("textures/portraits/", path -> {
            String lower = path.toLowerCase(Locale.ROOT);
            boolean exists = this.portraits.stream().anyMatch(p -> p.getPath() != null && p.getPath().equalsIgnoreCase(lower));
            if (!exists) {
                this.portraits.add(new PortraitInfo(lower, PortraitPosition.RIGHT, 1.0f, PortraitAnimationType.NONE));
                this.selectedIndex = this.portraits.size() - 1;
                this.updatePreview();
            }
        }, Minecraft.getInstance().screen));
    }

    private void releasePreviewTexture() {
        if (this.previewTex != null) {
            // 不释放内置纹理（由资源管理器管理），仅释放编辑器动态注册的预览纹理
            if (!this.previewTex.getPath().startsWith("textures/portraits/")) {
                Minecraft.getInstance().getTextureManager().release(this.previewTex);
            }
            this.previewTex = null;
            this.previewPath = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int contentH = this.height - HEADER - FOOTER;
        this.renderLeftList(g, mx, my, contentH);
        this.renderMiddlePanel(g, mx, my, contentH);
        this.renderPreview(g);
        this.updateDynamicButtons();
        super.render(g, mx, my, pt);
        // 在所有控件之后渲染展开的下拉弹出列表，确保不被遮挡
        this.posDropdown.renderPopup(g, mx, my, pt);
        this.animDropdown.renderPopup(g, mx, my, pt);
    }

    private void updateDynamicButtons() {
        PortraitInfo info = this.getSelected();
        boolean visible = info != null;
        this.posDropdown.visible = visible;
        this.animDropdown.visible = visible;
        this.sizeBox.visible = visible;
        this.brightnessBox.visible = visible;
        this.delBtn.visible = visible;
        this.upBtn.visible = visible && this.selectedIndex > 0;
        this.downBtn.visible = visible && this.selectedIndex < this.portraits.size() - 1;
        if (visible) {
            int line1Y = 40;
            int line2Y = 65;
            int line3Y = 90;
            int line4Y = 115;
            int line5Y = 140;
            this.posDropdown.setX(200);
            this.posDropdown.setY(line1Y);
            this.posDropdown.setSelected(this.getPositionDisplay(info.getPosition()).getString());
            this.animDropdown.setX(200);
            this.animDropdown.setY(line2Y);
            this.animDropdown.setSelected(this.getAnimationDisplay(info.getAnimationType()).getString());
            this.sizeBox.setX(200);
            this.sizeBox.setY(line3Y);
            this.sizeBox.setResponder(null);
            this.sizeBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getSize()));
            this.sizeBox.setResponder(s -> {
                PortraitInfo si = this.getSelected();
                if (si != null && !s.isEmpty()) {
                    try {
                        float v = Float.parseFloat(s.trim());
                        si.setSize(Math.max(0.0f, Math.min(5.0f, v)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.brightnessBox.setX(200);
            this.brightnessBox.setY(line4Y);
            this.brightnessBox.setResponder(null);
            this.brightnessBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getBrightness()));
            this.brightnessBox.setResponder(s -> {
                PortraitInfo si = this.getSelected();
                if (si != null && !s.isEmpty()) {
                    try {
                        float v = Float.parseFloat(s.trim());
                        si.setBrightness(Math.max(0.0f, Math.min(1.0f, v)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.delBtn.setX(145);
            this.delBtn.setY(line5Y);
            this.upBtn.setX(260);
            this.upBtn.setY(line5Y);
            this.downBtn.setX(285);
            this.downBtn.setY(line5Y);
        }
    }

    private void renderLeftList(GuiGraphics g, int mx, int my, int contentH) {
        int x = 10;
        int y = HEADER;
        int w = LEFT_W;
        int h = contentH;
        g.fill(x, y, x + w, y + h, EditorTheme.BG_SURFACE);
        g.enableScissor(x, y, x + w, y + h);
        int maxScroll = Math.max(0, this.portraits.size() * ROW_H - h);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);
        int curY = y - this.scrollOffset;
        for (int i = 0; i < this.portraits.size(); i++) {
            PortraitInfo info = this.portraits.get(i);
            int rowY = curY + i * ROW_H;
            if (rowY + ROW_H < y || rowY > y + h) {
                continue;
            }
            boolean hover = isMouseInRect(mx, my, x, rowY, w, ROW_H);
            int bg = i == this.selectedIndex ? EditorTheme.BG_SELECTED : (hover ? EditorTheme.BG_HOVER : 0);
            g.fill(x, rowY, x + w, rowY + ROW_H, bg);
            String name = info.getPath() != null ? info.getPath() : "";
            String trimmed = this.font.plainSubstrByWidth(name, w - 10);
            g.drawString(this.font, trimmed, x + 4, rowY + 2, EditorTheme.TEXT_PRIMARY);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int sbH = Math.max(10, h * h / (this.portraits.size() * ROW_H));
            int sbY = y + (int) ((float) this.scrollOffset / (float) maxScroll * (float) (h - sbH));
            g.fill(x + w - 4, y, x + w, y + h, 0x33FFFFFF);
            g.fill(x + w - 4, sbY, x + w, sbY + sbH, EditorTheme.TEXT_MUTED);
        }
    }

    private void renderMiddlePanel(GuiGraphics g, int mx, int my, int contentH) {
        int x = 140;
        int y = HEADER;
        int w = 180;
        int h = contentH;
        g.fill(x, y, x + w, y + h, EditorTheme.BG_ELEVATED);
        if (this.selectedIndex < 0 || this.selectedIndex >= this.portraits.size()) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_portrait_selected"), x + w / 2, y + 20, EditorTheme.TEXT_MUTED);
            return;
        }
        g.drawString(this.font, Component.translatable("gui.vn_edit.position"), x + 5, 44, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.animation"), x + 5, 69, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.size"), x + 5, 94, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.brightness"), x + 5, 119, EditorTheme.TEXT_SECONDARY);
    }

    private void renderPreview(GuiGraphics g) {
        int x = 330;
        int y = HEADER;
        int size = PREVIEW_SIZE;
        g.fill(x, y, x + size, y + size, EditorTheme.BG_SURFACE);
        if (this.previewTex != null && this.getSelected() != null) {
            float ratio = this.previewW > 0 && this.previewH > 0 ? (float) this.previewW / (float) this.previewH : 1.0f;
            int dw = size;
            int dh = size;
            if (ratio > 1.0f) {
                dh = (int) ((float) size / ratio);
            } else {
                dw = (int) ((float) size * ratio);
            }
            int dx = x + (size - dw) / 2;
            int dy = y + (size - dh) / 2;
            RenderSystem.setShaderTexture(0, this.previewTex);
            g.blit(this.previewTex, dx, dy, 0.0f, 0.0f, dw, dh, dw, dh);
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_preview"), x + size / 2, y + size / 2 - 4, EditorTheme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 如果下拉框已展开，优先处理
        if (this.posDropdown.isExpanded()) {
            if (this.posDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.posDropdown.close();
            return true;
        }
        if (this.animDropdown.isExpanded()) {
            if (this.animDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.animDropdown.close();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int contentH = this.height - HEADER - FOOTER;
        if (isMouseInRect(mouseX, mouseY, 10, HEADER, LEFT_W, contentH)) {
            int idx = ((int) mouseY - HEADER + this.scrollOffset) / ROW_H;
            if (idx >= 0 && idx < this.portraits.size()) {
                this.selectedIndex = idx;
                this.updatePreview();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isMouseInRect(mx, my, 10, HEADER, LEFT_W, this.height - HEADER - FOOTER)) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY * ROW_H, 0, Integer.MAX_VALUE);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void updatePreview() {
        PortraitInfo info = this.getSelected();
        if (info != null) {
            this.loadPreview(info.getPath());
        } else {
            this.previewTex = null;
            this.previewPath = null;
        }
    }

    private void loadPreview(String path) {
        if (path == null) {
            return;
        }
        if (path.equals(this.previewPath)) {
            return;
        }
        this.releasePreviewTexture();
        this.previewPath = path;
        File f = EditorConfig.PORTRAITS_DIR.resolve(path).toFile();
        if (!f.exists()) {
            // 配置目录没有该文件，检查是否为模组内置纹理
            ResourceLocation builtinLoc = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/portraits/" + path);
            try {
                java.io.InputStream stream = Minecraft.getInstance().getResourceManager()
                        .getResource(builtinLoc).orElseThrow().open();
                NativeImage img = NativeImage.read(stream);
                this.previewW = img.getWidth();
                this.previewH = img.getHeight();
                img.close();
                stream.close();
                this.previewTex = builtinLoc;
            } catch (Exception e) {
                this.previewTex = null;
            }
            return;
        }
        try (FileInputStream fis = new FileInputStream(f)) {
            NativeImage img = NativeImage.read(fis);
            this.previewW = img.getWidth();
            this.previewH = img.getHeight();
            DynamicTexture dyn = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "editor_preview/portrait_" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(loc, dyn);
            this.previewTex = loc;
        } catch (IOException e) {
            Dialog.LOGGER.warn("Failed to load portrait preview: {}", path, e);
            this.previewTex = null;
        }
    }

    private static boolean isMouseInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private Component getPositionDisplay(PortraitPosition pos) {
        return switch (pos) {
            case LEFT -> Component.translatable("gui.vn_edit.position.left");
            case RIGHT -> Component.translatable("gui.vn_edit.position.right");
            case CENTER -> Component.translatable("gui.vn_edit.position.center");
        };
    }

    /**
     * 适配 VNDialogReborn 新增的所有立绘动画类型。
     */
    private Component getAnimationDisplay(PortraitAnimationType anim) {
        return switch (anim) {
            case NONE -> Component.translatable("gui.vn_edit.animation.none");
            case FADE_IN -> Component.translatable("gui.vn_edit.animation.fade_in");
            case SLIDE_IN_FROM_BOTTOM -> Component.translatable("gui.vn_edit.animation.slide_in_from_bottom");
            case BOUNCE -> Component.translatable("gui.vn_edit.animation.bounce");
            case IMPACT -> Component.translatable("gui.vn_edit.animation.impact");
            case IMPACT_MAX -> Component.translatable("gui.vn_edit.animation.impact_max");
            case ROTATE -> Component.translatable("gui.vn_edit.animation.rotate");
            case REVERSE -> Component.translatable("gui.vn_edit.animation.reverse");
            case FLASH -> Component.translatable("gui.vn_edit.animation.flash");
        };
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public void onClose() {
        this.releasePreviewTexture();
        if (this.onSave != null) {
            this.onSave.accept(this.portraits);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
