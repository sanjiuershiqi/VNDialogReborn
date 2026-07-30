package top.yourzi.dialog.editor.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
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
import top.yourzi.dialog.editor.util.FileSystemTextureLoader;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final int STAGE_X = 330;
    private static final int MAX_CACHE_SIZE = 30;

    // 预览纹理缓存：与 AppearancePropertyPage 完全一致的机制（静态 LRU + sizeCache）。
    private static final java.util.Map<String, ResourceLocation> textureCache = new java.util.LinkedHashMap<String, ResourceLocation>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, ResourceLocation> eldest) {
            if (this.size() > MAX_CACHE_SIZE) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                sizeCache.remove(eldest.getKey());
                return true;
            }
            return false;
        }
    };
    private static final java.util.Map<String, int[]> sizeCache = new java.util.LinkedHashMap<>();

    private final List<PortraitInfo> portraits;
    private final Consumer<List<PortraitInfo>> onSave;
    private final Screen parent;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private ResourceLocation previewTex = null;
    private String previewPath = null;
    private int previewW;
    private int previewH;
    private boolean draggingPortrait = false;
    private double lastDragX = 0;
    private double lastDragY = 0;
    private DropdownWidget posDropdown;
    private DropdownWidget animDropdown;
    private EditBox sizeBox;
    private EditBox brightnessBox;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditorButton delBtn;
    private EditorButton upBtn;
    private EditorButton downBtn;
    private EditorButton folderBtn;
    private EditorButton resetOffsetBtn;
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
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.add_portrait"), b -> this.openFileBrowser())
                .bounds(10, this.height - 25, 80, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.builtin_portrait"), b -> this.openBuiltInBrowser())
                .bounds(95, this.height - 25, 60, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.save"), b -> this.onClose())
                .bounds(this.width / 2 - 105, this.height - 25, 100, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), b -> this.onClose())
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
        this.offsetXBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.offset_x")));
        this.offsetXBox.setMaxLength(10);
        this.offsetXBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setOffsetX(v);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.offsetYBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.offset_y")));
        this.offsetYBox.setMaxLength(10);
        this.offsetYBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setOffsetY(v);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.delBtn = this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.delete"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.remove(this.selectedIndex);
                if (this.selectedIndex >= this.portraits.size()) {
                    this.selectedIndex = this.portraits.size() - 1;
                }
                this.updatePreview();
            }
        }).bounds(0, 0, 60, 16).build());
        this.resetOffsetBtn = this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.reset_offset"), b -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                info.setOffsetX(0.0f);
                info.setOffsetY(0.0f);
            }
        }).bounds(0, 0, 38, 16).build());
        this.upBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\u25b2"), b -> {
            if (this.selectedIndex > 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.add(this.selectedIndex - 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex--;
            }
        }).bounds(0, 0, 20, 16).build());
        this.downBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\u25bc"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size() - 1) {
                this.portraits.add(this.selectedIndex + 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex++;
            }
        }).bounds(0, 0, 20, 16).build());
        this.folderBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\uD83D\uDCC2"), b -> EditorConfig.openFolder(EditorConfig.PORTRAITS_DIR))
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

    /** 清空当前预览引用（纹理由静态缓存管理，切换/关闭时不释放）。 */
    private void clearPreviewRef() {
        this.previewTex = null;
        this.previewPath = null;
        this.previewW = 0;
        this.previewH = 0;
    }

    /**
     * 释放所有静态缓存的预览纹理。编辑器关闭时调用。
     */
    public static void releaseTextures() {
        for (ResourceLocation rl : textureCache.values()) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
        textureCache.clear();
        sizeCache.clear();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int contentH = this.height - HEADER - FOOTER;
        this.renderLeftList(g, mx, my, contentH);
        this.renderMiddlePanel(g, mx, my, contentH);
        // 舞台背景在 widget 之前绘制（作为底层背景）
        this.renderStageBackground(g);
        this.updateDynamicButtons();
        super.render(g, mx, my, pt);
        // 立绘 blit 必须在 super.render() 之后绘制：本屏是独立 Screen，
        // 立绘 blit 若在 super.render() 之前调用会被 GuiGraphics 缓冲，随后渲染 widget 时
        // 批量 flush 会把 DynamicTexture 纹理绑定与其它 RenderType 混在一起导致不可见。
        this.renderPortraitBlit(g);
        // 下拉弹出列表最后渲染，确保不被遮挡
        this.posDropdown.renderPopup(g, mx, my, pt);
        this.animDropdown.renderPopup(g, mx, my, pt);
    }

    /** 舞台宽度（右侧预览区）。 */
    private int stageWidth() {
        return Math.max(80, this.width - STAGE_X - 10);
    }

    /**
     * 绘制舞台背景与边框（底层，在 widget 之前）。
     */
    private void renderStageBackground(GuiGraphics g) {
        int x = STAGE_X;
        int y = HEADER;
        int w = stageWidth();
        int h = this.height - HEADER - FOOTER;
        g.fill(x, y, x + w, y + h, EditorTheme.BG_SURFACE);
        g.fill(x, y, x + w, y + 1, EditorTheme.BG_ELEVATED);
        g.fill(x, y + h - 1, x + w, y + h, EditorTheme.BG_ELEVATED);
        g.fill(x, y, x + 1, y + h, EditorTheme.BG_ELEVATED);
        g.fill(x + w - 1, y, x + w, y + h, EditorTheme.BG_ELEVATED);
    }

    /**
     * 绘制立绘 blit 与提示文字（在所有 widget 之后）。
     * 立绘始终居中显示，方便拖动微调 offset。
     */
    private void renderPortraitBlit(GuiGraphics g) {
        int x = STAGE_X;
        int y = HEADER;
        int w = stageWidth();
        int h = this.height - HEADER - FOOTER;
        PortraitInfo info = this.getSelected();
        if (this.previewTex != null && info != null) {
            float size = Mth.clamp(info.getSize(), 0.1f, 5.0f);
            // 立绘高度按舞台高度 * size 缩放，最大不超过舞台高度
            int portraitH = (int) (h * 0.8f * size);
            if (portraitH > h) {
                portraitH = h;
            }
            float ratio = (this.previewW > 0 && this.previewH > 0)
                    ? (float) this.previewW / (float) this.previewH
                    : 1.0f;
            int portraitW = Math.max(1, (int) (portraitH * ratio));
            if (portraitW > w) {
                portraitW = w;
                portraitH = (int) (portraitW / ratio);
            }
            // 始终居中（水平+垂直），offset 微调以居中为基准
            int baseX = x + (w - portraitW) / 2;
            int baseY = y + (h - portraitH) / 2;
            int renderX = baseX + (int) (info.getOffsetX() * w);
            int renderY = baseY + (int) (info.getOffsetY() * h);
            // 与背景预览完全一致的渲染方式：setShaderTexture + 8 参数 float blit (width==textureWidth)
            RenderSystem.setShaderTexture(0, this.previewTex);
            g.blit(this.previewTex, renderX, renderY, 0.0f, 0.0f, portraitW, portraitH, portraitW, portraitH);
            if (this.draggingPortrait) {
                g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.drag_hint"), x + w / 2, y + 4, EditorTheme.ACCENT);
            } else {
                g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.drag_to_adjust"), x + w / 2, y + 4, EditorTheme.TEXT_MUTED);
            }
        } else if (info == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_portrait_selected"), x + w / 2, y + h / 2 - 4, EditorTheme.TEXT_MUTED);
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_preview"), x + w / 2, y + h / 2 - 4, EditorTheme.TEXT_MUTED);
        }
    }

    private void updateDynamicButtons() {
        PortraitInfo info = this.getSelected();
        boolean visible = info != null;
        this.posDropdown.visible = visible;
        this.animDropdown.visible = visible;
        this.sizeBox.visible = visible;
        this.brightnessBox.visible = visible;
        this.offsetXBox.visible = visible;
        this.offsetYBox.visible = visible;
        this.delBtn.visible = visible;
        this.upBtn.visible = visible && this.selectedIndex > 0;
        this.downBtn.visible = visible && this.selectedIndex < this.portraits.size() - 1;
        this.resetOffsetBtn.visible = visible;
        if (visible) {
            this.posDropdown.setX(200);
            this.posDropdown.setY(40);
            this.posDropdown.setSelected(this.getPositionDisplay(info.getPosition()).getString());
            this.animDropdown.setX(200);
            this.animDropdown.setY(65);
            this.animDropdown.setSelected(this.getAnimationDisplay(info.getAnimationType()).getString());
            setFloatBox(this.sizeBox, 90, info.getSize(), v -> info.setSize(Mth.clamp(v, 0.0f, 5.0f)));
            setFloatBox(this.brightnessBox, 115, info.getBrightness(), v -> info.setBrightness(Mth.clamp(v, 0.0f, 1.0f)));
            setFloatBox(this.offsetXBox, 140, info.getOffsetX(), info::setOffsetX);
            setFloatBox(this.offsetYBox, 165, info.getOffsetY(), info::setOffsetY);
            this.delBtn.setX(145);
            this.delBtn.setY(190);
            this.upBtn.setX(260);
            this.upBtn.setY(190);
            this.downBtn.setX(285);
            this.downBtn.setY(190);
            this.resetOffsetBtn.setX(282);
            this.resetOffsetBtn.setY(165);
        }
    }

    /**
     * 设置浮点输入框的值与 responder（先置 null 避免 setValue 触发回写）。
     */
    private void setFloatBox(EditBox box, int y, float value, java.util.function.Consumer<Float> setter) {
        box.setX(200);
        box.setY(y);
        box.setResponder(null);
        box.setValue(String.format(java.util.Locale.ROOT, "%.2f", value));
        box.setResponder(s -> {
            PortraitInfo si = this.getSelected();
            if (si != null && !s.isEmpty()) {
                try {
                    setter.accept(Float.parseFloat(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        });
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
        g.drawString(this.font, Component.translatable("gui.vn_edit.offset_x"), x + 5, 144, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.offset_y"), x + 5, 169, EditorTheme.TEXT_SECONDARY);
    }

    private boolean isMouseInStage(double mx, double my) {
        int x = STAGE_X;
        int y = HEADER;
        int w = stageWidth();
        int h = this.height - HEADER - FOOTER;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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
        // 在舞台内按下：开始拖动立绘调整偏移
        if (button == 0 && this.isMouseInStage(mouseX, mouseY) && this.getSelected() != null && this.previewTex != null) {
            this.draggingPortrait = true;
            this.lastDragX = mouseX;
            this.lastDragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingPortrait && button == 0) {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int stageW = Math.max(1, stageWidth());
                int stageH = Math.max(1, this.height - HEADER - FOOTER);
                info.setOffsetX(info.getOffsetX() + (float) ((mouseX - this.lastDragX) / stageW));
                info.setOffsetY(info.getOffsetY() + (float) ((mouseY - this.lastDragY) / stageH));
                this.lastDragX = mouseX;
                this.lastDragY = mouseY;
                // 同步输入框显示
                setFloatBox(this.offsetXBox, 140, info.getOffsetX(), info::setOffsetX);
                setFloatBox(this.offsetYBox, 165, info.getOffsetY(), info::setOffsetY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingPortrait && button == 0) {
            this.draggingPortrait = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
            this.clearPreviewRef();
        }
    }

    /**
     * 加载立绘预览。先尝试配置目录文件，再回退内置纹理资源。
     */
    private void loadPreview(String path) {
        if (path == null || path.isEmpty()) {
            this.clearPreviewRef();
            return;
        }
        if (path.equals(this.previewPath)) {
            return;
        }
        this.clearPreviewRef();
        this.previewPath = path;
        File file = EditorConfig.PORTRAITS_DIR.resolve(path).toFile();
        if (file.exists()) {
            this.previewTex = this.loadTexture(file, "portrait_" + path);
            if (this.previewTex != null) {
                Dialog.LOGGER.info("Portrait preview loaded: {} ({}x{})", path, this.previewW, this.previewH);
            }
            return;
        }
        // 内置纹理路径必须合法；含非法字符（如中文）时直接视为无预览
        try {
            ResourceLocation builtinLoc = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/portraits/" + path);
            if (Minecraft.getInstance().getResourceManager().getResource(builtinLoc).isPresent()) {
                this.previewTex = builtinLoc;
                this.previewW = 256;
                this.previewH = 256;
                Dialog.LOGGER.info("Portrait preview loaded from builtin: {}", path);
            } else {
                Dialog.LOGGER.warn("Portrait preview not found in config dir or builtin: {}", path);
            }
        } catch (net.minecraft.ResourceLocationException e) {
            Dialog.LOGGER.warn("Portrait preview path invalid: {}", path, e);
        }
    }

    /**
     * 加载纹理：静态缓存复用，命中时恢复尺寸，未命中时解码并注册。
     */
    private ResourceLocation loadTexture(File file, String cacheKey) {
        String safeKey = cacheKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (textureCache.containsKey(safeKey)) {
            int[] size = sizeCache.get(safeKey);
            if (size != null) {
                this.previewW = size[0];
                this.previewH = size[1];
            }
            return textureCache.get(safeKey);
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && parent.isDirectory()) {
                File finalFile = file;
                File[] matches = parent.listFiles((dir, name) -> name.equalsIgnoreCase(finalFile.getName()));
                if (matches != null && matches.length > 0) {
                    file = matches[0];
                } else {
                    Dialog.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
                    return null;
                }
            } else {
                Dialog.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
                return null;
            }
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = FileSystemTextureLoader.decodeToNativeImage(fis);
            this.previewW = image.getWidth();
            this.previewH = image.getHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            dynamicTexture.upload();
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "editor_preview/" + safeKey);
            Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
            textureCache.put(safeKey, rl);
            sizeCache.put(safeKey, new int[]{this.previewW, this.previewH});
            return rl;
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to load preview texture: {}", file, e);
            return null;
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
        this.clearPreviewRef();
        if (this.onSave != null) {
            this.onSave.accept(this.portraits);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
