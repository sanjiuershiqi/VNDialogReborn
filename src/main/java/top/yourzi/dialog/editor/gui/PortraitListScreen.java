package top.yourzi.dialog.editor.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.util.EditorConfig;
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
    private Button posBtn;
    private Button animBtn;
    private Button delBtn;
    private Button upBtn;
    private Button downBtn;

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
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.save"), b -> this.onClose())
                .bounds(this.width / 2 - 105, this.height - 25, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 25, 100, 20).build());
        this.posBtn = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                PortraitPosition[] vals = PortraitPosition.values();
                info.setPosition(vals[(info.getPosition().ordinal() + 1) % vals.length]);
            }
        }).bounds(0, 0, 100, 16).build());
        this.animBtn = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                PortraitAnimationType[] vals = PortraitAnimationType.values();
                info.setAnimationType(vals[(info.getAnimationType().ordinal() + 1) % vals.length]);
            }
        }).bounds(0, 0, 100, 16).build());
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

    private void releasePreviewTexture() {
        if (this.previewTex != null) {
            Minecraft.getInstance().getTextureManager().release(this.previewTex);
            this.previewTex = null;
            this.previewPath = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        int contentH = this.height - HEADER - FOOTER;
        this.renderLeftList(g, mx, my, contentH);
        this.renderMiddlePanel(g, mx, my, contentH);
        this.renderPreview(g);
        this.updateDynamicButtons();
        super.render(g, mx, my, pt);
    }

    private void updateDynamicButtons() {
        PortraitInfo info = this.getSelected();
        boolean visible = info != null;
        this.posBtn.visible = visible;
        this.animBtn.visible = visible;
        this.delBtn.visible = visible;
        this.upBtn.visible = visible && this.selectedIndex > 0;
        this.downBtn.visible = visible && this.selectedIndex < this.portraits.size() - 1;
        if (visible) {
            int line1Y = 40;
            int line2Y = 65;
            int line3Y = 90;
            this.posBtn.setX(200);
            this.posBtn.setY(line1Y);
            this.posBtn.setMessage(this.getPositionDisplay(info.getPosition()));
            this.animBtn.setX(200);
            this.animBtn.setY(line2Y);
            this.animBtn.setMessage(this.getAnimationDisplay(info.getAnimationType()));
            this.delBtn.setX(145);
            this.delBtn.setY(line3Y);
            this.upBtn.setX(260);
            this.upBtn.setY(line3Y);
            this.downBtn.setX(285);
            this.downBtn.setY(line3Y);
        }
    }

    private void renderLeftList(GuiGraphics g, int mx, int my, int contentH) {
        int x = 10;
        int y = HEADER;
        int w = LEFT_W;
        int h = contentH;
        g.fill(x, y, x + w, y + h, -1442840576);
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
            int bg = i == this.selectedIndex ? 0x66FFFFFF : (hover ? 0x33FFFFFF : 0);
            g.fill(x, rowY, x + w, rowY + ROW_H, bg);
            String name = info.getPath() != null ? info.getPath() : "";
            String trimmed = this.font.plainSubstrByWidth(name, w - 10);
            g.drawString(this.font, trimmed, x + 4, rowY + 2, 0xFFFFFF);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int sbH = Math.max(10, h * h / (this.portraits.size() * ROW_H));
            int sbY = y + (int) ((float) this.scrollOffset / (float) maxScroll * (float) (h - sbH));
            g.fill(x + w - 4, y, x + w, y + h, 0x33FFFFFF);
            g.fill(x + w - 4, sbY, x + w, sbY + sbH, -1);
        }
    }

    private void renderMiddlePanel(GuiGraphics g, int mx, int my, int contentH) {
        int x = 140;
        int y = HEADER;
        int w = 180;
        int h = contentH;
        g.fill(x, y, x + w, y + h, -1441722095);
        if (this.selectedIndex < 0 || this.selectedIndex >= this.portraits.size()) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_portrait_selected"), x + w / 2, y + 20, 0x888888);
            return;
        }
        g.drawString(this.font, Component.translatable("gui.vn_edit.position"), x + 5, 44, 0xCCCCCC);
        g.drawString(this.font, Component.translatable("gui.vn_edit.animation"), x + 5, 69, 0xCCCCCC);
    }

    private void renderPreview(GuiGraphics g) {
        int x = 330;
        int y = HEADER;
        int size = PREVIEW_SIZE;
        g.fill(x, y, x + size, y + size, -1440603614);
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
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_preview"), x + size / 2, y + size / 2 - 4, 0x888888);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) {
            return true;
        }
        int contentH = this.height - HEADER - FOOTER;
        if (isMouseInRect(mx, my, 10, HEADER, LEFT_W, contentH)) {
            int idx = ((int) my - HEADER + this.scrollOffset) / ROW_H;
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
            this.previewTex = null;
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
